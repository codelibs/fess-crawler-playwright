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

import java.util.Optional;

import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
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

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;

/**
 * Tests for how {@link PlaywrightClient} manages the browser resources it owns: the pages a crawled
 * document leaves behind, and the teardown of the worker itself.
 */
public class PlaywrightClientLifecycleTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    private static PlaywrightClient newClient() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        client.setDownloadTimeout(5);
        client.setCloseTimeout(10);
        return client;
    }

    private static Server startServer(final int port, final String body) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, body, callback);
                return true;
            }
        });
        server.start();
        return server;
    }

    /**
     * Pages a crawled document opens for itself must not outlive the request.
     *
     * <p>A {@code window.open()} creates a real page in the same context that nothing else closes -
     * resetting to {@code about:blank} only touches the page the client drives - so over a long crawl
     * they accumulate, each holding a renderer.</p>
     */
    @Test
    @Timeout(60)
    public void test_execute_closesPagesOpenedByTheDocument() throws Exception {
        final int port = 7610;
        final Server server = startServer(port,
                "<html><body>popup host<script>" + "window.open('about:blank');window.open('about:blank');</script></body></html>");
        final PlaywrightClient client = newClient();
        try {
            client.init();
            final BrowserContext context = client.worker.getValue3();

            client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());

            // Only the page the client drives is left.
            assertEquals(1, context.pages().size());
            assertTrue(client.worker.getValue4() == context.pages().get(0));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * A worker whose page is gone must say so, instead of failing with whatever opaque error the next
     * Playwright call happens to raise.
     */
    @Test
    @Timeout(60)
    public void test_execute_unusableWorkerIsReported() throws Exception {
        final int port = 7611;
        final Server server = startServer(port, "<html><body>page</body></html>");
        final PlaywrightClient client = newClient();
        try {
            client.init();
            // Simulate what a renderer crash leaves behind: the page is gone but the client is not closed.
            client.worker.getValue4().close();

            try {
                client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
                fail();
            } catch (final CrawlerSystemException e) {
                assertTrue(e.getMessage().contains("no longer usable"));
            }
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * Teardown must actually reach the browser. The components are closed in sequence on a single
     * thread - Playwright's connection is not safe to call from two threads at once - so a step that
     * failed or was skipped would leave the browser and driver processes running.
     */
    @Test
    @Timeout(60)
    public void test_close_disconnectsTheBrowser() throws Exception {
        final PlaywrightClient client = newClient();
        client.init();
        final Browser browser = client.worker.getValue2();
        assertTrue(browser.isConnected());

        client.close();

        assertFalse(browser.isConnected());
    }
}
