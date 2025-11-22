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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.core.lang.ThreadUtil;
import org.codelibs.core.misc.Tuple4;
import org.codelibs.core.stream.StreamUtil;
import org.codelibs.fess.crawler.Constants;
import org.codelibs.fess.crawler.CrawlerContext;
import org.codelibs.fess.crawler.client.AbstractCrawlerClient;
import org.codelibs.fess.crawler.container.CrawlerContainer;
import org.codelibs.fess.crawler.entity.RequestData;
import org.codelibs.fess.crawler.entity.RequestData.Method;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.ChildUrlsException;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.util.CrawlingParameterUtil;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Proxy;

import jakarta.annotation.Resource;

/**
 * PlaywrightClient is an implementation of AbstractCrawlerClient that uses Playwright to interact with web pages.
 * It supports various configurations for browser types, context options, and timeouts.
 *
 * <p>This client can be configured to use a shared Playwright worker or create a new one for each instance.
 * It also supports SSL certificate ignoring, proxy settings, and authentication through Fess's built-in HcHttpClient.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Initialization and configuration of Playwright browser and context</li>
 *   <li>Execution of HTTP requests and handling of responses</li>
 *   <li>Support for downloading files and handling different content types</li>
 *   <li>Graceful closing of Playwright resources</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * PlaywrightClient client = new PlaywrightClient();
 * client.setBrowserName("chromium");
 * client.setDownloadTimeout(30);
 * client.init();
 * ResponseData response = client.execute(requestData);
 * client.close();
 * }
 * </pre>
 *
 * <p>Note: This class is designed to be used within the Fess framework and relies on its components and configurations.</p>
 *
 */
public class PlaywrightClient extends AbstractCrawlerClient {

    private static final Logger logger = LogManager.getLogger(PlaywrightClient.class);

    private static final Object INITIALIZATION_LOCK = new Object();

    /**
     * A shared worker instance for Playwright.
     */
    protected static Tuple4<Playwright, Browser, BrowserContext, Page> SHARED_WORKER = null;

    /**
     * The key to specify a shared client.
     */
    protected static final String SHARED_CLIENT = "sharedClient";

    /**
     * The key to specify a rendered state.
     */
    protected static final String RENDERED_STATE = "renderedState";

    /**
     * The key to specify a content wait duration.
     */
    protected static final String CONTENT_WAIT_DURATION = "contentWaitDuration";

    /**
     * The key to specify whether to ignore HTTPS errors.
     */
    protected static final String IGNORE_HTTPS_ERRORS_PROPERTY = "ignoreHttpsErrors";

    /**
     * The key to specify a proxy bypass.
     */
    protected static final String PROXY_BYPASS_PROPERTY = "proxyBypass";

    /**
     * The date format for the last modified header.
     */
    protected static final String LAST_MODIFIED_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";

    /**
     * A map of options for Playwright.
     */
    protected Map<String, String> options = new HashMap<>();

    /**
     * The name of the browser to use (e.g., "chromium", "firefox", "webkit").
     */
    protected String browserName = "chromium";

    /**
     * The launch options for the browser.
     */
    protected LaunchOptions launchOptions;

    /**
     * The options for a new browser context.
     */
    protected NewContextOptions newContextOptions;

    /**
     * The timeout for downloading a file, in seconds.
     */
    protected int downloadTimeout = 15; // 15s

    /**
     * The timeout for closing the client, in seconds.
     */
    protected int closeTimeout = 15; // 15s

    /**
     * The rendered state to wait for.
     */
    protected LoadState renderedState = LoadState.NETWORKIDLE;

    /**
     * The duration to wait for the content to be rendered, in milliseconds.
     */
    protected long contentWaitDuration = 0;

    /**
     * The worker instance for Playwright.
     */
    protected Tuple4<Playwright, Browser, BrowserContext, Page> worker;

    /**
     * The crawler container instance.
     */
    @Resource
    protected CrawlerContainer crawlerContainer;

    /**
     * Default constructor for {@code PlaywrightClient}.
     * Initializes a new instance of the PlaywrightClient class.
     */
    public PlaywrightClient() {
        // Default constructor
    }

