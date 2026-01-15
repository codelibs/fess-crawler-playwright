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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Optional;

import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;

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

    private String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }
}
