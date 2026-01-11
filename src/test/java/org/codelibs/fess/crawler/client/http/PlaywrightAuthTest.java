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
package org.codelibs.fess.crawler.client.http;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.DigestScheme;
import org.codelibs.core.exception.UnsupportedEncodingRuntimeException;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.fess.crawler.builder.RequestDataBuilder;
import org.codelibs.fess.crawler.client.http.form.Hc5FormScheme;
import org.codelibs.fess.crawler.client.http.Hc5Authentication;
import org.codelibs.fess.crawler.entity.RequestData;
import org.codelibs.fess.crawler.entity.ResponseData;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.crawler.util.CrawlerAuthenticationServer;
import org.codelibs.fess.crawler.util.CrawlerAuthenticationServer.AuthMethod;
import org.dbflute.utflute.core.PlainTestCase;

/**
 * Automated test case for the Playwright crawler's authentication mechanisms (Basic, Digest & Form)
 */
public class PlaywrightAuthTest extends PlainTestCase {
    private CrawlerAuthenticationServer authServer;

    private PlaywrightClientWithAuthSettings playwrightClient;

    @BeforeEach
    protected void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);

        this.authServer = new CrawlerAuthenticationServer();
        this.playwrightClient = new PlaywrightClientWithAuthSettings();
    }

    @AfterEach
    protected void tearDown() throws Exception {
        this.playwrightClient.close();
        this.authServer.stop();

        super.tearDown();
    }

    public void test_crawler_basicAuth() {
        // prepare server
        this.authServer.addUser("user", "password");
        this.authServer.setAuthMethod(AuthMethod.BASIC);
        this.authServer.start();

        final var basicAuthConfig =
                new Hc5Authentication(new AuthScope(null, -1), new UsernamePasswordCredentials("user", "password".toCharArray()));
        basicAuthConfig.setAuthScheme(new BasicScheme());
        this.playwrightClient.addAuthentication(basicAuthConfig);

        final String url = "http://[::1]:7070/";
        final ResponseData response = this.playwrightClient.execute(makeRequestData(url));

        assertAuthSuccessful(response);
    }

    public void test_crawler_digestAuth() {
        // prepare server
        this.authServer.addUser("user", "password");
        this.authServer.setAuthMethod(AuthMethod.DIGEST);
        this.authServer.start();

        final var digestAuthConfig =
                new Hc5Authentication(new AuthScope(null, -1), new UsernamePasswordCredentials("user", "password".toCharArray()));
        digestAuthConfig.setAuthScheme(new DigestScheme());
        this.playwrightClient.addAuthentication(digestAuthConfig);

        final String url = "http://[::1]:7070/";
        final ResponseData response = this.playwrightClient.execute(makeRequestData(url));

        assertAuthSuccessful(response);
    }

    public void test_crawler_formAuth() {
        // prepare server
        this.authServer.addUser("user", "password");
        this.authServer.setAuthMethod(AuthMethod.FORM);
        this.authServer.start();

        final var formAuthConfig =
                new Hc5Authentication(new AuthScope(null, -1), new UsernamePasswordCredentials("user", "password".toCharArray()));
        final Map<String, String> formSchemeConfiguration = Map.of("encoding", "utf-8", "token_method", "GET", "token_url",
                "http://[::1]:7070/login", "token_pattern", "name=\"authenticity_token\" +value=\"([^\"]+)\"", "token_name",
                "authenticity_token", "login_method", "POST", "login_url", "http://[::1]:7070/j_security_check", "login_parameters",
                "j_username=${username}&j_password=${password}");
        final var formScheme = new Hc5FormScheme(formSchemeConfiguration);
        formAuthConfig.setAuthScheme(formScheme);
        this.playwrightClient.addAuthentication(formAuthConfig);

        final String url = "http://[::1]:7070/";
        final ResponseData response = this.playwrightClient.execute(makeRequestData(url));

        assertAuthSuccessful(response);
    }

    private void assertAuthSuccessful(final ResponseData responseData) {
        assertEquals(200, responseData.getHttpStatusCode());
        assertEquals("GET", responseData.getMethod());
        assertEquals("UTF-8", responseData.getCharSet());
        assertEquals("text/html", responseData.getMimeType());

        final String response = getBodyAsString(responseData);
        assertContainsAny(response, "Authentication successful");
    }

    private String getBodyAsString(final ResponseData responseData) {
        try {
            return new String(InputStreamUtil.getBytes(responseData.getResponseBody()), responseData.getCharSet());
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedEncodingRuntimeException(e);
        }
    }

    private static RequestData makeRequestData(final String url) {
        return RequestDataBuilder.newRequestData().get().url(url).build();
    }

    private static class PlaywrightClientWithAuthSettings extends PlaywrightClient {
        private final List<Hc5Authentication> authConfigs = new ArrayList<>();

        PlaywrightClientWithAuthSettings() {
            initParamMap = new HashMap<>();
        }

        @Override
        protected Optional<MimeTypeHelper> getMimeTypeHelper() {
            return Optional.of(new MimeTypeHelperImpl());
        }

        private void addAuthentication(final Hc5Authentication authentication) {
            this.authConfigs.add(authentication);
            initParamMap.put(HcHttpClient.AUTHENTICATIONS_PROPERTY, authConfigs.toArray(Hc5Authentication[]::new));
        }
    }
}
