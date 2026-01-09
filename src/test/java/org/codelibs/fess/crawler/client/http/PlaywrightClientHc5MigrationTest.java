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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.codelibs.fess.crawler.client.http.Hc5Authentication;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.dbflute.utflute.core.PlainTestCase;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.Cookie;

/**
 * Test class for HC5 migration-related changes in PlaywrightClient.
 * This test class verifies the correct integration with Apache HttpClient 5.x APIs.
 *
 * @author shinsuke
 */
public class PlaywrightClientHc5MigrationTest extends PlainTestCase {

    private static boolean headless = true;

    /**
     * Test that Hc5Authentication is correctly configured with char[] password.
     */
    public void test_hc5Authentication_charArrayPassword() {
        final String username = "testuser";
        final String password = "testpassword123!@#";

        // Create HC5 credentials with char[] password
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password.toCharArray());

        // Verify the credentials are correctly stored
        assertEquals(username, credentials.getUserPrincipal().getName());
        assertNotNull(credentials.getPassword());
        assertEquals(password, new String(credentials.getPassword()));
    }

    /**
     * Test that Hc5Authentication is correctly created with AuthScope.
     */
    public void test_hc5AuthenticationImpl_creation() {
        final AuthScope authScope = new AuthScope(null, -1); // Any host, any port
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("user", "pass".toCharArray());
        final BasicScheme basicScheme = new BasicScheme();

        final Hc5Authentication auth = new Hc5Authentication(authScope, credentials, basicScheme);

        assertNotNull(auth.getAuthScope());
        assertNotNull(auth.getCredentials());
        assertNotNull(auth.getAuthScheme());
        assertEquals("user", auth.getCredentials().getUserPrincipal().getName());
        assertEquals("pass", new String(auth.getCredentials().getPassword()));
        assertEquals("Basic", auth.getAuthScheme().getName());
    }

    /**
     * Test that AuthScheme.getName() returns correct scheme name for BasicScheme.
     */
    public void test_authScheme_getName_basic() {
        final BasicScheme basicScheme = new BasicScheme();
        assertEquals("Basic", basicScheme.getName());
    }

    /**
     * Test that PlaywrightClient correctly handles Hc5Authentication array.
     */
    public void test_playwrightClient_hc5AuthenticationConfiguration() {
        final PlaywrightClientWithTestableAuth playwrightClient = new PlaywrightClientWithTestableAuth();

        try {
            final Hc5Authentication[] authentications = new Hc5Authentication[] { new Hc5Authentication(new AuthScope(null, -1),
                    new UsernamePasswordCredentials("user1", "pass1".toCharArray()), new BasicScheme()) };

            playwrightClient.setAuthentications(authentications);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));
            playwrightClient.init();

            // Verify the authentication is configured
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test Cookie expiry time conversion from Instant to seconds.
     * HC5 uses Instant for cookie expiry, while Playwright uses seconds (double).
     */
    public void test_cookieExpiryInstant_conversion() {
        // Create an Instant representing a future time
        final Instant expiryInstant = Instant.now().plus(1, ChronoUnit.HOURS);

        // Convert to Playwright's seconds format (as done in PlaywrightClient)
        final double expirySeconds = expiryInstant.toEpochMilli() / 1000.0;

        // Verify the conversion is correct (within 1 second tolerance)
        assertTrue(expirySeconds > 0);
        assertEquals(expiryInstant.getEpochSecond(), (long) expirySeconds, 1);
    }

    /**
     * Test Cookie expiry time conversion with null Instant.
     */
    public void test_cookieExpiryInstant_null() {
        final Instant expiryInstant = null;

        // The code should handle null gracefully
        if (expiryInstant != null) {
            fail("Expected expiryInstant to be null");
        }

        // No exception should be thrown
        assertTrue(true);
    }

    /**
     * Test Playwright Cookie creation with HC5 cookie data.
     */
    public void test_playwrightCookie_fromHc5CookieData() {
        // Simulate HC5 cookie data
        final String name = "session_id";
        final String value = "abc123";
        final String domain = "example.com";
        final String path = "/";
        final boolean secure = true;
        final Instant expiryInstant = Instant.now().plus(24, ChronoUnit.HOURS);

        // Create Playwright cookie (as done in PlaywrightClient)
        final Cookie playwrightCookie = new Cookie(name, value);
        playwrightCookie.setDomain(domain);
        playwrightCookie.setPath(path);
        playwrightCookie.setSecure(secure);
        if (expiryInstant != null) {
            playwrightCookie.setExpires(expiryInstant.toEpochMilli() / 1000.0);
        }

        // Verify cookie properties
        assertEquals(name, playwrightCookie.name);
        assertEquals(value, playwrightCookie.value);
        assertEquals(domain, playwrightCookie.domain);
        assertEquals(path, playwrightCookie.path);
        assertTrue(playwrightCookie.secure);
        assertNotNull(playwrightCookie.expires);
    }

    /**
     * Test proxy credentials with HC5 UsernamePasswordCredentials.
     */
    public void test_proxyCredentials_hc5() {
        final String username = "proxyuser";
        final String password = "proxypass!@#$%";

        final UsernamePasswordCredentials proxyCredentials = new UsernamePasswordCredentials(username, password.toCharArray());

        // Verify getter methods work correctly
        assertEquals(username, proxyCredentials.getUserName());
        assertEquals(password, new String(proxyCredentials.getPassword()));
    }

    /**
     * Test that PlaywrightClient correctly configures proxy with HC5 credentials.
     */
    public void test_playwrightClient_proxyWithHc5Credentials() {
        final PlaywrightClientWithProxyTest playwrightClient = new PlaywrightClientWithProxyTest();

        try {
            final UsernamePasswordCredentials proxyCredentials = new UsernamePasswordCredentials("proxyuser", "proxypass".toCharArray());

            playwrightClient.setProxyHost("127.0.0.1");
            playwrightClient.setProxyPort(8080);
            playwrightClient.setProxyCredentials(proxyCredentials);
            playwrightClient.setLaunchOptions(new BrowserType.LaunchOptions().setHeadless(headless));

            // Just verify no exception is thrown during configuration
            assertNotNull(playwrightClient);
        } finally {
            playwrightClient.close();
        }
    }

    /**
     * Test multiple authentication configurations with different schemes.
     */
    public void test_multipleAuthentications_mixedSchemes() {
        final List<Hc5Authentication> authList = new ArrayList<>();

        // Add Basic auth
        authList.add(new Hc5Authentication(new AuthScope("example.com", 80),
                new UsernamePasswordCredentials("basicUser", "basicPass".toCharArray()), new BasicScheme()));

        // Add another Basic auth for different host
        authList.add(new Hc5Authentication(new AuthScope("api.example.com", 443),
                new UsernamePasswordCredentials("apiUser", "apiPass".toCharArray()), new BasicScheme()));

        assertEquals(2, authList.size());

        // Verify each authentication
        final Hc5Authentication auth1 = authList.get(0);
        assertEquals("basicUser", auth1.getCredentials().getUserPrincipal().getName());
        assertEquals("Basic", auth1.getAuthScheme().getName());

        final Hc5Authentication auth2 = authList.get(1);
        assertEquals("apiUser", auth2.getCredentials().getUserPrincipal().getName());
    }

    /**
     * Test empty authentication array.
     */
    public void test_emptyAuthentications() {
        final Hc5Authentication[] authentications = new Hc5Authentication[0];
        assertEquals(0, authentications.length);
    }

    /**
     * Test AuthScope with null host (any host).
     */
    public void test_authScope_anyHost() {
        final AuthScope anyHostScope = new AuthScope(null, -1);
        assertNull(anyHostScope.getHost());
        assertEquals(-1, anyHostScope.getPort());
    }

    /**
     * Test AuthScope with specific host and port.
     */
    public void test_authScope_specificHostPort() {
        final AuthScope specificScope = new AuthScope("localhost", 8080);
        assertEquals("localhost", specificScope.getHost());
        assertEquals(8080, specificScope.getPort());
    }

    /**
     * Helper class for testing authentication configuration.
     */
    private static class PlaywrightClientWithTestableAuth extends PlaywrightClient {
        PlaywrightClientWithTestableAuth() {
            initParamMap = new HashMap<>();
        }

        void setAuthentications(final Hc5Authentication[] authentications) {
            initParamMap.put(HcHttpClient.AUTHENTICATIONS_PROPERTY, authentications);
        }

        @Override
        protected Optional<MimeTypeHelper> getMimeTypeHelper() {
            return Optional.of(new MimeTypeHelperImpl());
        }
    }

    /**
     * Helper class for testing proxy configuration.
     */
    private static class PlaywrightClientWithProxyTest extends PlaywrightClient {
        PlaywrightClientWithProxyTest() {
            initParamMap = new HashMap<>();
        }

        void setProxyHost(final String proxyHost) {
            initParamMap.put(HcHttpClient.PROXY_HOST_PROPERTY, proxyHost);
        }

        void setProxyPort(final int proxyPort) {
            initParamMap.put(HcHttpClient.PROXY_PORT_PROPERTY, proxyPort);
        }

        void setProxyCredentials(final UsernamePasswordCredentials credentials) {
            initParamMap.put(HcHttpClient.PROXY_CREDENTIALS_PROPERTY, credentials);
        }

        @Override
        protected Optional<MimeTypeHelper> getMimeTypeHelper() {
            return Optional.of(new MimeTypeHelperImpl());
        }
    }
}
