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
package org.conductoross.conductor.ai.providers.anthropic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicConfigurationTest {

    @Test
    void testDefaultBaseURL() {
        AnthropicConfiguration config = new AnthropicConfiguration();
        assertEquals("https://api.anthropic.com", config.getBaseURL());
    }

    @Test
    void testCustomBaseURL() {
        AnthropicConfiguration config = new AnthropicConfiguration();
        config.setBaseURL("https://custom.anthropic.com");
        assertEquals("https://custom.anthropic.com", config.getBaseURL());
    }

    @Test
    void testGetCreatesAnthropicInstance() {
        AnthropicConfiguration config = new AnthropicConfiguration();
        config.setApiKey("test-key");

        Anthropic result = config.get();

        assertNotNull(result);
        assertEquals("anthropic", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        AnthropicConfiguration config =
                new AnthropicConfiguration(
                        "api-key", "https://custom.url", "v1", "beta", "/completions");

        assertEquals("api-key", config.getApiKey());
        assertEquals("https://custom.url", config.getBaseURL());
        assertEquals("v1", config.getVersion());
        assertEquals("beta", config.getBetaVersion());
        assertEquals("/completions", config.getCompletionsPath());
    }

    @Test
    void testNoArgsConstructor() {
        AnthropicConfiguration config = new AnthropicConfiguration();
        assertNull(config.getApiKey());
        assertNull(config.getVersion());
        assertNull(config.getBetaVersion());
        assertNull(config.getCompletionsPath());
    }
}
