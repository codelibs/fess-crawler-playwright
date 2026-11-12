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

import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.entity.RequestData;
import org.dbflute.utflute.core.PlainTestCase;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests that a URL with a user name and password is refused before a browser is started.
 */
public class PlaywrightClientUserInfoTest extends PlainTestCase {

    @Test
    public void test_rejectUserInfo_refusesUserAndPassword() {
        final CrawlingAccessException e = assertThrows(CrawlingAccessException.class,
                () -> PlaywrightClient.rejectUserInfo("http://user:s3cret@example.com/path?q=1"));
        assertTrue(e.getMessage().contains("http://example.com/path?q=1"));
        assertTrue(e.getMessage().contains("web authentication"));
        assertFalse(e.getMessage().contains("s3cret"));
        assertFalse(e.getMessage().contains("user:"));
    }

    @Test
    public void test_rejectUserInfo_refusesUserOnly() {
        final CrawlingAccessException e =
                assertThrows(CrawlingAccessException.class, () -> PlaywrightClient.rejectUserInfo("https://user@example.com/"));
        assertFalse(e.getMessage().contains("user@"));
    }

    @Test
    public void test_rejectUserInfo_acceptsOtherUrls() {
        PlaywrightClient.rejectUserInfo("http://example.com/");
        PlaywrightClient.rejectUserInfo("http://example.com:8080/a@b");
        PlaywrightClient.rejectUserInfo("http://example.com/path?mail=a@b.example");
        PlaywrightClient.rejectUserInfo(null);
    }

    @Test
    public void test_execute_refusesBeforeStartingABrowser() {
        // The client is never init()ed: a browser would have to be started to get any further.
        final PlaywrightClient client = new PlaywrightClient();
        final RequestData request = new RequestData();
        request.setUrl("http://user:s3cret@example.com/");
        final CrawlingAccessException e = assertThrows(CrawlingAccessException.class, () -> client.execute(request));
        assertFalse(e.getMessage().contains("s3cret"));
    }
}
