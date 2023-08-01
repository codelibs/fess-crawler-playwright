/*
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
import java.security.Principal;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.security.BasicAuthenticator;
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.DigestAuthenticator;
import org.mortbay.jetty.security.FormAuthenticator;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.SecurityHandler;
import org.mortbay.jetty.servlet.DefaultServlet;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.SessionHandler;

/**
 * A mock authentication server implementation for testing purposes, using Jetty 6.
 * Supports Basic, Digest & Form authentication method
 */
public class CrawlerAuthenticationServer {
    public enum AuthMethod {
        BASIC, DIGEST, FORM
    }

    /** Value of authenticity_token input entry that will be used for verification when FORM authMethod is used */
    private static final String TOKEN_VALUE_FOR_FORM_AUTH = "Abcdef1234!@#$";

    private final Server server = new Server();
    private final HashUserRealm userRealm = new AuthenticityTokenHashUserRealm();
    private final AtomicReference<AuthMethod> currentAuthMethod = new AtomicReference<>();

    private int port = 7070;

    public void setPort(int port) {
        this.port = port;
    }

    public void addUser(String userName, Object password) {
        this.userRealm.put(userName, password);
    }

    public void setAuthMethod(final AuthMethod authMethod) {
        switch (authMethod) {
        case BASIC -> useBasicAuth();
        case DIGEST -> useDigestAuth();
        case FORM -> useFormAuth();
        }
        this.currentAuthMethod.set(authMethod);
    }

    public AuthMethod getCurrentAuthMethod() {
        return this.currentAuthMethod.get();
    }

    public void start() {
        try {
            // add port to server
            final var connector = new SocketConnector();
            connector.setPort(this.port);
            this.server.setConnectors(ArrayUtils.toArray(connector));

            server.start();
        } catch (Exception e) {
            throw new CrawlerSystemException(e);
        }
    }

    public void stop() {
        try {
            this.server.stop();
        } catch (Exception e) {
            throw new CrawlerSystemException(e);
        }
    }

    private void useBasicAuth() {
        final var servletSuccess = new AuthSuccessServlet();

        final var contextHandler = new ContextHandler("/");
        final var sessionHandler = new SessionHandler();
        final var securityHandler = new SecurityHandler();
        final var servletHandler = new ServletHandler();

        servletHandler.addServletWithMapping(new ServletHolder(servletSuccess), "/*");
        securityHandler.setHandler(servletHandler);
        sessionHandler.setHandler(securityHandler);
        contextHandler.setHandler(sessionHandler);
        securityHandler.setUserRealm(this.userRealm);

        final var constraint = new Constraint(Constraint.__BASIC_AUTH, Constraint.ANY_ROLE);
        constraint.setAuthenticate(true);
        final var constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");
        securityHandler.setConstraintMappings(ArrayUtils.toArray(constraintMapping));

        securityHandler.setAuthenticator(new BasicAuthenticator());
        this.server.setHandler(contextHandler);
    }

    private void useDigestAuth() {
        final var servletSuccess = new AuthSuccessServlet();

        final var contextHandler = new ContextHandler("/");
        final var sessionHandler = new SessionHandler();
        final var securityHandler = new SecurityHandler();
        final var servletHandler = new ServletHandler();

        servletHandler.addServletWithMapping(new ServletHolder(servletSuccess), "/*");
        securityHandler.setHandler(servletHandler);
        sessionHandler.setHandler(securityHandler);
        contextHandler.setHandler(sessionHandler);
        securityHandler.setUserRealm(this.userRealm);

        final var constraint = new Constraint(Constraint.__DIGEST_AUTH, Constraint.ANY_ROLE);
        constraint.setAuthenticate(true);
        final var constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");
        securityHandler.setConstraintMappings(ArrayUtils.toArray(constraintMapping));

        securityHandler.setAuthenticator(new DigestAuthenticator());
        this.server.setHandler(contextHandler);
    }

    private void useFormAuth() {
        final var servletSuccess = new AuthSuccessServlet();
        final var servletPrompt = new AuthFormServlet();
        final var servletAuthError = new AuthFailedServlet();

        final var contextHandler = new ContextHandler("/");
        final var sessionHandler = new SessionHandler();
        final var securityHandler = new SecurityHandler();
        final var servletHandler = new ServletHandler();

        servletHandler.addServletWithMapping(new ServletHolder(servletSuccess), "/*");
        servletHandler.addServletWithMapping(new ServletHolder(servletPrompt), "/login");
        servletHandler.addServletWithMapping(new ServletHolder(servletAuthError), "/error");

        securityHandler.setHandler(servletHandler);
        sessionHandler.setHandler(securityHandler);
        contextHandler.setHandler(sessionHandler);

        securityHandler.setUserRealm(this.userRealm);

        final var constraint = new Constraint(Constraint.__FORM_AUTH, Constraint.ANY_ROLE);
        constraint.setAuthenticate(true);
        final var constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");
        securityHandler.setConstraintMappings(ArrayUtils.toArray(constraintMapping));

        final var authenticator = new FormAuthenticator();
        authenticator.setLoginPage("/login");
        authenticator.setErrorPage("/error");
        securityHandler.setAuthenticator(authenticator);
        this.server.setHandler(contextHandler);
    }

    private static class AuthSuccessServlet extends DefaultServlet {
        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
            response.getWriter().append("Authentication successful");
        }
    }

    private static class AuthFormServlet extends DefaultServlet {
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
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
            response.getWriter().append(LOGIN_FORM_TEMPLATE.formatted(TOKEN_VALUE_FOR_FORM_AUTH));
        }
    }

    private static class AuthFailedServlet extends DefaultServlet {
        @Override
        protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
            response.getWriter().append("Authentication failed");
        }
    }

    private class AuthenticityTokenHashUserRealm extends HashUserRealm {
        @Override
        public Principal authenticate(final String username, final Object credentials, final Request request) {
            // authenticate normally for all methods other than form
            final AuthMethod authMethod = CrawlerAuthenticationServer.this.getCurrentAuthMethod();
            if (authMethod != AuthMethod.FORM) {
                return super.authenticate(username, credentials, request);
            }

            // validate authenticity_token as well for form authentication
            // Used for testing crawler's token retrieval function
            final String requestToken = request.getParameter("authenticity_token");
            if (StringUtils.equals(requestToken, TOKEN_VALUE_FOR_FORM_AUTH)) {
                return super.authenticate(username, credentials, request);
            } else {
                return null;
            }
        }
    }
}
