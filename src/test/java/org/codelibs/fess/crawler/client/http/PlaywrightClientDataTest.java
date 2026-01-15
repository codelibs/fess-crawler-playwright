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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Optional;

import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;

/**
 * Test class for PlaywrightClient data handling scenarios.
 * Tests for various response types, encodings, and content handling.
 */
public class PlaywrightClientDataTest extends PlainTestCase {

    private static final boolean HEADLESS = true;
    private static final int SERVER_PORT = 7130;

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

    // ==================== HTML content tests ====================

    /**
     * Test HTML response with UTF-8 charset.
     */
    @Test
    public void test_htmlResponse_utf8() {
        final String url = "http://[::1]:" + SERVER_PORT + "/";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("text/html", responseData.getMimeType());
        assertEquals("UTF-8", responseData.getCharSet());

        final String body = getBodyAsString(responseData);
        assertNotNull(body);
        assertTrue(body.contains("content page"));
    }

    /**
     * Test HTML response extracts rendered content.
     */
    @Test
    public void test_htmlResponse_renderedContent() {
        final String url = "http://[::1]:" + SERVER_PORT + "/";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("text/html", responseData.getMimeType());

        // Response body should contain HTML
        final String body = getBodyAsString(responseData);
        assertTrue(body.contains("<html") || body.contains("<HTML"));
    }

    // ==================== Text content tests ====================

    /**
     * Test plain text response.
     */
    @Test
    public void test_textResponse() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.txt";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("text/plain", responseData.getMimeType());

