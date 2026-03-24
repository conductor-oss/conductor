/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuration for a named webhook endpoint.
 *
 * <p>A {@code WebhookConfig} binds an inbound webhook URL to one or more target workflows. When an
 * HTTP request arrives at {@code POST /webhook/{id}}, the verifier is used to authenticate it, and
 * matching waiting {@code WAIT_FOR_WEBHOOK} tasks are completed with the payload.
 *
 * <p>This class is intentionally free of enterprise concerns (orgId, tags, audit). Those are added
 * by Orkes Enterprise via its own DAO implementations and REST layer adapters.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookConfig {

    private String name;

    private String id;

    /**
     * Map of workflow definition names to versions. Inbound events are matched against {@code
     * WAIT_FOR_WEBHOOK} tasks in running instances of these workflows.
     */
    private Map<String, Integer> receiverWorkflowNamesToVersions;

    /**
     * Map describing workflows to start when a matching webhook event arrives. Used by PR 5
     * (workflowsToStart). Stored here for round-trip fidelity; not processed until that PR.
     */
    private Map<String, Object> workflowsToStart;

    /** True once the webhook URL has been verified (e.g. challenge/response). */
    private boolean urlVerified;

    /** Identifies the source platform (e.g. "github", "stripe"). Informational. */
    private String sourcePlatform;

    /** Selects the verification strategy for inbound requests. */
    private Verifier verifier;

    /**
     * Header key/value pairs required for {@link Verifier#HEADER_BASED} verification. All
     * configured headers must be present with exact values in the inbound request.
     */
    private Map<String, String> headers;

    /** Header name used to carry the signature for {@link Verifier#HMAC_BASED} verification. */
    private String headerKey;

    /** Key name component for HMAC-based verification. */
    private String secretKey;

    /**
     * Secret value used in HMAC or signature verification. Masked ("***") when returned from the
     * API.
     */
    private String secretValue;

    /** Identifies the user who created this webhook config. Set by the REST layer. */
    private String createdBy;

    /**
     * Optional JSONPath expression evaluated against the inbound payload to decide whether this
     * webhook config matches. Stored for future expression-based routing; not evaluated in the
     * initial PRs.
     */
    private String expression;

    /** Evaluator type for {@link #expression} (e.g. "javascript"). */
    private String evaluatorType;

    public enum Verifier {
        HEADER_BASED,
        HMAC_BASED,
        SIGNATURE_BASED,
        SLACK_BASED,
        STRIPE,
        TWITTER,
        SENDGRID
    }

    @JsonIgnore
    public List<String> getWorkflowNames() {
        return receiverWorkflowNamesToVersions == null
                ? List.of()
                : new ArrayList<>(receiverWorkflowNamesToVersions.keySet());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Integer> getReceiverWorkflowNamesToVersions() {
        return receiverWorkflowNamesToVersions;
    }

    public void setReceiverWorkflowNamesToVersions(
            Map<String, Integer> receiverWorkflowNamesToVersions) {
        this.receiverWorkflowNamesToVersions = receiverWorkflowNamesToVersions;
    }

    public Map<String, Object> getWorkflowsToStart() {
        return workflowsToStart;
    }

    public void setWorkflowsToStart(Map<String, Object> workflowsToStart) {
        this.workflowsToStart = workflowsToStart;
    }

    public boolean isUrlVerified() {
        return urlVerified;
    }

    public void setUrlVerified(boolean urlVerified) {
        this.urlVerified = urlVerified;
    }

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public Verifier getVerifier() {
        return verifier;
    }

    public void setVerifier(Verifier verifier) {
        this.verifier = verifier;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getHeaderKey() {
        return headerKey;
    }

    public void setHeaderKey(String headerKey) {
        this.headerKey = headerKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSecretValue() {
        return secretValue;
    }

    public void setSecretValue(String secretValue) {
        this.secretValue = secretValue;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getEvaluatorType() {
        return evaluatorType;
    }

    public void setEvaluatorType(String evaluatorType) {
        this.evaluatorType = evaluatorType;
    }
}
