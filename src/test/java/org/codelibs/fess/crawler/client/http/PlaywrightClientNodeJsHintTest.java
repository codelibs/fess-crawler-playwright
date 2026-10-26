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

import org.dbflute.utflute.core.PlainTestCase;
import org.junit.jupiter.api.Test;

/**
 * Tests the hint added to the failure that Playwright reports when it has no Node.js.
 */
public class PlaywrightClientNodeJsHintTest extends PlainTestCase {

    @Test
    public void test_nodeJsHint_missingNodeJs() {
        // The message Playwright itself raises, wrapped the way the client wraps it.
        final Exception cause = new RuntimeException("Failed to find the bundled Node.js for platform 'linux-arm64'. "
                + "Add the com.microsoft.playwright:driver-bundle dependency, or set the PLAYWRIGHT_NODEJS_PATH environment variable.");
        final String hint = PlaywrightClient.nodeJsHint(new IllegalStateException("outer", cause));
        assertTrue(hint.contains("bin/fess-setup install nodejs"));
        assertTrue(hint.contains("PLAYWRIGHT_NODEJS_PATH"));
    }

    @Test
    public void test_nodeJsHint_otherFailure() {
        assertEquals("", PlaywrightClient.nodeJsHint(new RuntimeException("Connection refused")));
        assertEquals("", PlaywrightClient.nodeJsHint(new RuntimeException()));
    }

    @Test
    public void test_nodeJsHint_invalidNodeJsPath() {
        // The message Playwright itself raises when PLAYWRIGHT_NODEJS_PATH names a file that does not exist.
        // The driver wraps it in "Failed to create driver", so the path message is deeper in the cause chain.
        final Exception cause =
                new RuntimeException("Failed to create driver", new RuntimeException("Invalid Node.js path specified: /no/such/node"));
        final String hint = PlaywrightClient.nodeJsHint(new IllegalStateException("outer", cause));
        assertTrue(hint.contains("PLAYWRIGHT_NODEJS_PATH"));
        assertTrue(hint.contains("bin/fess-setup install nodejs"));
    }
}
