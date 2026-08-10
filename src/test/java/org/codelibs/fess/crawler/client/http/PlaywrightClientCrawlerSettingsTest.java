/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.crawler.client.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.exception.MaxLengthExceededException;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.dbflute.utflute.core.PlainTestCase;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.microsoft.playwright.BrowserType;

/**
 * Tests that the crawler settings Fess passes down actually reach the browser. Each of these was read
 * from the init parameters (or not read at all) and then dropped, so the configuration silently had no
 * effect on a Playwright crawl.
 */
public class PlaywrightClientCrawlerSettingsTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    private static PlaywrightClient newClient(final Map<String, Object> paramMap) {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        client.setInitParameterMap(paramMap);
        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        client.setDownloadTimeout(5);
        client.setCloseTimeout(10);
        return client;
    }

    /**
     * Starts a server that echoes one request header back as the page body, so the test can see what
     * the browser actually sent.
     */
    private static Server startEchoServer(final int port, final String headerName) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                final String value = request.getHeaders().get(headerName);
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, "<html><body>" + (value == null ? "" : value) + "</body></html>", callback);
                return true;
            }
        });
        server.start();
        return server;
    }

    private static String bodyOf(final ResponseData responseData) throws Exception {
        return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
    }

    /**
     * The configured user agent must be sent. Without it the browser announces itself as
     * HeadlessChrome, which is not what the crawl is configured to send and is routinely blocked.
     */
    @Test
    @Timeout(60)
    public void test_userAgent() throws Exception {
        final int port = 7620;
        final Server server = startEchoServer(port, "User-Agent");
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(HcHttpClient.USER_AGENT_PROPERTY, "TestCrawler/1.0 (+http://example.com/bot)");
        final PlaywrightClient client = newClient(paramMap);
        try {
            client.init();
            final ResponseData responseData =
                    client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
            assertTrue(bodyOf(responseData).contains("TestCrawler/1.0 (+http://example.com/bot)"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * Configured request headers must be sent. Fess always supplies this parameter for a web crawl.
     */
    @Test
    @Timeout(60)
    public void test_requestHeaders() throws Exception {
        final int port = 7621;
        final Server server = startEchoServer(port, "X-Test-Header");
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(HcHttpClient.REQUEST_HEADERS_PROPERTY, new RequestHeader[] { new RequestHeader("X-Test-Header", "header-value") });
        final PlaywrightClient client = newClient(paramMap);
        try {
            client.init();
            final ResponseData responseData =
                    client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
            assertTrue(bodyOf(responseData).contains("header-value"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * Repeated header names are legal in HTTP but Playwright takes a plain map, so they have to be
     * joined into the equivalent comma-separated value rather than silently losing one.
     */
    @Test
    @Timeout(60)
    public void test_requestHeaders_repeatedName() throws Exception {
        final int port = 7622;
        final Server server = startEchoServer(port, "X-Test-Header");
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(HcHttpClient.REQUEST_HEADERS_PROPERTY,
                new RequestHeader[] { new RequestHeader("X-Test-Header", "first"), new RequestHeader("X-Test-Header", "second") });
        final PlaywrightClient client = newClient(paramMap);
        try {
            client.init();
            final ResponseData responseData =
                    client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
            final String body = bodyOf(responseData);
            assertTrue(body.contains("first"));
            assertTrue(body.contains("second"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * maxContentLength was read by AbstractCrawlerClient.init() but never enforced here, so this client
     * indexed content the rest of the crawler would have rejected.
     */
    @Test
    @Timeout(60)
    public void test_maxContentLength() throws Exception {
        final int port = 7623;
        final StringBuilder body = new StringBuilder("<html><body>");
        while (body.length() < 4096) {
            body.append("0123456789");
        }
        body.append("</body></html>");
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, body.toString(), callback);
                return true;
            }
        });
        server.start();

        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("maxContentLength", 1024L);
        final PlaywrightClient client = newClient(paramMap);
        try {
            client.init();
            try {
                client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
                fail();
            } catch (final MaxLengthExceededException e) {
                assertTrue(e.getMessage().contains("1024"));
            }
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * The configured connection timeout must bound the navigation. Without it the page keeps
     * Playwright's own 30 second default, so a crawl configured to give up sooner did not.
     */
    @Test
    @Timeout(60)
    public void test_connectionTimeout() throws Exception {
        final int port = 7624;
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                // Never respond, so only the navigation timeout can end the request.
                return true;
            }
        });
        server.start();

        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(HcHttpClient.CONNECTION_TIMEOUT_PROPERTY, 3000);
        final PlaywrightClient client = newClient(paramMap);
        try {
            client.init();
            final long start = System.currentTimeMillis();
            try {
                client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
                fail();
            } catch (final CrawlingAccessException e) {
                assertTrue(e.getMessage().contains("Failed to access"));
            }
            final long elapsed = System.currentTimeMillis() - start;
            // Comfortably under Playwright's own 30s default, which is what applied before.
            assertTrue(elapsed < 15000L);
        } finally {
            client.close();
            server.stop();
        }
    }
}
