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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import java.util.HashMap;
import java.util.Optional;

import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.RequestData;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

public class PlaywrightClientSslIgnoreTest extends PlainTestCase {
    private CrawlerWebServer crawlerWebServer;

    private PlaywrightClientWithSslSettings playwrightClient;

    @Override
    @BeforeEach
    protected void setUp() throws Exception {
        super.setUp();

        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        this.crawlerWebServer = new CrawlerWebServer(7070, docRootDir, true);
        this.playwrightClient = new PlaywrightClientWithSslSettings();
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        this.playwrightClient.close();
        this.crawlerWebServer.stop();

        super.tearDown();
    }

    public void test_ensureClientThrowsErrors() {
        // start web server & client
        this.crawlerWebServer.start();
        this.playwrightClient.init();

        // evaluate
        try {
            final String url = "https://[::1]:7070/test.txt";
            this.playwrightClient.execute(makeRequestData(url));
            fail("No exception thrown.");
        } catch (final CrawlerSystemException e) {
            assertEquals("Page should be inaccessible with default client settings",
                    "Failed to access the URL. URL: https://[::1]:7070/test.txt, Response received: false, Download started: false, Timeout: 15s",
                    e.getMessage());
        }
    }

    public void test_ignoreSslCertificate() {
        // start web server & client
        this.crawlerWebServer.start();

        this.playwrightClient.setIgnoreSslCertificate(true);
        this.playwrightClient.init();

        // evaluate
        final String url = "https://[::1]:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);
    }

    public void test_ignoreHttpsErrors() {
        // start web server & client
        this.crawlerWebServer.start();

        this.playwrightClient.setIgnoreHttpsErrors(true);
        this.playwrightClient.init();

        // evaluate
        final String url = "https://[::1]:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);
    }

    public void test_bothOptionsEnabled() {
        // start web server & client
        this.crawlerWebServer.start();

        this.playwrightClient.setIgnoreSslCertificate(true);
        this.playwrightClient.setIgnoreHttpsErrors(true);
        this.playwrightClient.init();

        // evaluate
        final String url = "https://[::1]:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);
    }

    private static RequestData makeRequestData(final String url) {
        return RequestDataBuilder.newRequestData().get().url(url).build();
    }

    private static void assertTextFileIsCorrect(final ResponseData responseData) {
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("text/plain", responseData.getMimeType());
        assertEquals("This is a test document.", getBodyAsString(responseData).trim());
        assertEquals(25, responseData.getContentLength());
    }

    private static String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }

    private static class PlaywrightClientWithSslSettings extends PlaywrightClient {
        PlaywrightClientWithSslSettings() {
            initParamMap = new HashMap<>();
        }

        @Override
        protected Optional<MimeTypeHelper> getMimeTypeHelper() {
            return Optional.of(new MimeTypeHelperImpl());
        }

        void setIgnoreSslCertificate(final boolean ignoreSslCertificate) {
            initParamMap.put(HcHttpClient.IGNORE_SSL_CERTIFICATE_PROPERTY, ignoreSslCertificate);
        }

        void setIgnoreHttpsErrors(final boolean ignoreHttpsErrors) {
            initParamMap.put(IGNORE_HTTPS_ERRORS_PROPERTY, ignoreHttpsErrors);
        }
    }
}
