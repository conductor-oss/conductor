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
package org.conductoross.conductor.ai.providers.azureopenai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AzureOpenAIConfigurationTest {

    @Test
    void testGetCreatesAzureOpenAIInstance() {
        AzureOpenAIConfiguration config = new AzureOpenAIConfiguration();
        config.setApiKey("test-key");
        config.setBaseURL("https://myresource.openai.azure.com");
        config.setDeploymentName("gpt-4");

        AzureOpenAI result = config.get();

        assertNotNull(result);
        assertEquals("azure_openai", result.getModelProvider());
    }

    @Test
    void testAllArgsConstructor() {
        AzureOpenAIConfiguration config =
                new AzureOpenAIConfiguration("api-key", "https://custom.url", "user-1", "gpt-4");

        assertEquals("api-key", config.getApiKey());
        assertEquals("https://custom.url", config.getBaseURL());
        assertEquals("user-1", config.getUser());
        assertEquals("gpt-4", config.getDeploymentName());
    }

    @Test
    void testNoArgsConstructor() {
        AzureOpenAIConfiguration config = new AzureOpenAIConfiguration();
        assertNull(config.getApiKey());
        assertNull(config.getBaseURL());
        assertNull(config.getUser());
        assertNull(config.getDeploymentName());
    }
}
