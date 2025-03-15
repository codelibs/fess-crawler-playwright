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

    private static boolean headless = true;

    private PlaywrightClient playwrightClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };
        playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
        playwrightClient.init();
    }

    @Override
    protected void tearDown() throws Exception {
        playwrightClient.close();
        super.tearDown();
    }

    public void test_doGet() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        for (int i = 0; i < 5; i++) {
            try {
                server.start();
                {
                    final String url = "http://localhost:7070/";
                    final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                    assertEquals(200, responseData.getHttpStatusCode());
                    assertEquals("GET", responseData.getMethod());
                    assertEquals("UTF-8", responseData.getCharSet());
                    assertEquals("text/html", responseData.getMimeType());
                    final String body = getBodyAsString(responseData);
                    assertTrue(body.contains("content page"));
                    assertEquals(1051, responseData.getContentLength());
                }
                {
                    final String url = "http://localhost:7070/notfound.html";
                    final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                    assertEquals(404, responseData.getHttpStatusCode());
                    assertEquals("GET", responseData.getMethod());
                    assertEquals("iso-8859-1", responseData.getCharSet());
                    assertEquals("text/html", responseData.getMimeType());
                    assertEquals(0, responseData.getContentLength());
                    final String body = getBodyAsString(responseData);
                    assertEquals("", body);
                }
            } finally {
                server.stop();
            }
        }
    }

    public void test_doGet_download() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/download.zip";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals(467, responseData.getContentLength());
            assertEquals("application/zip", responseData.getMimeType());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_text() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.txt";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("text/plain", responseData.getMimeType());
            assertEquals("This is a test document.", getBodyAsString(responseData).trim());
            assertEquals(25, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_pdf() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.pdf";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("application/pdf", responseData.getMimeType());
            assertEquals(7336, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_docx() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.docx";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", responseData.getMimeType());
            assertEquals(7500, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_epub() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.epub";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("application/epub+zip", responseData.getMimeType());
            assertEquals(7416, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_gif() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.gif";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("image/gif", responseData.getMimeType());
            assertEquals(63, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_jpg() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.jpg";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("image/jpeg", responseData.getMimeType());
            assertEquals(7118, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_json() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.json";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("application/json", responseData.getMimeType());
            final String body = getBodyAsString(responseData);
            assertEquals("{\"message\":\"Thisisatestdocument.\"}", body.replaceAll("\\s", ""));
            assertEquals(39, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_png() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.png";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("image/png", responseData.getMimeType());
            assertEquals(5484, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_rtf() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.rtf";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("application/rtf", responseData.getMimeType());
            assertEquals(393, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doGet_sh() {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/test.sh";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("application/x-sh", responseData.getMimeType());
            final String body = getBodyAsString(responseData);
            assertEquals("#!/bin/bashecho\"Thisisatestdocument.\"", body.replaceAll("\\s", ""));
            assertEquals(45, responseData.getContentLength());
        } finally {
            server.stop();
        }
    }

    public void test_doHead() throws Exception {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().head().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("HEAD", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("text/html", responseData.getMimeType());
            assertEquals(1051, responseData.getContentLength());
            assertNotNull(responseData.getLastModified());
            assertTrue(responseData.getLastModified().getTime() < SystemUtil.currentTimeMillis());
        } finally {
            server.stop();
        }
    }

    public void test_filename() throws Exception {
        assertEquals("test.html", playwrightClient.getFilename("test.html"));
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html"));
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html?123=abc"));
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html?123=abc#xyz"));
        assertEquals("test.html", playwrightClient.getFilename("http://host/test.html#xyz"));
        assertEquals("index.html", playwrightClient.getFilename("http://host/"));
        assertEquals("index.html", playwrightClient.getFilename("http://host/?123=abc"));
        assertEquals("index.html", playwrightClient.getFilename("http://host/?123=abc#xyz"));
        assertEquals("index.html", playwrightClient.getFilename("http://host/#xyz"));
        assertNull(playwrightClient.getFilename(null));
        assertNull(playwrightClient.getFilename(""));
    }

    public void test_parseDate() throws Exception {
        assertEquals(1674353794000L, playwrightClient.parseDate("Sun, 22 Jan 2023 02:16:34 GMT").getTime());
        assertNull(playwrightClient.parseDate(null));
        assertNull(playwrightClient.parseDate(""));
    }

    private String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }
}
