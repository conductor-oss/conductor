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
package com.netflix.conductor.common.metadata.tasks;

import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;

/**
 * Execution metadata for capturing NEW operational metadata not already present in Task/TaskResult
 * models. Contains enhanced timing measurements and additional context for operational purposes.
 */
@ProtoMessage
public class ExecutionMetadata {

    // Direct timing fields
    @ProtoField(id = 1)
    private Long serverSendTime;

    @ProtoField(id = 2)
    private Long clientReceiveTime;

    @ProtoField(id = 3)
    private Long executionStartTime;

    @ProtoField(id = 4)
    private Long executionEndTime;

    @ProtoField(id = 5)
    private Long clientSendTime;

    @ProtoField(id = 6)
    private Long pollNetworkLatency;

    @ProtoField(id = 7)
    private Long updateNetworkLatency;

    // Additional context as Map for flexibility
    @ProtoField(id = 8)
    private Map<String, Object> additionalContext = new HashMap<>();

    public ExecutionMetadata() {}

    // ============ TIMING METHODS ============

    /** Sets server send time */
    public void setServerSendTime(long timestamp) {
        this.serverSendTime = timestamp;
    }

    /** Sets client receive time */
    public void setClientReceiveTime(long timestamp) {
        this.clientReceiveTime = timestamp;
    }

    /** Sets execution start time */
    public void setExecutionStartTime(long timestamp) {
        this.executionStartTime = timestamp;
    }

    /** Sets execution end time */
    public void setExecutionEndTime(long timestamp) {
        this.executionEndTime = timestamp;
    }

    /** Sets client send time */
    public void setClientSendTime(long timestamp) {
        this.clientSendTime = timestamp;
    }

    /** Sets poll network latency */
    public void setPollNetworkLatency(long latencyMs) {
        this.pollNetworkLatency = latencyMs;
    }

    /** Sets update network latency */
    public void setUpdateNetworkLatency(long latencyMs) {
        this.updateNetworkLatency = latencyMs;
    }

    /** Gets server send time */
    public Long getServerSendTime() {
        return serverSendTime;
    }

    /** Gets client receive time */
    public Long getClientReceiveTime() {
        return clientReceiveTime;
    }

    /** Gets execution start time */
    public Long getExecutionStartTime() {
        return executionStartTime;
    }

    /** Gets execution end time */
    public Long getExecutionEndTime() {
        return executionEndTime;
    }

    /** Gets client send time */
    public Long getClientSendTime() {
        return clientSendTime;
    }

    /** Gets poll network latency */
    public Long getPollNetworkLatency() {
        return pollNetworkLatency;
    }

    /** Gets update network latency */
    public Long getUpdateNetworkLatency() {
        return updateNetworkLatency;
    }

    /** Calculates total execution time */
    public Long getExecutionDuration() {
        if (executionStartTime != null && executionEndTime != null) {
            return executionEndTime - executionStartTime;
        }
        return null;
    }

    // ============ ADDITIONAL CONTEXT METHODS ============

    /** Sets additional context data */
    public void setAdditionalContext(String key, Object value) {
        additionalContext.put(key, value);
    }

    /** Gets additional context data */
    public Object getAdditionalContext(String key) {
        return additionalContext.get(key);
    }

    /** Gets the additional context map (for protogen compatibility) */
    public Map<String, Object> getAdditionalContext() {
        return additionalContext;
    }

    // ============ GETTERS AND SETTERS ============

    public void setServerSendTime(Long serverSendTime) {
        this.serverSendTime = serverSendTime;
    }

    public void setClientReceiveTime(Long clientReceiveTime) {
        this.clientReceiveTime = clientReceiveTime;
    }

    public void setExecutionStartTime(Long executionStartTime) {
        this.executionStartTime = executionStartTime;
    }

    public void setExecutionEndTime(Long executionEndTime) {
        this.executionEndTime = executionEndTime;
    }

    public void setClientSendTime(Long clientSendTime) {
        this.clientSendTime = clientSendTime;
    }

    public void setPollNetworkLatency(Long pollNetworkLatency) {
        this.pollNetworkLatency = pollNetworkLatency;
    }

    public void setUpdateNetworkLatency(Long updateNetworkLatency) {
        this.updateNetworkLatency = updateNetworkLatency;
    }

    public Map<String, Object> getAdditionalContextMap() {
        return additionalContext;
    }

    public void setAdditionalContextMap(Map<String, Object> additionalContext) {
        this.additionalContext = additionalContext != null ? additionalContext : new HashMap<>();
    }

    /** Sets the additional context map (for protogen compatibility) */
    public void setAdditionalContext(Map<String, Object> additionalContext) {
        this.additionalContext = additionalContext != null ? additionalContext : new HashMap<>();
    }

    /** Checks if this ExecutionMetadata has any meaningful data */
    public boolean hasData() {
        return serverSendTime != null
                || clientReceiveTime != null
                || executionStartTime != null
                || executionEndTime != null
                || clientSendTime != null
                || pollNetworkLatency != null
                || updateNetworkLatency != null
                || (additionalContext != null && !additionalContext.isEmpty());
    }

    /** Checks if this ExecutionMetadata is completely empty (used by protobuf serialization) */
    public boolean isEmpty() {
        return !hasData();
    }

    @Override
    public String toString() {
        return "ExecutionMetadata{"
                + "serverSendTime="
                + serverSendTime
                + ", clientReceiveTime="
                + clientReceiveTime
                + ", executionStartTime="
                + executionStartTime
                + ", executionEndTime="
                + executionEndTime
                + ", clientSendTime="
                + clientSendTime
                + ", pollNetworkLatency="
                + pollNetworkLatency
                + ", updateNetworkLatency="
                + updateNetworkLatency
                + ", additionalContext="
                + additionalContext
                + '}';
    }
}
