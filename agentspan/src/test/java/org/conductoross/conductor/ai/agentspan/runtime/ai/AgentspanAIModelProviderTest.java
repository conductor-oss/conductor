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
}
