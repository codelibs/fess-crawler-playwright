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

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.codelibs.core.exception.IORuntimeException;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.core.misc.Tuple3;
import org.codelibs.core.stream.StreamUtil;
import org.codelibs.fess.crawler.Constants;
import org.codelibs.fess.crawler.client.AbstractCrawlerClient;
import org.codelibs.fess.crawler.entity.RequestData;
import org.codelibs.fess.crawler.entity.RequestData.Method;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserType.LaunchOptions;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;

/**
 * @author shinsuke
 *
 */
public class PlaywrightClient extends AbstractCrawlerClient {

    private static final Logger logger = LoggerFactory.getLogger(PlaywrightClient.class);

    private static final String RENDERED_STATE = "renderedState";

    private static final String LAST_MODIFIED_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    protected Map<String, String> options = new HashMap<>();

    protected String browserName = "chromium";

    protected LaunchOptions launchOptions;

    protected int downloadTimeout = 15; // 15s

    protected LoadState renderedState = LoadState.NETWORKIDLE;

    private Tuple3<Playwright, Browser, Page> worker;

    @Override
    public synchronized void init() {
        if (worker != null) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Initiaizing Playwright...");
        }
        super.init();

        final String renderedStateParam = getInitParameter(RENDERED_STATE, renderedState.name(), String.class);
        if (renderedStateParam != null) {
            renderedState = LoadState.valueOf(renderedStateParam);
        }

        Playwright playwright = null;
        Browser browser = null;
        Page page = null;
        try {
            playwright = Playwright.create(new Playwright.CreateOptions().setEnv(options));
            browser = getBrowserType(playwright).launch(launchOptions);
            page = browser.newPage();
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to create Playwright instance.", e);
            }
            close(playwright, browser, page);
            throw new CrawlerSystemException("Failed to ccreate PlaywrightClient.", e);
        }

        worker = new Tuple3<>(playwright, browser, page);
    }

    @Override
    public void close() {
        if (worker == null) {
            return;
        }
        try {
            close(worker.getValue1(), worker.getValue2(), worker.getValue3());
        } finally {
            worker = null;
        }
    }

    protected void close(final Playwright playwright, final Browser browser, final Page page) {
        try {
            try {
                if (page != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Closing Page...");
                    }
                    page.close();
                }
            } finally {
                if (browser != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Closing Browser...");
                    }
                    browser.close();
                }
            }
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing Playwright...");
            }
            playwright.close();
        }
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
        final Page page = worker.getValue3();
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
            }
        }
    }

    private ResponseData createResponseData(final Page page, final RequestData request, final Response response, final Download download) {
        final ResponseData responseData = new ResponseData();

        final String url = response.url();
        responseData.setUrl(url);
        responseData.setMethod(request.getMethod().name());

        final String charSet = getCharSet(response);
        responseData.setCharSet(charSet);
        final int statusCode = getStatusCode(response);
        responseData.setHttpStatusCode(statusCode);
        responseData.setLastModified(getLastModified(response));
        final String contentType = getContentType(response);
        responseData.setMimeType(contentType);

        response.allHeaders().entrySet().forEach(e -> responseData.addMetaData(e.getKey(), e.getValue()));

        if (statusCode > 400) {
            responseData.setContentLength(0);
            responseData.setResponseBody(new byte[0]);
        } else if (download == null) {
            final byte[] body = response.body();
            responseData.setContentLength(body.length);
            if (Method.HEAD != request.getMethod()) {
                responseData.setResponseBody(body);
            }
        } else {
            try {
                final File tempFile = File.createTempFile("fess-crawler-playwright-", ".tmp");
                download.saveAs(tempFile.toPath());
                responseData.setContentLength(tempFile.length());
                responseData.setResponseBody(tempFile, true);
                if ("text/html".equals(contentType)) {
                    // TODO replace content-type
                }
                download.delete();
            } catch (final IOException e) {
                throw new IORuntimeException(e);
            }
        }

        return responseData;
    }

    /**
     * @param wd
     * @return
     */
    private String getContentType(final Response response) {
        final String contentType = response.headerValue("content-type");
        if (StringUtil.isNotBlank(contentType)) {
            return contentType;
        }
        return "text/html";
    }

    /**
     * @param wd
     * @return
     */
    private Date getLastModified(final Response response) {
        final String lastModified = response.headerValue("last-modified");
        if (StringUtil.isNotBlank(lastModified)) {
            try {
                final SimpleDateFormat dateFormat = new SimpleDateFormat(LAST_MODIFIED_FORMAT);
                return dateFormat.parse(lastModified);
            } catch (final ParseException e) {
                logger.warn("Invalid format: " + lastModified, e);
            }
        }
        return null;
    }

    /**
     * @param wd
     * @return
     */
    private int getStatusCode(final Response response) {
        return response.status();
    }

    /**
     * @param wd
     * @return
     */
    private String getCharSet(final Response response) {
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
}
