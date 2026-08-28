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
package com.netflix.conductor.common.metadata.workflow;

import java.util.List;

/**
 * Declares that a workflow requires delegated access to an external provider before it can run.
 *
 * <p>When present in {@link WorkflowDef#getRequiredDelegations()}, the Run Workflow UI renders an
 * "Authorize" button for each entry. Clicking it opens an OAuth popup; after the user consents, the
 * resulting refresh token is stored as a Conductor secret under {@code secretRef}. Subsequent runs
 * find the secret already present and skip the popup.
 *
 * <p>The workflow itself references the stored token via an HTTP task that exchanges it for a
 * short-lived access token before calling downstream agents.
 */
public class DelegationRequirement {

    /** Unique identifier within the workflow definition (e.g. {@code "microsoft"}). */
    private String key;

    /**
     * OAuth provider. Currently only {@code "microsoft"} is supported; reserved for future
     * providers (e.g. {@code "google"}).
     */
    private String provider;

    /**
     * Human-readable label shown in the UI (e.g. {@code "Microsoft Account"}). Defaults to {@code
     * provider} if omitted.
     */
    private String label;

    /**
     * OAuth scopes to request. Should include {@code offline_access} to obtain a refresh token.
     * Example: {@code ["https://ai.azure.com/.default", "offline_access"]}.
     */
    private List<String> scopes;

    /**
     * Name under which the refresh token is stored as a Conductor secret. Referenced by the
     * workflow via {@code ${workflow.secrets.<secretRef>}}. Example: {@code
     * "alice-ms-delegated-token"}.
     */
    private String secretRef;

    public DelegationRequirement() {}

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLabel() {
        return label != null ? label : provider;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }
}
