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
import java.util.Optional;

import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;

/**
 * Test class for PlaywrightClient exception handling scenarios.
 * Tests for various error conditions and exception cases.
 */
public class PlaywrightClientExceptionTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    // ==================== Invalid browser name tests ====================

    /**
     * Test that invalid browser name throws CrawlerSystemException.
     */
    public void test_init_invalidBrowserName() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setBrowserName("invalid-browser");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();
            fail("Expected CrawlerSystemException for invalid browser name");
        } catch (final CrawlerSystemException e) {
            assertTrue(e.getMessage().contains("Failed to create Playwright worker")
                    || e.getMessage().contains("Unsupported browser") || e.getMessage().contains("invalid-browser"));
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test that uppercase browser name is handled.
     */
    public void test_init_uppercaseBrowserName() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setBrowserName("CHROMIUM");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();
            fail("Expected CrawlerSystemException for uppercase browser name");
        } catch (final CrawlerSystemException e) {
            // Expected - browser names are case-sensitive
            assertNotNull(e.getMessage());
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test that empty browser name throws exception.
     */
    public void test_init_emptyBrowserName() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setBrowserName("");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();
            fail("Expected exception for empty browser name");
        } catch (final Exception e) {
            // Expected exception
            assertNotNull(e);
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Invalid URL tests ====================

    /**
     * Test accessing invalid URL throws CrawlingAccessException.
     */
    public void test_execute_invalidUrl() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setDownloadTimeout(2);
            playwrightClient.init();

            final String url = "http://invalid-domain-that-does-not-exist-xyz123.com/";
            playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            fail("Expected CrawlingAccessException for invalid URL");
        } catch (final CrawlingAccessException e) {
            assertTrue(e.getMessage().contains("Failed to access"));
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test accessing malformed URL.
     */
    public void test_execute_malformedUrl() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setDownloadTimeout(2);
            playwrightClient.init();

            final String url = "not-a-valid-url";
            playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            fail("Expected exception for malformed URL");
        } catch (final Exception e) {
            // Expected exception
            assertNotNull(e);
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Connection refused tests ====================

    /**
     * Test accessing URL with connection refused.
     */
    public void test_execute_connectionRefused() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setDownloadTimeout(2);
            playwrightClient.init();

            // Use a port that is likely not in use
            final String url = "http://127.0.0.1:59999/";
            playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            fail("Expected CrawlingAccessException for connection refused");
        } catch (final CrawlingAccessException e) {
            assertTrue(e.getMessage().contains("Failed to access"));
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== HTTP error status tests ====================

    /**
     * Test 404 Not Found response.
     */
    public void test_execute_status404() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7200, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7200/nonexistent.html";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(404, responseData.getHttpStatusCode());
                assertEquals(0, responseData.getContentLength());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test that status codes > 400 return empty body.
     */
    public void test_execute_errorStatusReturnsEmptyBody() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7201, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7201/does-not-exist.xyz";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertTrue(responseData.getHttpStatusCode() >= 400);
                assertEquals(0, responseData.getContentLength());
                assertNotNull(responseData.getResponseBody());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Timeout tests ====================

    /**
     * Test with very short download timeout.
     */
    public void test_execute_shortDownloadTimeout() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setDownloadTimeout(1); // 1 second timeout
            playwrightClient.init();

            // Try to access a non-responding server
            final String url = "http://[::1]:59998/";
            playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            fail("Expected CrawlingAccessException for timeout");
        } catch (final CrawlingAccessException e) {
            assertTrue(e.getMessage().contains("Failed to access"));
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Close timeout tests ====================

    /**
     * Test close with very short timeout.
     */
    public void test_close_shortTimeout() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setCloseTimeout(1); // 1 second timeout
            playwrightClient.init();
        } finally {
            // Should complete even with short timeout
            playwrightClient.close();
        }
    }

    // ==================== Worker state tests ====================

    /**
     * Test execute triggers auto-init when worker is null.
     */
    public void test_execute_autoInitOnNullWorker() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            // Don't call init() - execute should auto-init

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7202, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7202/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test close after close (should not throw).
     */
    public void test_close_afterClose() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        playwrightClient.init();

        playwrightClient.close();
        // Second close should not throw
        playwrightClient.close();
        // Third close should not throw
        playwrightClient.close();
    }

    /**
     * Test init after close (re-initialization).
     */
    public void test_init_afterClose() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));

            // First init
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7203, docRootDir);

            try {
                server.start();

                // First request
                String url = "http://[::1]:7203/";
                ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());

                // Close
                playwrightClient.close();

                // Re-init
                playwrightClient.init();

                // Second request after re-init
                responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Multiple init calls tests ====================

    /**
     * Test multiple init calls (should be idempotent).
     */
    public void test_init_multipleCalls() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));

            playwrightClient.init();
            playwrightClient.init(); // Should skip
            playwrightClient.init(); // Should skip
            playwrightClient.init(); // Should skip

            // No exception means success
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Exception message content tests ====================

    /**
     * Test that exception messages contain useful information.
     */
    public void test_exception_messageContainsUrl() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setDownloadTimeout(2);
            playwrightClient.init();

            final String url = "http://127.0.0.1:59997/test";
            playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            fail("Expected CrawlingAccessException");
        } catch (final CrawlingAccessException e) {
            // Exception message should contain URL information
            assertTrue("Exception should contain URL", e.getMessage().contains("URL"));
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test that exception messages contain timeout information.
     */
    public void test_exception_messageContainsTimeout() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setDownloadTimeout(3);
            playwrightClient.init();

            final String url = "http://127.0.0.1:59996/";
            playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
            fail("Expected CrawlingAccessException");
        } catch (final CrawlingAccessException e) {
            // Exception message should contain timeout information
            assertTrue("Exception should contain Timeout", e.getMessage().contains("Timeout"));
        } finally {
            playwrightClient.close();
        }
    }
}
