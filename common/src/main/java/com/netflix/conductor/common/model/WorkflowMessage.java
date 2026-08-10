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
package com.netflix.conductor.common.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single message in a workflow's message queue (WMQ).
 *
 * <p>The {@code payload} field contains arbitrary user-supplied JSON. The {@code id} and {@code
 * receivedAt} fields are populated by the push endpoint at ingestion time.
 */
public class WorkflowMessage {

    /** UUID v4 string, generated at push time. */
    private String id;

    /** The workflow instance that owns this message. */
    private String workflowId;

    /**
     * Arbitrary caller-supplied data. Conductor does not interpret or validate this structure.
     *
     * <p>Stored as a shallow unmodifiable copy: the top-level map cannot be mutated, but nested
     * {@code Map} or {@code List} values within the payload remain mutable. Immutability is
     * enforced via {@link #setPayload}; any future {@code @JsonCreator} constructor must apply the
     * same defensive copy.
     */
    private Map<String, Object> payload;

    /** ISO-8601 UTC timestamp recorded at ingestion time. */
    private String receivedAt;

    public WorkflowMessage() {}

    public WorkflowMessage(
            String id, String workflowId, Map<String, Object> payload, String receivedAt) {
        this.id = id;
        this.workflowId = workflowId;
        this.payload =
                payload == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        this.receivedAt = receivedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload =
                payload == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }
}
