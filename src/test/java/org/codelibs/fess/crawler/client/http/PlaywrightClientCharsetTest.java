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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.codelibs.core.misc.Pair;

import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.transformer.impl.HtmlTransformer;
import org.dbflute.utflute.core.PlainTestCase;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.microsoft.playwright.BrowserType;

/**
 * Tests that the bytes {@link PlaywrightClient} stores can be read back with the charset the
 * downstream HTML transformer will pick for them.
 *
 * <p>The client serialises the DOM and encodes it itself, and the transformer downstream then
 * re-reads a charset declaration out of those bytes and overrides the response's charset with it.
 * Nothing reconciles the two, so these tests do what the crawler does: run the real
 * {@link HtmlTransformer#updateCharset(ResponseData)} over the response this client produced, and
 * decode the stored bytes with whatever charset that left behind. Asserting on the recovered
 * Japanese text - rather than on the charset name - is what makes a disagreement visible.</p>
 */
public class PlaywrightClientCharsetTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    /** Text that is representable in every charset these tests use, and mangled by decoding with the wrong one. */
    private static final String JAPANESE = "日本語のテキストです";

    private static final String SHIFT_JIS_META = "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=Shift_JIS\">";

    private static final String EUC_JP_META = "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=EUC-JP\">";

    /**
     * Exposes the protected hooks of the transformer this client has to agree with, so these tests
     * pin the real downstream rule rather than a local restatement of it.
     */
    private static class DownstreamTransformer extends HtmlTransformer {
        void update(final ResponseData responseData) {
            updateCharset(responseData);
        }

        String parse(final String content) {
            return parseCharset(content);
        }
    }

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

    private static Server startServer(final int port, final String contentType, final byte[] body) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType);
                response.write(true, ByteBuffer.wrap(body), callback);
                return true;
            }
        });
        server.start();
        return server;
    }

    private static String html(final String metaTag) {
        return "<html><head>" + metaTag + "<title>" + JAPANESE + "</title></head><body><p>" + JAPANESE + "</p></body></html>";
    }

    /**
     * Pulls the paragraph text out of a decoded document, so a failure reports the mangled text next to
     * the text that was expected rather than just "false".
     *
     * @param html the decoded document
     * @return the paragraph text, or the whole document if it has no paragraph
     */
    private static String paragraphOf(final String html) {
        final int start = html.indexOf("<p>");
        final int end = html.indexOf("</p>");
        return start < 0 || end < start ? html : html.substring(start + "<p>".length(), end);
    }

    private static byte[] readBody(final ResponseData responseData) throws Exception {
        try (final InputStream in = responseData.getResponseBody()) {
            return in.readAllBytes();
        }
    }

    /**
     * Crawls a page and reads the stored bytes back exactly as the crawler would: the downstream
     * transformer decides the charset, and the bytes are decoded with it.
     *
     * @param port the port to serve on
     * @param contentType the {@code Content-Type} header to send
     * @param metaTag the charset declaration to put in the document, or an empty string for none
     * @param bodyCharset the charset the served bytes are actually encoded in
     * @return the paragraph text the crawler would go on to index
     */
    private static String crawlAndDecode(final int port, final String contentType, final String metaTag, final Charset bodyCharset)
            throws Exception {
        final Server server = startServer(port, contentType, html(metaTag).getBytes(bodyCharset));
        final PlaywrightClient client = newClient();
        try {
            client.setDownloadTimeout(10);
            client.init();

            final ResponseData responseData =
                    client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
            new DownstreamTransformer().update(responseData);
            return paragraphOf(new String(readBody(responseData), Charset.forName(responseData.getCharSet())));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * The HTML5 short form carries no semicolon, so the transformer downstream cannot see it and
     * settles on UTF-8. The client has to reach the same answer rather than trusting the declaration.
     */
    @Test
    @Timeout(120)
    public void test_metaCharsetShortForm() throws Exception {
        assertEquals(JAPANESE, crawlAndDecode(7640, "text/html", "<meta charset=\"Shift_JIS\">", Charset.forName("Shift_JIS")));
    }

    /**
     * A declaration the transformer downstream does see, with no charset in the header: the client
     * used to encode with UTF-8 while the transformer read the bytes as Shift_JIS.
     */
    @Test
    @Timeout(120)
    public void test_metaHttpEquivShiftJis_noHeaderCharset() throws Exception {
        assertEquals(JAPANESE, crawlAndDecode(7641, "text/html", SHIFT_JIS_META, Charset.forName("Shift_JIS")));
    }

    /**
     * The same disagreement, with a charset whose bytes differ from Shift_JIS - so this fails even if
     * Shift_JIS happened to survive by accident.
     */
    @Test
    @Timeout(120)
    public void test_metaHttpEquivEucJp_noHeaderCharset() throws Exception {
        assertEquals(JAPANESE, crawlAndDecode(7642, "text/html", EUC_JP_META, Charset.forName("EUC-JP")));
    }

    /**
     * A charset declared only in the header. The client encoded with it, but the transformer
     * downstream finds no declaration in the document and forces UTF-8, so the bytes were read back
     * with the wrong charset.
     */
    @Test
    @Timeout(120)
    public void test_headerCharsetOnly() throws Exception {
        assertEquals(JAPANESE, crawlAndDecode(7643, "text/html; charset=Shift_JIS", "", Charset.forName("Shift_JIS")));
    }

    /**
     * A stale declaration left in a document that is now served as UTF-8. The header wins in the
     * browser, so the text itself is correct, but the transformer downstream only ever sees the stale
     * declaration - so that is the charset the bytes have to be written in.
     */
    @Test
    @Timeout(120)
    public void test_headerCharsetOverriddenByStaleMeta() throws Exception {
        assertEquals(JAPANESE, crawlAndDecode(7644, "text/html; charset=UTF-8", SHIFT_JIS_META, StandardCharsets.UTF_8));
    }

    /**
     * Pins the two properties of the downstream rule this client mirrors. Both are read from the
     * transformer itself, so a change to either turns this red instead of silently reintroducing the
     * disagreement these tests exist to prevent.
     */
    @Test
    public void test_downstreamContract() {
        final DownstreamTransformer transformer = new DownstreamTransformer();
        // The window PlaywrightClient scans before deciding how to encode.
        assertEquals(2048, transformer.getPreloadSizeForCharset());
        // The semicolon is required, so the HTML5 short form is invisible downstream.
        assertNull(transformer.parse("<meta charset=\"Shift_JIS\">"));
        assertEquals("Shift_JIS", transformer.parse(SHIFT_JIS_META));
        // The declaration has to sit inside a meta tag, so body text does not count as one.
        assertNull(transformer.parse("<p>Content-Type: text/html; charset=Shift_JIS</p>"));
        // A meta tag the scan window cut in half still declares a charset, on both sides of the rule.
        assertEquals("Shift_JIS", transformer.parse(SHIFT_JIS_META.substring(0, SHIFT_JIS_META.length() - 1)));
    }

    /**
     * A declaration past the scan window is not a declaration. The client must not encode with one the
     * transformer downstream will never reach.
     */
    @Test
    public void test_encodeContent_declarationOutsideWindow() {
        final PlaywrightClient client = new PlaywrightClient();
        final String content = "<html><body>" + "x".repeat(3000) + SHIFT_JIS_META + "<p>" + JAPANESE + "</p></body></html>";

        final Pair<byte[], String> encoded = client.encodeContent(content);

        assertEquals("UTF-8", encoded.getSecond());
        assertEquals(JAPANESE, paragraphOf(new String(encoded.getFirst(), StandardCharsets.UTF_8)));
        assertDownstreamAgrees(encoded);
    }

    /**
     * The same document with the declaration inside the window: the other half of the window
     * assertion above, which fails if the client stops honouring a declaration the transformer can see.
     */
    @Test
    public void test_encodeContent_declarationInsideWindow() {
        final PlaywrightClient client = new PlaywrightClient();
        final String content = "<html><body>" + "x".repeat(1000) + SHIFT_JIS_META + "<p>" + JAPANESE + "</p></body></html>";

        final Pair<byte[], String> encoded = client.encodeContent(content);

        assertEquals("Shift_JIS", encoded.getSecond());
        assertEquals(JAPANESE, paragraphOf(new String(encoded.getFirst(), Charset.forName("Shift_JIS"))));
        assertDownstreamAgrees(encoded);
    }

    /**
     * A declaration in body text is not a declaration. The transformer downstream reads only the ones
     * inside a meta tag, so encoding with this one would write bytes it goes on to read as UTF-8.
     */
    @Test
    public void test_encodeContent_declarationInBodyText() {
        final PlaywrightClient client = new PlaywrightClient();
        final String content = "<html><body><p>Content-Type: text/html; charset=Shift_JIS</p><p>" + JAPANESE + "</p></body></html>";

        final Pair<byte[], String> encoded = client.encodeContent(content);

        assertEquals("UTF-8", encoded.getSecond());
        assertDownstreamAgrees(encoded);
    }

    /**
     * A charset name nothing can resolve. The transformer downstream falls back to UTF-8 for it, so the
     * client has to store UTF-8 rather than fail.
     */
    @Test
    public void test_encodeContent_unresolvableCharset() {
        final PlaywrightClient client = new PlaywrightClient();
        final String content = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=NOT-A-CHARSET\"></head><body><p>"
                + JAPANESE + "</p></body></html>";

        final Pair<byte[], String> encoded = client.encodeContent(content);

        assertEquals("UTF-8", encoded.getSecond());
        assertEquals(JAPANESE, paragraphOf(new String(encoded.getFirst(), StandardCharsets.UTF_8)));
        assertDownstreamAgrees(encoded);
    }

    /**
     * Asserts the transformer downstream reaches the same charset the client labelled the bytes with -
     * the invariant this mirroring exists to hold.
     *
     * @param encoded the bytes and the charset the client labelled them with
     */
    private void assertDownstreamAgrees(final Pair<byte[], String> encoded) {
        final ResponseData responseData = new ResponseData();
        responseData.setCharSet(encoded.getSecond());
        responseData.setResponseBody(encoded.getFirst());
        new DownstreamTransformer().update(responseData);
        assertEquals(Charset.forName(encoded.getSecond()), Charset.forName(responseData.getCharSet()));
    }
}
