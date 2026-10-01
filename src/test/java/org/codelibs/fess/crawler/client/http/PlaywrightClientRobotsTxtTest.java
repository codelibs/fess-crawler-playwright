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

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.codelibs.fess.crawler.CrawlerContext;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.RobotsTxtDisallowedException;
import org.codelibs.fess.crawler.exception.RobotsTxtUnavailableException;
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.RobotsTxtHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlingParameterUtil;
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
 * Tests that a Playwright crawl honours robots.txt.
 *
 * <p>Which client a crawl configuration routes to must not decide whether robots.txt is obeyed. The
 * HTTP clients resolve it through {@link RobotsTxtHelper#checkRobotsTxt}; this client is not one of
 * them, so these tests pin the same outcome for it: a URL robots.txt does not allow fails with
 * {@link RobotsTxtDisallowedException} before the page is fetched, and a robots.txt that cannot be read
 * right now fails it with {@link RobotsTxtUnavailableException} so the crawler can try again later.</p>
 */
public class PlaywrightClientRobotsTxtTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    /**
     * Records whether the crawl touched the filter. robots.txt used to be turned into filter patterns,
     * which made an Allow line a crawl-wide include; it must not reach the filter at all any more.
     */
    private static class RecordingUrlFilter implements UrlFilter {
        final List<String> includes = Collections.synchronizedList(new ArrayList<>());
        final List<String> excludes = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void init(final String sessionId) {
        }

        @Override
        public boolean match(final String url) {
            return true;
        }

        @Override
        public void addInclude(final String urlPattern) {
            includes.add(urlPattern);
        }

        @Override
        public void addExclude(final String urlPattern) {
            excludes.add(urlPattern);
        }

        @Override
        public void processUrl(final String url) {
        }

        @Override
        public void clear() {
        }
    }

    /** How the test site answers one path. */
    @FunctionalInterface
    private interface Route {
        void serve(Response response, Callback callback) throws Exception;
    }

    private static Route text(final String body) {
        return (response, callback) -> {
            response.setStatus(200);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=UTF-8");
            Content.Sink.write(response, true, body, callback);
        };
    }

    private static Route status(final int statusCode) {
        return (response, callback) -> {
            response.setStatus(statusCode);
            callback.succeeded();
        };
    }

    private static Route redirect(final String location) {
        return (response, callback) -> {
            response.setStatus(301);
            response.getHeaders().put(HttpHeader.LOCATION, location);
            callback.succeeded();
        };
    }

    /** Sends the body in two flushed parts, so the response is chunked and declares no Content-Length. */
    private static Route chunkedText(final String body) {
        return (response, callback) -> {
            response.setStatus(200);
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=UTF-8");
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = Content.Sink.asOutputStream(response)) {
                out.write(bytes, 0, bytes.length / 2);
                out.flush();
                out.write(bytes, bytes.length / 2, bytes.length - bytes.length / 2);
            }
            callback.succeeded();
        };
    }

    /**
     * Returns a port that is free right now. Test classes run concurrently, so a fixed port can
     * already be taken by another class's server.
     */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Serves {@code routes} as given and one HTML page for every other path, and records every path
     * asked for so a test can tell a fetch from a cached decision.
     */
    private static Server startSite(final int port, final Map<String, Route> routes, final List<String> requestedPaths) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                final String path = request.getHttpURI().getPath();
                requestedPaths.add(path);
                final Route route = routes.get(path);
                if (route != null) {
                    route.serve(response, callback);
                    return true;
                }
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, "<html><body>page</body></html>", callback);
                return true;
            }
        });
        server.start();
        return server;
    }

    /** A site whose robots.txt is {@code robotsTxt}, or answers 404 for it when that is null. */
    private static Map<String, Route> robotsTxt(final String robotsTxt) {
        return Map.of("/robots.txt", robotsTxt == null ? status(404) : text(robotsTxt));
    }

    private static PlaywrightClient newClient(final Map<String, Object> paramMap, final RobotsTxtHelper robotsTxtHelper) {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.of(mimeTypeHelper);
            }

            @Override
            protected Optional<RobotsTxtHelper> getRobotsTxtHelper() {
                return Optional.ofNullable(robotsTxtHelper);
            }
        };
        client.setInitParameterMap(paramMap);
        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        client.setDownloadTimeout(5);
        client.setCloseTimeout(10);
        return client;
    }

    /** What a test does with the client once the site is up and the crawl context is set. */
    @FunctionalInterface
    private interface CrawlAction {
        void run(PlaywrightClient client, String baseUrl, CrawlerContext crawlerContext) throws Exception;
    }

    /** Runs {@code action} against a site serving {@code routes}, inside a crawl context. */
    private static void crawl(final int port, final Map<String, Route> routes, final Map<String, Object> paramMap,
            final RecordingUrlFilter urlFilter, final List<String> requestedPaths, final CrawlAction action) throws Exception {
        final Server server = startSite(port, routes, requestedPaths);
        final CrawlerContext crawlerContext = new CrawlerContext();
        crawlerContext.setUrlFilter(urlFilter);
        final PlaywrightClient client = newClient(paramMap, new RobotsTxtHelper());
        try {
            client.init();
            CrawlingParameterUtil.setCrawlerContext(crawlerContext);
            action.run(client, "http://[::1]:" + port, crawlerContext);
        } finally {
            CrawlingParameterUtil.setCrawlerContext(null);
            client.close();
            server.stop();
        }
    }

    private static ResponseData get(final PlaywrightClient client, final String url) {
        return client.execute(RequestDataBuilder.newRequestData().get().url(url).build());
    }

    /**
     * The reason this exists: a URL robots.txt disallows is not fetched, and the crawler is told why so
     * it can log it rather than record a failure.
     */
    @Test
    @Timeout(60)
    public void test_execute_disallowedUrlIsNotFetched() throws Exception {
        final int port = freePort();
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, robotsTxt("User-agent: *\nDisallow: /secret/\n"), new HashMap<>(), urlFilter, requestedPaths,
                (client, baseUrl, crawlerContext) -> {
                    try {
                        get(client, baseUrl + "/secret/page.html");
                        fail(); // a disallowed URL must not be fetched
                    } catch (final RobotsTxtDisallowedException e) {
                        assertTrue(e.getMessage().endsWith(baseUrl + "/secret/page.html"));
                    }
                    assertEquals(200, get(client, baseUrl + "/open/page.html").getHttpStatusCode());
                });

        assertTrue(requestedPaths.contains("/robots.txt"));
        assertFalse(requestedPaths.contains("/secret/page.html"));
        assertTrue(requestedPaths.contains("/open/page.html"));
        // The rules are evaluated per URL, not written into the crawl's filter.
        assertTrue(urlFilter.excludes.isEmpty());
        assertTrue(urlFilter.includes.isEmpty());
    }

    /** The longest matching rule wins, so an Allow inside a disallowed directory opens that part of it. */
    @Test
    @Timeout(60)
    public void test_execute_longerAllowWins() throws Exception {
        final int port = freePort();
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, robotsTxt("User-agent: *\nDisallow: /secret/\nAllow: /secret/public/\n"), new HashMap<>(), urlFilter, requestedPaths,
                (client, baseUrl, crawlerContext) -> {
                    assertEquals(200, get(client, baseUrl + "/secret/public/page.html").getHttpStatusCode());
                    try {
                        get(client, baseUrl + "/secret/private.html");
                        fail(); // the rest of the directory stays disallowed
                    } catch (final RobotsTxtDisallowedException e) {
                        // expected
                    }
                });

        assertTrue(requestedPaths.contains("/secret/public/page.html"));
        assertFalse(requestedPaths.contains("/secret/private.html"));
    }

    /**
     * A robots.txt with only Allow lines restricts nothing. It used to become a crawl-wide include,
     * which kept the crawl out of every other path of every other site.
     */
    @Test
    @Timeout(60)
    public void test_execute_allowOnlyRobotsTxtRestrictsNothing() throws Exception {
        final int port = freePort();
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, robotsTxt("User-agent: *\nAllow: /docs/\n"), new HashMap<>(), urlFilter, requestedPaths,
                (client, baseUrl, crawlerContext) -> {
                    assertEquals(200, get(client, baseUrl + "/other/page.html").getHttpStatusCode());
                });

        assertTrue(requestedPaths.contains("/other/page.html"));
        assertTrue(urlFilter.includes.isEmpty());
        assertTrue(urlFilter.excludes.isEmpty());
    }

    /** robots.txt is also where a site advertises its sitemaps, and the crawler reads them from the context. */
    @Test
    @Timeout(60)
    public void test_execute_sitemapsReachTheCrawlerContext() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, robotsTxt("Sitemap: http://[::1]:" + port + "/sitemap.xml\nUser-agent: *\n"), new HashMap<>(), new RecordingUrlFilter(),
                requestedPaths, (client, baseUrl, crawlerContext) -> {
                    get(client, baseUrl + "/");
                    // removeSitemaps() reads the thread-local the crawl wrote, so it has to be read on this thread.
                    assertEquals(1, crawlerContext.removeSitemaps().length);
                });
    }

    /**
     * One fetch per host, however many pages are crawled. Without it every page of a site would pull
     * robots.txt again, which is a request the site did not ask for and a cost the crawl pays for nothing.
     */
    @Test
    @Timeout(60)
    public void test_execute_robotsTxtIsFetchedOncePerHost() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, robotsTxt("User-agent: *\nDisallow: /secret/\n"), new HashMap<>(), new RecordingUrlFilter(), requestedPaths,
                (client, baseUrl, crawlerContext) -> {
                    get(client, baseUrl + "/");
                    get(client, baseUrl + "/a.html");
                    try {
                        get(client, baseUrl + "/secret/b.html");
                        fail(); // disallowed
                    } catch (final RobotsTxtDisallowedException e) {
                        // expected: decided from the rules fetched for the first page
                    }
                });

        assertEquals(1L, requestedPaths.stream().filter("/robots.txt"::equals).count());
    }

    /** A site without robots.txt is the common case and must not turn into a failed crawl. */
    @Test
    @Timeout(60)
    public void test_execute_missingRobotsTxtIsNotAFailure() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, robotsTxt(null), new HashMap<>(), new RecordingUrlFilter(), requestedPaths, (client, baseUrl, crawlerContext) -> {
            assertEquals(200, get(client, baseUrl + "/secret/page.html").getHttpStatusCode());
        });

        assertTrue(requestedPaths.contains("/robots.txt"));
        assertTrue(requestedPaths.contains("/secret/page.html"));
    }

    /**
     * A robots.txt the server cannot give right now does not mean "crawl everything": the URL fails as
     * unavailable so the crawler re-queues it, and the page itself is not fetched in the meantime.
     */
    @Test
    @Timeout(60)
    public void test_execute_unavailableRobotsTxtDefersTheUrl() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, Map.of("/robots.txt", status(503)), new HashMap<>(), new RecordingUrlFilter(), requestedPaths,
                (client, baseUrl, crawlerContext) -> {
                    try {
                        get(client, baseUrl + "/page.html");
                        fail(); // an unavailable robots.txt must defer the URL
                    } catch (final RobotsTxtUnavailableException e) {
                        assertTrue(e.getMessage().endsWith(baseUrl + "/page.html"));
                    }
                });

        assertTrue(requestedPaths.contains("/robots.txt"));
        assertFalse(requestedPaths.contains("/page.html"));
    }

    /** The crawl configuration can choose to crawl the site anyway, with the parameter the HTTP clients read. */
    @Test
    @Timeout(60)
    public void test_execute_unavailableRobotsTxtAllowedByConfiguration() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(HcHttpClient.ROBOTS_TXT_ALLOW_ON_UNAVAILABLE_PROPERTY, Boolean.TRUE);

        crawl(port, Map.of("/robots.txt", status(503)), paramMap, new RecordingUrlFilter(), requestedPaths,
                (client, baseUrl, crawlerContext) -> {
                    assertEquals(200, get(client, baseUrl + "/page.html").getHttpStatusCode());
                });

        assertTrue(requestedPaths.contains("/page.html"));
    }

    /**
     * A redirected robots.txt is followed, and its rules apply. The browser must hand the redirect back
     * instead of following it: the shared resolution counts the hops and stops a loop.
     */
    @Test
    @Timeout(60)
    public void test_execute_redirectedRobotsTxtIsFollowed() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Route> routes =
                Map.of("/robots.txt", redirect("/real-robots.txt"), "/real-robots.txt", text("User-agent: *\nDisallow: /secret/\n"));

        crawl(port, routes, new HashMap<>(), new RecordingUrlFilter(), requestedPaths, (client, baseUrl, crawlerContext) -> {
            try {
                get(client, baseUrl + "/secret/page.html");
                fail(); // the rules of the redirect target apply
            } catch (final RobotsTxtDisallowedException e) {
                // expected
            }
        });

        assertEquals(1L, requestedPaths.stream().filter("/robots.txt"::equals).count());
        assertEquals(1L, requestedPaths.stream().filter("/real-robots.txt"::equals).count());
        assertFalse(requestedPaths.contains("/secret/page.html"));
    }

    /** A robots.txt that loops redirecting is given up after a bounded number of hops, allowing the site. */
    @Test
    @Timeout(60)
    public void test_execute_robotsTxtRedirectLoopAllowsTheSite() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Route> routes = Map.of("/robots.txt", redirect("/loop.txt"), "/loop.txt", redirect("/robots.txt"));

        crawl(port, routes, new HashMap<>(), new RecordingUrlFilter(), requestedPaths, (client, baseUrl, crawlerContext) -> {
            assertEquals(200, get(client, baseUrl + "/page.html").getHttpStatusCode());
        });

        // 1 request plus 5 followed hops.
        assertEquals(6L, requestedPaths.stream().filter(p -> "/robots.txt".equals(p) || "/loop.txt".equals(p)).count());
        assertTrue(requestedPaths.contains("/page.html"));
    }

    /**
     * robots.txt comes from whatever host the crawl reached, so it is bounded like a page even when the
     * response declares no length. One over the limit is ignored, which allows the site.
     */
    @Test
    @Timeout(60)
    public void test_execute_oversizedRobotsTxtIsIgnored() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final StringBuilder robotsTxt = new StringBuilder("User-agent: *\nDisallow: /secret/\n");
        while (robotsTxt.length() < 4096) {
            robotsTxt.append("# padding to push the file over the limit\n");
        }
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("maxContentLength", 1024L);

        crawl(port, Map.of("/robots.txt", chunkedText(robotsTxt.toString())), paramMap, new RecordingUrlFilter(), requestedPaths,
                (client, baseUrl, crawlerContext) -> {
                    assertEquals(200, get(client, baseUrl + "/secret/page.html").getHttpStatusCode());
                });

        assertTrue(requestedPaths.contains("/robots.txt"));
        assertTrue(requestedPaths.contains("/secret/page.html"));
    }

    /**
     * The charset handed to the shared resolution comes from the Content-Type parameter. Nothing is
     * guessed here: without one it is null, and the resolution reads the file as UTF-8.
     */
    @Test
    public void test_getContentTypeCharset() {
        assertEquals("UTF-8", PlaywrightClient.getContentTypeCharset("text/plain;charset=UTF-8"));
        assertEquals("ISO-8859-1", PlaywrightClient.getContentTypeCharset("text/plain; Charset=\"ISO-8859-1\""));
        assertEquals("Shift_JIS", PlaywrightClient.getContentTypeCharset("text/plain; format=flowed; charset = Shift_JIS ;"));
        assertNull(PlaywrightClient.getContentTypeCharset("text/plain"));
        assertNull(PlaywrightClient.getContentTypeCharset("text/plain; charset="));
        assertNull(PlaywrightClient.getContentTypeCharset("text/plain; charset=\"\""));
        assertNull(PlaywrightClient.getContentTypeCharset(""));
        assertNull(PlaywrightClient.getContentTypeCharset(null));
    }

    /**
     * A client that has no container to resolve the helper from still crawls.
     *
     * <p>Only a client built through the container has one, and the README shows building one directly.
     * Every page of every crawl passes through the robots.txt step, so a lookup that cannot fail softly
     * fails all of them - and this is the one test that builds a client the way those callers do.</p>
     */
    @Test
    @Timeout(60)
    public void test_execute_withoutAContainerStillCrawls() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final Server server = startSite(port, robotsTxt("User-agent: *\nDisallow: /secret/\n"), requestedPaths);
        final CrawlerContext crawlerContext = new CrawlerContext();
        crawlerContext.setUrlFilter(new RecordingUrlFilter());
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        // No getRobotsTxtHelper() override: this exercises the real lookup, with no container behind it.
        final PlaywrightClient client = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.of(mimeTypeHelper);
            }
        };
        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        client.setDownloadTimeout(5);
        client.setCloseTimeout(10);
        try {
            client.init();
            CrawlingParameterUtil.setCrawlerContext(crawlerContext);

            assertEquals(200, get(client, "http://[::1]:" + port + "/").getHttpStatusCode());
            assertFalse(requestedPaths.contains("/robots.txt"));
        } finally {
            CrawlingParameterUtil.setCrawlerContext(null);
            client.close();
            server.stop();
        }
    }

    /**
     * The crawl configuration can turn it off, with the same parameter name the HTTP clients read. Off
     * has to mean the request is never made - not made and then ignored.
     */
    @Test
    @Timeout(60)
    public void test_execute_disabledByConfigurationDoesNotFetchRobotsTxt() throws Exception {
        final int port = freePort();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(HcHttpClient.ROBOTS_TXT_ENABLED_PROPERTY, Boolean.FALSE);

        crawl(port, robotsTxt("User-agent: *\nDisallow: /secret/\n"), paramMap, new RecordingUrlFilter(), requestedPaths,
                (client, baseUrl, crawlerContext) -> {
                    assertEquals(200, get(client, baseUrl + "/secret/page.html").getHttpStatusCode());
                });

        assertFalse(requestedPaths.contains("/robots.txt"));
    }
}
