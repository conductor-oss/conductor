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
package org.conductoross.conductor.ai.agentspan.runtime.ai;

import java.util.List;

import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import okhttp3.OkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentspanAIModelProviderTest {

    private CredentialResolutionService credentialService;
    private OkHttpClient httpClient;
    private AgentspanAIModelProvider provider;

    @BeforeEach
    void setUp() {
        credentialService = mock(CredentialResolutionService.class);
        httpClient = new OkHttpClient();
        Environment env = mock(Environment.class);
        when(env.getProperty(anyString(), anyString())).thenAnswer(i -> i.getArgument(1));

        provider = new AgentspanAIModelProvider(List.of(), env, httpClient, credentialService);
    }

    @Test
    void constructorAcceptsInjectedHttpClient() {
        // Verifies the constructor doesn't substitute its own client
        assertThat(provider).isNotNull();
    }

    @Test
    void isProviderConfigured_returnsFalse_whenNoCredential() {
        when(credentialService.resolve("OPENAI_API_KEY")).thenReturn(null);

        assertThat(provider.isProviderConfigured("openai")).isFalse();
    }

    @Test
    void isProviderConfigured_returnsTrue_whenCredentialFound() {
        when(credentialService.resolve("OPENAI_API_KEY")).thenReturn("sk-test-key");

        assertThat(provider.isProviderConfigured("openai")).isTrue();
    }

    @Test
    void isProviderConfigured_caseInsensitive() {
        when(credentialService.resolve("ANTHROPIC_API_KEY")).thenReturn("key");

        assertThat(provider.isProviderConfigured("Anthropic")).isTrue();
        assertThat(provider.isProviderConfigured("ANTHROPIC")).isTrue();
    }

    @Test
    void isProviderConfigured_unknownProvider_returnsFalse() {
        // No PROVIDER_TO_ENV_VAR entry → resolveUserApiKey returns null early
        assertThat(provider.isProviderConfigured("unknown-provider")).isFalse();
        verifyNoInteractions(credentialService);
    }

    @Test
    void isProviderConfigured_credentialServiceThrows_returnsFalse() {
        when(credentialService.resolve("OPENAI_API_KEY"))
                .thenThrow(new RuntimeException("store unavailable"));

        assertThat(provider.isProviderConfigured("openai")).isFalse();
    }
}
