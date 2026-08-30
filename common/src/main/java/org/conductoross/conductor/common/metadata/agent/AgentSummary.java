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
package org.conductoross.conductor.common.metadata.agent;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSummary {

    private String name;
    private int version;
    private String type;
    /** Provider-specific connection target: Foundry project URL, Bedrock region, etc. */
    private String endpoint;
    /** Conductor secret name used to authenticate with this agent's provider. */
    private String credentialRef;
    private List<String> tags;
    private Long createTime;
    private Long updateTime;
    private String description;
    private String checksum;

    // These fields mirror the underlying workflow definition so the agent list can use the same
    // metadata columns as the workflow definition list.
    private Integer schemaVersion;
    private Boolean restartable;
    private Boolean workflowStatusListenerEnabled;
    private String ownerEmail;
    private List<String> inputParameters;
    private Map<String, Object> outputParameters;
    private String timeoutPolicy;
    private Long timeoutSeconds;
    private String failureWorkflow;
}
