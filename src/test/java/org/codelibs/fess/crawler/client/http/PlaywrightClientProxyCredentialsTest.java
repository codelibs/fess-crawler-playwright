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

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.codelibs.fess.crawler.client.http.config.CredentialsConfig;
import org.codelibs.fess.crawler.client.http.config.WebAuthenticationConfig;
import org.dbflute.utflute.core.PlainTestCase;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link PlaywrightClient} reads the credentials configured for the proxy.
 *
 * <p>The shape that matters here is the one a crawl config produces: a
 * {@link WebAuthenticationConfig}. Reading it as an HC5 {@code UsernamePasswordCredentials} threw
 * {@link ClassCastException} while the browser context was being created, which failed every URL of
 * the crawl rather than the proxy authentication.</p>
 */
public class PlaywrightClientProxyCredentialsTest extends PlainTestCase {

    private PlaywrightClient createClient(final Object configuredCredentials) {
        final PlaywrightClient client = new PlaywrightClient();
        final Map<String, Object> initParamMap = new HashMap<>();
        if (configuredCredentials != null) {
            initParamMap.put(HcHttpClient.PROXY_CREDENTIALS_PROPERTY, configuredCredentials);
        }
        client.setInitParameterMap(initParamMap);
        return client;
    }

    @Test
    public void test_resolveProxyCredentials_config() {
        final CredentialsConfig credentials = new CredentialsConfig();
        credentials.setUsername("proxyuser");
        credentials.setPassword("proxypass");
        final WebAuthenticationConfig config = new WebAuthenticationConfig();
        config.setCredentials(credentials);

        final CredentialsConfig resolved = createClient(config).resolveProxyCredentials();

        assertNotNull(resolved);
        assertEquals("proxyuser", resolved.getUsername());
        assertEquals("proxypass", resolved.getPassword());
    }

    /**
     * A proxy configured without credentials carries none.
     */
    @Test
    public void test_resolveProxyCredentials_configWithoutCredentials() {
        assertNull(createClient(new WebAuthenticationConfig()).resolveProxyCredentials());
    }

    @Test
    public void test_resolveProxyCredentials_noParameter() {
        assertNull(createClient(null).resolveProxyCredentials());

        final PlaywrightClient clientWithoutMap = new PlaywrightClient();
        clientWithoutMap.setInitParameterMap(null);
        assertNull(clientWithoutMap.resolveProxyCredentials());
    }

    /**
     * A shape this client does not understand is reported and skipped, never cast. Casting is what
     * turned a mismatch into a crawl-wide failure in the first place.
     */
    @Test
    public void test_resolveProxyCredentials_unsupportedShape() {
        assertNull(createClient(new UsernamePasswordCredentials("proxyuser", "proxypass".toCharArray())).resolveProxyCredentials());
        assertNull(createClient("not a credential").resolveProxyCredentials());
    }
}
