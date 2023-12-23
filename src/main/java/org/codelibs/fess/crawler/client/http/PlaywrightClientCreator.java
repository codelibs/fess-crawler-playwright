/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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

public class PlaywrightClientCreator {

    @Resource
    protected CrawlerContainer crawlerContainer;

    public void register(final List<String> regexList, final String componentName) {
        final CrawlerClientCreator creator = crawlerContainer.getComponent("crawlerClientCreator");
        regexList.stream().forEach(regex -> creator.register(regex, componentName));
    }
}