    @Override
    public void init() {
        synchronized (INITIALIZATION_LOCK) {
            if (worker != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Worker already initialized, skipping init()");
                }
                return;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Initializing Playwright...");
            }
            super.init();

            final String renderedStateParam = getInitParameter(RENDERED_STATE, renderedState.name(), String.class);
            if (renderedStateParam != null) {
                renderedState = LoadState.valueOf(renderedStateParam);
            }

            contentWaitDuration = getInitParameter(CONTENT_WAIT_DURATION, 0L, Long.class);

            if (logger.isDebugEnabled()) {
                logger.debug("Configured renderedState: {}, contentWaitDuration: {}ms", renderedState, contentWaitDuration);
            }

            final Boolean shared = getInitParameter(SHARED_CLIENT, Boolean.FALSE, Boolean.class);
            if (logger.isDebugEnabled()) {
                logger.debug("Shared client configuration: {}", shared);
            }

            if (shared) {
                if (SHARED_WORKER == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Creating a shared Playwright worker...");
                    }
                    SHARED_WORKER = createPlaywrightWorker();
                }
                logger.info("Use a shared Playwright worker.");
                worker = SHARED_WORKER;
            } else {
                worker = createPlaywrightWorker();
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Playwright initialization completed successfully");
            }
        }
    }

    /**
     * Creates a Playwright worker.
     *
     * @return A tuple containing the Playwright instance, browser, browser context, and page.
     */
    protected Tuple4<Playwright, Browser, BrowserContext, Page> createPlaywrightWorker() {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating Playwright worker with browser: {}", browserName);
        }

        // initialize Playwright's browser context
        final NewContextOptions newContextOptions = initNewContextOptions();

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext browserContext = null;
        Page page = null;
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating Playwright instance with environment options");
            }
            playwright = Playwright.create(new Playwright.CreateOptions().setEnv(options));

            if (logger.isDebugEnabled()) {
                logger.debug("Playwright instance created successfully");
            }

            browser = getBrowserType(playwright).launch(launchOptions);
            if (logger.isDebugEnabled()) {
                logger.debug("Browser '{}' launched successfully", browserName);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Creating authenticated browser context");
            }
            browserContext = createAuthenticatedContext(browser, newContextOptions);

            if (logger.isDebugEnabled()) {
                logger.debug("Creating new page in browser context");
            }
            page = browserContext.newPage();

            if (logger.isDebugEnabled()) {
                logger.debug("Playwright worker created successfully");
            }
        } catch (final Exception e) {
            logger.warn("Failed to create Playwright worker (browser: {}, playwright: {}, context: {}, page: {})", browserName,
                    playwright != null, browserContext != null, page != null, e);
            close(playwright, browser, browserContext, page);
            throw new CrawlerSystemException("Failed to create PlaywrightClient.", e);
        }

        return new Tuple4<>(playwright, browser, browserContext, page);
    }

    @Override
    public void close() {
        if (worker == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Worker already null, nothing to close");
            }
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Initiating Playwright worker cleanup");
        }

        try {
            close(worker.getValue1(), worker.getValue2(), worker.getValue3(), worker.getValue4());
        } finally {
            worker = null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Playwright worker cleanup completed");
        }
    }

    /**
     * Closes the Playwright worker in the background.
     *
     * @param closer The runnable to close the worker.
     */
    protected void closeInBackground(final Runnable closer) {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Starting background closer thread");
            }

            final Thread thread = new Thread(() -> {
                try {
                    closer.run();
                } catch (final Exception e) {
                    logger.warn("Failed to close Playwright component in background thread", e);
                }
                latch.countDown();
            }, "Playwright-Closer");
            thread.setDaemon(true);
            thread.start();
            if (!latch.await(closeTimeout, TimeUnit.SECONDS)) {
                logger.warn("The close process timed out after {}s", closeTimeout);
            }
        } catch (final InterruptedException e) {
            logger.warn("Interrupted while waiting for the close process to complete", e);
        } catch (final Exception e) {
            logger.warn("Unexpected error during Playwright component closure", e);
        }
    }

    /**
     * Closes the Playwright worker.
     *
     * @param playwright The Playwright instance.
     * @param browser The browser instance.
     * @param context The browser context.
     * @param page The page.
     */
    protected void close(final Playwright playwright, final Browser browser, final BrowserContext context, final Page page) {
        closeInBackground(() -> {
            if (page != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Closing Page...");
                }
                page.close();
            }
        });
        closeInBackground(() -> {
            if (context != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Closing BrowserContext...");
                }
                context.close();
            }
        });
        closeInBackground(() -> {
            if (browser != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Closing Browser...");
                }
                browser.close();
            }
        });
        closeInBackground(() -> {
            if (playwright != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Closing Playwright...");
                }
                playwright.close();
            }
        });
    }

    /**
     * Gets the browser type.
     *
     * @param playwright The Playwright instance.
     * @return The browser type.
     */
    protected BrowserType getBrowserType(final Playwright playwright) {
        if (logger.isDebugEnabled()) {
            logger.debug("Getting browser type for: {}", browserName);
        }
        final BrowserType browserType = switch (browserName) {
        case "firefox":
            yield playwright.firefox();
        case "webkit":
            yield playwright.webkit();
        case "chromium":
            yield playwright.chromium();
        default:
            logger.warn("Unknown browser name specified: {}. Supported browsers: chromium, firefox, webkit", browserName);
            throw new CrawlerSystemException("Unknown browser name: " + browserName);
        };
        if (logger.isDebugEnabled()) {
            logger.debug("Successfully obtained {} browser type", browserName);
        }
        return browserType;
    }

    /**
     * Adds an option.
     *
     * @param key The key.
     * @param value The value.
     */
    public void addOption(final String key, final String value) {
        options.put(key, value);
    }

    @Override
    public ResponseData execute(final RequestData request) {
        if (worker == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Worker not initialized, triggering init()");
            }
            init();
        }

        final String url = request.getUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("Executing request - URL: {}, Method: {}", url, request.getMethod());
        }

        final Page page = worker.getValue4();
        final AtomicReference<Response> responseRef = new AtomicReference<>();
        final AtomicReference<Download> downloadRef = new AtomicReference<>();
        synchronized (page) {
            if (logger.isDebugEnabled()) {
                logger.debug("Acquired page lock for URL: {}", url);
            }

            try {
                page.onResponse(response -> {
                    if (responseRef.get() == null) {
                        responseRef.set(response);
                    }
                });
                page.onDownload(downloadRef::set);

                if (logger.isDebugEnabled()) {
                    logger.debug("Download handler registered for potential file downloads");
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("Accessing {}", url);
                }
                final Response response = page.navigate(url);

                if (logger.isDebugEnabled()) {
                    logger.debug("Waiting for LoadState: {}", renderedState);
                }
                page.waitForLoadState(renderedState);

                if (logger.isDebugEnabled()) {
                    logger.debug("Page reached LoadState: {}", renderedState);
                }

                if (contentWaitDuration > 0L) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Waiting {} ms before downloading the content.", contentWaitDuration);
                    }
                    ThreadUtil.sleep(contentWaitDuration);
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("Loaded: Base URL: {}, Response URL: {}", url, response.url());
                }
                return createResponseData(page, request, response, null);
            } catch (final Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Page navigation failed, attempting to handle as file download: {}", e.getMessage());
                }
                for (int i = 0; i < downloadTimeout * 10 && (downloadRef.get() == null || responseRef.get() == null); i++) {
                    try {
                        page.waitForTimeout(100L);
                        if (logger.isDebugEnabled() && i % 10 == 0) {
                            logger.debug("Waiting for download completion (timeout: {}s), waited: {}s", downloadTimeout, i / 10);
                        }
                    } catch (final Exception e1) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Failed to wait for page loading.", e1);
                        }
                    }
                }
                final Response response = responseRef.get();
                final Download download = downloadRef.get();
                if (response != null && download != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Downloaded:  URL: {}", response.url());
                    }
                    return createResponseData(page, request, response, download);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to access URL - response: {}, download: {}", response != null, download != null);
                }
                throw new CrawlingAccessException("Failed to access " + request.getUrl(), e);
            } finally {
                if (logger.isDebugEnabled()) {
                    logger.debug("Resetting page to about:blank");
                }
                resetPage(page);
            }
        }
    }

    /**
     * Resets the page.
     *
     * @param page The page.
     */
    protected void resetPage(final Page page) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Resetting page to blank state");
            }
            page.navigate("about:blank");
            page.waitForLoadState(LoadState.LOAD);
            if (logger.isDebugEnabled()) {
                logger.debug("Page reset completed successfully");
            }
        } catch (final Exception e) {
            logger.warn("Could not reset a page.", e);
        }
    }

    /**
     * Creates a response data.
     *
     * @param page The page.
     * @param request The request data.
     * @param response The response.
     * @param download The download.
     * @return The response data.
     */
    protected ResponseData createResponseData(final Page page, final RequestData request, final Response response,
            final Download download) {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating ResponseData for URL: {}", response.url());
        }

        final ResponseData responseData = new ResponseData();

        final String originalUrl = request.getUrl();
        final String url = response.url();
        if (!originalUrl.equals(url)) {
            final CrawlerContext context = CrawlingParameterUtil.getCrawlerContext();
            if (context != null) {
                final UrlFilter urlFilter = context.getUrlFilter();
                if (urlFilter != null && !urlFilter.match(url)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} is not a target URL", url);
                    }
                    throw new ChildUrlsException(Collections.emptySet(), "#crawledUrlNotTarget");
                }
            }
            logger.info("Crawled URL: {} -> {}", originalUrl, url);
        }

        responseData.setUrl(url);
        responseData.setMethod(request.getMethod().name());

        final String charSet = getCharSet(response);
        responseData.setCharSet(charSet);
        final int statusCode = getStatusCode(response);
        responseData.setHttpStatusCode(statusCode);
        responseData.setLastModified(getLastModified(response));

        if (logger.isDebugEnabled()) {
            logger.debug("Response - StatusCode: {}, CharSet: {}, LastModified: {}", statusCode, charSet, responseData.getLastModified());
        }

        response.allHeaders().entrySet().forEach(e -> responseData.addMetaData(e.getKey(), e.getValue()));

        if (logger.isDebugEnabled()) {
            logger.debug("Response headers count: {}", response.allHeaders().size());
        }

        if (statusCode > 400) {
            if (logger.isDebugEnabled()) {
                logger.debug("Error status code {}, returning empty response body", statusCode);
            }
            responseData.setContentLength(0);
            responseData.setResponseBody(new byte[0]);
            responseData.setMimeType(getContentType(response));
        } else if (download == null) {
            final byte[] body = response.body();
            final byte[] responseBody = getMimeTypeHelper().map(mimeTypeHelper -> {
                final String filename = getFilename(url);
                try (final InputStream in = new ByteArrayInputStream(body)) {
                    final String contentType = mimeTypeHelper.getContentType(in, filename);
                    responseData.setMimeType(contentType);
                    if (logger.isDebugEnabled()) {
                        logger.debug("filename:{} content-type:{}", filename, contentType);
                    }
                    if ("text/html".equals(contentType)) {
                        try {
                            final String content = page.content();
                            if (logger.isDebugEnabled()) {
                                logger.debug("html content: {}", content);
                            }
                            return content.getBytes(charSet);
                        } catch (final Exception e) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Could not get a content from page.", e);
                            }
                        }
                    }
                } catch (final IOException e) {
                    logger.warn("Failed to read response body for MIME type detection from URL: {}", url, e);
                }
                return body;
            }).orElse(body);
            responseData.setContentLength(responseBody.length);
            if (Method.HEAD != request.getMethod()) {
                responseData.setResponseBody(responseBody);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Processing file download for URL: {}", url);
            }
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Saving download to temporary file");
                }
                final File tempFile = createTempFile("fess-crawler-playwright-", ".tmp", null);
                download.saveAs(tempFile.toPath());
                responseData.setContentLength(tempFile.length());

                if (logger.isDebugEnabled()) {
                    logger.debug("Download saved to: {}, size: {} bytes", tempFile.getAbsolutePath(), tempFile.length());
                }

                getMimeTypeHelper().ifPresent(mimeTypeHelper -> {
                    final String filename = getFilename(url);
                    try (final InputStream in = new FileInputStream(tempFile)) {
                        final String contentType = mimeTypeHelper.getContentType(in, filename);
                        responseData.setMimeType(contentType);
                        if (logger.isDebugEnabled()) {
                            logger.debug("filename:{} content-type:{}", filename, contentType);
                        }
                    } catch (final IOException e) {
                        logger.warn("Failed to read downloaded file for MIME type detection: {}", tempFile.getAbsolutePath(), e);
                    }
                });
                responseData.setResponseBody(tempFile, true);
            } finally {
                download.delete();
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("ResponseData created successfully - ContentLength: {}, MimeType: {}", responseData.getContentLength(),
                    responseData.getMimeType());
        }

        return responseData;
    }

    /**
     * Gets the filename from the URL.
     *
     * @param url The URL.
     * @return The filename.
     */
    protected String getFilename(final String url) {
        if (StringUtil.isBlank(url)) {
            return null;
        }
        final String[] values = StringUtils.splitPreserveAllTokens(url, '/');
        final String value = values[values.length - 1].split("#")[0].split("\\?")[0];
        if (StringUtil.isBlank(value)) {
            return "index.html";
        }
        return value;
    }

    /**
     * Gets the MimeTypeHelper.
     *
     * @return The MimeTypeHelper.
     */
    protected Optional<MimeTypeHelper> getMimeTypeHelper() {
        return Optional.ofNullable(crawlerContainer.getComponent("mimeTypeHelper"));
    }

    /**
     * Gets the content type from the response.
     *
     * @param response The response.
     * @return The content type.
     */
    protected String getContentType(final Response response) {
        final String contentType = response.headerValue("content-type");
        if (StringUtil.isNotBlank(contentType)) {
            return contentType.split(";")[0].trim();
        }
        return "text/html";
    }

    /**
     * Gets the last modified date from the response.
     *
     * @param response The response.
     * @return The last modified date.
     */
    protected Date getLastModified(final Response response) {
        return parseDate(response.headerValue("last-modified"));
    }

    /**
     * Parses a date string.
     *
     * @param value The date string.
     * @return The parsed date.
     */
    protected Date parseDate(final String value) {
        if (StringUtil.isNotBlank(value)) {
            try {
                final SimpleDateFormat dateFormat = new SimpleDateFormat(LAST_MODIFIED_FORMAT, Locale.ENGLISH);
                return dateFormat.parse(value);
            } catch (final ParseException e) {
                logger.warn("Failed to parse date header value '{}' with expected format '{}'", value, LAST_MODIFIED_FORMAT, e);
            }
        }
        return null;
    }

    /**
     * Gets the status code from the response.
     *
     * @param response The response.
     * @return The status code.
     */
    protected int getStatusCode(final Response response) {
        return response.status();
    }

    /**
     * Gets the character set from the response.
     *
     * @param response The response.
     * @return The character set.
     */
    protected String getCharSet(final Response response) {
        final String contentType = response.headerValue("content-type");
        if (StringUtil.isNotBlank(contentType)) {
            final String[] result = StreamUtil.split(contentType, ";").get(stream -> stream.map(s -> {
                final String[] values = s.split("=");
                if (values.length == 2 && "charset".equalsIgnoreCase(values[0].trim())) {
                    return values[1].trim();
                }
                return null;
            }).filter(StringUtil::isNotBlank).toArray(n -> new String[n]));
            if (result.length > 0) {
                return result[0];
            }
        }
        return Constants.UTF_8;
    }

    /**
     * Initializes and configures Playwright's NewContextOptions based on the parameters
     * provided through the Web UI. This method reads various configuration settings such as
     * SSL certificate ignoring, proxy settings, and authentication credentials, and applies
     * them to the NewContextOptions object.
     *
     * @return a configured NewContextOptions object to be used for creating a Playwright BrowserContext
     */
    protected NewContextOptions initNewContextOptions() {
        if (logger.isDebugEnabled()) {
            logger.debug("Initializing NewContextOptions");
        }

        final NewContextOptions options = newContextOptions != null ? newContextOptions : new NewContextOptions();

        // Check whether to skip SSL certificate checking
        // Also check ignoreSslCertificate for backward compatibility with HcHttpClient's config
        final boolean ignoreHttpsErrors = getInitParameter(IGNORE_HTTPS_ERRORS_PROPERTY, false, Boolean.class);
        final boolean ignoreSslCertificate = getInitParameter(HcHttpClient.IGNORE_SSL_CERTIFICATE_PROPERTY, false, Boolean.class);

        if (ignoreHttpsErrors || ignoreSslCertificate) {
            if (logger.isDebugEnabled()) {
                logger.debug("SSL certificate validation disabled (ignoreHttpsErrors: {}, ignoreSslCertificate: {})", ignoreHttpsErrors,
                        ignoreSslCertificate);
            }
            options.ignoreHTTPSErrors = true;
        }

        // append existing proxy configuration
        final String proxyHost = getInitParameter(HcHttpClient.PROXY_HOST_PROPERTY, null, String.class);
        final Integer proxyPort = getInitParameter(HcHttpClient.PROXY_PORT_PROPERTY, null, Integer.class);
        final UsernamePasswordCredentials proxyCredentials =
                getInitParameter(HcHttpClient.PROXY_CREDENTIALS_PROPERTY, null, UsernamePasswordCredentials.class);
        final String proxyBypass = getInitParameter(PROXY_BYPASS_PROPERTY, null, String.class);

        if (!StringUtils.isBlank(proxyHost)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Proxy configured - host: {}, port: {}, hasCredentials: {}, bypass: {}", proxyHost, proxyPort,
                        proxyCredentials != null, proxyBypass);
            }
            final String proxyAddress = proxyPort == null ? proxyHost : proxyHost + ":" + proxyPort;
            final Proxy proxy = new Proxy(proxyAddress);
            if (proxyCredentials != null) {
                proxy.setUsername(proxyCredentials.getUserName());
                proxy.setPassword(proxyCredentials.getPassword());
            }
            proxy.setBypass(proxyBypass);
            options.setProxy(proxy);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("NewContextOptions initialized successfully");
        }

        return options;
    }

    /**
     * Creates an authenticated Playwright context, by using Fess's built-in HcHttpClient to do authentication,
     * then passes its cookies to Playwright.
     *
     * @param browser The browser instance.
     * @param newContextOptions The new context options.
     * @return The browser context.
     */
    protected BrowserContext createAuthenticatedContext(final Browser browser, final NewContextOptions newContextOptions) {
        final Authentication[] authentications =
                getInitParameter(HcHttpClient.AUTHENTICATIONS_PROPERTY, new Authentication[0], Authentication[].class);

        if (logger.isDebugEnabled()) {
            logger.debug("Processing {} authentication configuration(s)", authentications.length);
        }

        if (authentications.length == 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("No authentication configured, creating standard browser context");
            }
            return browser.newContext(newContextOptions);
        }

        for (final Authentication authentication : authentications) {
            if (logger.isDebugEnabled()) {
                logger.debug("Processing authentication scheme: {}", authentication.getAuthScheme().getSchemeName());
            }
            if (!Strings.CS.equals(authentication.getAuthScheme().getSchemeName(), "form")) {
                // Use the first non-form auth credentials to fill the browser's credential prompt
                if (logger.isDebugEnabled()) {
                    logger.debug("Setting HTTP credentials for non-form authentication");
                }
                final String username = authentication.getCredentials().getUserPrincipal().getName();
                final String password = authentication.getCredentials().getPassword();
                newContextOptions.setHttpCredentials(username, password);
                break;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Creating browser context with authentication");
        }
        final BrowserContext playwrightContext = browser.newContext(newContextOptions);
        try (final var fessHttpClient = new HcHttpClient()) {
            fessHttpClient.setInitParameterMap(initParamMap);
            fessHttpClient.init();
            final List<org.apache.http.cookie.Cookie> fessCookies = fessHttpClient.cookieStore.getCookies();
            if (logger.isDebugEnabled()) {
                logger.debug("Transferring {} cookies from HcHttpClient to Playwright", fessCookies.size());
            }
            final List<Cookie> playwrightCookies = fessCookies.stream().map(apacheCookie -> {
                final var playwrightCookie = new Cookie(apacheCookie.getName(), apacheCookie.getValue());
                playwrightCookie.setDomain(apacheCookie.getDomain());
                playwrightCookie.setPath(apacheCookie.getPath());
                playwrightCookie.setSecure(apacheCookie.isSecure());

                // Set expiry time - Apache's cookies use milliseconds as time unit (via Date object),
                // while Playwright uses seconds.
                final Date cookieExpiryDate = apacheCookie.getExpiryDate();
                if (cookieExpiryDate != null) {
                    playwrightCookie.setExpires(cookieExpiryDate.getTime() / 1000.0);
                }

                return playwrightCookie;
            }).toList();
            playwrightContext.addCookies(playwrightCookies);

            if (logger.isDebugEnabled()) {
                logger.debug("Authenticated context created with {} cookies", playwrightCookies.size());
            }

            return playwrightContext;
        }
    }

    /**
     * Sets the launch options.
     *
     * @param launchOptions The launch options.
     */
    public void setLaunchOptions(final LaunchOptions launchOptions) {
        this.launchOptions = launchOptions;
    }

    /**
     * Sets the browser name.
     *
     * @param browserName The browser name.
     */
    public void setBrowserName(final String browserName) {
        this.browserName = browserName;
    }

    /**
     * Sets the download timeout.
     *
     * @param downloadTimeout The download timeout.
     */
    public void setDownloadTimeout(final int downloadTimeout) {
        this.downloadTimeout = downloadTimeout;
    }

    /**
     * Sets the rendered state.
     *
     * @param loadState The load state.
     */
    public void setRenderedState(final LoadState loadState) {
        renderedState = loadState;
    }

    /**
     * Sets the close timeout.
     *
     * @param closeTimeout The close timeout.
     */
    public void setCloseTimeout(final int closeTimeout) {
        this.closeTimeout = closeTimeout;
    }

    /**
     * Sets the new context options.
     *
     * @param newContextOptions The new context options.
     */
    public void setNewContextOptions(final NewContextOptions newContextOptions) {
        this.newContextOptions = newContextOptions;
    }
}
