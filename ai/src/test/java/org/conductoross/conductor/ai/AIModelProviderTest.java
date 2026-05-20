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
package org.conductoross.conductor.ai;

import java.util.List;

import org.conductoross.conductor.ai.models.ChatCompletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIModelProviderTest {

    private Environment mockEnv;

    @BeforeEach
    void setUp() {
        mockEnv = mock(Environment.class);
        when(mockEnv.getProperty(eq("conductor.file-storage.parentDir"), anyString()))
                .thenReturn("/tmp/test-payload");
    }

    @Test
    void testEmptyProviderList() {
        AIModelProvider provider = new AIModelProvider(List.of(), mockEnv);

        assertNotNull(provider);
        // payloadStoreLocation is null when no configs are provided because it's set
        // inside the loop
        assertNull(provider.getPayloadStoreLocation());
    }

    @Test
    void testGetModel_nullProviderThrowsException() {
        AIModelProvider provider = new AIModelProvider(List.of(), mockEnv);
        ChatCompletion input = new ChatCompletion();
        input.setLlmProvider(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> provider.getModel(input));
        assertEquals("llmProvider not specified: null", ex.getMessage());
    }

    @Test
    void testGetModel_unknownProviderThrowsException() {
        AIModelProvider provider = new AIModelProvider(List.of(), mockEnv);
        ChatCompletion input = new ChatCompletion();
        input.setLlmProvider("unknown_provider");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> provider.getModel(input));
        assertEquals("no configuration found for: unknown_provider", ex.getMessage());
    }

    @Test
    void testGetModel_registeredProviderReturnsModel() {
        // Create a mock model configuration
        AIModel mockModel = mock(AIModel.class);
        when(mockModel.getModelProvider()).thenReturn("test_provider");

        @SuppressWarnings("unchecked")
        ModelConfiguration<AIModel> mockConfig = mock(ModelConfiguration.class);
        when(mockConfig.get()).thenReturn(mockModel);

        AIModelProvider provider = new AIModelProvider(List.of(mockConfig), mockEnv);

        ChatCompletion input = new ChatCompletion();
        input.setLlmProvider("test_provider");

        AIModel result = provider.getModel(input);
        assertNotNull(result);
        assertEquals("test_provider", result.getModelProvider());
    }

    @Test
    void testMultipleProviders() {
        AIModel mockModel1 = mock(AIModel.class);
        when(mockModel1.getModelProvider()).thenReturn("provider1");

        AIModel mockModel2 = mock(AIModel.class);
        when(mockModel2.getModelProvider()).thenReturn("provider2");

        @SuppressWarnings("unchecked")
        ModelConfiguration<AIModel> mockConfig1 = mock(ModelConfiguration.class);
        when(mockConfig1.get()).thenReturn(mockModel1);

        @SuppressWarnings("unchecked")
        ModelConfiguration<AIModel> mockConfig2 = mock(ModelConfiguration.class);
        when(mockConfig2.get()).thenReturn(mockModel2);

        AIModelProvider provider = new AIModelProvider(List.of(mockConfig1, mockConfig2), mockEnv);

        ChatCompletion input1 = new ChatCompletion();
        input1.setLlmProvider("provider1");
        assertEquals("provider1", provider.getModel(input1).getModelProvider());

        ChatCompletion input2 = new ChatCompletion();
        input2.setLlmProvider("provider2");
        assertEquals("provider2", provider.getModel(input2).getModelProvider());
    }

    @Test
    void testProviderInitializationFailure_logsAndContinues() {
        // Provider that throws during initialization
        @SuppressWarnings("unchecked")
        ModelConfiguration<AIModel> failingConfig = mock(ModelConfiguration.class);
        when(failingConfig.get()).thenThrow(new RuntimeException("Initialization failed"));

        // Valid provider
        AIModel mockModel = mock(AIModel.class);
        when(mockModel.getModelProvider()).thenReturn("valid_provider");

        @SuppressWarnings("unchecked")
        ModelConfiguration<AIModel> validConfig = mock(ModelConfiguration.class);
        when(validConfig.get()).thenReturn(mockModel);

        // Should not throw, failing provider is skipped
        AIModelProvider provider =
                new AIModelProvider(List.of(failingConfig, validConfig), mockEnv);

        ChatCompletion input = new ChatCompletion();
        input.setLlmProvider("valid_provider");

        assertNotNull(provider.getModel(input));
    }

    @Test
    void testGetTokenUsageLogger_returnsConsumer() {
        AIModelProvider provider = new AIModelProvider(List.of(), mockEnv);

        assertNotNull(provider.getTokenUsageLogger());
    }

    @Test
    void testPayloadStoreLocation_defaultValue() {
        Environment envWithDefault = mock(Environment.class);
        String defaultPath = System.getProperty("user.home") + "/worker-payload/";
        when(envWithDefault.getProperty(eq("conductor.file-storage.parentDir"), anyString()))
                .thenAnswer(inv -> inv.getArgument(1)); // Return the default

        AIModel mockModel = mock(AIModel.class);
        when(mockModel.getModelProvider()).thenReturn("test");

        @SuppressWarnings("unchecked")
        ModelConfiguration<AIModel> mockConfig = mock(ModelConfiguration.class);
        when(mockConfig.get()).thenReturn(mockModel);

        AIModelProvider provider = new AIModelProvider(List.of(mockConfig), envWithDefault);

        assertEquals(defaultPath, provider.getPayloadStoreLocation());
    }
}
