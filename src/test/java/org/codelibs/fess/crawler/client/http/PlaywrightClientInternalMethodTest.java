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

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import org.dbflute.utflute.core.PlainTestCase;

/**
 * Test class for PlaywrightClient internal helper methods.
 * Tests for getFilename, parseDate, getContentType, getCharSet, and other utility methods.
 */
public class PlaywrightClientInternalMethodTest extends PlainTestCase {

    private PlaywrightClient playwrightClient;

    @Override
    protected void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        playwrightClient = new PlaywrightClient();
    }

    @Override
    protected void tearDown(final TestInfo testInfo) throws Exception {
        playwrightClient = null;
        super.tearDown(testInfo);
    }

    // ==================== getFilename tests ====================

    /**
     * Test getFilename with basic filenames.
     */
    @Test
    public void test_getFilename_basicFilename() {
        assertEquals("test.html", playwrightClient.getFilename("test.html"));
        assertEquals("document.pdf", playwrightClient.getFilename("document.pdf"));
        assertEquals("image.png", playwrightClient.getFilename("image.png"));
    }

    /**
     * Test getFilename with URLs containing paths.
     */
    @Test
    public void test_getFilename_withPaths() {
        assertEquals("test.html", playwrightClient.getFilename("http://example.com/test.html"));
        assertEquals("test.html", playwrightClient.getFilename("http://example.com/path/to/test.html"));
        assertEquals("file.pdf", playwrightClient.getFilename("https://example.com/a/b/c/d/file.pdf"));
    }

    /**
     * Test getFilename with URLs containing query strings.
     */
    @Test
    public void test_getFilename_withQueryStrings() {
        assertEquals("test.html", playwrightClient.getFilename("http://example.com/test.html?param=value"));
        assertEquals("test.html", playwrightClient.getFilename("http://example.com/test.html?a=1&b=2&c=3"));
        assertEquals("doc.pdf", playwrightClient.getFilename("http://example.com/doc.pdf?version=1.0&format=pdf"));
    }

    /**
     * Test getFilename with URLs containing fragments.
     */
    @Test
    public void test_getFilename_withFragments() {
        assertEquals("test.html", playwrightClient.getFilename("http://example.com/test.html#section1"));
        assertEquals("test.html", playwrightClient.getFilename("http://example.com/test.html?a=1#anchor"));
        assertEquals("page.html", playwrightClient.getFilename("http://example.com/page.html#top"));
    }

    /**
     * Test getFilename with root path (should return index.html).
     */
    @Test
    public void test_getFilename_rootPath() {
        assertEquals("index.html", playwrightClient.getFilename("http://example.com/"));
        assertEquals("index.html", playwrightClient.getFilename("http://example.com/?param=value"));
        assertEquals("index.html", playwrightClient.getFilename("http://example.com/#section"));
        assertEquals("index.html", playwrightClient.getFilename("http://example.com/path/"));
    }

    /**
     * Test getFilename with null and empty values.
     */
    @Test
    public void test_getFilename_nullAndEmpty() {
        assertNull(playwrightClient.getFilename(null));
        assertNull(playwrightClient.getFilename(""));
        assertNull(playwrightClient.getFilename("   "));
    }

    /**
     * Test getFilename with special characters.
     */
    @Test
    public void test_getFilename_specialCharacters() {
        assertEquals("file-name.html", playwrightClient.getFilename("http://example.com/file-name.html"));
        assertEquals("file_name.html", playwrightClient.getFilename("http://example.com/file_name.html"));
        assertEquals("file.name.test.html", playwrightClient.getFilename("http://example.com/file.name.test.html"));
    }

    /**
     * Test getFilename with Unicode characters.
     */
    @Test
    public void test_getFilename_unicodeCharacters() {
        assertEquals("ファイル.html", playwrightClient.getFilename("http://example.com/ファイル.html"));
        assertEquals("文档.pdf", playwrightClient.getFilename("http://example.com/文档.pdf"));
        assertEquals("документ.txt", playwrightClient.getFilename("http://example.com/документ.txt"));
    }

    /**
     * Test getFilename with complex URLs.
     */
    @Test
    public void test_getFilename_complexUrls() {
        assertEquals("file.html", playwrightClient.getFilename("http://user:pass@example.com:8080/path/file.html?q=1#s"));
        assertEquals("index.html", playwrightClient.getFilename("https://example.com:443/"));
    }

    // ==================== parseDate tests ====================

    /**
     * Test parseDate with valid RFC 1123 date format.
     */
    @Test
    public void test_parseDate_validRfc1123() {
        final Date date = playwrightClient.parseDate("Sun, 22 Jan 2023 02:16:34 GMT");
        assertNotNull(date);
        assertEquals(1674353794000L, date.getTime());
    }

    /**
     * Test parseDate with various valid dates.
     */
    @Test
    public void test_parseDate_variousValidDates() {
        assertNotNull(playwrightClient.parseDate("Mon, 01 Jan 2024 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Tue, 15 Feb 2022 12:30:45 GMT"));
        assertNotNull(playwrightClient.parseDate("Wed, 31 Dec 2025 23:59:59 GMT"));
    }

    /**
     * Test parseDate with null and empty values.
     */
    @Test
    public void test_parseDate_nullAndEmpty() {
        assertNull(playwrightClient.parseDate(null));
        assertNull(playwrightClient.parseDate(""));
        assertNull(playwrightClient.parseDate("   "));
    }

    /**
     * Test parseDate with invalid formats.
     */
    @Test
    public void test_parseDate_invalidFormats() {
        assertNull(playwrightClient.parseDate("invalid date"));
        assertNull(playwrightClient.parseDate("2023-01-22"));
        assertNull(playwrightClient.parseDate("2023-01-22 02:16:34"));
        assertNull(playwrightClient.parseDate("22/01/2023"));
        assertNull(playwrightClient.parseDate("January 22, 2023"));
    }

    /**
     * Test parseDate with ISO 8601 format (should return null as it's not supported).
     */
    @Test
    public void test_parseDate_iso8601Format() {
        assertNull(playwrightClient.parseDate("2023-01-22T02:16:34Z"));
        assertNull(playwrightClient.parseDate("2023-01-22T02:16:34+00:00"));
    }

    /**
     * Test parseDate with partial dates.
     */
    @Test
    public void test_parseDate_partialDates() {
        assertNull(playwrightClient.parseDate("Sun, 22 Jan 2023"));
        assertNull(playwrightClient.parseDate("22 Jan 2023 02:16:34 GMT"));
    }

    /**
     * Test parseDate with different day names.
     */
    @Test
    public void test_parseDate_differentDayNames() {
        assertNotNull(playwrightClient.parseDate("Mon, 23 Jan 2023 02:16:34 GMT"));
        assertNotNull(playwrightClient.parseDate("Tue, 24 Jan 2023 02:16:34 GMT"));
        assertNotNull(playwrightClient.parseDate("Wed, 25 Jan 2023 02:16:34 GMT"));
        assertNotNull(playwrightClient.parseDate("Thu, 26 Jan 2023 02:16:34 GMT"));
        assertNotNull(playwrightClient.parseDate("Fri, 27 Jan 2023 02:16:34 GMT"));
        assertNotNull(playwrightClient.parseDate("Sat, 28 Jan 2023 02:16:34 GMT"));
    }

    /**
     * Test parseDate with different month names.
     */
    @Test
    public void test_parseDate_differentMonthNames() {
        assertNotNull(playwrightClient.parseDate("Sun, 01 Jan 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Wed, 01 Feb 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Wed, 01 Mar 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Sat, 01 Apr 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Mon, 01 May 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Thu, 01 Jun 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Sat, 01 Jul 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Tue, 01 Aug 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Fri, 01 Sep 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Sun, 01 Oct 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Wed, 01 Nov 2023 00:00:00 GMT"));
        assertNotNull(playwrightClient.parseDate("Fri, 01 Dec 2023 00:00:00 GMT"));
    }

    // ==================== Setter method tests ====================

    /**
     * Test setBrowserName with valid values.
     */
    @Test
    public void test_setBrowserName_validValues() {
        playwrightClient.setBrowserName("chromium");
        playwrightClient.setBrowserName("firefox");
        playwrightClient.setBrowserName("webkit");
        // No exception means success
    }

    /**
     * Test setDownloadTimeout with various values.
     */
    @Test
    public void test_setDownloadTimeout_variousValues() {
        playwrightClient.setDownloadTimeout(1);
        playwrightClient.setDownloadTimeout(15);
        playwrightClient.setDownloadTimeout(60);
        playwrightClient.setDownloadTimeout(300);
        // No exception means success
    }

    /**
     * Test setCloseTimeout with various values.
     */
    @Test
    public void test_setCloseTimeout_variousValues() {
        playwrightClient.setCloseTimeout(1);
        playwrightClient.setCloseTimeout(15);
        playwrightClient.setCloseTimeout(60);
        playwrightClient.setCloseTimeout(300);
        // No exception means success
    }

    /**
     * Test addOption method.
     */
    @Test
    public void test_addOption_singleOption() {
        playwrightClient.addOption("KEY1", "value1");
        // No exception means success
    }

    /**
     * Test addOption with multiple options.
     */
    @Test
    public void test_addOption_multipleOptions() {
        playwrightClient.addOption("KEY1", "value1");
        playwrightClient.addOption("KEY2", "value2");
        playwrightClient.addOption("KEY3", "value3");
        // No exception means success
    }

    /**
     * Test addOption with override.
     */
    @Test
    public void test_addOption_override() {
        playwrightClient.addOption("KEY1", "value1");
        playwrightClient.addOption("KEY1", "value2"); // Override
        // No exception means success
    }

    /**
     * Test addOption with null key.
     */
    @Test
    public void test_addOption_nullKey() {
        playwrightClient.addOption(null, "value");
        // No exception means success (HashMap allows null keys)
    }

    /**
     * Test addOption with null value.
     */
    @Test
    public void test_addOption_nullValue() {
        playwrightClient.addOption("KEY", null);
        // No exception means success (HashMap allows null values)
    }

    /**
     * Test addOption with empty strings.
     */
    @Test
    public void test_addOption_emptyStrings() {
        playwrightClient.addOption("", "value");
        playwrightClient.addOption("KEY", "");
        playwrightClient.addOption("", "");
        // No exception means success
    }

    // ==================== Close without init tests ====================

    /**
     * Test close when worker is null (not initialized).
     */
    @Test
    public void test_close_whenNotInitialized() {
        // Should not throw exception
        playwrightClient.close();
    }

    /**
     * Test multiple close calls.
     */
    @Test
    public void test_close_multipleCalls() {
        playwrightClient.close();
        playwrightClient.close();
        playwrightClient.close();
        // No exception means success
    }
}
