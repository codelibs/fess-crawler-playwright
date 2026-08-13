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

import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.codelibs.fess.crawler.client.http.config.CredentialsConfig;
import org.codelibs.fess.crawler.client.http.config.WebAuthenticationConfig;
import org.codelibs.fess.crawler.client.http.config.WebAuthenticationConfig.AuthSchemeType;
import org.dbflute.utflute.core.PlainTestCase;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link PlaywrightClient} reads the authentications configured for a crawl.
 *
 * <p>The shape that matters here is the one a crawl config produces: {@code WebAuthenticationConfig[]},
 * and a zero-length array of that type when no authentication is configured at all. Reading that as
 * {@code Hc5Authentication[]} threw {@link ClassCastException} on the array type itself, which took
 * down every Playwright crawl regardless of whether authentication was in use. These tests pin that
 * shape, which the browser-driven tests never exercised because they build the init parameter map
 * themselves.</p>
 */
public class PlaywrightClientAuthResolutionTest extends PlainTestCase {

    private PlaywrightClient createClient(final Object configuredAuthentications) {
        final PlaywrightClient client = new PlaywrightClient();
        final Map<String, Object> initParamMap = new HashMap<>();
        if (configuredAuthentications != null) {
            initParamMap.put(HcHttpClient.AUTHENTICATIONS_PROPERTY, configuredAuthentications);
        }
        client.setInitParameterMap(initParamMap);
        return client;
    }

    private WebAuthenticationConfig createConfig(final AuthSchemeType authSchemeType, final String username, final String password) {
        final WebAuthenticationConfig config = new WebAuthenticationConfig();
        config.setAuthSchemeType(authSchemeType);
        final CredentialsConfig credentials = new CredentialsConfig();
        credentials.setUsername(username);
        credentials.setPassword(password);
        config.setCredentials(credentials);
        return config;
    }

    /**
     * A crawl config with no authentication still puts a zero-length WebAuthenticationConfig[] into the
     * parameter map. This is the case that broke every crawl, so it is the one to keep pinned.
     */
    @Test
    public void test_resolveAuthentications_emptyConfigArray() {
        final WebAuthenticationConfig[] authentications = createClient(new WebAuthenticationConfig[0]).resolveAuthentications();

        assertNotNull(authentications);
        assertEquals(0, authentications.length);
    }

    /**
     * The configured shape is used as it is - no conversion, so nothing about it can be lost.
     */
    @Test
    public void test_resolveAuthentications_configArray() {
        final WebAuthenticationConfig config = createConfig(AuthSchemeType.BASIC, "user", "password");
        final WebAuthenticationConfig[] configured = { config };

        final WebAuthenticationConfig[] authentications = createClient(configured).resolveAuthentications();

        assertEquals(1, authentications.length);
        assertTrue(authentications[0] == config);
        assertEquals(AuthSchemeType.BASIC, authentications[0].getAuthSchemeType());
        assertEquals("user", authentications[0].getCredentials().getUsername());
        assertEquals("password", authentications[0].getCredentials().getPassword());
    }

    /**
     * An NTLM authentication reaches the browser's credential prompt with the user name as configured,
     * rather than the domain-qualified form an HC5 {@code NTCredentials} would report.
     */
    @Test
    public void test_resolveAuthentications_ntlmConfigKeepsPlainUsername() {
        final WebAuthenticationConfig config = createConfig(AuthSchemeType.NTLM, "user", "password");
        config.getCredentials().setDomain("MYDOMAIN");

        final WebAuthenticationConfig[] authentications = createClient(new WebAuthenticationConfig[] { config }).resolveAuthentications();

        assertEquals(1, authentications.length);
        assertEquals("user", authentications[0].getCredentials().getUsername());
        assertEquals("MYDOMAIN", authentications[0].getCredentials().getDomain());
    }

    /**
     * Nothing configured at all - neither the parameter nor the map itself.
     */
    @Test
    public void test_resolveAuthentications_noParameter() {
        assertEquals(0, createClient(null).resolveAuthentications().length);

        final PlaywrightClient clientWithoutMap = new PlaywrightClient();
        clientWithoutMap.setInitParameterMap(null);
        assertEquals(0, clientWithoutMap.resolveAuthentications().length);
    }

    /**
     * A shape this client does not understand is reported and skipped, never cast. Casting is what
     * turned a mismatch into a crawl-wide failure in the first place. The HC5-specific
     * {@code Hc5Authentication[]} is one of those shapes: no crawl config produces it, and the
     * authentication it describes is still carried out by {@link Hc5HttpClient}, which reads the same
     * parameter itself.
     */
    @Test
    public void test_resolveAuthentications_unsupportedShape() {
        final Hc5Authentication hc5Authentication =
                new Hc5Authentication(new AuthScope(null, -1), new UsernamePasswordCredentials("user", "password".toCharArray()));

        assertEquals(0, createClient(new Hc5Authentication[] { hc5Authentication }).resolveAuthentications().length);
        assertEquals(0, createClient("not an authentication array").resolveAuthentications().length);
        assertEquals(0, createClient(new String[] { "still not one" }).resolveAuthentications().length);
    }
}
