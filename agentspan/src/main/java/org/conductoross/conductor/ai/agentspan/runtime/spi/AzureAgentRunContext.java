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
package org.conductoross.conductor.ai.agentspan.runtime.spi;

// Serializable snapshot of an Azure AI Foundry agent run. Stored in AzureAgentRunStore so
// any server replica can reconstruct and authenticate against the Azure run without in-process
// state. credentialRef and scope are stored here so respond() and cancelAgent() — which
// carry no credential in their request objects — can re-authenticate on any pod.
public class AzureAgentRunContext {

    private final String threadId;
    private final String runId;
    private final String endpoint;
    private final String assistantId;
    private final String apiVersion;
    private final String credentialRef;
    private final String scope;

    public AzureAgentRunContext(
            String threadId,
            String runId,
            String endpoint,
            String assistantId,
            String apiVersion,
            String credentialRef,
            String scope) {
        this.threadId = threadId;
        this.runId = runId;
        this.endpoint = endpoint;
        this.assistantId = assistantId;
        this.apiVersion = apiVersion;
        this.credentialRef = credentialRef;
        this.scope = scope;
    }

    public AzureAgentRunContext withRunId(String newRunId) {
        return new AzureAgentRunContext(
                threadId, newRunId, endpoint, assistantId, apiVersion, credentialRef, scope);
    }

    public String getThreadId() {
        return threadId;
    }

    public String getRunId() {
        return runId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getAssistantId() {
        return assistantId;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public String getScope() {
        return scope;
    }
}
