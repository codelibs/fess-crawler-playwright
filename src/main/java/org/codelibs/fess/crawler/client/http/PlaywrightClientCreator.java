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

import java.util.List;

import org.codelibs.fess.crawler.client.CrawlerClientCreator;
import org.codelibs.fess.crawler.container.CrawlerContainer;

import jakarta.annotation.Resource;

/**
 * The PlaywrightClientCreator class is responsible for registering a list of regex patterns
 * with a specified component name in the crawler container.
 *
 * <p>This class uses the CrawlerContainer to retrieve a CrawlerClientCreator component
 * and registers each regex pattern with the provided component name.</p>
 *
 * <p>Dependencies:</p>
 * <ul>
 *   <li>{@link CrawlerContainer} - The container that holds the crawler components.</li>
 * </ul>
 *
 * <p>Methods:</p>
 * <ul>
 *   <li>{@link #register(List, String)} - Registers a list of regex patterns with a specified component name.</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * PlaywrightClientCreator creator = new PlaywrightClientCreator();
 * creator.register(Arrays.asList("regex1", "regex2"), "componentName");
 * }
 * </pre>
 */
public class PlaywrightClientCreator {

    /**
     * The {@code crawlerContainer} holds the instance of {@link CrawlerContainer} used by this client creator.
     * It manages the lifecycle and configuration of crawler-related components required for HTTP client operations.
     */
    @Resource
    protected CrawlerContainer crawlerContainer;

    /**
     * Default constructor for {@code PlaywrightClientCreator}.
     * Initializes a new instance of the class with default settings.
     */
    public PlaywrightClientCreator() {
        // Default constructor
    }

    /**
     * Registers a list of regex patterns with a specified component name.
     *
     * @param regexList the list of regex patterns to register
     * @param componentName the name of the component to register the patterns with
     * @throws IllegalStateException if the CrawlerClientCreator component is not found in the container
     */
    public void register(final List<String> regexList, final String componentName) {
        final CrawlerClientCreator creator = crawlerContainer.getComponent("crawlerClientCreator");
        if (creator != null) {
            regexList.stream().forEach(regex -> creator.register(regex, componentName));
        } else {
            throw new IllegalStateException("CrawlerClientCreator component not found in the container.");
        }
    }
}
