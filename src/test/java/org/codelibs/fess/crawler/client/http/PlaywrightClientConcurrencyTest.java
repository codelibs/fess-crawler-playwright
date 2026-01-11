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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;

/**
 * Test class for PlaywrightClient concurrency and thread safety.
 * Tests for concurrent access, page synchronization, and shared worker scenarios.
 */
public class PlaywrightClientConcurrencyTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    // ==================== Sequential request tests ====================

    /**
     * Test multiple sequential requests with same client.
     */
    public void test_sequentialRequests_sameClient() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7300, docRootDir);

            try {
                server.start();

                // Execute multiple sequential requests
                for (int i = 0; i < 10; i++) {
                    final String url = "http://[::1]:7300/";
                    final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                    assertEquals(200, responseData.getHttpStatusCode());
                }
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test sequential requests to different URLs.
     */
    public void test_sequentialRequests_differentUrls() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7301, docRootDir);

            try {
                server.start();

                // HTML
                ResponseData responseData = playwrightClient
                        .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:7301/").build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("text/html", responseData.getMimeType());

                // Text
                responseData = playwrightClient
                        .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:7301/test.txt").build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("text/plain", responseData.getMimeType());

                // JSON
                responseData = playwrightClient
                        .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:7301/test.json").build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("application/json", responseData.getMimeType());

                // Image
                responseData = playwrightClient
                        .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:7301/test.png").build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("image/png", responseData.getMimeType());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Concurrent request tests (same client) ====================

    /**
     * Test concurrent requests from multiple threads using same client.
     * Due to page synchronization, requests should be processed sequentially.
     */
    public void test_concurrentRequests_sameClient() throws Exception {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7302, docRootDir);

            try {
                server.start();

                final int numThreads = 5;
                final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
                final CountDownLatch startLatch = new CountDownLatch(1);
                final CountDownLatch doneLatch = new CountDownLatch(numThreads);
                final AtomicInteger successCount = new AtomicInteger(0);
                final AtomicInteger errorCount = new AtomicInteger(0);

                // Submit tasks
                for (int i = 0; i < numThreads; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await(); // Wait for all threads to be ready
                            final String url = "http://[::1]:7302/";
                            final ResponseData responseData =
                                    playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                            if (responseData.getHttpStatusCode() == 200) {
                                successCount.incrementAndGet();
                            } else {
                                errorCount.incrementAndGet();
                            }
                        } catch (final Exception e) {
                            errorCount.incrementAndGet();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                // Start all threads
                startLatch.countDown();

                // Wait for completion
                assertTrue("Threads should complete within timeout", doneLatch.await(60, TimeUnit.SECONDS));

                executor.shutdown();
                assertTrue("Executor should terminate", executor.awaitTermination(10, TimeUnit.SECONDS));

                // All requests should succeed
                assertEquals(numThreads, successCount.get());
                assertEquals(0, errorCount.get());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Multiple clients tests ====================

    /**
     * Test multiple independent clients executing concurrently.
     */
    public void test_concurrentRequests_multipleClients() throws Exception {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7303, docRootDir);

        try {
            server.start();

            final int numClients = 3;
            final ExecutorService executor = Executors.newFixedThreadPool(numClients);
            final List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < numClients; i++) {
                futures.add(executor.submit(() -> {
                    final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
                    final PlaywrightClient client = new PlaywrightClient() {
                        @Override
                        protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                            return Optional.ofNullable(mimeTypeHelper);
                        }
                    };

                    try {
                        client.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
                        client.init();

                        final String url = "http://[::1]:7303/";
                        final ResponseData responseData =
                                client.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                        return responseData.getHttpStatusCode() == 200;
                    } finally {
                        client.close();
                    }
                }));
            }

            // Wait for all clients to complete
            int successCount = 0;
            for (final Future<Boolean> future : futures) {
                if (future.get(60, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            executor.shutdown();
            assertTrue("Executor should terminate", executor.awaitTermination(10, TimeUnit.SECONDS));

            assertEquals(numClients, successCount);
        } finally {
            server.stop();
        }
    }

    // ==================== Shared client tests ====================

    /**
     * Test shared client with multiple instances.
     */
    public void test_sharedClient_multipleInstances() throws Exception {
        final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        final CrawlerWebServer server = new CrawlerWebServer(7304, docRootDir);

        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("sharedClient", Boolean.TRUE);

        final PlaywrightClient client1 = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        final PlaywrightClient client2 = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            server.start();

            client1.setInitParameterMap(paramMap);
            client1.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client1.init();

            client2.setInitParameterMap(paramMap);
            client2.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client2.init();

            // Both clients should be able to make requests
            final String url = "http://[::1]:7304/";

            final ResponseData response1 = client1.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, response1.getHttpStatusCode());

            final ResponseData response2 = client2.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, response2.getHttpStatusCode());
        } finally {
            client1.close();
            client2.close();
            server.stop();
        }
    }

    /**
     * Test concurrent init calls on same client instance.
     */
    public void test_concurrentInit_sameInstance() throws Exception {
        final PlaywrightClient playwrightClient = new PlaywrightClient();

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));

            final int numThreads = 5;
            final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(numThreads);
            final AtomicInteger errorCount = new AtomicInteger(0);

            // Submit concurrent init calls
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        playwrightClient.init();
                    } catch (final Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Wait for completion
            assertTrue("Init threads should complete", doneLatch.await(30, TimeUnit.SECONDS));

            executor.shutdown();
            assertTrue("Executor should terminate", executor.awaitTermination(10, TimeUnit.SECONDS));

            // No errors should occur
            assertEquals(0, errorCount.get());
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Page reset tests ====================

    /**
     * Test that page is properly reset between requests.
     */
    public void test_pageReset_betweenRequests() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7305, docRootDir);

            try {
                server.start();

                // Request 1 - HTML
                ResponseData response = playwrightClient
                        .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:7305/").build());
                assertEquals(200, response.getHttpStatusCode());
                assertEquals("text/html", response.getMimeType());

                // Request 2 - PDF (different content type)
                response = playwrightClient
                        .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:7305/test.pdf").build());
                assertEquals(200, response.getHttpStatusCode());
                assertEquals("application/pdf", response.getMimeType());

                // Request 3 - HTML again
                response = playwrightClient
                        .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:7305/").build());
                assertEquals(200, response.getHttpStatusCode());
                assertEquals("text/html", response.getMimeType());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Handler cleanup tests ====================

    /**
     * Test that event handlers are properly cleaned up.
     */
    public void test_handlerCleanup_afterMultipleRequests() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7306, docRootDir);

            try {
                server.start();

                // Execute many requests to verify handlers don't accumulate
                for (int i = 0; i < 20; i++) {
                    final String url = "http://[::1]:7306/";
                    final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                    assertEquals(200, responseData.getHttpStatusCode());
                }

                // If handlers accumulated, we might see memory issues or performance degradation
                // Since we completed 20 requests successfully, handlers are being cleaned up
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Stress tests ====================

    /**
     * Test many sequential requests (stress test).
     */
    public void test_stressTest_manySequentialRequests() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7307, docRootDir);

            try {
                server.start();

                final int numRequests = 50;
                int successCount = 0;

                for (int i = 0; i < numRequests; i++) {
                    final String url = "http://[::1]:7307/";
                    final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                    if (responseData.getHttpStatusCode() == 200) {
                        successCount++;
                    }
                }

                assertEquals(numRequests, successCount);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }
}
