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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.RequestData;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
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
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;

/**
 * Tests for how {@link PlaywrightClient} turns what the browser reports into a {@link ResponseData},
 * and for how it tells a navigation that was aborted for a download apart from one that simply failed.
 */
public class PlaywrightClientResponseTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    /**
     * A payload with no magic bytes any detector recognises, so the MIME type can only come from the
     * filename. That is what makes {@link #test_execute_redirectToDownload} able to tell whether the
     * name came from the download's {@code Content-Disposition} or from the (extension-less) URL.
     */
    private static final byte[] OPAQUE_BYTES = { (byte) 0xa7, (byte) 0x3c, (byte) 0x91, (byte) 0x08, (byte) 0xd5, (byte) 0x2e, (byte) 0x60,
            (byte) 0xbb, (byte) 0x14, (byte) 0xf9, (byte) 0x4d, (byte) 0x82, (byte) 0x37, (byte) 0xc1, (byte) 0x0a, (byte) 0x6e };

    private static final String LAST_MODIFIED = "Wed, 21 Oct 2015 07:28:00 GMT";

    private static PlaywrightClient newClient() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        client.setCloseTimeout(5);
        return client;
    }

    private static Server startServer(final int port, final Handler handler) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(handler);
        server.start();
        return server;
    }

    /**
     * A download reached through a redirect must be described by the response that actually carried the
     * file, not by the redirect that pointed at it.
     *
     * <p>Every hop of a redirect emits its own response event, so a handler that keeps the first one
     * keeps the 3xx: it has no content-type and no last-modified, and its URL is the pre-redirect one.
     * The MIME type likewise has to come from the name the browser derived from
     * {@code Content-Disposition}, because the download URL here carries no extension at all.</p>
     */
    @Test
    @Timeout(60)
    public void test_execute_redirectToDownload() throws Exception {
        final int port = 7600;
        final Server server = startServer(port, new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                if ("/redirect-download".equals(request.getHttpURI().getPath())) {
                    response.setStatus(302);
                    response.getHeaders().put(HttpHeader.LOCATION, "/dl?id=1");
                    Content.Sink.write(response, true, "", callback);
                    return true;
                }
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/octet-stream");
                response.getHeaders().put(HttpHeader.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"");
                response.getHeaders().put(HttpHeader.LAST_MODIFIED, LAST_MODIFIED);
                response.write(true, ByteBuffer.wrap(OPAQUE_BYTES), callback);
                return true;
            }
        });
        final PlaywrightClient client = newClient();
        try {
            client.setDownloadTimeout(10);
            client.init();

            final String url = "http://[::1]:" + port + "/redirect-download";
            final ResponseData responseData = client.execute(RequestDataBuilder.newRequestData().get().url(url).build());

            // The redirect hop would report 302, no last-modified, and the pre-redirect URL.
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("http://[::1]:" + port + "/dl?id=1", responseData.getUrl());
            assertNotNull(responseData.getLastModified());
            // "/dl?id=1" has no extension, so this can only come from the suggested filename.
            assertEquals("application/pdf", responseData.getMimeType());
            assertEquals((long) OPAQUE_BYTES.length, responseData.getContentLength());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * A 400 response is an error page, so its body must not be stored as content - the same treatment
     * every other 4xx and 5xx status already gets.
     */
    @Test
    @Timeout(60)
    public void test_execute_status400() throws Exception {
        final int port = 7602;
        final Server server = startServer(port, new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                response.setStatus(400);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, "<html><body>bad request</body></html>", callback);
                return true;
            }
        });
        final PlaywrightClient client = newClient();
        try {
            client.setDownloadTimeout(10);
            client.init();

            final String url = "http://[::1]:" + port + "/";
            final ResponseData responseData = client.execute(RequestDataBuilder.newRequestData().get().url(url).build());

            assertEquals(400, responseData.getHttpStatusCode());
            assertEquals(0L, responseData.getContentLength());
            assertEquals("text/html", responseData.getMimeType());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * When a download really was detected as starting but never completes, the failure still has to say
     * how long it waited - that is the only clue that {@code downloadTimeout} is the knob to turn.
     */
    @Test
    @Timeout(60)
    public void test_waitForDownloadOrFail_reportsTheTimeout() throws Exception {
        final int port = 7603;
        final Server server = startServer(port, new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, "<html><body>page</body></html>", callback);
                return true;
            }
        });
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        client.setCloseTimeout(5);
        try {
            client.setDownloadTimeout(1);
            client.init();
            client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());

            final RequestData request = RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/never").build();
            final Page page = client.worker.getValue4();
            final long start = System.currentTimeMillis();
            try {
                // Nothing will ever populate these references, so the wait has to run out.
                client.waitForDownloadOrFail(page, request, new AtomicReference<>(), new AtomicReference<Download>(),
                        new IllegalStateException("Download is starting"));
                fail();
            } catch (final CrawlingAccessException e) {
                assertTrue(e.getMessage().contains("Timeout: 1s"));
                assertTrue(e.getMessage().contains(request.getUrl()));
            }
            final long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed >= 1000L);
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * A {@code downloadTimeout} of zero means "do not wait", so the failure has to come back
     * immediately - it must never turn into a wait with no end.
     *
     * <p>Playwright reads a timeout of exactly {@code 0} as "no timeout at all", so handing the
     * configured value straight to {@link Page#waitForCondition} makes a zero block forever on a
     * download that never arrives. That wait is held inside the page's monitor, so it takes the crawl
     * and {@link PlaywrightClient#close()} down with it, and neither can be interrupted.</p>
     *
     * <p>Asserted twice on purpose: the {@link Timeout} keeps a regression from hanging the build, and
     * the elapsed-time check keeps the test honest about "immediately" rather than merely "eventually".</p>
     */
    @Test
    @Timeout(30)
    public void test_waitForDownloadOrFail_zeroTimeoutFailsFast() throws Exception {
        final int port = 7604;
        final Server server = startServer(port, new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, "<html><body>page</body></html>", callback);
                return true;
            }
        });
        final PlaywrightClient client = newClient();
        try {
            client.setDownloadTimeout(0);
            client.init();
            client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());

            final RequestData request = RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/never").build();
            final Page page = client.worker.getValue4();
            final long start = System.currentTimeMillis();
            try {
                // Nothing will ever populate these references, and nothing is allowed to wait for them.
                client.waitForDownloadOrFail(page, request, new AtomicReference<>(), new AtomicReference<Download>(),
                        new IllegalStateException("Download is starting"));
                fail();
            } catch (final CrawlingAccessException e) {
                assertTrue(e.getMessage().contains("Timeout: 0s"));
                assertTrue(e.getMessage().contains(request.getUrl()));
            }
            final long elapsed = System.currentTimeMillis() - start;
            log("waitForDownloadOrFail returned after " + elapsed + "ms with downloadTimeout=0");
            assertTrue(elapsed < 5000L);
        } finally {
            client.close();
            server.stop();
        }
    }
}
