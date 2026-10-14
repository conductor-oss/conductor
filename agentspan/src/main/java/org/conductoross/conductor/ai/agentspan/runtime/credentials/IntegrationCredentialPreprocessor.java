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
package org.conductoross.conductor.ai.agentspan.runtime.credentials;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.agent.AgentCredentialPreprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Resolves {@code rawConfig.integrationName} references against the integration store before the
 * task reaches an agent client, so clients are handed plain credential values rather than store
 * handles.
 */
@Component
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class IntegrationCredentialPreprocessor implements AgentCredentialPreprocessor {

    private static final Logger log =
            LoggerFactory.getLogger(IntegrationCredentialPreprocessor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CredentialResolutionService credentialResolutionService;

    public IntegrationCredentialPreprocessor(
            CredentialResolutionService credentialResolutionService) {
        this.credentialResolutionService = credentialResolutionService;
    }

    @Override
    public Map<String, String> resolve(
            Map<String, String> credentials, Map<String, Object> rawConfig) {
        Object integrationNameObj = rawConfig != null ? rawConfig.get("integrationName") : null;
        String integrationName =
                integrationNameObj instanceof String s ? StringUtils.trimToNull(s) : null;
        if (integrationName == null) {
            return credentials;
        }
        try {
            String json = credentialResolutionService.resolve("integration:" + integrationName);
            if (json == null) {
                log.warn("No credentials resolved for integration '{}'", integrationName);
                return credentials;
            }
            Map<String, String> result = new LinkedHashMap<>();
            MAPPER.readTree(json)
                    .fields()
                    .forEachRemaining(
                            e -> {
                                if (e.getValue().isTextual()) {
                                    result.put(e.getKey(), e.getValue().asText());
                                }
                            });
            return result;
        } catch (Exception e) {
            log.warn(
                    "Could not resolve credentials for integration '{}': {}",
                    integrationName,
                    e.getMessage());
            return credentials;
        }
    }
}
