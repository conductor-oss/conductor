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
package org.conductoross.conductor.ai.providers.cohere;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CohereAIConfigurationTest {

    @Test
    void testDefaultBaseURL() {
        CohereAIConfiguration config = new CohereAIConfiguration();
        assertEquals("https://api.cohere.ai", config.getBaseURL());
    }

    @Test
    void testGetCreatesCohereAIInstance() {
        CohereAIConfiguration config = new CohereAIConfiguration();
        config.setApiKey("test-key");

        CohereAI result = config.get();

        assertNotNull(result);
        assertEquals("cohere", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        CohereAIConfiguration config =
                new CohereAIConfiguration("api-key", "https://custom.cohere.ai");

        assertEquals("api-key", config.getApiKey());
        assertEquals("https://custom.cohere.ai", config.getBaseURL());
    }

    @Test
    void testNoArgsConstructor() {
        CohereAIConfiguration config = new CohereAIConfiguration();
        assertNull(config.getApiKey());
    }
}
