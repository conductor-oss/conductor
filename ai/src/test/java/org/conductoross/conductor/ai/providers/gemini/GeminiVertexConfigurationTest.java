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
package org.conductoross.conductor.ai.providers.gemini;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiVertexConfigurationTest {

    @Test
    void testDefaultBaseURL_withLocation() {
        GeminiVertexConfiguration config = new GeminiVertexConfiguration();
        config.setLocation("us-central1");
        assertEquals("us-central1-aiplatform.googleapis.com:443", config.getBaseURL());
    }

    @Test
    void testCustomBaseURL() {
        GeminiVertexConfiguration config = new GeminiVertexConfiguration();
        config.setBaseURL("https://custom.googleapis.com");
        assertEquals("https://custom.googleapis.com", config.getBaseURL());
    }

    @Test
    void testGetCreatesGeminiVertexInstance() {
        GeminiVertexConfiguration config = new GeminiVertexConfiguration();
        config.setProjectId("my-project");
        config.setLocation("us-central1");

        GeminiVertex result = config.get();

        assertNotNull(result);
        assertEquals("vertex_ai", result.getModelProvider());
    }

    @Test
    void testNoArgsConstructor() {
        GeminiVertexConfiguration config = new GeminiVertexConfiguration();
        assertNull(config.getProjectId());
        assertNull(config.getLocation());
        assertNull(config.getPublisher());
        assertNull(config.getGoogleCredentials());
        assertNull(config.getPredictionServiceClient());
    }

    @Test
    void testDefaultBaseURL_nullLocationReturnsNullPrefix() {
        GeminiVertexConfiguration config = new GeminiVertexConfiguration();
        // With null location, baseURL is constructed with null prefix
        assertEquals("null-aiplatform.googleapis.com:443", config.getBaseURL());
    }
}
