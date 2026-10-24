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

import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.codelibs.fess.crawler.CrawlerContext;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.ChildUrlsException;
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlingParameterUtil;
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
 * Tests a page that navigates itself - {@code location.replace} or {@code <meta http-equiv="refresh">}.
 *
 * <p>{@code Page.navigate()} returns the response of the document the navigation started with. Once that
 * document has moved on, the body of that response is gone, so reading it used to fail the whole page
 * and the page the browser ended up on was never crawled.</p>
 */
public class PlaywrightClientClientSideRedirectTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    private static final String SOURCE = "/source.html";

    private static final String TARGET = "/target.html";

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Server startServer(final int port, final String sourceHtml) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                final String body = SOURCE.equals(request.getHttpURI().getPath()) ? sourceHtml
                        : "<html><head><title>target</title></head><body><p>TARGET-CONTENT</p></body></html>";
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, body, callback);
                return true;
            }
        });
        server.start();
        return server;
    }

    private static PlaywrightClient newClient() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        client.setDownloadTimeout(10);
        client.setCloseTimeout(5);
        return client;
    }

    private static String bodyOf(final ResponseData responseData) throws Exception {
        try (final InputStream in = responseData.getResponseBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertCrawlsTarget(final String sourceHtml) throws Exception {
        final int port = freePort();
        final Server server = startServer(port, sourceHtml);
        final PlaywrightClient client = newClient();
        try {
            client.init();
            final ResponseData responseData =
                    client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + SOURCE).build());
            assertEquals("http://[::1]:" + port + TARGET, responseData.getUrl());
            assertEquals(200, responseData.getHttpStatusCode());
            final String body = bodyOf(responseData);
            assertTrue(body.contains("TARGET-CONTENT"));
            assertFalse(body.contains("SOURCE-CONTENT"));
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    @Timeout(120)
    public void test_locationReplace_crawlsTheTarget() throws Exception {
        assertCrawlsTarget("<html><body><p>SOURCE-CONTENT</p><script>location.replace('" + TARGET + "');</script></body></html>");
    }

    @Test
    @Timeout(120)
    public void test_metaRefresh_crawlsTheTarget() throws Exception {
        assertCrawlsTarget("<html><head><meta http-equiv=\"refresh\" content=\"0;url=" + TARGET
                + "\"></head><body><p>SOURCE-CONTENT</p></body></html>");
    }

    /** The target goes through the same {@link UrlFilter} check as a redirect the server sends. */
    @Test
    @Timeout(120)
    public void test_locationReplace_toNonTargetUrl_childUrlsException() throws Exception {
        final int port = freePort();
        final Server server = startServer(port, "<html><body><script>location.replace('" + TARGET + "');</script></body></html>");
        final PlaywrightClient client = newClient();
        final CrawlerContext crawlerContext = new CrawlerContext();
        crawlerContext.setUrlFilter(new UrlFilter() {
            @Override
            public void init(final String sessionId) {
            }

            @Override
            public boolean match(final String url) {
                return false;
            }

            @Override
            public void addInclude(final String urlPattern) {
            }

            @Override
            public void addExclude(final String urlPattern) {
            }

            @Override
            public void processUrl(final String url) {
            }

            @Override
            public void clear() {
            }
        });
        try {
            client.init();
            CrawlingParameterUtil.setCrawlerContext(crawlerContext);
            try {
                client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + SOURCE).build());
                fail();
            } catch (final ChildUrlsException e) {
                // Expected: the self-navigation target is outside the crawl, as for a server redirect.
            }
        } finally {
            CrawlingParameterUtil.setCrawlerContext(null);
            client.close();
            server.stop();
        }
    }
}
