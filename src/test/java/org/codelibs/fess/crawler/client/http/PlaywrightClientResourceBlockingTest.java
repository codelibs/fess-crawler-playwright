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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
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
 * Tests for declining to fetch the resource types a crawl does not read.
 */
public class PlaywrightClientResourceBlockingTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    /** A 1x1 GIF, so the browser has a real image to request. */
    private static final byte[] PIXEL_GIF = { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00, 0x00,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x21, (byte) 0xf9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2c, 0x00,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3b };

    private static final String PAGE = "<html><head><link rel=\"stylesheet\" href=\"/style.css\"></head>"
            + "<body>blocked resource page<img src=\"/pixel.gif\"></body></html>";

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
     * Starts a server that records which paths were actually requested.
     */
    private static Server startServer(final int port, final Set<String> requestedPaths) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                final String path = request.getHttpURI().getPath();
                requestedPaths.add(path);
                response.setStatus(200);
                if ("/pixel.gif".equals(path)) {
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE, "image/gif");
                    response.write(true, ByteBuffer.wrap(PIXEL_GIF), callback);
                    return true;
                }
                if ("/style.css".equals(path)) {
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/css");
                    Content.Sink.write(response, true, "body{color:#000}", callback);
                    return true;
                }
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, PAGE, callback);
                return true;
            }
        });
        server.start();
        return server;
    }

    /**
     * With nothing configured, everything the page references is still fetched - interception costs a
     * round trip per request, so it must not be switched on by default.
     */
    @Test
    @Timeout(60)
    public void test_notConfigured_fetchesEverything() throws Exception {
        final int port = 7630;
        final Set<String> requestedPaths = ConcurrentHashMap.newKeySet();
        final Server server = startServer(port, requestedPaths);
        final PlaywrightClient client = newClient(new HashMap<>());
        try {
            client.init();
            final ResponseData responseData =
                    client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
            assertTrue(new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet())
                    .contains("blocked resource page"));

            assertTrue(requestedPaths.contains("/pixel.gif"));
            assertTrue(requestedPaths.contains("/style.css"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * A blocked resource type never reaches the server, while the rest of the page - including the
     * document itself and the types that were not blocked - is unaffected.
     */
    @Test
    @Timeout(60)
    public void test_blockedTypesAreNotFetched() throws Exception {
        final int port = 7631;
        final Set<String> requestedPaths = ConcurrentHashMap.newKeySet();
        final Server server = startServer(port, requestedPaths);
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("blockedResourceTypes", "image, font ,media");
        final PlaywrightClient client = newClient(paramMap);
        try {
            client.init();
            final ResponseData responseData =
                    client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());

            assertEquals(200, responseData.getHttpStatusCode());
            assertTrue(new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet())
                    .contains("blocked resource page"));

            assertFalse(requestedPaths.contains("/pixel.gif"));
            // Not blocked, so still fetched.
            assertTrue(requestedPaths.contains("/style.css"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * The configured list is parsed leniently: whitespace and case must not silently disable blocking.
     */
    @Test
    @Timeout(30)
    public void test_getBlockedResourceTypes() {
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("blockedResourceTypes", " Image , MEDIA ,, font ");
        final PlaywrightClient client = newClient(paramMap);
        assertEquals(Set.of("image", "media", "font"), client.getBlockedResourceTypes());

        assertTrue(newClient(new HashMap<>()).getBlockedResourceTypes().isEmpty());

        final Map<String, Object> blankMap = new HashMap<>();
        blankMap.put("blockedResourceTypes", "   ");
        assertTrue(newClient(blankMap).getBlockedResourceTypes().isEmpty());
    }
}
