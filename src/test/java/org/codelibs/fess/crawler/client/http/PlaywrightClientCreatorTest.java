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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codelibs.fess.crawler.client.CrawlerClientCreator;
import org.codelibs.fess.crawler.container.CrawlerContainer;
import org.dbflute.utflute.core.PlainTestCase;

/**
 * Test class for PlaywrightClientCreator.
 *
 * @author shinsuke
 */
public class PlaywrightClientCreatorTest extends PlainTestCase {

    private PlaywrightClientCreator playwrightClientCreator;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        playwrightClientCreator = new PlaywrightClientCreator();
    }

    /**
     * Test for register method with valid regexList and componentName.
     */
    public void test_register_withValidParameters() {
        // Setup
        final List<String> regexList = Arrays.asList("https?://.*", "http://example\\.com/.*");
        final String componentName = "playwrightClient";
        final List<String> registeredPatterns = new ArrayList<>();

        // Create mock CrawlerClientCreator
        final CrawlerClientCreator mockCreator = new CrawlerClientCreator() {
            @Override
            public void register(final String regex, final String name) {
                registeredPatterns.add(regex + ":" + name);
            }
        };

        // Create mock CrawlerContainer
        playwrightClientCreator.crawlerContainer = new CrawlerContainer() {
            @Override
            public <T> T getComponent(final String name) {
                if ("crawlerClientCreator".equals(name)) {
                    return (T) mockCreator;
                }
                return null;
            }

            @Override
            public void destroy() {
                // No-op for test
            }
        };

        // Execute
        playwrightClientCreator.register(regexList, componentName);

        // Verify
        assertEquals(2, registeredPatterns.size());
        assertTrue(registeredPatterns.contains("https?://.*:playwrightClient"));
        assertTrue(registeredPatterns.contains("http://example\\.com/.*:playwrightClient"));
    }

    /**
     * Test for register method with empty regexList.
     */
    public void test_register_withEmptyRegexList() {
        // Setup
        final List<String> regexList = new ArrayList<>();
        final String componentName = "playwrightClient";
        final List<String> registeredPatterns = new ArrayList<>();

        // Create mock CrawlerClientCreator
        final CrawlerClientCreator mockCreator = new CrawlerClientCreator() {
            @Override
            public void register(final String regex, final String name) {
                registeredPatterns.add(regex + ":" + name);
            }
        };

        // Create mock CrawlerContainer
        playwrightClientCreator.crawlerContainer = new CrawlerContainer() {
            @Override
            public <T> T getComponent(final String name) {
                if ("crawlerClientCreator".equals(name)) {
                    return (T) mockCreator;
                }
                return null;
            }

            @Override
            public void destroy() {
                // No-op for test
            }
        };

        // Execute
        playwrightClientCreator.register(regexList, componentName);

        // Verify - no patterns should be registered
        assertEquals(0, registeredPatterns.size());
    }

    /**
     * Test for register method with single regex pattern.
     */
    public void test_register_withSinglePattern() {
        // Setup
        final List<String> regexList = Arrays.asList("https://secure\\.example\\.com/.*");
        final String componentName = "secureClient";
        final List<String> registeredPatterns = new ArrayList<>();

        // Create mock CrawlerClientCreator
        final CrawlerClientCreator mockCreator = new CrawlerClientCreator() {
            @Override
            public void register(final String regex, final String name) {
                registeredPatterns.add(regex + ":" + name);
            }
        };

        // Create mock CrawlerContainer
        playwrightClientCreator.crawlerContainer = new CrawlerContainer() {
            @Override
            public <T> T getComponent(final String name) {
                if ("crawlerClientCreator".equals(name)) {
                    return (T) mockCreator;
                }
                return null;
            }

            @Override
            public void destroy() {
                // No-op for test
            }
        };

        // Execute
        playwrightClientCreator.register(regexList, componentName);

        // Verify
        assertEquals(1, registeredPatterns.size());
        assertTrue(registeredPatterns.contains("https://secure\\.example\\.com/.*:secureClient"));
    }

    /**
     * Test for register method when CrawlerClientCreator is not found.
     */
    public void test_register_throwsException_whenCreatorNotFound() {
        // Setup
        final List<String> regexList = Arrays.asList("https?://.*");
        final String componentName = "playwrightClient";

        // Create mock CrawlerContainer that returns null
        playwrightClientCreator.crawlerContainer = new CrawlerContainer() {
            @Override
            public <T> T getComponent(final String name) {
                return null; // Simulate component not found
            }

            @Override
            public void destroy() {
                // No-op for test
            }
        };

        // Execute & Verify
        try {
            playwrightClientCreator.register(regexList, componentName);
            fail("Expected IllegalStateException to be thrown");
        } catch (final IllegalStateException e) {
            assertEquals("CrawlerClientCreator component not found in the container.", e.getMessage());
        }
    }

    /**
     * Test for register method with multiple regex patterns.
     */
    public void test_register_withMultiplePatterns() {
        // Setup
        final List<String> regexList = Arrays.asList(
            "https?://www\\.example\\.com/.*",
            "https?://api\\.example\\.com/.*",
            "https?://cdn\\.example\\.com/.*",
            "https?://static\\.example\\.com/.*"
        );
        final String componentName = "multiPatternClient";
        final List<String> registeredPatterns = new ArrayList<>();

        // Create mock CrawlerClientCreator
        final CrawlerClientCreator mockCreator = new CrawlerClientCreator() {
            @Override
            public void register(final String regex, final String name) {
                registeredPatterns.add(regex + ":" + name);
            }
        };

        // Create mock CrawlerContainer
        playwrightClientCreator.crawlerContainer = new CrawlerContainer() {
            @Override
            public <T> T getComponent(final String name) {
                if ("crawlerClientCreator".equals(name)) {
                    return (T) mockCreator;
                }
                return null;
            }

            @Override
            public void destroy() {
                // No-op for test
            }
        };

        // Execute
        playwrightClientCreator.register(regexList, componentName);

        // Verify
        assertEquals(4, registeredPatterns.size());
        assertTrue(registeredPatterns.contains("https?://www\\.example\\.com/.*:multiPatternClient"));
        assertTrue(registeredPatterns.contains("https?://api\\.example\\.com/.*:multiPatternClient"));
        assertTrue(registeredPatterns.contains("https?://cdn\\.example\\.com/.*:multiPatternClient"));
        assertTrue(registeredPatterns.contains("https?://static\\.example\\.com/.*:multiPatternClient"));
    }

    /**
     * Test for register method with special characters in regex patterns.
     */
    public void test_register_withSpecialCharactersInPattern() {
        // Setup
        final List<String> regexList = Arrays.asList(
            "https?://[a-z0-9]+\\.example\\.com/.*",
            "https?://example\\.com/path/[0-9]+/.*",
            "https?://.*\\.example\\.(com|org|net)/.*"
        );
        final String componentName = "specialPatternClient";
        final List<String> registeredPatterns = new ArrayList<>();

        // Create mock CrawlerClientCreator
        final CrawlerClientCreator mockCreator = new CrawlerClientCreator() {
            @Override
            public void register(final String regex, final String name) {
                registeredPatterns.add(regex + ":" + name);
            }
        };

        // Create mock CrawlerContainer
        playwrightClientCreator.crawlerContainer = new CrawlerContainer() {
            @Override
            public <T> T getComponent(final String name) {
                if ("crawlerClientCreator".equals(name)) {
                    return (T) mockCreator;
                }
                return null;
            }

            @Override
            public void destroy() {
                // No-op for test
            }
        };

        // Execute
        playwrightClientCreator.register(regexList, componentName);

        // Verify
        assertEquals(3, registeredPatterns.size());
        assertTrue(registeredPatterns.contains("https?://[a-z0-9]+\\.example\\.com/.*:specialPatternClient"));
        assertTrue(registeredPatterns.contains("https?://example\\.com/path/[0-9]+/.*:specialPatternClient"));
        assertTrue(registeredPatterns.contains("https?://.*\\.example\\.(com|org|net)/.*:specialPatternClient"));
    }

    /**
     * Test for constructor.
     */
    public void test_constructor() {
        // Verify that constructor creates a valid instance
        final PlaywrightClientCreator creator = new PlaywrightClientCreator();
        assertNotNull(creator);
    }
}
