package org.codelibs.fess.crawler.client.http;

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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Optional;

public class PlaywrightClientSslIgnoreTest extends PlainTestCase {
    private CrawlerWebServer crawlerWebServer;

    private PlaywrightClientWithSslSettings playwrightClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        this.crawlerWebServer = new CrawlerWebServer(7070, docRootDir, true);
        this.playwrightClient = new PlaywrightClientWithSslSettings();
    }

    @Override
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
            final String url = "https://localhost:7070/test.txt";
            this.playwrightClient.execute(makeRequestData(url));
            fail("No exception thrown.");
        } catch (CrawlerSystemException e) {
            assertEquals("Page should be inaccessible with default client settings", "Failed to access https://localhost:7070/test.txt",
                    e.getMessage());
        }
    }

    public void test_ignoreSslCertificate() {
        // start web server & client
        this.crawlerWebServer.start();

        this.playwrightClient.setIgnoreSslCertificate(true);
        this.playwrightClient.init();

        // evaluate
        final String url = "https://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);
    }

    public void test_ignoreHttpsErrors() {
        // start web server & client
        this.crawlerWebServer.start();

        this.playwrightClient.setIgnoreHttpsErrors(true);
        this.playwrightClient.init();

        // evaluate
        final String url = "https://localhost:7070/test.txt";
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
        final String url = "https://localhost:7070/test.txt";
        final ResponseData responseData = this.playwrightClient.execute(makeRequestData(url));
        assertTextFileIsCorrect(responseData);
    }

    private static RequestData makeRequestData(String url) {
        return RequestDataBuilder.newRequestData().get().url(url).build();
    }

    private static void assertTextFileIsCorrect(ResponseData responseData) {
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
        } catch (UnsupportedEncodingException e) {
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
