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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Optional;

import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerWebServer;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;

/**
 * Test class for PlaywrightClient data handling scenarios.
 * Tests for various response types, encodings, and content handling.
 */
public class PlaywrightClientDataTest extends PlainTestCase {

    private static final boolean HEADLESS = true;

    // ==================== HTML content tests ====================

    /**
     * Test HTML response with UTF-8 charset.
     */
    public void test_htmlResponse_utf8() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7500, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7500/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("text/html", responseData.getMimeType());
                assertEquals("UTF-8", responseData.getCharSet());

                final String body = getBodyAsString(responseData);
                assertNotNull(body);
                assertTrue(body.contains("content page"));
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test HTML response extracts rendered content.
     */
    public void test_htmlResponse_renderedContent() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7501, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7501/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("text/html", responseData.getMimeType());

                // Response body should contain HTML
                final String body = getBodyAsString(responseData);
                assertTrue("Body should contain HTML tags", body.contains("<html") || body.contains("<HTML"));
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Text content tests ====================

    /**
     * Test plain text response.
     */
    public void test_textResponse() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7502, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7502/test.txt";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("text/plain", responseData.getMimeType());

                final String body = getBodyAsString(responseData);
                assertEquals("This is a test document.", body.trim());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== JSON content tests ====================

    /**
     * Test JSON response.
     */
    public void test_jsonResponse() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7503, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7503/test.json";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("application/json", responseData.getMimeType());

                final String body = getBodyAsString(responseData);
                assertTrue(body.contains("message"));
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Binary content tests ====================

    /**
     * Test PDF response (binary).
     */
    public void test_pdfResponse() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7504, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7504/test.pdf";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("application/pdf", responseData.getMimeType());
                assertTrue(responseData.getContentLength() > 0);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test PNG image response (binary).
     */
    public void test_pngResponse() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7505, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7505/test.png";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("image/png", responseData.getMimeType());
                assertTrue(responseData.getContentLength() > 0);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test JPEG image response (binary).
     */
    public void test_jpgResponse() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7506, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7506/test.jpg";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("image/jpeg", responseData.getMimeType());
                assertTrue(responseData.getContentLength() > 0);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test GIF image response (binary).
     */
    public void test_gifResponse() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7507, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7507/test.gif";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("image/gif", responseData.getMimeType());
                assertTrue(responseData.getContentLength() > 0);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Download content tests ====================

    /**
     * Test ZIP file download.
     */
    public void test_zipDownload() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7508, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7508/download.zip";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("application/zip", responseData.getMimeType());
                assertTrue(responseData.getContentLength() > 0);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test DOCX file download.
     */
    public void test_docxDownload() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7509, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7509/test.docx";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", responseData.getMimeType());
                assertTrue(responseData.getContentLength() > 0);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test EPUB file download.
     */
    public void test_epubDownload() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7510, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7510/test.epub";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("application/epub+zip", responseData.getMimeType());
                assertTrue(responseData.getContentLength() > 0);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Metadata tests ====================

    /**
     * Test response headers are captured in metadata.
     */
    public void test_responseMetadata() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7511, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7511/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());

                // Verify metadata map contains headers
                assertNotNull(responseData.getMetaDataMap());
                assertTrue(responseData.getMetaDataMap().size() > 0);
                assertTrue(responseData.getMetaDataMap().containsKey("content-type"));
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test last-modified header is parsed.
     */
    public void test_lastModifiedHeader() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7512, docRootDir);

            try {
                server.start();
                final String url = "http://[::1]:7512/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());

                // Last-modified may or may not be present depending on server
                // If present, it should be a valid date
                if (responseData.getLastModified() != null) {
                    assertTrue(responseData.getLastModified().getTime() > 0);
                }
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Content length tests ====================

    /**
     * Test content length is correctly reported.
     */
    public void test_contentLength() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7513, docRootDir);

            try {
                server.start();

                // Test text file - known content
                final String url = "http://[::1]:7513/test.txt";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals(25, responseData.getContentLength());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test content length for HTML response.
     */
    public void test_contentLength_html() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7514, docRootDir);

            try {
                server.start();

                final String url = "http://[::1]:7514/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertTrue(responseData.getContentLength() > 0);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Response body tests ====================

    /**
     * Test response body is accessible as InputStream.
     */
    public void test_responseBody_asInputStream() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7515, docRootDir);

            try {
                server.start();

                final String url = "http://[::1]:7515/test.txt";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());

                final InputStream body = responseData.getResponseBody();
                assertNotNull(body);

                final byte[] bytes = InputStreamUtil.getBytes(body);
                assertEquals(25, bytes.length);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== HEAD request tests ====================

    /**
     * Test HEAD request returns no body.
     */
    public void test_headRequest_noBody() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7516, docRootDir);

            try {
                server.start();

                final String url = "http://[::1]:7516/";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().head().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("HEAD", responseData.getMethod());
                assertNull(responseData.getResponseBody());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test HEAD request for various file types.
     */
    public void test_headRequest_variousFileTypes() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7517, docRootDir);

            try {
                server.start();

                // HTML
                ResponseData responseData =
                        playwrightClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:7517/").build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("text/html", responseData.getMimeType());
                assertNull(responseData.getResponseBody());

                // Text
                responseData =
                        playwrightClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:7517/test.txt").build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("text/plain", responseData.getMimeType());

                // PDF
                responseData =
                        playwrightClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:7517/test.pdf").build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("application/pdf", responseData.getMimeType());

                // PNG
                responseData =
                        playwrightClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:7517/test.png").build());
                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals("image/png", responseData.getMimeType());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== Error response tests ====================

    /**
     * Test 404 response has empty body.
     */
    public void test_404Response_emptyBody() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7518, docRootDir);

            try {
                server.start();

                final String url = "http://[::1]:7518/nonexistent.html";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(404, responseData.getHttpStatusCode());
                assertEquals(0, responseData.getContentLength());
                assertNotNull(responseData.getResponseBody());

                final String body = getBodyAsString(responseData);
                assertEquals("", body);
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    // ==================== URL handling tests ====================

    /**
     * Test URL is correctly set in response.
     */
    public void test_responseUrl() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7519, docRootDir);

            try {
                server.start();

                final String url = "http://[::1]:7519/test.txt";
                final ResponseData responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().get().url(url).build());

                assertEquals(200, responseData.getHttpStatusCode());
                assertEquals(url, responseData.getUrl());
            } finally {
                server.stop();
            }
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test method is correctly set in response.
     */
    public void test_responseMethod() {
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
            final CrawlerWebServer server = new CrawlerWebServer(7520, docRootDir);

            try {
                server.start();

                // GET
                ResponseData responseData =
                        playwrightClient.execute(RequestDataBuilder.newRequestData().get().url("http://[::1]:7520/").build());
                assertEquals("GET", responseData.getMethod());

                // HEAD
                responseData = playwrightClient.execute(RequestDataBuilder.newRequestData().head().url("http://[::1]:7520/").build());
                assertEquals("HEAD", responseData.getMethod());
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
