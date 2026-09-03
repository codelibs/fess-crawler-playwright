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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.codelibs.fess.crawler.CrawlerContext;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
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
 * HTTP clients read it in {@code processRobotsTxt}; this client is not one of them, so it needs its
 * own, and these tests pin the same outcome: the directives reach the {@link CrawlerContext} that
 * decides what gets queued.</p>
 */
public class PlaywrightClientRobotsTxtTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    /** Records what the crawl asked the filter to keep out, which is where robots.txt directives land. */
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

    /**
     * Serves one page for everything, plus a robots.txt (or a 404 for it when {@code robotsTxt} is
     * null), and records every path asked for so a test can tell a fetch from a cached decision.
     */
    private static Server startSite(final int port, final String robotsTxt, final List<String> requestedPaths) throws Exception {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
                final String path = request.getHttpURI().getPath();
                requestedPaths.add(path);
                if ("/robots.txt".equals(path)) {
                    if (robotsTxt == null) {
                        response.setStatus(404);
                        callback.succeeded();
                        return true;
                    }
                    response.setStatus(200);
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=UTF-8");
                    Content.Sink.write(response, true, robotsTxt, callback);
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

    /** Runs one crawl of {@code /} against a site with the given robots.txt, and reports what it did. */
    private static void crawl(final int port, final String robotsTxt, final Map<String, Object> paramMap,
            final RobotsTxtHelper robotsTxtHelper, final RecordingUrlFilter urlFilter, final List<String> requestedPaths, final int times)
            throws Exception {
        final Server server = startSite(port, robotsTxt, requestedPaths);
        final CrawlerContext crawlerContext = new CrawlerContext();
        crawlerContext.setUrlFilter(urlFilter);
        final PlaywrightClient client = newClient(paramMap, robotsTxtHelper);
        try {
            client.init();
            CrawlingParameterUtil.setCrawlerContext(crawlerContext);
            for (int i = 0; i < times; i++) {
                client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());
            }
        } finally {
            CrawlingParameterUtil.setCrawlerContext(null);
            client.close();
            server.stop();
        }
    }

    /**
     * The reason this exists: a Disallow has to keep the crawl out of that path, and the only thing that
     * can act on it is the URL filter the crawler consults before queueing a link.
     */
    @Test
    @Timeout(60)
    public void test_execute_disallowBecomesAnExclude() throws Exception {
        final int port = 7630;
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, "User-agent: *\nDisallow: /secret/\n", new HashMap<>(), new RobotsTxtHelper(), urlFilter, requestedPaths, 1);

        assertEquals(List.of("http://[::1]:" + port + "/secret/.*"), urlFilter.excludes);
        assertTrue(requestedPaths.contains("/robots.txt"));
    }

    /** An Allow is the other half of the same directive and reaches the filter the same way. */
    @Test
    @Timeout(60)
    public void test_execute_allowBecomesAnInclude() throws Exception {
        final int port = 7631;
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, "User-agent: *\nDisallow: /secret/\nAllow: /secret/public/\n", new HashMap<>(), new RobotsTxtHelper(), urlFilter,
                requestedPaths, 1);

        assertEquals(List.of("http://[::1]:" + port + "/secret/public/.*"), urlFilter.includes);
    }

    /** robots.txt is also where a site advertises its sitemaps, and the crawler reads them from the context. */
    @Test
    @Timeout(60)
    public void test_execute_sitemapsReachTheCrawlerContext() throws Exception {
        final int port = 7632;
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final Server server = startSite(port, "Sitemap: http://[::1]:" + port + "/sitemap.xml\nUser-agent: *\n", requestedPaths);
        final CrawlerContext crawlerContext = new CrawlerContext();
        crawlerContext.setUrlFilter(urlFilter);
        final PlaywrightClient client = newClient(new HashMap<>(), new RobotsTxtHelper());
        try {
            client.init();
            CrawlingParameterUtil.setCrawlerContext(crawlerContext);
            client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build());

            // removeSitemaps() reads the thread-local the crawl wrote, so it has to be read on this thread.
            assertEquals(1, crawlerContext.removeSitemaps().length);
        } finally {
            CrawlingParameterUtil.setCrawlerContext(null);
            client.close();
            server.stop();
        }
    }

    /**
     * One fetch per host, however many pages are crawled. Without the check every page of a site would
     * pull robots.txt again, which is a request the site did not ask for and a cost the crawl pays for
     * nothing.
     */
    @Test
    @Timeout(60)
    public void test_execute_robotsTxtIsFetchedOncePerHost() throws Exception {
        final int port = 7633;
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, "User-agent: *\nDisallow: /secret/\n", new HashMap<>(), new RobotsTxtHelper(), urlFilter, requestedPaths, 3);

        assertEquals(1L, requestedPaths.stream().filter("/robots.txt"::equals).count());
        // ... and the directive is still applied exactly once, not once per page.
        assertEquals(1, urlFilter.excludes.size());
    }

    /** A site without robots.txt is the common case and must not turn into a failed crawl. */
    @Test
    @Timeout(60)
    public void test_execute_missingRobotsTxtIsNotAFailure() throws Exception {
        final int port = 7634;
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());

        crawl(port, null, new HashMap<>(), new RobotsTxtHelper(), urlFilter, requestedPaths, 1);

        assertTrue(requestedPaths.contains("/robots.txt"));
        assertTrue(urlFilter.excludes.isEmpty());
        // The page itself was still crawled.
        assertTrue(requestedPaths.contains("/"));
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
        final int port = 7636;
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final Server server = startSite(port, "User-agent: *\nDisallow: /secret/\n", requestedPaths);
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

            assertEquals(200, client.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + port + "/").build())
                    .getHttpStatusCode());
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
        final int port = 7635;
        final RecordingUrlFilter urlFilter = new RecordingUrlFilter();
        final List<String> requestedPaths = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put(HcHttpClient.ROBOTS_TXT_ENABLED_PROPERTY, Boolean.FALSE);

        crawl(port, "User-agent: *\nDisallow: /secret/\n", paramMap, new RobotsTxtHelper(), urlFilter, requestedPaths, 1);

        assertFalse(requestedPaths.contains("/robots.txt"));
        assertTrue(urlFilter.excludes.isEmpty());
    }
}
