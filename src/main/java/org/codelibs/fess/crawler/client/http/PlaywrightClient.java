/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Resource;

import com.microsoft.playwright.*;
import com.microsoft.playwright.Browser.NewContextOptions;
import com.microsoft.playwright.options.Proxy;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.codelibs.core.exception.IORuntimeException;
import org.codelibs.core.lang.StringUtil;
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
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.util.CrawlingParameterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.options.LoadState;

/**
 * @author shinsuke
 *
 */
public class PlaywrightClient extends AbstractCrawlerClient {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightClient.class);

    protected static final String RENDERED_STATE = "renderedState";

    protected static final String IGNORE_HTTPS_ERRORS_PROPERTY = "ignoreHttpsErrors";

    protected static final String PROXY_BYPASS_PROPERTY = "proxyBypass";

    protected static final String LAST_MODIFIED_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";

    protected Map<String, String> options = new HashMap<>();

    protected String browserName = "chromium";

    protected LaunchOptions launchOptions;

    protected NewContextOptions newContextOptions = new NewContextOptions();

    protected int downloadTimeout = 15; // 15s

    protected int closeTimeout = 15; // 15s

    protected LoadState renderedState = LoadState.NETWORKIDLE;

    protected Tuple4<Playwright, Browser, BrowserContext, Page> worker;

    @Resource
    protected CrawlerContainer crawlerContainer;

    @Override
    public synchronized void init() {
        if (worker != null) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Initiaizing Playwright...");
        }
        super.init();

        // initialize Playwright's browser context
        this.initNewContextOptions();

        final String renderedStateParam = getInitParameter(RENDERED_STATE, renderedState.name(), String.class);
        if (renderedStateParam != null) {
            renderedState = LoadState.valueOf(renderedStateParam);
        }

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext browserContext = null;
        Page page = null;
        try {
            playwright = Playwright.create(new Playwright.CreateOptions().setEnv(options));
            browser = getBrowserType(playwright).launch(launchOptions);
            browserContext = browser.newContext(newContextOptions);
            page = browser.newContext(newContextOptions).newPage();
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to create Playwright instance.", e);
            }
            close(playwright, browser, browserContext, page);
            throw new CrawlerSystemException("Failed to ccreate PlaywrightClient.", e);
        }

        worker = new Tuple4<>(playwright, browser, browserContext, page);
    }

    @Override
    public void close() {
        if (worker == null) {
            return;
        }
        try {
            close(worker.getValue1(), worker.getValue2(), worker.getValue3(), worker.getValue4());
        } finally {
            worker = null;
        }
    }

    protected void closeInBackground(final Runnable closer) {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            final Thread thread = new Thread(() -> {
                try {
                    closer.run();
                } catch (final Exception e) {
                    logger.warn("Failed to close the playwright instance.", e);
                }
                latch.countDown();
            }, "Playwright-Closer");
            thread.setDaemon(true);
            thread.start();
            try {
                latch.await(closeTimeout, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                logger.warn("Interrupted to wait a process.", e);
            }
        } catch (final Exception e) {
            logger.warn("Failed to close the playwright instance.", e);
        }
    }

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

    protected BrowserType getBrowserType(final Playwright playwright) {
        if (logger.isDebugEnabled()) {
            logger.debug("Create {}...", browserName);
        }
        return switch (browserName) {
        case "firefox":
            yield playwright.firefox();
        case "webkit":
            yield playwright.webkit();
        case "chromium":
            yield playwright.chromium();
        default:
            throw new CrawlerSystemException("Unknown browser name: " + browserName);
        };
    }

    public void addOption(final String key, final String value) {
        options.put(key, value);
    }

    @Override
    public ResponseData execute(final RequestData request) {
        if (worker == null) {
            init();
        }

        final String url = request.getUrl();
        final Page page = worker.getValue4();
        final AtomicReference<Response> responseRef = new AtomicReference<>();
        final AtomicReference<Download> downloadRef = new AtomicReference<>();
        synchronized (page) {
            try {
                page.onResponse(response -> {
                    if (responseRef.get() == null) {
                        responseRef.set(response);
                    }
                });
                page.onDownload(downloadRef::set);

                if (logger.isDebugEnabled()) {
                    logger.debug("Accessing {}", url);
                }
                final Response response = page.navigate(url);

                page.waitForLoadState(renderedState);

                if (logger.isDebugEnabled()) {
                    logger.debug("Loaded: Base URL: {}, Response URL: {}", url, response.url());
                }
                return createResponseData(page, request, response, null);
            } catch (final Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Waiting for downloaded file: {}", e.getMessage());
                }
                for (int i = 0; i < downloadTimeout * 10 && (downloadRef.get() == null || responseRef.get() == null); i++) {
                    page.waitForTimeout(100L);
                }
                final Response response = responseRef.get();
                final Download download = downloadRef.get();
                if (response != null && download != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Downloaded:  URL: {}", response.url());
                    }
                    return createResponseData(page, request, response, download);
                }
                throw new CrawlerSystemException("Failed to access " + request.getUrl(), e);
            } finally {
                resetPage(page);
            }
        }
    }

    protected void resetPage(final Page page) {
        try {
            page.navigate("about:blank");
            page.waitForLoadState(LoadState.LOAD);
        } catch (final Exception e) {
            logger.warn("Could not reset a page.", e);
        }
    }

    protected ResponseData createResponseData(final Page page, final RequestData request, final Response response,
            final Download download) {
        final ResponseData responseData = new ResponseData();

        final String originalUrl = request.getUrl();
        final String url = response.url();
        if (!originalUrl.equals(url)) {
            CrawlerContext context = CrawlingParameterUtil.getCrawlerContext();
            if (context != null) {
                UrlFilter urlFilter = context.getUrlFilter();
                if (urlFilter != null && !urlFilter.match(url)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} is not a target url:", url);
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

        response.allHeaders().entrySet().forEach(e -> responseData.addMetaData(e.getKey(), e.getValue()));

        if (statusCode > 400) {
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
                    logger.warn("Could not read from {}", url, e);
                }
                return body;
            }).orElse(body);
            responseData.setContentLength(responseBody.length);
            if (Method.HEAD != request.getMethod()) {
                responseData.setResponseBody(responseBody);
            }
        } else {
            try {
                final File tempFile = File.createTempFile("fess-crawler-playwright-", ".tmp");
                download.saveAs(tempFile.toPath());
                responseData.setContentLength(tempFile.length());
                getMimeTypeHelper().ifPresent(mimeTypeHelper -> {
                    final String filename = getFilename(url);
                    try (final InputStream in = new FileInputStream(tempFile)) {
                        final String contentType = mimeTypeHelper.getContentType(in, filename);
                        responseData.setMimeType(contentType);
                        if (logger.isDebugEnabled()) {
                            logger.debug("filename:{} content-type:{}", filename, contentType);
                        }
                    } catch (final IOException e) {
                        logger.warn("Could not read {}", tempFile.getAbsolutePath(), e);
                    }
                });
                responseData.setResponseBody(tempFile, true);
            } catch (final IOException e) {
                throw new IORuntimeException(e);
            } finally {
                download.delete();
            }
        }

        return responseData;
    }

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

    protected Optional<MimeTypeHelper> getMimeTypeHelper() {
        return Optional.ofNullable(crawlerContainer.getComponent("mimeTypeHelper"));
    }

    protected String getContentType(final Response response) {
        final String contentType = response.headerValue("content-type");
        if (StringUtil.isNotBlank(contentType)) {
            return contentType.split(";")[0].trim();
        }
        return "text/html";
    }

    protected Date getLastModified(final Response response) {
        return parseDate(response.headerValue("last-modified"));
    }

    protected Date parseDate(final String value) {
        if (StringUtil.isNotBlank(value)) {
            try {
                final SimpleDateFormat dateFormat = new SimpleDateFormat(LAST_MODIFIED_FORMAT, Locale.ENGLISH);
                return dateFormat.parse(value);
            } catch (final ParseException e) {
                logger.warn("Invalid format: " + value, e);
            }
        }
        return null;
    }

    protected int getStatusCode(final Response response) {
        return response.status();
    }

    protected String getCharSet(final Response response) {
        final String contentType = response.headerValue("content-type");
        if (StringUtil.isNotBlank(contentType)) {
            final String[] result = StreamUtil.split(contentType, ";").get(stream -> stream.map(s -> {
                final String[] values = s.split("=");
                if ((values.length == 2) && "charset".equalsIgnoreCase(values[0].trim())) {
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
     * Reads configurations from Web UI & pass it to Playwright Context
     */
    private void initNewContextOptions() {
        if (this.newContextOptions == null) {
            this.newContextOptions = new NewContextOptions();
        }

        // Check whether to skip SSL certificate checking
        // Also check ignoreSslCertificate for backward compatibility with HcHttpClient's config
        final boolean ignoreHttpsErrors = getInitParameter(IGNORE_HTTPS_ERRORS_PROPERTY, false, Boolean.class);
        final boolean ignoreSslCertificate = getInitParameter(HcHttpClient.IGNORE_SSL_CERTIFICATE_PROPERTY, false, Boolean.class);

        if (ignoreHttpsErrors || ignoreSslCertificate) {
            this.newContextOptions.ignoreHTTPSErrors = true;
        }

        // append existing proxy configuration
        final String proxyHost = getInitParameter(HcHttpClient.PROXY_HOST_PROPERTY, null, String.class);
        final Integer proxyPort = getInitParameter(HcHttpClient.PROXY_PORT_PROPERTY, null, Integer.class);
        final UsernamePasswordCredentials proxyCredentials =
                getInitParameter(HcHttpClient.PROXY_CREDENTIALS_PROPERTY, null, UsernamePasswordCredentials.class);
        final String proxyBypass = getInitParameter(PROXY_BYPASS_PROPERTY, null, String.class);

        if (!StringUtils.isBlank(proxyHost)) {
            final String proxyAddress = proxyPort == null ? proxyHost : (proxyHost + ":" + proxyPort);
            final Proxy proxy = new Proxy(proxyAddress);
            if (proxyCredentials != null) {
                proxy.setUsername(proxyCredentials.getUserName());
                proxy.setPassword(proxyCredentials.getPassword());
            }
            proxy.setBypass(proxyBypass);
            this.newContextOptions.setProxy(proxy);
        }
    }

    public void setLaunchOptions(final LaunchOptions launchOptions) {
        this.launchOptions = launchOptions;
    }

    public void setBrowserName(final String browserName) {
        this.browserName = browserName;
    }

    public void setDownloadTimeout(final int downloadTimeout) {
        this.downloadTimeout = downloadTimeout;
    }

    public void setRenderedState(final LoadState loadState) {
        this.renderedState = loadState;
    }

    public void setCloseTimeout(final int closeTimeout) {
        this.closeTimeout = closeTimeout;
    }

    public void setNewContextOptions(NewContextOptions newContextOptions) {
        this.newContextOptions = newContextOptions;
    }
}
