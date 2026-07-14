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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProviderValidator {

    private final AgentspanAIModelProvider aiModelProvider;

    private static final String DOCS_URL =
            "https://github.com/agentspan-ai/agentspan/blob/main/docs/ai-models.md";

    /**
     * When embedded in a host (e.g. orkes-conductor), Conductor is the authority for model
     * providers and credentials: conductor-ai <b>integrations</b> resolve providers by name, and
     * the host's <b>credential store</b> (AWS SSM / Vault / etc., reached via the {@code
     * SecretsDAO}/{@code CredentialsDAO} SPI) supplies raw keys. This standalone pre-flight check
     * only knows AgentSpan's own provider model, so it would wrongly reject host-configured
     * providers. The execution path already delegates to conductor-ai (which resolves or rejects
     * the provider), so when embedded we defer to Conductor and skip this check.
     */
    @Value("${agentspan.embedded:false}")
    private boolean embedded;

    /**
     * Returns Optional.empty() if the provider is configured (either via startup environment
     * variables or via a credential added in the UI), or Optional.of(errorMessage) if not.
     */
    public Optional<String> validateProvider(String provider) {
        if (embedded || aiModelProvider.isProviderConfigured(provider)) {
            return Optional.empty();
        }
        return Optional.of(
                "Model provider '"
                        + provider
                        + "' is not configured. "
                        + "Add an API key for '"
                        + provider
                        + "' on the Credentials page. "
                        + "Docs: "
                        + DOCS_URL);
    }
}
