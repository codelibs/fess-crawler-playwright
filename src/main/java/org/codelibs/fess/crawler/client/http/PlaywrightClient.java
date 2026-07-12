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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import java.time.Instant;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
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
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Proxy;

import jakarta.annotation.Resource;

/**
 * PlaywrightClient is an implementation of AbstractCrawlerClient that uses Playwright to interact with web pages.
 * It supports various configurations for browser types, context options, and timeouts.
 *
 * <p>This client can be configured to use a shared Playwright worker or create a new one for each instance.
 * It also supports SSL certificate ignoring, proxy settings, and authentication through Fess's built-in Hc5HttpClient.</p>
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

    /**
     * Guards {@link #init()} (JVM-wide, across every instance) and, nested inside a {@code Page}
     * monitor, the shared-worker reference-count decrement in {@link #close()}.
     *
     * <p><b>Lock-ordering invariant: a {@code Page} monitor (see {@link #close()}/{@link #execute(RequestData)})
     * is always acquired before this lock, never after.</b> {@link #init()} holds only this lock for
     * its entire body and never synchronizes on a {@code Page}; keep it that way - acquiring this lock
     * first and then a {@code Page} monitor anywhere would create the reverse ordering and a real
     * deadlock risk with {@link #close()}.</p>
     */
    private static final Object INITIALIZATION_LOCK = new Object();

    /**
     * A shared worker instance for Playwright.
     */
    protected static volatile Tuple4<Playwright, Browser, BrowserContext, Page> SHARED_WORKER = null;

    /**
     * Reference count for SHARED_WORKER. Tracks how many clients are using the shared worker.
     */
    private static final AtomicInteger SHARED_WORKER_REF_COUNT = new AtomicInteger(0);

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
     * How long {@link #execute(RequestData)} keeps polling for a download after a LoadState wait
     * times out with no download detected yet, in milliseconds.
     *
     * <p>Deliberately much shorter than {@link #downloadTimeout} (the wait used once a download is
     * already known to be in progress): a LoadState timeout with no download detected is usually a
     * genuinely-loaded-but-chatty page, so this short grace poll only needs to catch a download that
     * fires within a moment of the timeout. Kept as a simple internal constant rather than a
     * configurable knob, since the timeouts in this class are wired via setters (not init parameters)
     * and this value is an implementation detail, not something operators need to tune.</p>
     */
    protected static final long LOAD_STATE_TIMEOUT_GRACE_PERIOD_MILLIS = 1500L;

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
     *
     * <p>Volatile (like {@link #usingSharedWorker} and {@link #closed}) so that
     * {@link #execute(RequestData)} can capture it into a local with a single reliable read and a
     * concurrent {@link #close()} nulling the field cannot cause a torn/inconsistent read.</p>
     */
    protected volatile Tuple4<Playwright, Browser, BrowserContext, Page> worker;

    /**
     * Flag indicating whether this instance is using the shared worker.
     * Used to prevent duplicate increment/decrement of reference count.
     */
    private volatile boolean usingSharedWorker = false;

    /**
     * Flag indicating whether this instance has been closed.
     * Checked as the first statement inside execute()'s {@code synchronized (page)} block so that
     * a thread already in-flight inside execute() (or one that is about to enter its monitor) can
     * never observe a page/context/browser that close() has torn down out from under it.
     */
    private volatile boolean closed = false;

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
        // Holds only INITIALIZATION_LOCK for this entire method - never nest a `synchronized(page)`
        // (or any Page monitor) inside it. See the lock-ordering invariant documented on
        // INITIALIZATION_LOCK's declaration.
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
                final int refCount = SHARED_WORKER_REF_COUNT.incrementAndGet();
                if (logger.isDebugEnabled()) {
                    logger.debug("Shared worker reference count incremented to: {}", refCount);
                }
                usingSharedWorker = true;
                logger.info("Use a shared Playwright worker. (refCount={})", refCount);
                worker = SHARED_WORKER;
            } else {
                worker = createPlaywrightWorker();
                usingSharedWorker = false;
            }

            // This instance now holds a live (not-yet-closed) worker/page: a prior close() (if any)
            // no longer applies. Once a thread has captured a page reference and entered execute()'s
            // synchronized(page) block, the volatile `closed` check there is reliable against a
            // concurrent close() (see close()'s synchronized(pageRef)). execute() additionally captures
            // the volatile `worker` field into a single local before dereferencing it, so a close()
            // that nulls the field in the window after execute()'s initial `worker == null` check
            // surfaces as this class's own "already closed" CrawlerSystemException rather than a raw
            // NullPointerException.
            closed = false;

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
            close(playwright, browser, browserContext, page);
            final String failureStage = playwright == null ? "Playwright initialization"
                    : browser == null ? "Browser launch (" + browserName + ")"
                            : browserContext == null ? "BrowserContext creation" : "Page creation";
            throw new CrawlerSystemException("Failed to create Playwright worker at stage: " + failureStage + ". Browser: " + browserName
                    + ", LaunchOptions: " + (launchOptions != null), e);
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

        final boolean isSharedWorker = usingSharedWorker;
        // Capture the same Page instance that execute() synchronizes on, before any teardown,
        // so close() contends on that exact monitor instead of racing an in-flight execute().
        final Page pageRef = worker.getValue4();

        if (logger.isDebugEnabled()) {
            logger.debug("Initiating Playwright worker cleanup (shared: {})", isSharedWorker);
        }

        try {
            synchronized (pageRef) {
                // Mark this instance closed before/alongside teardown so that any thread that is
                // currently inside, or next enters, execute()'s synchronized(page) block on this
                // same monitor observes it immediately.
                closed = true;

                if (isSharedWorker) {
                    // Page monitor (pageRef, held above) THEN INITIALIZATION_LOCK - never the reverse
                    // order. See the lock-ordering invariant documented on INITIALIZATION_LOCK's
                    // declaration; do not hoist this out to acquire INITIALIZATION_LOCK first.
                    synchronized (INITIALIZATION_LOCK) {
                        final int refCount = SHARED_WORKER_REF_COUNT.decrementAndGet();
                        if (logger.isDebugEnabled()) {
                            logger.debug("Shared worker reference count decremented to: {}", refCount);
                        }

                        if (refCount <= 0) {
                            if (logger.isInfoEnabled()) {
                                logger.info("No more references to shared worker, closing resources");
                            }
                            close(worker.getValue1(), worker.getValue2(), worker.getValue3(), worker.getValue4());
                            SHARED_WORKER = null;
                            SHARED_WORKER_REF_COUNT.set(0);
                        } else {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Shared worker still in use by {} other client(s), not closing", refCount);
                            }
                        }
                    }
                } else {
                    close(worker.getValue1(), worker.getValue2(), worker.getValue3(), worker.getValue4());
                }
            }
        } finally {
            worker = null;
            usingSharedWorker = false;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Playwright worker cleanup completed");
        }
    }

    /**
     * Closes the Playwright worker in the background.
     *
     * <p>Note: if {@code closer} (e.g. {@code page.close()}) itself hangs past {@link #closeTimeout},
     * this method gives up waiting and returns, but the abandoned daemon thread may still be running
     * afterward. This is a pre-existing, separate limitation and is not addressed here.</p>
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
            throw new CrawlerSystemException("Unsupported browser: '" + browserName
                    + "'. Supported values are: 'chromium', 'firefox', or 'webkit'. Please check your browser configuration.");
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
            if (closed) {
                // This instance was already closed and never re-init()'d since: reject immediately
                // instead of silently resurrecting it via auto-init. (Calling init() explicitly again
                // after close() is still supported and clears this flag - see init().)
                throw new CrawlerSystemException("PlaywrightClient has already been closed. URL: " + request.getUrl());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Worker not initialized, triggering init()");
            }
            init();
        }

        final String url = request.getUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("Executing request - URL: {}, Method: {}", url, request.getMethod());
        }

        // Capture the volatile worker into a single local and use only that local below. A concurrent
        // close() may null the field at any moment; reading it once here means a race can only ever
        // produce a clean "already closed" error (when the capture reads null) instead of a raw NPE
        // from re-reading a field that close() nulled between the check above and the dereference.
        final Tuple4<Playwright, Browser, BrowserContext, Page> currentWorker = worker;
        if (currentWorker == null) {
            throw new CrawlerSystemException("PlaywrightClient has already been closed. URL: " + url);
        }

        final Page page = currentWorker.getValue4();
        final AtomicReference<Response> responseRef = new AtomicReference<>();
        final AtomicReference<Download> downloadRef = new AtomicReference<>();

        // Create handler references for proper cleanup
        final Consumer<Response> responseHandler = response -> responseRef.compareAndSet(null, response);
        final Consumer<Download> downloadHandler = download -> downloadRef.compareAndSet(null, download);

        synchronized (page) {
            if (closed) {
                throw new CrawlerSystemException("PlaywrightClient has already been closed. URL: " + url);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Acquired page lock for URL: {}", url);
            }

            try {
                page.onResponse(responseHandler);
                page.onDownload(downloadHandler);

                if (logger.isDebugEnabled()) {
                    logger.debug("Download handler registered for potential file downloads");
                }

                final Response response;
                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Accessing {}", url);
                    }
                    response = navigate(page, url);
                } catch (final Exception e) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Page navigation failed, attempting to handle as file download: {}", e.getMessage());
                    }
                    return waitForDownloadOrFail(page, request, responseRef, downloadRef, e);
                }

                // Playwright's navigate() legitimately returns null for some same-document navigations
                // (e.g. a hash-fragment-only change). Everything downstream (the debug log below and
                // createResponseData) dereferences this response, so surface a clean, catchable failure
                // here instead of letting it propagate as a raw NullPointerException. Must stay OUTSIDE
                // the catch above: CrawlingAccessException is a RuntimeException, so throwing it inside
                // that try would be swallowed and misrouted to the download fallback.
                if (response == null) {
                    throw new CrawlingAccessException("Failed to access the URL. Navigation returned no response. URL: " + url);
                }

                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Waiting for LoadState: {}", renderedState);
                    }
                    waitForLoadState(page, renderedState);

                    if (logger.isDebugEnabled()) {
                        logger.debug("Page reached LoadState: {}", renderedState);
                    }
                } catch (final TimeoutError e) {
                    // Only a genuine LoadState timeout is handled here. Other PlaywrightExceptions (e.g.
                    // the page/browser having crashed or been closed) are deliberately NOT caught: the
                    // page is no longer in a state we can trust, so they should propagate as real
                    // failures instead of being treated as "content is fine, just slow to settle".
                    if (downloadRef.get() != null) {
                        // A download may have started as a side effect even though the load-state wait
                        // itself timed out (e.g. a JS-triggered download on a page that is also chatty).
                        if (logger.isDebugEnabled()) {
                            logger.debug("waitForLoadState failed but a download was already detected, "
                                    + "attempting to handle as file download: {}", e.getMessage());
                        }
                        return waitForDownloadOrFail(page, request, responseRef, downloadRef, e);
                    }
                    // No download has been detected YET at the instant of the timeout, but one may fire
                    // a moment later (e.g. a JS-triggered download that races the load-state timeout).
                    // Poll briefly - far shorter than downloadTimeout - before giving up. Tradeoff: this
                    // adds a small, bounded latency (at most LOAD_STATE_TIMEOUT_GRACE_PERIOD_MILLIS) to
                    // every load-state-timeout fallback, in exchange for catching downloads that surface
                    // just after the timeout is detected. Unlike waitForDownloadOrFail, a grace period
                    // that elapses with nothing detected must NOT fail: the page is still successfully
                    // loaded, so we fall through to returning that content.
                    final ResponseData gracePeriodDownload =
                            pollForDownload(page, request, responseRef, downloadRef, LOAD_STATE_TIMEOUT_GRACE_PERIOD_MILLIS);
                    if (gracePeriodDownload != null) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("A download surfaced within the grace period after the LoadState timeout for URL: {}", url);
                        }
                        return gracePeriodDownload;
                    }
                    // No download fired: the page still navigated and rendered successfully, it just
                    // never reached the configured LoadState (e.g. NETWORKIDLE never fires for pages
                    // with persistent connections). Don't discard the successfully-loaded content.
                    logger.warn(
                            "Timed out waiting for LoadState '{}' on URL: {}. Falling back to the content " + "that was already loaded.",
                            renderedState, url, e);
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
            } finally {
                // Clean up event handlers to prevent memory leaks
                page.offResponse(responseHandler);
                page.offDownload(downloadHandler);

                if (logger.isDebugEnabled()) {
                    logger.debug("Resetting page to about:blank");
                }
                resetPage(page);
            }
        }
    }

    /**
     * Navigates the page to the given URL and returns the main-resource response.
     *
     * <p>Extracted as its own protected method (rather than calling {@link Page#navigate(String)}
     * directly from {@link #execute(RequestData)}) purely as a test seam, so tests can simulate a
     * {@code null} response - which Playwright's API permits for some same-document navigations - without
     * needing a real page that performs such a navigation.</p>
     *
     * @param page The page.
     * @param url The URL to navigate to.
     * @return The main-resource response, or {@code null} for a same-document navigation.
     */
    protected Response navigate(final Page page, final String url) {
        return page.navigate(url);
    }

    /**
     * Waits for the page to reach the given load state.
     *
     * <p>Extracted as its own protected method (rather than calling {@link Page#waitForLoadState(LoadState)}
     * directly from {@link #execute(RequestData)}) purely as a test seam, so tests can simulate a
     * non-timeout {@link com.microsoft.playwright.PlaywrightException} (e.g. a page/browser crash) without
     * needing a real race against Playwright's internals.</p>
     *
     * @param page The page.
     * @param state The load state to wait for.
     */
    protected void waitForLoadState(final Page page, final LoadState state) {
        page.waitForLoadState(state);
    }

    /**
     * Waits for a download to be detected (via the {@code responseRef}/{@code downloadRef} handlers
     * registered in {@link #execute(RequestData)}), or fails with a {@link CrawlingAccessException}
     * if none is detected within {@link #downloadTimeout}.
     *
     * <p>This is used when the browser reports an in-page navigation failure that may actually be a
     * file download (Chromium/Firefox/WebKit abort in-page navigation when the target triggers a
     * download), or when a download was detected as a side effect of an otherwise-failed load-state
     * wait.</p>
     *
     * @param page The page.
     * @param request The request data.
     * @param responseRef A reference populated by the page's {@code onResponse} handler, if any.
     * @param downloadRef A reference populated by the page's {@code onDownload} handler, if any.
     * @param cause The exception that triggered this fallback, used as the cause of the thrown
     *            {@link CrawlingAccessException} if no download is detected.
     * @return The response data for the detected download.
     */
    protected ResponseData waitForDownloadOrFail(final Page page, final RequestData request, final AtomicReference<Response> responseRef,
            final AtomicReference<Download> downloadRef, final Exception cause) {
        final ResponseData responseData = pollForDownload(page, request, responseRef, downloadRef, downloadTimeout * 1000L);
        if (responseData != null) {
            return responseData;
        }

        final Response response = responseRef.get();
        final Download download = downloadRef.get();
        if (logger.isDebugEnabled()) {
            logger.debug("Failed to access URL - response: {}, download: {}", response != null, download != null);
        }
        final String errorDetails = "URL: " + request.getUrl() + ", Response received: " + (response != null) + ", Download started: "
                + (download != null) + ", Timeout: " + downloadTimeout + "s";
        throw new CrawlingAccessException("Failed to access the URL. " + errorDetails, cause);
    }

    /**
     * Polls for a download to be detected (via the {@code responseRef}/{@code downloadRef} handlers
     * registered in {@link #execute(RequestData)}), driving Playwright's event loop with
     * {@link Page#waitForTimeout(double)} using a progressive backoff, for up to {@code maxWaitMillis}.
     *
     * <p>Shared by both {@link #waitForDownloadOrFail} (called with the full {@link #downloadTimeout}
     * once a download is known to be in progress) and {@link #execute(RequestData)}'s short grace-poll
     * after a LoadState timeout (called with {@link #LOAD_STATE_TIMEOUT_GRACE_PERIOD_MILLIS}). This
     * method itself never fails on timeout - it returns {@code null} so the caller can decide whether a
     * miss is a hard failure or a graceful fall-back.</p>
     *
     * @param page The page.
     * @param request The request data.
     * @param responseRef A reference populated by the page's {@code onResponse} handler, if any.
     * @param downloadRef A reference populated by the page's {@code onDownload} handler, if any.
     * @param maxWaitMillis The maximum time to poll, in milliseconds.
     * @return The response data for the detected download, or {@code null} if a response and a download
     *         were not both observed within {@code maxWaitMillis}.
     */
    protected ResponseData pollForDownload(final Page page, final RequestData request, final AtomicReference<Response> responseRef,
            final AtomicReference<Download> downloadRef, final long maxWaitMillis) {
        // Wait for download with progressive backoff for responsiveness
        // Start with short intervals, increase over time to balance responsiveness and CPU efficiency
        // Note: page.waitForTimeout() is required to drive Playwright's event loop
        final long startTime = System.currentTimeMillis();
        long pollInterval = 100L; // Start with 100ms
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            if (responseRef.get() != null && downloadRef.get() != null) {
                break;
            }
            try {
                page.waitForTimeout(pollInterval);
                // Progressive backoff: 100ms -> 200ms -> 400ms -> 500ms (max)
                if (pollInterval < 500L) {
                    pollInterval = Math.min(pollInterval * 2, 500L);
                }
            } catch (final Exception ignored) {
                // ignore timeout exceptions during polling
            }
        }
        if (logger.isDebugEnabled()) {
            final long elapsed = System.currentTimeMillis() - startTime;
            logger.debug("Download wait completed after {}ms, maxWait: {}ms", elapsed, maxWaitMillis);
        }

        final Response response = responseRef.get();
        final Download download = downloadRef.get();
        if (response != null && download != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Downloaded:  URL: {}", response.url());
            }
            return createResponseData(page, request, response, download);
        }
        return null;
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
        // Also check ignoreSslCertificate for backward compatibility with Hc5HttpClient's config
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
                proxy.setPassword(new String(proxyCredentials.getPassword()));
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
     * Creates an authenticated Playwright context, by using Fess's built-in Hc5HttpClient to do authentication,
     * then passes its cookies to Playwright.
     *
     * @param browser The browser instance.
     * @param newContextOptions The new context options.
     * @return The browser context.
     */
    protected BrowserContext createAuthenticatedContext(final Browser browser, final NewContextOptions newContextOptions) {
        final Hc5Authentication[] authentications =
                getInitParameter(HcHttpClient.AUTHENTICATIONS_PROPERTY, new Hc5Authentication[0], Hc5Authentication[].class);

        if (logger.isDebugEnabled()) {
            logger.debug("Processing {} authentication configuration(s)", authentications.length);
        }

        if (authentications.length == 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("No authentication configured, creating standard browser context");
            }
            return browser.newContext(newContextOptions);
        }

        for (final Hc5Authentication authentication : authentications) {
            if (logger.isDebugEnabled()) {
                logger.debug("Processing authentication scheme: {}", authentication.getAuthScheme().getName());
            }
            if (!Strings.CS.equals(authentication.getAuthScheme().getName(), "form")) {
                // Use the first non-form auth credentials to fill the browser's credential prompt
                if (logger.isDebugEnabled()) {
                    logger.debug("Setting HTTP credentials for non-form authentication");
                }
                final String username = authentication.getCredentials().getUserPrincipal().getName();
                final String password = new String(authentication.getCredentials().getPassword());
                newContextOptions.setHttpCredentials(username, password);
                break;
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Creating browser context with authentication");
        }
        final BrowserContext playwrightContext = browser.newContext(newContextOptions);
        try (final var fessHttpClient = new Hc5HttpClient()) {
            fessHttpClient.setInitParameterMap(initParamMap);
            fessHttpClient.init();
            final List<org.apache.hc.client5.http.cookie.Cookie> fessCookies = fessHttpClient.cookieStore.getCookies();
            if (logger.isDebugEnabled()) {
                logger.debug("Transferring {} cookies from Hc5HttpClient to Playwright", fessCookies.size());
            }
            final List<Cookie> playwrightCookies = fessCookies.stream().map(apacheCookie -> {
                final var playwrightCookie = new Cookie(apacheCookie.getName(), apacheCookie.getValue());
                playwrightCookie.setDomain(apacheCookie.getDomain());
                playwrightCookie.setPath(apacheCookie.getPath());
                playwrightCookie.setSecure(apacheCookie.isSecure());

                // Set expiry time - Apache HC5's cookies use Instant,
                // while Playwright uses seconds.
                final Instant cookieExpiryInstant = apacheCookie.getExpiryInstant();
                if (cookieExpiryInstant != null) {
                    playwrightCookie.setExpires(cookieExpiryInstant.toEpochMilli() / 1000.0);
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
