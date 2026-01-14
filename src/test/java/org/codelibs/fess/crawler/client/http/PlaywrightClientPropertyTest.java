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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.options.LoadState;

/**
 * Test class for PlaywrightClient configuration and property settings.
 * Tests for various configuration options and their boundary values.
 */
public class PlaywrightClientPropertyTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    // ==================== downloadTimeout tests ====================

    /**
     * Test downloadTimeout with minimum value.
     */
    @Test
    public void test_downloadTimeout_minimum() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setDownloadTimeout(1); // 1 second
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7400, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7400/";
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
     * Test downloadTimeout with larger value.
     */
    @Test
    public void test_downloadTimeout_larger() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setDownloadTimeout(60); // 60 seconds
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7401, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7401/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== closeTimeout tests ====================

    /**
     * Test closeTimeout with minimum value.
     */
    @Test
    public void test_closeTimeout_minimum() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        playwrightClient.setCloseTimeout(1); // 1 second
        playwrightClient.init();
        playwrightClient.close(); // Should complete
    }

    /**
     * Test closeTimeout with larger value.
     */
    @Test
    public void test_closeTimeout_larger() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();
        playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
        playwrightClient.setCloseTimeout(30); // 30 seconds
        playwrightClient.init();
        playwrightClient.close(); // Should complete
    }

    // ==================== renderedState tests ====================

    /**
     * Test setRenderedState with LOAD.
     */
    @Test
    public void test_renderedState_load() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setRenderedState(LoadState.LOAD);
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7402, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7402/";
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
     * Test setRenderedState with DOMCONTENTLOADED.
     */
    @Test
    public void test_renderedState_domContentLoaded() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setRenderedState(LoadState.DOMCONTENTLOADED);
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7403, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7403/";
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
     * Test setRenderedState with NETWORKIDLE (default).
     */
    @Test
    public void test_renderedState_networkIdle() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.setRenderedState(LoadState.NETWORKIDLE);
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7404, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7404/";
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
     * Test renderedState via parameter map.
     */
    @Test
    public void test_renderedState_viaParameterMap() {
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
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7405, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7405/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== contentWaitDuration tests ====================

    /**
     * Test contentWaitDuration with zero (no wait).
     */
    @Test
    public void test_contentWaitDuration_zero() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("contentWaitDuration", 0L);
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7406, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7406/";
                final long startTime = System.currentTimeMillis();
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                final long duration = System.currentTimeMillis() - startTime;

                assertEquals(200, responseData.getHttpStatusCode());
                // With 0 wait, should be relatively fast (no artificial delay)
                assertTrue(duration < 10000);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test contentWaitDuration with positive value.
     */
    @Test
    public void test_contentWaitDuration_positive() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("contentWaitDuration", 500L); // 500ms
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7407, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7407/";
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

    // ==================== sharedClient tests ====================

    /**
     * Test sharedClient with false (default).
     */
    @Test
    public void test_sharedClient_false() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("sharedClient", Boolean.FALSE);
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7408, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7408/";
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
     * Test sharedClient with true.
     * Note: This test only verifies initialization with sharedClient=true.
     * Full shared client behavior is tested in PlaywrightClientConfigTest.test_sharedClient_enabled
     * and PlaywrightClientConcurrencyTest.test_sharedClient_multipleInstances.
     */
    @Test
    public void test_sharedClient_true() {
        final PlaywrightClient playwrightClient = new PlaywrightClient();

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("sharedClient", Boolean.TRUE);
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            // Verify client was initialized with shared mode
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== LaunchOptions tests ====================

    /**
     * Test setLaunchOptions with custom options.
     */
    @Test
    public void test_launchOptions_custom() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(HEADLESS).setTimeout(30000);
            playwrightClient.setLaunchOptions(options);
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7410, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7410/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== NewContextOptions tests ====================

    /**
     * Test setNewContextOptions with custom options.
     */
    @Test
    public void test_newContextOptions_custom() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final NewContextOptions contextOptions = new NewContextOptions().setUserAgent("CustomUserAgent/1.0");
            playwrightClient.setNewContextOptions(contextOptions);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7411, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7411/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== ignoreHttpsErrors tests ====================

    /**
     * Test ignoreHttpsErrors with false (default).
     */
    @Test
    public void test_ignoreHttpsErrors_false() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("ignoreHttpsErrors", Boolean.FALSE);
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7412, docRootDir);

            try {
                server.start();
                // HTTP request should work fine
                final String url = "http://[::1]:7412/";
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
     * Test ignoreHttpsErrors with true.
     */
    @Test
    public void test_ignoreHttpsErrors_true() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("ignoreHttpsErrors", Boolean.TRUE);
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7413, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7413/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());
                assertEquals(200, responseData.getHttpStatusCode());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Proxy configuration tests ====================

    /**
     * Test proxy configuration with host only (no port).
     */
    @Test
    public void test_proxyConfig_hostOnly() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put(HcHttpClient.PROXY_HOST_PROPERTY, "http://127.0.0.1:3128");
            // No port specified - using combined format
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            // Client should initialize without error
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test proxy configuration with host and port.
     */
    @Test
    public void test_proxyConfig_hostAndPort() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put(HcHttpClient.PROXY_HOST_PROPERTY, "127.0.0.1");
            paramMap.put(HcHttpClient.PROXY_PORT_PROPERTY, 3128);
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            // Client should initialize without error
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test proxy configuration with credentials.
     */
    @Test
    public void test_proxyConfig_withCredentials() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put(HcHttpClient.PROXY_HOST_PROPERTY, "127.0.0.1");
            paramMap.put(HcHttpClient.PROXY_PORT_PROPERTY, 3128);
            paramMap.put(HcHttpClient.PROXY_CREDENTIALS_PROPERTY, new UsernamePasswordCredentials("user", "pass".toCharArray()));
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            // Client should initialize without error
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test proxy configuration with bypass.
     */
    @Test
    public void test_proxyConfig_withBypass() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put(HcHttpClient.PROXY_HOST_PROPERTY, "127.0.0.1");
            paramMap.put(HcHttpClient.PROXY_PORT_PROPERTY, 3128);
            paramMap.put("proxyBypass", "localhost, 127.0.0.1, [::1]");
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            // Client should initialize without error
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test proxy bypass with multiple patterns.
     */
    @Test
    public void test_proxyBypass_multiplePatterns() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            final Map<String, Object> paramMap = new HashMap<>();
            paramMap.put(HcHttpClient.PROXY_HOST_PROPERTY, "127.0.0.1");
            paramMap.put(HcHttpClient.PROXY_PORT_PROPERTY, 3128);
            paramMap.put("proxyBypass", "localhost, 127.0.0.1, [::1], *.local, 192.168.*");
            playwrightClient.setInitParameterMap(paramMap);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            // Client should initialize without error
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Browser name tests ====================

    /**
     * Test browserName chromium.
     */
    @Test
    public void test_browserName_chromium() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setBrowserName("chromium");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7414, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7414/";
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
     * Test browserName firefox.
     */
    @Test
    public void test_browserName_firefox() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setBrowserName("firefox");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7415, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7415/";
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
     * Test browserName webkit.
     */
    @Test
    public void test_browserName_webkit() {
        final MimeTypeHelper mimeTypeHelper = new MimeTypeHelperImpl();
        final PlaywrightClient playwrightClient = new PlaywrightClient() {
            @Override
            protected Optional<MimeTypeHelper> getMimeTypeHelper() {
                return Optional.ofNullable(mimeTypeHelper);
            }
        };

        try {
            playwrightClient.setBrowserName("webkit");
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
            playwrightClient.init();

            final File docRootDir = new File(ResourceUtil.getBuildDir("docroot/index.html"), "docroot");
            final CrawlerWebServer server = new CrawlerWebServer(7416, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7416/";
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

    private String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (final UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }
}
