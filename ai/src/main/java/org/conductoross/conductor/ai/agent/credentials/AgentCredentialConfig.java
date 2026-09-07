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
package org.conductoross.conductor.ai.agent.credentials;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Credential configuration for an external agent platform resolved from the secret store. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCredentialConfig {

    public enum Platform {
        BEDROCK,
        AZURE_FOUNDRY,
        OPENAI,
        VERTEX_AI,
        STATIC
    }

    private Platform platform;

    /** Name of the secret in Conductor's secret store. Supports dotted JSON paths. */
    private String credentialRef;

    /** AWS region for Bedrock; ignored by other platforms. */
    private String region;

    /** Agent endpoint URL (e.g. Azure Foundry A2A endpoint). */
    private String endpoint;

    /** OAuth scope; defaults are applied per-platform if omitted. */
    private String scope;
}
