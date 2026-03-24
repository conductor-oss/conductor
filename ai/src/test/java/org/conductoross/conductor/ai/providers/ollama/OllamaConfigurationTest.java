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
package org.conductoross.conductor.ai.providers.ollama;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OllamaConfigurationTest {

    @Test
    void testDefaultBaseURL() {
        OllamaConfiguration config = new OllamaConfiguration();
        assertEquals("http://localhost:11434", config.getBaseURL());
    }

    @Test
    void testCustomBaseURL() {
        OllamaConfiguration config = new OllamaConfiguration();
        config.setBaseURL("http://remote-ollama:11434");
        assertEquals("http://remote-ollama:11434", config.getBaseURL());
    }

    @Test
    void testGetCreatesOllamaInstance() {
        OllamaConfiguration config = new OllamaConfiguration();

        Ollama result = config.get();

        assertNotNull(result);
        assertEquals("ollama", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        OllamaConfiguration config =
                new OllamaConfiguration("http://custom:11434", "Authorization", "Bearer token");

        assertEquals("http://custom:11434", config.getBaseURL());
        assertEquals("Authorization", config.getAuthHeaderName());
        assertEquals("Bearer token", config.getAuthHeader());
    }

    @Test
    void testNoArgsConstructor() {
        OllamaConfiguration config = new OllamaConfiguration();
        assertNull(config.getAuthHeaderName());
        assertNull(config.getAuthHeader());
    }
}
