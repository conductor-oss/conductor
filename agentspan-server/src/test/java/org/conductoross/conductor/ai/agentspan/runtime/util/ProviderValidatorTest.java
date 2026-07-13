/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import java.util.Optional;

import org.conductoross.conductor.ai.agentspan.runtime.ai.AgentspanAIModelProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderValidatorTest {

    private ProviderValidator validatorWith(AgentspanAIModelProvider mock) {
        return new ProviderValidator(mock);
    }

    private AgentspanAIModelProvider providerAvailable(String... providers) {
        AgentspanAIModelProvider mock = mock(AgentspanAIModelProvider.class);
        for (String p : providers) {
            when(mock.isProviderConfigured(p)).thenReturn(true);
            when(mock.isProviderConfigured(p.toLowerCase())).thenReturn(true);
        }
        return mock;
    }

    @Test
    void configuredProviderPasses() {
        ProviderValidator validator = validatorWith(providerAvailable("openai"));
        assertThat(validator.validateProvider("openai")).isEmpty();
    }

    @Test
    void unconfiguredProviderReturnsError() {
        ProviderValidator validator = validatorWith(providerAvailable("anthropic"));
        Optional<String> result = validator.validateProvider("openai");
        assertThat(result).isPresent();
        assertThat(result.get()).contains("openai");
    }

    @Test
    void errorMessageIncludesDocsUrl() {
        ProviderValidator validator = validatorWith(mock(AgentspanAIModelProvider.class));
        Optional<String> result = validator.validateProvider("mistral");
        assertThat(result).isPresent();
        assertThat(result.get()).contains("mistral").contains("Docs:");
    }

    @Test
    void errorMessageDoesNotSayRestart() {
        // Credentials added via UI take effect immediately — the error should not tell
        // users to restart the server.
        ProviderValidator validator = validatorWith(mock(AgentspanAIModelProvider.class));
        Optional<String> result = validator.validateProvider("openai");
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainIgnoringCase("restart");
    }

    @Test
    void multipleProvidersConfigured() {
        ProviderValidator validator =
                validatorWith(providerAvailable("openai", "anthropic", "gemini"));
        assertThat(validator.validateProvider("openai")).isEmpty();
        assertThat(validator.validateProvider("anthropic")).isEmpty();
        assertThat(validator.validateProvider("gemini")).isEmpty();
        assertThat(validator.validateProvider("mistral")).isPresent();
    }

    @Test
    void providerAvailableViaCredentialStorePassesValidation() {
        // Provider is NOT in the startup model map, but isProviderConfigured returns true
        // because the credential was added via the UI after startup.
        AgentspanAIModelProvider mockProvider = mock(AgentspanAIModelProvider.class);
        when(mockProvider.isProviderConfigured("openai")).thenReturn(true);

        ProviderValidator validator = validatorWith(mockProvider);
        assertThat(validator.validateProvider("openai")).isEmpty();
    }

    @Test
    void providerNotInStoreOrMapFailsValidation() {
        AgentspanAIModelProvider mockProvider = mock(AgentspanAIModelProvider.class);
        when(mockProvider.isProviderConfigured(any())).thenReturn(false);

        ProviderValidator validator = validatorWith(mockProvider);
        Optional<String> result = validator.validateProvider("openai");
        assertThat(result).isPresent();
        assertThat(result.get()).contains("openai").contains("Credentials page");
    }
}
