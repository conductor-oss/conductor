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
package com.netflix.conductor.core.secrets;

import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.dao.SecretsDAO;
import org.conductoross.conductor.model.secret.CredentialMeta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.env.EnvVarLookup;

@Component
@ConditionalOnProperty(name = "conductor.secrets.type", havingValue = "env", matchIfMissing = true)
public class EnvVariableSecretsDAO implements SecretsDAO {

    /**
     * Env vars that {@code conductor.ai.*.api-key} properties fall back to in {@code
     * application.properties} (e.g. {@code conductor.ai.openai.api-key=${OPENAI_API_KEY:}}).
     */
    private static final List<String> KNOWN_LLM_API_KEYS =
            List.of(
                    "OPENAI_API_KEY",
                    "ANTHROPIC_API_KEY",
                    "MISTRAL_API_KEY",
                    "COHERE_API_KEY",
                    "XAI_API_KEY",
                    "PERPLEXITY_API_KEY",
                    "HUGGINGFACE_API_KEY",
                    "STABILITY_API_KEY",
                    "AZURE_OPENAI_API_KEY",
                    "GEMINI_API_KEY");

    private final String prefix;

    public EnvVariableSecretsDAO(
            @Value("${conductor.secrets.env.prefix:CONDUCTOR_SECRET_}") String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getSecret(String name) {
        return EnvVarLookup.lookup(prefix, name);
    }

    @Override
    public boolean secretExists(String name) {
        return getSecret(name) != null;
    }

    @Override
    public List<String> listSecretNames() {
        return new ArrayList<>(EnvVarLookup.allWithPrefix(prefix).keySet());
    }

    @Override
    public void putSecret(String name, String value) {
        throw new UnsupportedOperationException("env-backed secrets are read-only");
    }

    @Override
    public void deleteSecret(String name) {
        throw new UnsupportedOperationException("env-backed secrets are read-only");
    }

    @Override
    public List<CredentialMeta> listWithMeta() {
        List<CredentialMeta> result = new ArrayList<>();
        EnvVarLookup.allWithPrefix(prefix)
                .forEach((name, value) -> result.add(toMeta(name, value)));
        for (String llmKey : KNOWN_LLM_API_KEYS) {
            String value = EnvVarLookup.lookup("", llmKey);
            if (value != null) {
                result.add(toMeta(llmKey, value));
            }
        }
        return result;
    }

    private static CredentialMeta toMeta(String name, String value) {
        return CredentialMeta.builder().name(name).partial(maskPartial(value)).build();
    }

    private static String maskPartial(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 8) {
            return "...";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
