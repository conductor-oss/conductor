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
package org.conductoross.conductor.ai.providers.huggingface;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HuggingFaceConfigurationTest {

    @Test
    void testDefaultBaseURL() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        assertEquals("https://huggingface.co/api", config.getBaseURL());
    }

    @Test
    void testCustomBaseURL() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setBaseURL("https://custom.huggingface.co");
        assertEquals("https://custom.huggingface.co", config.getBaseURL());
    }

    @Test
    void testGetCreatesHuggingFaceInstance() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setApiKey("test-key");

        HuggingFace result = config.get();

        assertNotNull(result);
        assertEquals("huggingface", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        HuggingFaceConfiguration config =
                new HuggingFaceConfiguration("api-key", "https://custom.url");

        assertEquals("api-key", config.getApiKey());
        assertEquals("https://custom.url", config.getBaseURL());
    }

    @Test
    void testNoArgsConstructor() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        assertNull(config.getApiKey());
    }
}
