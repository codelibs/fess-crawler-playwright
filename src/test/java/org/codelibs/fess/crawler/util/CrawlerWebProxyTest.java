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

import org.junit.jupiter.api.Test;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.codelibs.fess.crawler.util.CrawlerWebProxy.ProxyAccessStatus;
import org.dbflute.utflute.core.PlainTestCase;

/**
 * Test class for CrawlerWebProxy with HC5 credentials support.
 *
 * @author shinsuke
 */
public class CrawlerWebProxyTest extends PlainTestCase {

    /**
     * Test setting credentials with HC5 UsernamePasswordCredentials.
     */
    @Test
    public void test_setCredentials_hc5UsernamePasswordCredentials() {
        final CrawlerWebProxy proxy = new CrawlerWebProxy();

        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("testuser", "testpass123!@#".toCharArray());

        // Should not throw exception
        proxy.setCredentials(credentials);

        // Verify initial access status
        assertEquals(ProxyAccessStatus.NOT_ACCESSED, proxy.getAccessResult());
    }

    /**
     * Test setting credentials with null UsernamePasswordCredentials.
     */
    @Test
    public void test_setCredentials_nullCredentials() {
        final CrawlerWebProxy proxy = new CrawlerWebProxy();

        // Should handle null gracefully
        proxy.setCredentials((UsernamePasswordCredentials) null);

        assertEquals(ProxyAccessStatus.NOT_ACCESSED, proxy.getAccessResult());
    }

    /**
     * Test setting credentials with username and password strings.
     */
    @Test
    public void test_setCredentials_stringCredentials() {
        final CrawlerWebProxy proxy = new CrawlerWebProxy();

        proxy.setCredentials("username", "password");

        assertEquals(ProxyAccessStatus.NOT_ACCESSED, proxy.getAccessResult());
    }

    /**
     * Test setting credentials with special characters in password.
     */
    @Test
    public void test_setCredentials_specialCharactersInPassword() {
        final CrawlerWebProxy proxy = new CrawlerWebProxy();

        final String username = "admin";
        final String password = "P@ssw0rd!#$%^&*()";

        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password.toCharArray());

        // Should handle special characters correctly
        proxy.setCredentials(credentials);

        assertEquals(ProxyAccessStatus.NOT_ACCESSED, proxy.getAccessResult());
    }

    /**
     * Test setting port.
     */
    @Test
    public void test_setPort() {
        final CrawlerWebProxy proxy = new CrawlerWebProxy();

        proxy.setPort(8888);

        // Verify no exception is thrown
        assertNotNull(proxy);
    }

    /**
     * Test getAccessResult initial state.
     */
    @Test
    public void test_getAccessResult_initialState() {
        final CrawlerWebProxy proxy = new CrawlerWebProxy();

        assertEquals(ProxyAccessStatus.NOT_ACCESSED, proxy.getAccessResult());
    }

    /**
     * Test ProxyAccessStatus enum values.
     */
    @Test
    public void test_proxyAccessStatus_enumValues() {
        assertEquals(4, ProxyAccessStatus.values().length);

        assertNotNull(ProxyAccessStatus.NOT_ACCESSED);
        assertNotNull(ProxyAccessStatus.PROMPTED_FOR_CREDENTIALS);
        assertNotNull(ProxyAccessStatus.ACCESS_GRANTED);
        assertNotNull(ProxyAccessStatus.ACCESS_DENIED);
    }

    /**
     * Test UsernamePasswordCredentials password conversion from char[] to String.
     */
    @Test
    public void test_usernamePasswordCredentials_passwordConversion() {
        final String originalPassword = "mySecretPassword";
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("user", originalPassword.toCharArray());

        // Convert char[] back to String
        final String convertedPassword = new String(credentials.getPassword());

        assertEquals(originalPassword, convertedPassword);
    }

    /**
     * Test UsernamePasswordCredentials with empty password.
     */
    @Test
    public void test_usernamePasswordCredentials_emptyPassword() {
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("user", "".toCharArray());

        assertEquals("user", credentials.getUserName());
        assertEquals("", new String(credentials.getPassword()));
    }

    /**
     * Test UsernamePasswordCredentials with unicode characters.
     */
    @Test
    public void test_usernamePasswordCredentials_unicodeCharacters() {
        final String username = "用户";
        final String password = "密码123";

        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password.toCharArray());

        assertEquals(username, credentials.getUserName());
        assertEquals(password, new String(credentials.getPassword()));
    }
}
