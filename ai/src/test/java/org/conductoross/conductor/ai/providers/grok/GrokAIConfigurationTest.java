/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.ai.providers.grok;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrokAIConfigurationTest {

    @Test
    void testDefaultBaseURL() {
        GrokAIConfiguration config = new GrokAIConfiguration();
        assertEquals("https://api.x.ai", config.getBaseURL());
    }

    @Test
    void testCustomBaseURL() {
        GrokAIConfiguration config = new GrokAIConfiguration();
        config.setBaseURL("https://custom.x.ai");
        assertEquals("https://custom.x.ai", config.getBaseURL());
    }

    @Test
    void testGetCreatesGrokInstance() {
        GrokAIConfiguration config = new GrokAIConfiguration();
        config.setApiKey("test-key");

        Grok result = config.get();

        assertNotNull(result);
        assertEquals("Grok", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        GrokAIConfiguration config = new GrokAIConfiguration("api-key", "https://custom.x.ai");

        assertEquals("api-key", config.getApiKey());
        assertEquals("https://custom.x.ai", config.getBaseURL());
    }

    @Test
    void testNoArgsConstructor() {
        GrokAIConfiguration config = new GrokAIConfiguration();
        assertNull(config.getApiKey());
    }
}