        final String body = getBodyAsString(responseData);
        assertEquals("This is a test document.", body.trim());
    }

    // ==================== JSON content tests ====================

    /**
     * Test JSON response.
     */
    @Test
    public void test_jsonResponse() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.json";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("application/json", responseData.getMimeType());

        final String body = getBodyAsString(responseData);
        assertTrue(body.contains("message"));
    }

    // ==================== Binary content tests ====================

    /**
     * Test PDF response (binary).
     */
    @Test
    public void test_pdfResponse() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.pdf";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("application/pdf", responseData.getMimeType());
        assertTrue(responseData.getContentLength() > 0);
    }

    /**
     * Test PNG image response (binary).
     */
    @Test
    public void test_pngResponse() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.png";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("image/png", responseData.getMimeType());
        assertTrue(responseData.getContentLength() > 0);
    }

    /**
     * Test JPEG image response (binary).
     */
    @Test
    public void test_jpgResponse() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.jpg";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("image/jpeg", responseData.getMimeType());
        assertTrue(responseData.getContentLength() > 0);
    }

    /**
     * Test GIF image response (binary).
     */
    @Test
    public void test_gifResponse() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.gif";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("image/gif", responseData.getMimeType());
        assertTrue(responseData.getContentLength() > 0);
    }

    // ==================== Download content tests ====================

    /**
     * Test ZIP file download.
     */
    @Test
    public void test_zipDownload() {
        final String url = "http://[::1]:" + SERVER_PORT + "/download.zip";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("application/zip", responseData.getMimeType());
        assertTrue(responseData.getContentLength() > 0);
    }

    /**
     * Test DOCX file download.
     */
    @Test
    public void test_docxDownload() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.docx";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", responseData.getMimeType());
        assertTrue(responseData.getContentLength() > 0);
    }

    /**
     * Test EPUB file download.
     */
    @Test
    public void test_epubDownload() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.epub";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("application/epub+zip", responseData.getMimeType());
        assertTrue(responseData.getContentLength() > 0);
    }

    // ==================== Metadata tests ====================

    /**
     * Test response headers are captured in metadata.
     * Uses dedicated client to avoid parallel execution issues.
     */
    @Test
    public void test_responseMetadata() {
        // Create dedicated client for this test to avoid parallel execution issues
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient dedicatedClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        try {
            dedicatedClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            dedicatedClient.setCloseTimeout(5);
            dedicatedClient.init();

            final String url = "http://[::1]:" + SERVER_PORT + "/";
            final ResponseData responseData = dedicatedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

            assertEquals(200, responseData.getHttpStatusCode());

            // Verify metadata map contains headers
            assertNotNull(responseData.getMetaDataMap());
            assertTrue(responseData.getMetaDataMap().size() > 0);
            assertTrue(responseData.getMetaDataMap().containsKey("content-type"));
        } finally {
            dedicatedClient.close();
        }
    }

    /**
     * Test last-modified header is parsed.
     */
    @Test
    public void test_lastModifiedHeader() {
        final String url = "http://[::1]:" + SERVER_PORT + "/";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());

        // Last-modified may or may not be present depending on server
        // If present, it should be a valid date
        if (responseData.getLastModified() != null) {
            assertTrue(responseData.getLastModified().getTime() > 0);
        }
    }

    // ==================== Content length tests ====================

    /**
     * Test content length is correctly reported.
     */
    @Test
    public void test_contentLength() {
        // Test text file - known content
        final String url = "http://[::1]:" + SERVER_PORT + "/test.txt";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals(25L, responseData.getContentLength());
    }

    /**
     * Test content length for HTML response.
     */
    @Test
    public void test_contentLength_html() {
        final String url = "http://[::1]:" + SERVER_PORT + "/";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertTrue(responseData.getContentLength() > 0);
    }

    // ==================== Response body tests ====================

    /**
     * Test response body is accessible as InputStream.
     */
    @Test
    public void test_responseBody_asInputStream() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.txt";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());

        final InputStream body = responseData.getResponseBody();
        assertNotNull(body);

        final byte[] bytes = InputStreamUtil.getBytes(body);
        assertEquals(25, bytes.length);
    }

    // ==================== HEAD request tests ====================

    /**
     * Test HEAD request returns no body.
     */
    @Test
    public void test_headRequest_noBody() {
        final String url = "http://[::1]:" + SERVER_PORT + "/";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().head().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("HEAD", responseData.getMethod());
        assertNull(responseData.getResponseBody());
    }

    /**
     * Test HEAD request for various file types.
     */
    @Test
    public void test_headRequest_variousFileTypes() {
        // HTML
        ResponseData responseData =
                sharedClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:" + SERVER_PORT + "/").build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("text/html", responseData.getMimeType());
        assertNull(responseData.getResponseBody());

        // Text
        responseData =
                sharedClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:" + SERVER_PORT + "/test.txt").build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("text/plain", responseData.getMimeType());

        // PDF
        responseData =
                sharedClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:" + SERVER_PORT + "/test.pdf").build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("application/pdf", responseData.getMimeType());

        // PNG
        responseData =
                sharedClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:" + SERVER_PORT + "/test.png").build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("image/png", responseData.getMimeType());
    }

    // ==================== Error response tests ====================

    /**
     * Test 404 response has empty body.
     */
    @Test
    public void test_404Response_emptyBody() {
        final String url = "http://[::1]:" + SERVER_PORT + "/nonexistent.html";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(404, responseData.getHttpStatusCode());
        assertEquals(0L, responseData.getContentLength());
        assertNotNull(responseData.getResponseBody());

        final String body = getBodyAsString(responseData);
        assertEquals("", body);
    }

    // ==================== URL handling tests ====================

    /**
     * Test URL is correctly set in response.
     */
    @Test
    public void test_responseUrl() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.txt";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals(url, responseData.getUrl());
    }

    /**
     * Test method is correctly set in response.
     */
    @Test
    public void test_responseMethod() {
        // GET
        ResponseData responseData =
                sharedClient.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + SERVER_PORT + "/").build());
        assertEquals("GET", responseData.getMethod());

        // HEAD
        responseData = sharedClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:" + SERVER_PORT + "/").build());
        assertEquals("HEAD", responseData.getMethod());
    }

    private String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }
}
