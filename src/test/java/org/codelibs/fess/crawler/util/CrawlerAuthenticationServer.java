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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;

/**
 * A mock authentication server implementation for testing purposes, using Jetty 12.
 * Supports Basic, Digest &amp; Form authentication method
 */
public class CrawlerAuthenticationServer {
    private static final Logger logger = LogManager.getLogger(CrawlerAuthenticationServer.class);

    public enum AuthMethod {
        BASIC, DIGEST, FORM
    }

    /** Value of authenticity_token input entry that will be used for verification when FORM authMethod is used */
    private static final String TOKEN_VALUE_FOR_FORM_AUTH = "Abcdef1234!@#$";

    private static final String REALM = "test";

    private final Server server = new Server();
    private final Map<String, String> userCredentials = new ConcurrentHashMap<>();
    private final AtomicReference<AuthMethod> currentAuthMethod = new AtomicReference<>();

    // Simple session store for form authentication
    private final Set<String> authenticatedSessions = ConcurrentHashMap.newKeySet();

    private int port = 7070;

    public void setPort(final int port) {
        this.port = port;
    }

    public void addUser(final String userName, final Object password) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding user '{}' to authentication realm", userName);
        }
        this.userCredentials.put(userName, password.toString());
    }

    public void setAuthMethod(final AuthMethod authMethod) {
        if (logger.isInfoEnabled()) {
            logger.info("Setting authentication method to: {}", authMethod);
        }
        switch (authMethod) {
        case BASIC -> server.setHandler(new BasicAuthHandler());
        case DIGEST -> server.setHandler(new DigestAuthHandler());
        case FORM -> server.setHandler(new FormAuthHandler());
        }
        this.currentAuthMethod.set(authMethod);
    }

    public AuthMethod getCurrentAuthMethod() {
        return this.currentAuthMethod.get();
    }

    public void start() {
        try {
            if (logger.isInfoEnabled()) {
                logger.info("Starting CrawlerAuthenticationServer on port {} with authentication method: {}", port,
                        currentAuthMethod.get());
            }

            final var connector = new ServerConnector(server);
            connector.setPort(this.port);
            server.addConnector(connector);

            server.start();

            if (logger.isInfoEnabled()) {
                logger.info("CrawlerAuthenticationServer started successfully on port {}", port);
            }
        } catch (final Exception e) {
            throw new CrawlerSystemException("Failed to start CrawlerAuthenticationServer on port " + port, e);
        }
    }

    public void stop() {
        try {
            if (logger.isInfoEnabled()) {
                logger.info("Stopping CrawlerAuthenticationServer on port {}", port);
            }
            this.server.stop();
            if (logger.isInfoEnabled()) {
                logger.info("CrawlerAuthenticationServer stopped successfully on port {}", port);
            }
        } catch (final Exception e) {
            throw new CrawlerSystemException("Failed to stop CrawlerAuthenticationServer on port " + port, e);
        }
    }

    private boolean validateCredentials(final String username, final String password) {
        final String storedPassword = userCredentials.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }

    // =========================================================================
    //                                                       Basic Auth Handler
    // =========================================================================
    private class BasicAuthHandler extends Handler.Abstract {
        @Override
        public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
            final String authHeaderValue = request.getHeaders().get(HttpHeader.AUTHORIZATION);

            if (authHeaderValue != null && authHeaderValue.startsWith("Basic ")) {
                final String decoded = new String(Base64.getDecoder().decode(authHeaderValue.substring(6)), StandardCharsets.UTF_8);
                final String[] parts = decoded.split(":", 2);
                if (parts.length == 2 && validateCredentials(parts[0], parts[1])) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Basic authentication successful for user '{}'", parts[0]);
                    }
                    response.setStatus(200);
                    Content.Sink.write(response, true, "Authentication successful", callback);
                    return true;
                }
            }

            response.setStatus(401);
            response.getHeaders().put(HttpHeader.WWW_AUTHENTICATE, "Basic realm=\"" + REALM + "\"");
            Content.Sink.write(response, true, "", callback);
            return true;
        }
    }

    // =========================================================================
    //                                                      Digest Auth Handler
    // =========================================================================
    private class DigestAuthHandler extends Handler.Abstract {
        private final String nonce = UUID.randomUUID().toString();

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
            final String authHeaderValue = request.getHeaders().get(HttpHeader.AUTHORIZATION);

            if (authHeaderValue != null && authHeaderValue.startsWith("Digest ")) {
                if (validateDigestAuth(authHeaderValue, request.getMethod())) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Digest authentication successful");
                    }
                    response.setStatus(200);
                    Content.Sink.write(response, true, "Authentication successful", callback);
                    return true;
                }
            }

            response.setStatus(401);
            response.getHeaders().put(HttpHeader.WWW_AUTHENTICATE, "Digest realm=\"" + REALM + "\", nonce=\"" + nonce + "\", qop=\"auth\"");
            Content.Sink.write(response, true, "", callback);
            return true;
        }

        private boolean validateDigestAuth(final String authHeader, final String method) {
            try {
                final Map<String, String> params = parseDigestParams(authHeader.substring(7));
                final String username = params.get("username");
                final String storedPassword = userCredentials.get(username);
                if (storedPassword == null) {
                    return false;
                }

                final String realm = params.get("realm");
                final String digestNonce = params.get("nonce");
                final String uri = params.get("uri");
                final String nc = params.get("nc");
                final String cnonce = params.get("cnonce");
                final String qop = params.get("qop");
                final String clientResponse = params.get("response");

                final String ha1 = md5Hex(username + ":" + realm + ":" + storedPassword);
                final String ha2 = md5Hex(method + ":" + uri);

                final String expectedResponse;
                if ("auth".equals(qop)) {
                    expectedResponse = md5Hex(ha1 + ":" + digestNonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
                } else {
                    expectedResponse = md5Hex(ha1 + ":" + digestNonce + ":" + ha2);
                }

                return expectedResponse.equals(clientResponse);
            } catch (final Exception e) {
                logger.warn("Failed to validate digest auth", e);
                return false;
            }
        }

        private Map<String, String> parseDigestParams(final String params) {
            final Map<String, String> result = new ConcurrentHashMap<>();
            final String[] pairs = params.split(",\\s*");
            for (final String pair : pairs) {
                final int eqIndex = pair.indexOf('=');
                if (eqIndex > 0) {
                    final String key = pair.substring(0, eqIndex).trim();
                    String value = pair.substring(eqIndex + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    result.put(key, value);
                }
            }
            return result;
        }

        private String md5Hex(final String input) throws Exception {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            final byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder();
            for (final byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    // =========================================================================
    //                                                        Form Auth Handler
    // =========================================================================
    private class FormAuthHandler extends Handler.Abstract {
        private static final String LOGIN_FORM_TEMPLATE = """
                <!DOCTYPE html>
                <html lang="en-US">
                    <head>
                        <meta charset="UTF-8" />
                        <meta name="viewport" content="width=device-width" />
                        <title>Login page</title>
                    </head>
                    <body>
                        <form method="post" action="/j_security_check">
                            <input type="text" name="j_username">
                            <input type="password" name="j_password">
                            <input type="hidden" name="authenticity_token" value="%s">
                            <input type="submit" value="Login">
                        </form>
                    </body>
                </html>
                """;

        @Override
        public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
            final String path = request.getHttpURI().getPath();

            // Check session cookie
            final String sessionId = getSessionId(request);
            if (sessionId != null && authenticatedSessions.contains(sessionId)) {
                response.setStatus(200);
                Content.Sink.write(response, true, "Authentication successful", callback);
                return true;
            }

            // Serve login form
            if ("/login".equals(path)) {
                response.setStatus(200);
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html;charset=UTF-8");
                Content.Sink.write(response, true, LOGIN_FORM_TEMPLATE.formatted(TOKEN_VALUE_FOR_FORM_AUTH), callback);
                return true;
            }

            // Process login
            if ("/j_security_check".equals(path) && "POST".equals(request.getMethod())) {
                return handleLoginPost(request, response, callback);
            }

            // Serve error page
            if ("/error".equals(path)) {
                response.setStatus(200);
                Content.Sink.write(response, true, "Authentication failed", callback);
                return true;
            }

            // Redirect to login
            response.setStatus(302);
            response.getHeaders().put(HttpHeader.LOCATION, "/login");
            callback.succeeded();
            return true;
        }

        private boolean handleLoginPost(final Request request, final Response response, final Callback callback) throws Exception {
            final String body = Content.Source.asString(request, StandardCharsets.UTF_8);
            final Map<String, String> params = parseFormParams(body);

            final String username = params.get("j_username");
            final String password = params.get("j_password");
            final String token = params.get("authenticity_token");

            if (StringUtils.equals(token, TOKEN_VALUE_FOR_FORM_AUTH) && validateCredentials(username, password)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Form authentication successful for user '{}'", username);
                }
                final String newSessionId = UUID.randomUUID().toString();
                authenticatedSessions.add(newSessionId);

                response.setStatus(302);
                response.getHeaders().put(HttpHeader.SET_COOKIE, "JSESSIONID=" + newSessionId + "; Path=/");
                response.getHeaders().put(HttpHeader.LOCATION, "/");
                callback.succeeded();
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Form authentication failed for user '{}'", username);
                }
                response.setStatus(302);
                response.getHeaders().put(HttpHeader.LOCATION, "/error");
                callback.succeeded();
            }
            return true;
        }

        private String getSessionId(final Request request) {
            final String cookieHeader = request.getHeaders().get(HttpHeader.COOKIE);
            if (cookieHeader == null) {
                return null;
            }
            for (final String cookie : cookieHeader.split(";")) {
                final String trimmed = cookie.trim();
                if (trimmed.startsWith("JSESSIONID=")) {
                    return trimmed.substring("JSESSIONID=".length());
                }
            }
            return null;
        }

        private Map<String, String> parseFormParams(final String body) {
            final Map<String, String> params = new ConcurrentHashMap<>();
            if (body == null) {
                return params;
            }
            for (final String pair : body.split("&")) {
                final String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    params.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
            return params;
        }
    }
}
