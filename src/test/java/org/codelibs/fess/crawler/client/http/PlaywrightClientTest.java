/*
 * Copyright 2012-2022 CodeLibs Project and the Others.
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

import javax.annotation.Resource;

import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.core.lang.SystemUtil;
import org.codelibs.fess.crawler.Constants;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;

/**
 * @author shinsuke
 *
 */
public class PlaywrightClientTest extends PlainTestCase {

    private static boolean headless = true;

    @Resource
    private PlaywrightClient playwrightClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        playwrightClient = new PlaywrightClient();
        playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
        playwrightClient.init();
    }

    public void test_doGet() {
        File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("GET", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("text/html", responseData.getMimeType());
            assertEquals(668, responseData.getContentLength());
            final String body = new String(InputStreamUtil.getBytes(responseData.getResponseBody()), Constants.UTF_8_CHARSET);
            assertTrue(body.contains("content"));
        } finally {
            server.stop();
        }
    }

    public void test_doGet_download() {
        File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
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

    public void test_doHead() throws Exception {
        File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7070, docRootDir);

        final String url = "http://localhost:7070/";
        try {
            server.start();
            final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().head().url(url).build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("HEAD", responseData.getMethod());
            assertEquals("UTF-8", responseData.getCharSet());
            assertEquals("text/html", responseData.getMimeType());
            assertEquals(668, responseData.getContentLength());
            Thread.sleep(100);
            assertNotNull(responseData.getLastModified());
            assertTrue(responseData.getLastModified().getTime() < SystemUtil.currentTimeMillis());
        } finally {
            server.stop();
        }
    }
}
