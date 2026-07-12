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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Optional;

import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.core.misc.Tuple4;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;

/**
 * Test class for PlaywrightClient edge cases and error handling.
 *
 * @author shinsuke
 */
public class PlaywrightClientEdgeCaseTest extends PlainTestCase {

    private static final boolean HEADLESS = true;
    private static final int SERVER_PORT = 7090;

    private static PlaywrightClient sharedClient;
    private static CrawlerWebServer sharedServer;
    private static File docRootDir;

    @BeforeAll
    static void setUpClass() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        sharedClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        sharedClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        sharedClient.setCloseTimeout(5);
        sharedClient.init();

        docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        sharedServer = new CrawlerWebServer(SERVER_PORT, docRootDir);
        sharedServer.start();
    }

    @AfterAll
    static void tearDownClass() {
        if (sharedServer != null) {
            sharedServer.stop();
        }
        if (sharedClient != null) {
            sharedClient.close();
        }
    }

    /**
     * Test for getFilename with various edge cases.
     */
    @Test
    public void test_getFilename_edgeCases() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();

        // Basic filename
        assertEquals("test.html", playwrightClient.getFilename("test.html"));

        // URL with path
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html"));
        assertEquals("test.html", playwrightClient.getFilename("http://host/path/to/test.html"));

        // URL with query string
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html?123=abc"));
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html?foo=bar&baz=qux"));

        // URL with fragment
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html#xyz"));
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html?123=abc#xyz"));

        // Root path
        assertEquals("index.html", playwrightClient.getFilename("http://host/"));
        assertEquals("index.html", playwrightClient.getFilename("http://host/?123=abc"));
        assertEquals("index.html", playwrightClient.getFilename("http://host/?123=abc#xyz"));
        assertEquals("index.html", playwrightClient.getFilename("http://host/#xyz"));

        // Null and empty
        assertNull(playwrightClient.getFilename(null));
        assertNull(playwrightClient.getFilename(""));

        // Complex filenames
        assertEquals("file-name.test.html", playwrightClient.getFilename("http://host/file-name.test.html"));
        assertEquals("file_name.html", playwrightClient.getFilename("http://host/file_name.html"));
        assertEquals("ファイル.html", playwrightClient.getFilename("http://host/ファイル.html"));

        // Edge cases with trailing slash
        assertEquals("index.html", playwrightClient.getFilename("http://host/path/"));

        // Multiple fragments and query params
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html?a=1&b=2&c=3#section1"));
    }

    /**
     * Test for parseDate with various formats.
     */
    @Test
    public void test_parseDate_edgeCases() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();

        // Valid date
        final Date validDate = playwrightClient.parseDate("Sun, 22 Jan 2023 02:16:34 GMT");
        assertNotNull(validDate);
        assertEquals(1674353794000L, validDate.getTime());

        // Another valid date
        final Date validDate2 = playwrightClient.parseDate("Mon, 01 Jan 2024 00:00:00 GMT");
        assertNotNull(validDate2);

        // Null and empty
        assertNull(playwrightClient.parseDate(null));
        assertNull(playwrightClient.parseDate(""));

        // Invalid format
        assertNull(playwrightClient.parseDate("invalid date"));
        assertNull(playwrightClient.parseDate("2023-01-22 02:16:34"));
        assertNull(playwrightClient.parseDate("22/01/2023"));
    }

    /**
     * Test for accessing invalid URL.
     */
    @Test
    public void test_execute_invalidUrl() {
        // Create separate client with short timeout for this test
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient timeoutClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        try {
            timeoutClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS).setTimeout(5000));
            timeoutClient.setDownloadTimeout(3); // 3 seconds instead of default 15
            timeoutClient.setCloseTimeout(3);
            timeoutClient.init();

            // Try to access an invalid URL
            final String url = "http://invalid-domain-that-does-not-exist-12345.com/";
            timeoutClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            fail();
        } catch (final CrawlingAccessException e) {
            // Expected exception
            assertTrue(e.getMessage().contains("Failed to access"));
        } finally {
            timeoutClient.close();
        }
    }

    /**
     * Test for accessing URL with timeout.
     */
    @Test
    public void test_execute_timeout() {
        // Create separate client with short timeout for this test
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient timeoutClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        try {
            timeoutClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS).setTimeout(5000));
            timeoutClient.setDownloadTimeout(3); // 3 seconds instead of default 15
            timeoutClient.setCloseTimeout(3);
            timeoutClient.init();

            // Try to access a non-responding server
            final String url = "http://[::1]:19999/";
            timeoutClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            fail();
        } catch (final CrawlingAccessException e) {
            // Expected exception
            assertTrue(e.getMessage().contains("Failed to access"));
        } finally {
            timeoutClient.close();
        }
    }

    /**
     * Test for various HTTP status codes.
     */
    @Test
    public void test_execute_variousStatusCodes() {
        // 200 OK
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertTrue(responseData.getContentLength() > 0);
        }

        // 404 Not Found
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/notfound.html";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(404, responseData.getHttpStatusCode());
            assertEquals(0L, responseData.getContentLength());
            assertEquals("", getBodyAsString(responseData));
        }
    }

    /**
     * Test for HEAD request with various content types.
     */
    @Test
    public void test_execute_headRequest() {
        // HEAD request for HTML
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().head().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("HEAD", responseData.getMethod());
            assertNull(responseData.getResponseBody());
        }

        // HEAD request for PDF
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/test.pdf";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().head().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("HEAD", responseData.getMethod());
            assertEquals("application/pdf", responseData.getMimeType());
        }

        // HEAD request for image
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/test.png";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().head().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("HEAD", responseData.getMethod());
            assertEquals("image/png", responseData.getMimeType());
        }
    }

    /**
     * Test for concurrent requests (sequential execution due to page lock).
     */
    @Test
    public void test_execute_concurrentRequests() {
        // Execute multiple requests sequentially
        for (int i = 0; i < 3; i++) {
            final String url = "http://[::1]:" + SERVER_PORT + "/";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
        }
    }

    /**
     * Test for response with special characters in content.
     */
    @Test
    public void test_execute_specialCharacters() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.txt";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("text/plain", responseData.getMimeType());
        assertNotNull(responseData.getResponseBody());
    }

    /**
     * Test for response with empty body.
     */
    @Test
    public void test_execute_emptyBody() {
        // 404 response has empty body
        final String url = "http://[::1]:" + SERVER_PORT + "/notfound.html";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(404, responseData.getHttpStatusCode());
        assertEquals(0L, responseData.getContentLength());
        assertNotNull(responseData.getResponseBody());
        assertEquals("", getBodyAsString(responseData));
    }

    /**
     * Test for multiple file types in sequence.
     */
    @Test
    public void test_execute_multipleFileTypes() {
        // HTML
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("text/html", responseData.getMimeType());
        }

        // Text
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/test.txt";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("text/plain", responseData.getMimeType());
        }

        // JSON
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/test.json";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("application/json", responseData.getMimeType());
        }

        // Image
        {
            final String url = "http://[::1]:" + SERVER_PORT + "/test.png";
            final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("image/png", responseData.getMimeType());
        }
    }

    /**
     * Test for lastModified date handling.
     */
    @Test
    public void test_execute_lastModified() {
        final String url = "http://[::1]:" + SERVER_PORT + "/";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());

        // Verify lastModified is set if available
        if (responseData.getLastModified() != null) {
            assertTrue(responseData.getLastModified().getTime() > 0);
        }
    }

    // ==================== LoadState timeout handling tests ====================

    /**
     * Test that a page which loads successfully but never reaches the configured LoadState
     * (e.g. NETWORKIDLE never firing because of a lingering same-origin network request) still
     * returns the successfully-loaded content instead of being misclassified as a failed/absent
     * download and discarded via CrawlingAccessException.
     */
    @Test
    @Timeout(30)
    public void test_execute_neverReachesLoadState_returnsLoadedContent() throws Exception {
        final int neverIdlePort = 7091;
        final Server neverIdleServer = new Server();
        final ServerConnector connector = new ServerConnector(neverIdleServer);
        connector.setPort(neverIdlePort);
        neverIdleServer.addConnector(connector);
        neverIdleServer.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                final String path = request.getHttpURI().getPath();
                if ("/hang".equals(path)) {
                    // Bounded "hang": long enough to outlast the short page timeout configured
                    // below, short enough that server.stop() never has to wait for long.
                    Thread.sleep(2000L);
                    response.setStatus(200);
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=UTF-8");
                    Content.Sink.write(response, true, "done", callback);
                    return true;
                }
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                // A same-origin fetch() (unlike an <img>/<script src>) is invisible to the page's
                // "load" event, so navigate() itself returns promptly; but the pending connection
                // keeps the network non-idle, so waitForLoadState(NETWORKIDLE) never succeeds.
                Content.Sink.write(response, true,
                        "<html><body>Never Idle Page<script>fetch('/hang').catch(function(e){});</script></body></html>", callback);
                return true;
            }
        });
        neverIdleServer.start();

        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient neverIdleClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }

            @Override
            protected Tuple4<Playwright, Browser, BrowserContext, Page> createPlaywrightWorker() {
                final Tuple4<Playwright, Browser, BrowserContext, Page> tuple = super.createPlaywrightWorker();
                // Test-only: shorten the default timeout so waitForLoadState(NETWORKIDLE) times out
                // in ~0.5s instead of Playwright's real default (~30s), without adding a production
                // configuration knob. navigate() itself easily completes well under this on localhost.
                tuple.getValue4().setDefaultTimeout(500);
                return tuple;
            }
        };

        try {
            neverIdleClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            neverIdleClient.setCloseTimeout(5);
            neverIdleClient.init();

            final String url = "http://[::1]:" + neverIdlePort + "/";
            final ResponseData responseData = neverIdleClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("text/html", responseData.getMimeType());
            assertNotNull(responseData.getResponseBody());
            assertTrue(getBodyAsString(responseData).contains("Never Idle Page"));
        } finally {
            neverIdleClient.close();
            neverIdleServer.stop();
        }
    }

    /**
     * Test that a non-timeout {@link PlaywrightException} thrown while waiting for LoadState (e.g. the
     * page/browser having crashed or been closed mid-wait) is NOT swallowed as "just a LoadState
     * timeout, content is fine" the way a genuine {@link com.microsoft.playwright.TimeoutError} is. It
     * must propagate as a real failure instead of returning fabricated "successfully loaded" content
     * built from a page that is no longer in a trustworthy state.
     */
    @Test
    @Timeout(30)
    public void test_execute_nonTimeoutPlaywrightExceptionDuringLoadState_propagates() throws Exception {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient crashingClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }

            @Override
            protected void waitForLoadState(final Page page, final LoadState state) {
                // Simulate a non-timeout PlaywrightException (e.g. Playwright's own
                // "Target page, context or browser has been closed") firing during the LoadState
                // wait, after navigate() itself already succeeded.
                throw new PlaywrightException("Target page, context or browser has been closed");
            }
        };

        try {
            crashingClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            crashingClient.setCloseTimeout(5);
            crashingClient.init();

            final String url = "http://[::1]:" + SERVER_PORT + "/";
            try {
                crashingClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                // Expected the PlaywrightException to propagate instead of being treated as a LoadState timeout
                fail();
            } catch (final PlaywrightException e) {
                assertTrue(e.getMessage().contains("Target page, context or browser has been closed"));
            }
        } finally {
            crashingClient.close();
        }
    }

    /**
     * Test the grace-poll fix: when the LoadState wait times out with NO download detected yet at
     * that instant, but a download fires shortly afterwards (within the short grace window), the
     * download must be detected and handled as a file download instead of the already-loaded HTML
     * being served. Before the fix, execute() fell straight through to returning the loaded HTML the
     * moment the timeout was observed, silently missing the download.
     */
    @Test
    @Timeout(30)
    public void test_execute_loadStateTimeout_lateDownloadWithinGracePeriod_handledAsDownload() throws Exception {
        final int port = 7092;
        final Server server = newTwoEndpointServer(port, "GRACE-HTML-PAGE", "GRACE-POLL-DOWNLOAD-BODY");
        server.start();

        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }

            @Override
            protected void waitForLoadState(final Page page, final LoadState state) {
                // Schedule a download to fire shortly (well within the grace window) but AFTER this
                // method throws. No Playwright call runs between evaluate() returning and the throw,
                // so the download event cannot be delivered until execute()'s grace-poll pumps the
                // event loop - guaranteeing downloadRef is still null at the instant of the timeout,
                // and therefore that the grace-poll branch (not the already-detected branch) is
                // exercised.
                // Fire the download via a same-origin <a download> click deferred to the next event-
                // loop tick (setTimeout 0). Deferring guarantees the download event cannot be delivered
                // to the Java handler until execute()'s grace-poll pumps the loop - so downloadRef is
                // still null at the instant of the timeout and the grace-poll branch (not the already-
                // detected branch) is exercised. An <a download> click (rather than a top-level
                // navigation to the download URL) fires the download promptly and reliably.
                page.evaluate("setTimeout(() => { const a = document.createElement('a'); a.href = '/download.bin';"
                        + " a.download = 'download.bin'; document.body.appendChild(a); a.click(); }, 0)");
                throw new TimeoutError("simulated LoadState timeout with a download arriving shortly after");
            }
        };

        try {
            client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client.setDownloadTimeout(5);
            client.setCloseTimeout(5);
            client.init();

            final String url = "http://[::1]:" + port + "/";
            final ResponseData responseData = client.execute(RequestDataBuilder.newRequestData().get().url(url).build());

            assertEquals(200, responseData.getHttpStatusCode());
            assertTrue(responseData.getContentLength() > 0);
            final String body = getBodyAsString(responseData);
            // The late download was caught and its body served ...
            assertTrue(body.contains("GRACE-POLL-DOWNLOAD-BODY"));
            // ... instead of the already-loaded HTML page content.
            assertFalse(body.contains("GRACE-HTML-PAGE"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * Test the preserved already-detected branch: when the LoadState wait times out AND a download
     * has ALREADY been detected (downloadRef is non-null) at the instant of the timeout, the request
     * is routed to the download-handling path. This branch had no dedicated test before.
     */
    @Test
    @Timeout(30)
    public void test_execute_loadStateTimeout_downloadAlreadyDetected_handledAsDownload() throws Exception {
        final int port = 7093;
        final Server server = newTwoEndpointServer(port, "DETECTED-HTML-PAGE", "ALREADY-DETECTED-DOWNLOAD-BODY");
        server.start();

        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }

            @Override
            protected void waitForLoadState(final Page page, final LoadState state) {
                // Trigger a download (via a reliable same-origin <a download> click) and block until it
                // is observed, so the page's onDownload handler (registered in execute() before this
                // call) has already populated downloadRef by the time the simulated LoadState timeout is
                // thrown - exercising the already-detected branch rather than the grace-poll branch.
                page.waitForDownload(() -> page.evaluate("(() => { const a = document.createElement('a');"
                        + " a.href = '/download.bin'; a.download = 'download.bin'; document.body.appendChild(a); a.click(); })()"));
                throw new TimeoutError("simulated LoadState timeout with a download already in progress");
            }
        };

        try {
            client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client.setDownloadTimeout(5);
            client.setCloseTimeout(5);
            client.init();

            final String url = "http://[::1]:" + port + "/";
            final ResponseData responseData = client.execute(RequestDataBuilder.newRequestData().get().url(url).build());

            assertEquals(200, responseData.getHttpStatusCode());
            assertTrue(responseData.getContentLength() > 0);
            final String body = getBodyAsString(responseData);
            assertTrue(body.contains("ALREADY-DETECTED-DOWNLOAD-BODY"));
            assertFalse(body.contains("DETECTED-HTML-PAGE"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * Builds a Jetty server exposing two endpoints on {@code port}: {@code /download.bin} returns a
     * non-renderable {@code application/octet-stream} body (which chromium turns into a download) and
     * any other path returns a small HTML page. The two distinct marker strings let a test tell which
     * of the two was ultimately served in the response body.
     *
     * @param port The port to listen on.
     * @param htmlMarker A marker string embedded in the HTML page body.
     * @param downloadMarker The exact body returned for {@code /download.bin}.
     * @return An unstarted {@link Server}; the caller must {@code start()} and {@code stop()} it.
     */
    private Server newTwoEndpointServer(final int port, final String htmlMarker, final String downloadMarker) {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                final String path = request.getHttpURI().getPath();
                if ("/download.bin".equals(path)) {
                    response.setStatus(200);
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/octet-stream");
                    Content.Sink.write(response, true, downloadMarker, callback);
                    return true;
                }
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, "<html><body>" + htmlMarker + "</body></html>", callback);
                return true;
            }
        });
        return server;
    }

    private String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }
}
