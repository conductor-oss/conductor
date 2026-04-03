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
package org.conductoross.conductor.ai.providers.mistral;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MistralAIConfigurationTest {

    @Test
    void testDefaultBaseURL() {
        MistralAIConfiguration config = new MistralAIConfiguration();
        assertEquals("https://api.mistral.ai", config.getBaseURL());
    }

    @Test
    void testCustomBaseURL() {
        MistralAIConfiguration config = new MistralAIConfiguration();
        config.setBaseURL("https://custom.mistral.ai");
        assertEquals("https://custom.mistral.ai", config.getBaseURL());
    }

    @Test
    void testGetCreatesMistralAIInstance() {
        MistralAIConfiguration config = new MistralAIConfiguration();
        config.setApiKey("test-key");

        MistralAI result = config.get();

        assertNotNull(result);
        assertEquals("mistral", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        MistralAIConfiguration config = new MistralAIConfiguration("api-key", "https://custom.url");

        assertEquals("api-key", config.getApiKey());
        assertEquals("https://custom.url", config.getBaseURL());
    }

    @Test
    void testNoArgsConstructor() {
        MistralAIConfiguration config = new MistralAIConfiguration();
        assertNull(config.getApiKey());
    }
}
