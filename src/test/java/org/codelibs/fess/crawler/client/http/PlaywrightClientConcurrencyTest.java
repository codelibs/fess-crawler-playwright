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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    private static final int SERVER_PORT = 7150;

    private static CrawlerWebServer sharedServer;
    private static File docRootDir;

    @BeforeAll
    static void setUpClass() {
        docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
        sharedServer = new CrawlerWebServer(SERVER_PORT, docRootDir);
        sharedServer.start();
    }

    @AfterAll
    static void tearDownClass() {
        if (sharedServer != null) {
            sharedServer.stop();
        }
    }

    // ==================== Sequential request tests ====================

    /**
     * Test multiple sequential requests with same client.
     */
    @Test
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
            playwrightClient.setCloseTimeout(5);
            playwrightClient.init();

            // Execute multiple sequential requests
            for (int i = 0; i < 10; i++) {
                final String url = "http://[::1]:" + SERVER_PORT + "/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test sequential requests to different URLs.
     */
    @Test
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
            playwrightClient.setCloseTimeout(5);
            playwrightClient.init();

            // HTML
            ResponseData responseData =
                    playwrightClient.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + SERVER_PORT + "/").build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("text/html", responseData.getMimeType());

            // Text
            responseData = playwrightClient
                    .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + SERVER_PORT + "/test.txt").build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("text/plain", responseData.getMimeType());

            // JSON
            responseData = playwrightClient
                    .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + SERVER_PORT + "/test.json").build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("application/json", responseData.getMimeType());

            // Image
            responseData = playwrightClient
                    .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + SERVER_PORT + "/test.png").build());
            assertEquals(200, responseData.getHttpStatusCode());
            assertEquals("image/png", responseData.getMimeType());
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Concurrent request tests (same client) ====================

    /**
     * Test concurrent requests from multiple threads using same client.
     * Due to page synchronization, requests should be processed sequentially.
     */
    @Test
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
            playwrightClient.setCloseTimeout(5);
            playwrightClient.init();

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
                        final String url = "http://[::1]:" + SERVER_PORT + "/";
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
            assertTrue(doneLatch.await(60, TimeUnit.SECONDS));

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // All requests should succeed
            assertEquals(numThreads, successCount.get());
            assertEquals(0, errorCount.get());
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Multiple clients tests ====================

    /**
     * Test multiple independent clients executing concurrently.
     */
    @Test
    public void test_concurrentRequests_multipleClients() throws Exception {
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
                    client.setCloseTimeout(5);
                    client.init();

                    final String url = "http://[::1]:" + SERVER_PORT + "/";
                    final ResponseData responseData = client.execute(RequestDataBuilder.newRequestData().get().url(url).build());
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
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(numClients, successCount);
    }

    // ==================== Shared client tests ====================

    /**
     * Test shared client with multiple instances.
     */
    @Test
    public void test_sharedClient_multipleInstances() throws Exception {
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
            client1.setInitParameterMap(paramMap);
            client1.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client1.setCloseTimeout(5);
            client1.init();

            client2.setInitParameterMap(paramMap);
            client2.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client2.setCloseTimeout(5);
            client2.init();

            // Both clients should be able to make requests
            final String url = "http://[::1]:" + SERVER_PORT + "/";

            final ResponseData response1 = client1.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, response1.getHttpStatusCode());

            final ResponseData response2 = client2.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            assertEquals(200, response2.getHttpStatusCode());
        } finally {
            client1.close();
            client2.close();
        }
    }

    /**
     * Test concurrent init calls on same client instance.
     */
    @Test
    public void test_concurrentInit_sameInstance() throws Exception {
        final PlaywrightClient playwrightClient = new PlaywrightClient();

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setCloseTimeout(5);

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
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

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
    @Test
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
            playwrightClient.setCloseTimeout(5);
            playwrightClient.init();

            // Request 1 - HTML
            ResponseData response =
                    playwrightClient.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + SERVER_PORT + "/").build());
            assertEquals(200, response.getHttpStatusCode());
            assertEquals("text/html", response.getMimeType());

            // Request 2 - PDF (different content type)
            response = playwrightClient
                    .execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + SERVER_PORT + "/test.pdf").build());
            assertEquals(200, response.getHttpStatusCode());
            assertEquals("application/pdf", response.getMimeType());

            // Request 3 - HTML again
            response = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:" + SERVER_PORT + "/").build());
            assertEquals(200, response.getHttpStatusCode());
            assertEquals("text/html", response.getMimeType());
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Handler cleanup tests ====================

    /**
     * Test that event handlers are properly cleaned up.
     */
    @Test
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
            playwrightClient.setCloseTimeout(5);
            playwrightClient.init();

            // Execute many requests to verify handlers don't accumulate
            for (int i = 0; i < 20; i++) {
                final String url = "http://[::1]:" + SERVER_PORT + "/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            }

            // If handlers accumulated, we might see memory issues or performance degradation
            // Since we completed 20 requests successfully, handlers are being cleaned up
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Reference counting tests ====================

    /**
     * Test that closing one shared client doesn't affect other clients.
     * This verifies the reference counting implementation.
     */
    @Test
    public void test_sharedClient_referenceCountBehavior() throws Exception {
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

        final PlaywrightClient client3 = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            // Initialize all clients
            client1.setInitParameterMap(paramMap);
            client1.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client1.setCloseTimeout(5);
            client1.init();

            client2.setInitParameterMap(paramMap);
            client2.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client2.setCloseTimeout(5);
            client2.init();

            client3.setInitParameterMap(paramMap);
            client3.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client3.setCloseTimeout(5);
            client3.init();

            final String url = "http://[::1]:" + SERVER_PORT + "/";

            // All clients should work
            assertEquals(200, client1.execute(RequestDataBuilder.newRequestData().get().url(url).build()).getHttpStatusCode());
            assertEquals(200, client2.execute(RequestDataBuilder.newRequestData().get().url(url).build()).getHttpStatusCode());
            assertEquals(200, client3.execute(RequestDataBuilder.newRequestData().get().url(url).build()).getHttpStatusCode());

            // Close client1 - other clients should still work
            client1.close();
            assertEquals(200, client2.execute(RequestDataBuilder.newRequestData().get().url(url).build()).getHttpStatusCode());
            assertEquals(200, client3.execute(RequestDataBuilder.newRequestData().get().url(url).build()).getHttpStatusCode());

            // Close client2 - client3 should still work
            client2.close();
            assertEquals(200, client3.execute(RequestDataBuilder.newRequestData().get().url(url).build()).getHttpStatusCode());
        } finally {
            // Ensure cleanup
            try {
                client1.close();
            } catch (final Exception e) {
                // ignore
            }
            try {
                client2.close();
            } catch (final Exception e) {
                // ignore
            }
            try {
                client3.close();
            } catch (final Exception e) {
                // ignore
            }
        }
    }

    /**
     * Test that duplicate close() calls don't cause issues.
     */
    @Test
    public void test_sharedClient_duplicateClose() throws Exception {
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
            client1.setInitParameterMap(paramMap);
            client1.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client1.setCloseTimeout(5);
            client1.init();

            client2.setInitParameterMap(paramMap);
            client2.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            client2.setCloseTimeout(5);
            client2.init();

            // Close client1 multiple times - should not cause issues
            client1.close();
            client1.close(); // Should be a no-op
            client1.close(); // Should be a no-op

            // client2 should still work
            final String url = "http://[::1]:" + SERVER_PORT + "/";
            assertEquals(200, client2.execute(RequestDataBuilder.newRequestData().get().url(url).build()).getHttpStatusCode());
        } finally {
            try {
                client2.close();
            } catch (final Exception e) {
                // ignore
            }
        }
    }

    // ==================== Stress tests ====================

    /**
     * Test many sequential requests (stress test).
     */
    @Test
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
            playwrightClient.setCloseTimeout(5);
            playwrightClient.init();

            final int numRequests = 50;
            int successCount = 0;

            for (int i = 0; i < numRequests; i++) {
                final String url = "http://[::1]:" + SERVER_PORT + "/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                if (responseData.getHttpStatusCode() == 200) {
                    successCount++;
                }
            }

            assertEquals(numRequests, successCount);
        } finally {
            playwrightClient.close();
        }
    }
}
