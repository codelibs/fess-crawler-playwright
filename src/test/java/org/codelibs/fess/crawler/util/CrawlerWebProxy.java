/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
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

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.servlet.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sample single-threaded proxy server (based on Jetty) for testing purposes.
 */
public class CrawlerWebProxy {
    public enum ProxyAccessStatus {
        NOT_ACCESSED, PROMPTED_FOR_CREDENTIALS, ACCESS_GRANTED, ACCESS_DENIED
    }

    private static final Logger logger = LoggerFactory.getLogger(CrawlerWebProxy.class);
    public static final String PROXY_AUTHENTICATE = "proxy-authenticate";
    public static final String PROXY_AUTHORIZATION = "proxy-authorization";

    private final AtomicReference<String> authHeader = new AtomicReference<>("");

    // Records the last access result
    private final AtomicReference<ProxyAccessStatus> accessResult = new AtomicReference<>(ProxyAccessStatus.NOT_ACCESSED);
    private final Server proxyServer = new Server();

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
            setCredentials(apacheCredentials.getUserName(), apacheCredentials.getPassword());
        }
    }

    public ProxyAccessStatus getAccessResult() {
        return this.accessResult.get();
    }

    public void start() {
        try {
            // add port to server
            final SocketConnector connector = new SocketConnector();
            connector.setPort(this.port);
            this.proxyServer.setConnectors(ArrayUtils.toArray(connector));

            attachProxyServlet();
            proxyServer.start();
        } catch (final Exception e) {
            throw new CrawlerSystemException(e);
        }
    }

    public void stop() {
        try {
            proxyServer.stop();
        } catch (final Exception e) {
            throw new CrawlerSystemException(e);
        }
    }

    private void attachProxyServlet() {
        final ServletHandler handler = new ServletHandler();
        final CrawlerProxyServlet proxyServlet = new CrawlerProxyServlet();
        final ServletHolder holder = new ServletHolder(proxyServlet);
        handler.addServletWithMapping(holder, "/*");
        this.proxyServer.setHandler(handler);
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

    private class CrawlerProxyServlet extends ProxyServlet {
        @Override
        public void service(final ServletRequest req, final ServletResponse res) throws ServletException, IOException {
            logger.info("Proxy request received!");
            final String correctAuthHeader = CrawlerWebProxy.this.authHeader.get();

            // Allow access if there is no authHeader
            if (StringUtils.isBlank(correctAuthHeader)) {
                logger.info("No authentication required");
                grantAccess(req, res);
                return;
            }

            if (req instanceof final HttpServletRequest httpRequest && res instanceof final HttpServletResponse httpResponse) {
                final var requestAuthHeader = httpRequest.getHeader(PROXY_AUTHORIZATION);

                if (requestAuthHeader == null) {
                    requireAuth(httpResponse);
                } else if (StringUtils.equals(correctAuthHeader, requestAuthHeader)) {
                    logger.info("Authentication matches!");
                    grantAccess(req, res);
                } else {
                    denyAccess(httpResponse);
                }
            } else {
                res.setContentType("text/html;charset=UTF-8");
                res.setContentLength(0);
                res.flushBuffer();
            }
        }

        private void grantAccess(final ServletRequest req, final ServletResponse res) throws ServletException, IOException {
            logger.info("Access granted!");
            CrawlerWebProxy.this.accessResult.set(ProxyAccessStatus.ACCESS_GRANTED);
            super.service(req, res);
        }

        private void denyAccess(final HttpServletResponse httpResponse) throws IOException {
            logger.info("Access denied!");
            CrawlerWebProxy.this.accessResult.set(ProxyAccessStatus.ACCESS_DENIED);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.flushBuffer();
        }

        private void requireAuth(final HttpServletResponse httpResponse) throws IOException {
            logger.info("Username and password is required but wasn't provided, prompting client for credentials...");
            CrawlerWebProxy.this.accessResult.set(ProxyAccessStatus.PROMPTED_FOR_CREDENTIALS);
            httpResponse.setStatus(HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED);
            httpResponse.setHeader(PROXY_AUTHENTICATE, "Basic realm=\"Enter username and password\"");
            httpResponse.flushBuffer();
        }
    }
}
