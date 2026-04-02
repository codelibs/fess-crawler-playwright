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
package org.codelibs.fess.crawler.util;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

/**
 * A sample single-threaded proxy server (based on Jetty) for testing purposes.
 */
public class CrawlerWebProxy {
    public enum ProxyAccessStatus {
        NOT_ACCESSED, PROMPTED_FOR_CREDENTIALS, ACCESS_GRANTED, ACCESS_DENIED
    }

    private static final Logger logger = LogManager.getLogger(CrawlerWebProxy.class);
    public static final String PROXY_AUTHENTICATE = "proxy-authenticate";
    public static final String PROXY_AUTHORIZATION = "proxy-authorization";

    private final AtomicReference<String> authHeader = new AtomicReference<>("");

    // Records the last access result
    private final AtomicReference<ProxyAccessStatus> accessResult = new AtomicReference<>(ProxyAccessStatus.NOT_ACCESSED);
    private Server proxyServer;
    private HttpClient httpClient;

    // Ports below 1024 requires sudo permissions on linux.
    private int port = 3128;

    public void setPort(final int port) {
        this.port = port;
    }

    public void setCredentials(final String username, final String password) {
        final var encodedCredentials = encodeCredentials(username, password);
        this.authHeader.set(encodedCredentials);
    }

    public void setCredentials(final UsernamePasswordCredentials apacheCredentials) {
        if (apacheCredentials == null) {
            setCredentials("", "");
        } else {
            setCredentials(apacheCredentials.getUserName(), new String(apacheCredentials.getPassword()));
        }
    }

    public ProxyAccessStatus getAccessResult() {
        return this.accessResult.get();
    }

    public void start() {
        try {
            httpClient = new HttpClient();
            httpClient.start();

            proxyServer = new Server(this.port);
            proxyServer.setHandler(new CrawlerProxyHandler());
            proxyServer.start();
        } catch (final Exception e) {
            throw new CrawlerSystemException(e);
        }
    }

    public void stop() {
        try {
            proxyServer.stop();
            httpClient.stop();
        } catch (final Exception e) {
            throw new CrawlerSystemException(e);
        }
    }

    private static String encodeCredentials(final String username, final String password) {
        if (StringUtils.isAnyBlank(username, password)) {
            logger.warn("Username or password is blank - removing all credentials");
            return "";
        }
        final String usernameAndPassword = "%s:%s".formatted(username, password);
        final String encodedString = Base64.getEncoder().encodeToString(usernameAndPassword.getBytes());
        return "Basic %s".formatted(encodedString);
    }

    private class CrawlerProxyHandler extends Handler.Abstract {
        @Override
        public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
            logger.info("Proxy request received!");
            final String correctAuthHeader = CrawlerWebProxy.this.authHeader.get();

            // Allow access if there is no authHeader
            if (StringUtils.isBlank(correctAuthHeader)) {
                logger.info("No authentication required");
                grantAccess(request, response, callback);
                return true;
            }

            final var requestAuthHeader = request.getHeaders().get(PROXY_AUTHORIZATION);

            if (requestAuthHeader == null) {
                requireAuth(response, callback);
            } else if (StringUtils.equals(correctAuthHeader, requestAuthHeader)) {
                logger.info("Authentication matches!");
                grantAccess(request, response, callback);
            } else {
                denyAccess(response, callback);
            }
            return true;
        }

        private void grantAccess(final Request request, final Response response, final Callback callback) {
            logger.info("Access granted!");
            CrawlerWebProxy.this.accessResult.set(ProxyAccessStatus.ACCESS_GRANTED);
            try {
                final String targetUri = request.getHttpURI().toString();
                final ContentResponse proxyResponse = httpClient.newRequest(targetUri).method(request.getMethod()).send();

                response.setStatus(proxyResponse.getStatus());
                final String contentType = proxyResponse.getHeaders().get(HttpHeader.CONTENT_TYPE);
                if (contentType != null) {
                    response.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType);
                }
                response.write(true, ByteBuffer.wrap(proxyResponse.getContent()), callback);
            } catch (final Exception e) {
                logger.warn("Failed to proxy request", e);
                response.setStatus(502);
                callback.succeeded();
            }
        }

        private void denyAccess(final Response response, final Callback callback) {
            logger.info("Access denied!");
            CrawlerWebProxy.this.accessResult.set(ProxyAccessStatus.ACCESS_DENIED);
            response.setStatus(401);
            callback.succeeded();
        }

        private void requireAuth(final Response response, final Callback callback) {
            logger.info("Username and password is required but wasn't provided, prompting client for credentials...");
            CrawlerWebProxy.this.accessResult.set(ProxyAccessStatus.PROMPTED_FOR_CREDENTIALS);
            response.setStatus(407);
            response.getHeaders().put(PROXY_AUTHENTICATE, "Basic realm=\"Enter username and password\"");
            callback.succeeded();
        }
    }
}
