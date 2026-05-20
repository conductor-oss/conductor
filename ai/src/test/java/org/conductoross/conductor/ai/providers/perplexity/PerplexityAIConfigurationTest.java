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
package org.conductoross.conductor.ai.providers.perplexity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerplexityAIConfigurationTest {

    @Test
    void testDefaultBaseURL() {
        PerplexityAIConfiguration config = new PerplexityAIConfiguration();
        assertEquals("https://api.perplexity.ai/", config.getBaseURL());
    }

    @Test
    void testCustomBaseURL() {
        PerplexityAIConfiguration config = new PerplexityAIConfiguration();
        config.setBaseURL("https://custom.perplexity.ai");
        assertEquals("https://custom.perplexity.ai", config.getBaseURL());
    }

    @Test
    void testGetCreatesPerplexityAIInstance() {
        PerplexityAIConfiguration config = new PerplexityAIConfiguration();
        config.setApiKey("test-key");

        PerplexityAI result = config.get();

        assertNotNull(result);
        assertEquals("perplexity", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        PerplexityAIConfiguration config =
                new PerplexityAIConfiguration("api-key", "https://custom.perplexity.ai");

        assertEquals("api-key", config.getApiKey());
        assertEquals("https://custom.perplexity.ai", config.getBaseURL());
    }

    @Test
    void testNoArgsConstructor() {
        PerplexityAIConfiguration config = new PerplexityAIConfiguration();
        assertNull(config.getApiKey());
    }
}
