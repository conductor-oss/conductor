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
package org.conductoross.conductor.ai.agentspan.runtime.ai;

import java.util.List;

import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import com.netflix.conductor.core.secrets.NoopSecretsDAO;

import okhttp3.OkHttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses the production no-op secret backend instead of mocking credential lookup. */
class AgentspanAIModelProviderTest {

    private final AgentspanAIModelProvider provider =
            new AgentspanAIModelProvider(
                    List.of(),
                    new StandardEnvironment(),
                    new OkHttpClient(),
                    new CredentialResolutionService(new NoopSecretsDAO()));

    @Test
    void constructorAcceptsConcreteRuntimeDependencies() {
        assertThat(provider).isNotNull();
    }

    @Test
    void providerIsNotConfiguredWhenTheRealSecretBackendContainsNoCredential() {
        assertThat(provider.isProviderConfigured("openai")).isFalse();
        assertThat(provider.isProviderConfigured("Anthropic")).isFalse();
    }

    @Test
    void unknownProviderNeverAppearsConfigured() {
        assertThat(provider.isProviderConfigured("unknown-provider")).isFalse();
    }

    // =================================================================
    // OPENAI_BASE_URL env-var fallback (issue-1416 — Bug 2)
    // =================================================================

    /**
     * resolveConfiguredBaseUrl returns the value from the env-var fallback path when the credential
     * store has nothing — verified via a subclass that injects a fake env value.
     */
    @Test
    void resolveConfiguredBaseUrl_returnsEnvVarFallbackWhenCredentialStoreEmpty() {
        String fakeUrl = "http://localhost:9001/v1";

        AgentspanAIModelProvider providerWithEnv =
                new AgentspanAIModelProvider(
                        List.of(),
                        new StandardEnvironment(),
                        new OkHttpClient(),
                        new CredentialResolutionService(new NoopSecretsDAO())) {
                    @Override
                    protected String getSystemEnv(String name) {
                        return "OPENAI_BASE_URL".equals(name) ? fakeUrl : null;
                    }
                };

        assertThat(providerWithEnv.resolveConfiguredBaseUrl("openai")).isEqualTo(fakeUrl);
    }

    @Test
    void resolveConfiguredBaseUrl_returnsNullWhenNeitherStoreNorEnvHasValue() {
        assertThat(provider.resolveConfiguredBaseUrl("openai")).isNull();
    }

    // =================================================================
    // API key trimming (issue-1437)
    // =================================================================

    private AgentspanAIModelProvider providerWithRawEnv(String envName, String rawValue) {
        return new AgentspanAIModelProvider(
                List.of(),
                new StandardEnvironment(),
                new OkHttpClient(),
                new CredentialResolutionService(new NoopSecretsDAO())) {
            @Override
            String readRawEnv(String name) {
                return envName.equals(name) ? rawValue : null;
            }
        };
    }

    @Test
    void getSystemEnv_stripsTrailingNewline() {
        assertThat(providerWithRawEnv("OPENAI_API_KEY", "sk-key\n").getSystemEnv("OPENAI_API_KEY"))
                .isEqualTo("sk-key");
    }

    @Test
    void getSystemEnv_stripsLeadingAndTrailingWhitespace() {
        assertThat(
                        providerWithRawEnv("OPENAI_API_KEY", "  sk-key  ")
                                .getSystemEnv("OPENAI_API_KEY"))
                .isEqualTo("sk-key");
    }

    @Test
    void getSystemEnv_returnsNullWhenEnvVarAbsent() {
        assertThat(provider.getSystemEnv("OPENAI_API_KEY_NONEXISTENT_XYZ")).isNull();
    }
}
