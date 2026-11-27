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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.RequestData;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.dbflute.utflute.core.PlainTestCase;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
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
 * Tests that a URL with a user name and password is refused before a browser is started, and that a redirect to such a
 * URL is refused as well.
 */
public class PlaywrightClientUserInfoTest extends PlainTestCase {

    @Test
    public void test_rejectUserInfo_refusesUserAndPassword() {
        final CrawlingAccessException e = assertThrows(CrawlingAccessException.class,
                () -> PlaywrightClient.rejectUserInfo("http://user:s3cret@example.com/path?q=1"));
        assertTrue(e.getMessage().contains("http://example.com/path?q=1"));
        assertTrue(e.getMessage().contains("web authentication"));
        assertFalse(e.getMessage().contains("s3cret"));
        assertFalse(e.getMessage().contains("user:"));
    }

    @Test
    public void test_rejectUserInfo_refusesUserOnly() {
        final CrawlingAccessException e =
                assertThrows(CrawlingAccessException.class, () -> PlaywrightClient.rejectUserInfo("https://user@example.com/"));
        assertFalse(e.getMessage().contains("user@"));
    }

    @Test
    public void test_rejectUserInfo_acceptsOtherUrls() {
        PlaywrightClient.rejectUserInfo("http://example.com/");
        PlaywrightClient.rejectUserInfo("http://example.com:8080/a@b");
        PlaywrightClient.rejectUserInfo("http://example.com/path?mail=a@b.example");
        PlaywrightClient.rejectUserInfo(null);
    }

    @Test
    public void test_execute_refusesBeforeStartingABrowser() {
        // The client is never init()ed: a browser would have to be started to get any further.
        final PlaywrightClient client = new PlaywrightClient();
        final RequestData request = new RequestData();
        request.setUrl("http://user:s3cret@example.com/");
        final CrawlingAccessException e = assertThrows(CrawlingAccessException.class, () -> client.execute(request));
        assertFalse(e.getMessage().contains("s3cret"));
    }

    @Test
    @Timeout(60)
    public void test_execute_refusesARedirectToUserInfo() throws Exception {
        // The browser follows the redirect itself, so the requested URL has no userinfo but the page it lands on has.
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                if ("/redir".equals(request.getHttpURI().getPath())) {
                    response.setStatus(302);
                    response.getHeaders().put(HttpHeader.LOCATION, "http://user:s3cret@[::1]:" + connector.getLocalPort() + "/target.html");
                    Content.Sink.write(response, true, "", callback);
                    return true;
                }
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=UTF-8");
                Content.Sink.write(response, true, "<html><body>target</body></html>", callback);
                return true;
            }
        });
        server.start();
        final int port = connector.getLocalPort();
        // With a MimeTypeHelper the client gets as far as returning a document, which is what it did before the fix.
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.of(mimeTypeHelper);
            }
        };
        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(true));
        client.setCloseTimeout(5);
        try {
            client.init();
            final CrawlingAccessException e = assertThrows(CrawlingAccessException.class,
                    () -> client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/redir").build()));
            assertTrue(e.getMessage().contains("http://[::1]:" + port + "/target.html"));
            assertFalse(e.getMessage().contains("s3cret"));
        } finally {
            client.close();
            server.stop();
        }
    }
}
