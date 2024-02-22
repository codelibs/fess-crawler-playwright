/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
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
import java.util.HashMap;
import java.util.Optional;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.RequestData;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebProxy;
import org.codelibs.fess.crawler.util.CrawlerWebProxy.ProxyAccessStatus;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

public class PlaywrightClientProxyTest extends PlainTestCase {
    private CrawlerWebProxy proxyServer;
    private CrawlerWebServer crawlerWebServer;

    private PlaywrightClientWithProxySettings playwrightClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        this.proxyServer = new CrawlerWebProxy();
        this.crawlerWebServer = new CrawlerWebServer(7070, docRootDir);
        this.playwrightClient = new PlaywrightClientWithProxySettings();
    }

    @Override
    protected void tearDown() throws Exception {
        this.playwrightClient.close();
        this.proxyServer.stop();
        this.crawlerWebServer.stop();

        super.tearDown();
    }

    public void test_accessProxy_noProxyConfig() {
        // setup server
        this.crawlerWebServer.start();
        this.proxyServer.start();
        this.playwrightClient.init();

        // evaluate
        final String url = "http://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);

        // expect the proxy server to not be touched
        assertEquals("Proxy server must be untouched", ProxyAccessStatus.NOT_ACCESSED, this.proxyServer.getAccessResult());
    }

    public void test_accessProxy_separatedProxyHostAndPort_noAuth() {
        // setup server
        this.crawlerWebServer.start();

        this.proxyServer.setPort(3128);
        this.proxyServer.start();

        this.playwrightClient.setProxyHost("localhost");
        this.playwrightClient.setProxyPort("3128");
        this.playwrightClient.init();

        // evaluate
        final String url = "http://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);

        // expect the proxy server to be accessed
        assertEquals("Proxy server must be accessed", ProxyAccessStatus.ACCESS_GRANTED, this.proxyServer.getAccessResult());
    }

    public void test_accessProxy_combinedProxyHostAndPort_noAuth() {
        // setup server
        this.crawlerWebServer.start();

        this.proxyServer.setPort(3128);
        this.proxyServer.start();

        this.playwrightClient.setProxyHost("http://localhost:3128");
        this.playwrightClient.init();

        // evaluate
        final String url = "http://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);

        // expect the proxy server to be accessed
        assertEquals("Proxy server must be accessed", ProxyAccessStatus.ACCESS_GRANTED, this.proxyServer.getAccessResult());
    }

    public void test_accessProxy_proxyHostAndPort_correctAuth() {
        // setup server
        final var apacheCredentials = new UsernamePasswordCredentials("username", "Passw0rd!@#$");
        this.crawlerWebServer.start();

        this.proxyServer.setPort(3128);
        this.proxyServer.setCredentials(apacheCredentials);
        this.proxyServer.start();

        this.playwrightClient.setProxyHost("localhost");
        this.playwrightClient.setProxyPort("3128");
        this.playwrightClient.setProxyCredentials(apacheCredentials);
        this.playwrightClient.init();

        // evaluate
        final String url = "http://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);

        // expect the proxy server to be accessed
        assertEquals("Proxy server must be accessed", ProxyAccessStatus.ACCESS_GRANTED, this.proxyServer.getAccessResult());
    }

    public void test_accessProxy_proxyHostAndPort_missingRequiredAuth() {
        // setup server
        final var apacheCredentials = new UsernamePasswordCredentials("username", "Passw0rd!@#$");
        this.crawlerWebServer.start();

        this.proxyServer.setPort(3128);
        this.proxyServer.setCredentials(apacheCredentials);
        this.proxyServer.start();

        this.playwrightClient.setProxyHost("localhost");
        this.playwrightClient.setProxyPort("3128");
        this.playwrightClient.init();

        // evaluate
        final String url = "http://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertEquals(407, responseData.getHttpStatusCode());

        // expect the proxy server to not be touched
        assertEquals("Proxy server must prompt client for credentials", ProxyAccessStatus.PROMPTED_FOR_CREDENTIALS,
                this.proxyServer.getAccessResult());
    }

    public void test_accessProxy_proxyHostAndPort_incorrectAuth() {
        // setup server
        final var apacheCredentials = new UsernamePasswordCredentials("username", "Passw0rd!@#$");
        this.crawlerWebServer.start();

        this.proxyServer.setPort(3128);
        this.proxyServer.setCredentials("user", "password");
        this.proxyServer.start();

        this.playwrightClient.setProxyHost("localhost");
        this.playwrightClient.setProxyPort("3128");
        this.playwrightClient.setProxyCredentials(apacheCredentials);
        this.playwrightClient.init();

        // evaluate
        final String url = "http://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertEquals(401, responseData.getHttpStatusCode());

        // expect the proxy server to not be touched
        assertEquals("Proxy server must deny unauthorized access", ProxyAccessStatus.ACCESS_DENIED, this.proxyServer.getAccessResult());
    }

    public void test_accessProxy_proxyHostAndPort_bypassed() {
        // setup server
        this.crawlerWebServer.start();

        this.proxyServer.setPort(3128);
        this.proxyServer.start();

        this.playwrightClient.setProxyHost("localhost");
        this.playwrightClient.setProxyPort("3128");
        this.playwrightClient.setProxyBypass("localhost, 127.0.0.1");
        this.playwrightClient.init();

        // evaluate
        final String url = "http://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);

        // expect the proxy server to not be touched
        assertEquals("Proxy server must be untouched", ProxyAccessStatus.NOT_ACCESSED, this.proxyServer.getAccessResult());
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

    private static class PlaywrightClientWithProxySettings extends PlaywrightClient {
        PlaywrightClientWithProxySettings() {
            initParamMap = new HashMap<>();
        }

        @Override
        protected Optional<MimeTypeHelper> getMimeTypeHelper() {
            return Optional.of(new MimeTypeHelperImpl());
        }

        void setProxyHost(final String proxyHost) {
            initParamMap.put(HcHttpClient.PROXY_HOST_PROPERTY, proxyHost);
        }

        void setProxyPort(final String proxyPort) {
            initParamMap.put(HcHttpClient.PROXY_PORT_PROPERTY, proxyPort);
        }

        void setProxyCredentials(final UsernamePasswordCredentials credentials) {
            initParamMap.put(HcHttpClient.PROXY_CREDENTIALS_PROPERTY, credentials);
        }

        void setProxyBypass(final String proxyBypass) {
            initParamMap.put(PROXY_BYPASS_PROPERTY, proxyBypass);
        }
    }
}
