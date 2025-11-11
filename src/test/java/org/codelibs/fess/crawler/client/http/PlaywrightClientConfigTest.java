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
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.LoadState;

/**
 * Test class for PlaywrightClient configuration and browser types.
 *
 * @author shinsuke
 */
public class PlaywrightClientConfigTest extends PlainTestCase {

    private static boolean headless = true;

    /**
     * Test for Chromium browser type (default).
     */
    public void test_browserType_chromium() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setBrowserName("chromium");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7071, docRootDir);

            final String url = "http://[::1]:7071/";
            try {
                server.start();
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertTrue(getBodyAsString(responseData).contains("content page"));
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for Firefox browser type.
     */
    public void test_browserType_firefox() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setBrowserName("firefox");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7072, docRootDir);

            final String url = "http://[::1]:7072/";
            try {
                server.start();
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertTrue(getBodyAsString(responseData).contains("content page"));
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for Webkit browser type.
     */
    public void test_browserType_webkit() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setBrowserName("webkit");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7073, docRootDir);

            final String url = "http://[::1]:7073/";
            try {
                server.start();
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertTrue(getBodyAsString(responseData).contains("content page"));
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for invalid browser type.
     */
    public void test_browserType_invalid() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setBrowserName("invalid-browser");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();
            fail("Expected CrawlerSystemException for invalid browser name");
        } catch (final CrawlerSystemException e) {
            assertTrue(e.getMessage().contains("Unknown browser name"));
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for renderedState configuration with LOAD state.
     */
    public void test_renderedState_load() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("renderedState", "LOAD");
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7074, docRootDir);

            final String url = "http://[::1]:7074/";
            try {
                server.start();
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
     * Test for renderedState configuration with DOMCONTENTLOADED state.
     */
    public void test_renderedState_domContentLoaded() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("renderedState", "DOMCONTENTLOADED");
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7075, docRootDir);

            final String url = "http://[::1]:7075/";
            try {
                server.start();
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
     * Test for renderedState configuration with NETWORKIDLE state.
     */
    public void test_renderedState_networkIdle() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("renderedState", "NETWORKIDLE");
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7076, docRootDir);

            final String url = "http://[::1]:7076/";
            try {
                server.start();
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
     * Test for contentWaitDuration configuration.
     */
    public void test_contentWaitDuration() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("contentWaitDuration", 500L); // Wait 500ms
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7077, docRootDir);

            final String url = "http://[::1]:7077/";
            try {
                server.start();
                final long startTime = System.currentTimeMillis();
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                final long duration = System.currentTimeMillis() - startTime;

                assertEquals(200, responseData.getHttpStatusCode());
                // Should have waited at least 500ms
                assertTrue(duration >= 500);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for setRenderedState method.
     */
    public void test_setRenderedState() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setRenderedState(LoadState.LOAD);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            // Verify the client is initialized successfully
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for setDownloadTimeout method.
     */
    public void test_setDownloadTimeout() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setDownloadTimeout(30);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            // Verify the client is initialized successfully
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for setCloseTimeout method.
     */
    public void test_setCloseTimeout() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setCloseTimeout(30);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            // Verify the client is initialized successfully
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for addOption method.
     */
    public void test_addOption() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.addOption("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            // Verify the client is initialized successfully
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for multiple init calls (should skip subsequent calls).
     */
    public void test_multipleInitCalls() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();
            playwrightClient.init(); // Should skip this call
            playwrightClient.init(); // Should skip this call

            // Verify the client is still working
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test for sharedClient configuration.
     */
    public void test_sharedClient_enabled() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
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
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("sharedClient", Boolean.TRUE);

            client1.setInitParameterMap(paramMap);
            client1.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            client1.init();

            client2.setInitParameterMap(paramMap);
            client2.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            client2.init();

            // Both clients should be initialized
            assertNotNull(client1);
            assertNotNull(client2);
        } finally {
            client1.close();
            client2.close();
        }
    }

    /**
     * Test for close when worker is null.
     */
    public void test_close_whenWorkerIsNull() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        // Should not throw exception when closing without init
        playwrightClient.close();
    }

    /**
     * Test for execute with auto-init.
     */
    public void test_execute_autoInit() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            // Don't call init() - it should be called automatically

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7078, docRootDir);

            final String url = "http://[::1]:7078/";
            try {
                server.start();
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    private String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }
}
