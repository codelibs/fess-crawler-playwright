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

import java.io.File;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.UnsupportedEncodingException;
import java.util.Optional;

import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.core.lang.SystemUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;

/**
 * @author shinsuke
 *
 */
public class PlaywrightClientTest extends PlainTestCase {

    private static final boolean HEADLESS = true;
    private static final int SERVER_PORT = 7500;

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

    @Test
    public void test_doGet() {
        for (int i = 0; i < 5; i++) {
            {
                final String url = "http://[::1]:" + SERVER_PORT + "/";
                final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("GET", responseData.getMethod());
                assertEquals("UTF-8", responseData.getCharSet());
                assertEquals("text/html", responseData.getMimeType());
                final String body = getBodyAsString(responseData);
                assertTrue(body.contains("content page"));
                assertEquals(1051L, responseData.getContentLength());
            }
            {
                final String url = "http://[::1]:" + SERVER_PORT + "/notfound.html";
                final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(404, responseData.getHttpStatusCode());
                assertEquals("GET", responseData.getMethod());
                assertEquals("iso-8859-1", responseData.getCharSet());
                assertEquals("text/html", responseData.getMimeType());
                assertEquals(0L, responseData.getContentLength());
                final String body = getBodyAsString(responseData);
                assertEquals("", body);
            }
        }
    }

    @Test
    public void test_doGet_download() {
        final String url = "http://[::1]:" + SERVER_PORT + "/download.zip";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals(467L, responseData.getContentLength());
        assertEquals("application/zip", responseData.getMimeType());
    }

    @Test
    public void test_doGet_text() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.txt";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("text/plain", responseData.getMimeType());
        assertEquals("This is a test document.", getBodyAsString(responseData).trim());
        assertEquals(25L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_pdf() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.pdf";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("application/pdf", responseData.getMimeType());
        assertEquals(7336L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_docx() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.docx";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", responseData.getMimeType());
        assertEquals(7500L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_epub() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.epub";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("application/epub+zip", responseData.getMimeType());
        assertEquals(7416L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_gif() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.gif";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("image/gif", responseData.getMimeType());
        assertEquals(63L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_jpg() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.jpg";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("image/jpeg", responseData.getMimeType());
        assertEquals(7118L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_json() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.json";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("application/json", responseData.getMimeType());
        final String body = getBodyAsString(responseData);
        assertEquals("{\"message\":\"Thisisatestdocument.\"}", body.replaceAll("\\s", ""));
        assertEquals(39L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_png() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.png";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("image/png", responseData.getMimeType());
        assertEquals(5484L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_rtf() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.rtf";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("application/rtf", responseData.getMimeType());
        assertEquals(393L, responseData.getContentLength());
    }

    @Test
    public void test_doGet_sh() {
        final String url = "http://[::1]:" + SERVER_PORT + "/test.sh";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("application/x-sh", responseData.getMimeType());
        final String body = getBodyAsString(responseData);
        assertEquals("#!/bin/bashecho\"Thisisatestdocument.\"", body.replaceAll("\\s", ""));
        assertEquals(45L, responseData.getContentLength());
    }

    @Test
    public void test_doHead() throws Exception {
        final String url = "http://[::1]:" + SERVER_PORT + "/";
        final ResponseData responseData = sharedClient.execute(RequestDataBuilder.newRequestData().head().url(url).build());
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("HEAD", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("text/html", responseData.getMimeType());
        assertEquals(1051L, responseData.getContentLength());
        assertNotNull(responseData.getLastModified());
        assertTrue(responseData.getLastModified().getTime() < SystemUtil.currentTimeMillis());
    }

    @Test
    public void test_filename() throws Exception {
        assertEquals("test.html", sharedClient.getFilename("test.html"));
        assertEquals("test.html", sharedClient.getFilename("http://host/test.html"));
        assertEquals("test.html", sharedClient.getFilename("http://host/test.html?123=abc"));
        assertEquals("test.html", sharedClient.getFilename("http://host/test.html?123=abc#xyz"));
        assertEquals("test.html", sharedClient.getFilename("http://host/test.html#xyz"));
        assertEquals("index.html", sharedClient.getFilename("http://host/"));
        assertEquals("index.html", sharedClient.getFilename("http://host/?123=abc"));
        assertEquals("index.html", sharedClient.getFilename("http://host/?123=abc#xyz"));
        assertEquals("index.html", sharedClient.getFilename("http://host/#xyz"));
        assertNull(sharedClient.getFilename(null));
        assertNull(sharedClient.getFilename(""));
    }

    @Test
    public void test_parseDate() throws Exception {
        assertEquals(1674353794000L, sharedClient.parseDate("Sun, 22 Jan 2023 02:16:34 GMT").getTime());
        assertNull(sharedClient.parseDate(null));
        assertNull(sharedClient.parseDate(""));
    }

    private String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }
}
