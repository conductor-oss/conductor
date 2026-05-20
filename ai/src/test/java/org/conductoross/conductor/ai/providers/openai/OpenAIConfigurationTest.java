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
package org.conductoross.conductor.ai.providers.openai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAIConfigurationTest {

    @Test
    void testDefaultBaseURL() {
        OpenAIConfiguration config = new OpenAIConfiguration();
        assertEquals("https://api.openai.com/v1", config.getBaseURL());
    }

    @Test
    void testBlankBaseURLReturnsDefault() {
        OpenAIConfiguration config = new OpenAIConfiguration();
        config.setBaseURL("   ");
        assertEquals("https://api.openai.com/v1", config.getBaseURL());
    }

    @Test
    void testCustomBaseURL() {
        OpenAIConfiguration config = new OpenAIConfiguration();
        config.setBaseURL("https://custom.openai.com/v1");
        assertEquals("https://custom.openai.com/v1", config.getBaseURL());
    }

    @Test
    void testGetCreatesOpenAIInstance() {
        OpenAIConfiguration config = new OpenAIConfiguration();
        config.setApiKey("test-key");

        OpenAI result = config.get();

        assertNotNull(result);
        assertEquals("openai", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        OpenAIConfiguration config =
                new OpenAIConfiguration("api-key", "https://custom.url", "org-id");

        assertEquals("api-key", config.getApiKey());
        assertEquals("https://custom.url", config.getBaseURL());
        assertEquals("org-id", config.getOrganizationId());
    }

    @Test
    void testNoArgsConstructor() {
        OpenAIConfiguration config = new OpenAIConfiguration();
        assertNull(config.getApiKey());
        assertNull(config.getOrganizationId());
    }
}
