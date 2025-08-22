/*
 * Copyright 2020 Conductor Authors.
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

import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ProtoMessage
public class StartWorkflowRequest {

    @ProtoField(id = 1)
    @NotNull(message = "Workflow name cannot be null or empty")
    private String name;

    @ProtoField(id = 2)
    private Integer version;

    @ProtoField(id = 3)
    private String correlationId;

    @ProtoField(id = 4)
    private Map<String, Object> input = new HashMap<>();

    @ProtoField(id = 5)
    private Map<String, String> taskToDomain = new HashMap<>();

    @ProtoField(id = 6)
    @Valid
    private WorkflowDef workflowDef;

    @ProtoField(id = 7)
    private String externalInputPayloadStoragePath;

    @ProtoField(id = 8)
    @Min(value = 0, message = "priority: ${validatedValue} should be minimum {value}")
    @Max(value = 99, message = "priority: ${validatedValue} should be maximum {value}")
    private Integer priority = 0;

    @ProtoField(id = 9)
    private String createdBy;

    private String idempotencyKey;

    private IdempotencyStrategy idempotencyStrategy;

    /**
     * Optional runtime overrides for task-level rate limits.
     *
     * <p>Key : task reference name OR task definition name Value : {@link TaskRateLimitOverride}
     * containing per-frequency overrides.
     */
    @ProtoField(id = 10)
    private Map<String, TaskRateLimitOverride> taskRateLimitOverrides = new HashMap<>();

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public IdempotencyStrategy getIdempotencyStrategy() {
        return idempotencyStrategy;
    }

    public void setIdempotencyStrategy(IdempotencyStrategy idempotencyStrategy) {
        this.idempotencyStrategy = idempotencyStrategy;
    }

    /* -------------------------------------------------------
     *  Dynamic rate-limit override accessors
     * ------------------------------------------------------- */

    public Map<String, TaskRateLimitOverride> getTaskRateLimitOverrides() {
        return taskRateLimitOverrides;
    }

    public void setTaskRateLimitOverrides(
            Map<String, TaskRateLimitOverride> taskRateLimitOverrides) {
        if (taskRateLimitOverrides == null) {
            taskRateLimitOverrides = new HashMap<>();
        }
        this.taskRateLimitOverrides = taskRateLimitOverrides;
    }

    public StartWorkflowRequest withTaskRateLimitOverrides(
            Map<String, TaskRateLimitOverride> taskRateLimitOverrides) {
        setTaskRateLimitOverrides(taskRateLimitOverrides);
        return this;
    }

    /** Holder for per-task dynamic rate-limit configuration. */
    @ProtoMessage
    public static class TaskRateLimitOverride {
        /** Default value indicating no specific rate limit is set */
        public static final int NOT_SET = -1;

        @ProtoField(id = 1)
        private int rateLimitPerFrequency = NOT_SET;

        @ProtoField(id = 2)
        private int rateLimitFrequencyInSeconds = NOT_SET;

        private boolean rateLimitPerFrequencySet = false;
        private boolean rateLimitFrequencyInSecondsSet = false;

        /**
         * Get the rate limit per frequency value.
         *
         * @return the rate limit per frequency or null if not explicitly set
         */
        public Integer getRateLimitPerFrequency() {
            return rateLimitPerFrequencySet ? rateLimitPerFrequency : null;
        }

        /**
         * Set the rate limit per frequency value.
         *
         * @param rateLimitPerFrequency the rate limit per frequency (must be non-negative)
         * @throws IllegalArgumentException if value is negative
         */
        public void setRateLimitPerFrequency(Integer rateLimitPerFrequency) {
            if (rateLimitPerFrequency == null) {
                this.rateLimitPerFrequencySet = false;
                this.rateLimitPerFrequency = NOT_SET;
                return;
            }

            if (rateLimitPerFrequency < 0) {
                throw new IllegalArgumentException(
                        "Rate limit per frequency cannot be negative: " + rateLimitPerFrequency);
            }

            this.rateLimitPerFrequency = rateLimitPerFrequency;
            this.rateLimitPerFrequencySet = true;
        }

        /**
         * Fluent API for setting rate limit per frequency.
         *
         * @param rateLimitPerFrequency the rate limit per frequency (must be non-negative)
         * @return this instance for method chaining
         * @throws IllegalArgumentException if value is negative
         */
        public TaskRateLimitOverride withRateLimitPerFrequency(Integer rateLimitPerFrequency) {
            setRateLimitPerFrequency(rateLimitPerFrequency);
            return this;
        }

        /**
         * Get the rate limit frequency in seconds value.
         *
         * @return the rate limit frequency in seconds or null if not explicitly set
         */
        public Integer getRateLimitFrequencyInSeconds() {
            return rateLimitFrequencyInSecondsSet ? rateLimitFrequencyInSeconds : null;
        }

        /**
         * Set the rate limit frequency in seconds value.
         *
         * @param rateLimitFrequencyInSeconds the rate limit frequency in seconds (must be
         *     non-negative)
         * @throws IllegalArgumentException if value is negative
         */
        public void setRateLimitFrequencyInSeconds(Integer rateLimitFrequencyInSeconds) {
            if (rateLimitFrequencyInSeconds == null) {
                this.rateLimitFrequencyInSecondsSet = false;
                this.rateLimitFrequencyInSeconds = NOT_SET;
                return;
            }

            if (rateLimitFrequencyInSeconds < 0) {
                throw new IllegalArgumentException(
                        "Rate limit frequency in seconds cannot be negative: "
                                + rateLimitFrequencyInSeconds);
            }

            this.rateLimitFrequencyInSeconds = rateLimitFrequencyInSeconds;
            this.rateLimitFrequencyInSecondsSet = true;
        }

        /**
         * Fluent API for setting rate limit frequency in seconds.
         *
         * @param rateLimitFrequencyInSeconds the rate limit frequency in seconds (must be
         *     non-negative)
         * @return this instance for method chaining
         * @throws IllegalArgumentException if value is negative
         */
        public TaskRateLimitOverride withRateLimitFrequencyInSeconds(
                Integer rateLimitFrequencyInSeconds) {
            setRateLimitFrequencyInSeconds(rateLimitFrequencyInSeconds);
            return this;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StartWorkflowRequest withName(String name) {
        this.name = name;
        return this;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public StartWorkflowRequest withVersion(Integer version) {
        this.version = version;
        return this;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public StartWorkflowRequest withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public String getExternalInputPayloadStoragePath() {
        return externalInputPayloadStoragePath;
    }

    public void setExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
    }

    public StartWorkflowRequest withExternalInputPayloadStoragePath(
            String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
        return this;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public StartWorkflowRequest withPriority(Integer priority) {
        this.priority = priority;
        return this;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public StartWorkflowRequest withInput(Map<String, Object> input) {
        this.input = input;
        return this;
    }

    public Map<String, String> getTaskToDomain() {
        return taskToDomain;
    }

    public void setTaskToDomain(Map<String, String> taskToDomain) {
        this.taskToDomain = taskToDomain;
    }

    public StartWorkflowRequest withTaskToDomain(Map<String, String> taskToDomain) {
        this.taskToDomain = taskToDomain;
        return this;
    }

    public WorkflowDef getWorkflowDef() {
        return workflowDef;
    }

    public void setWorkflowDef(WorkflowDef workflowDef) {
        this.workflowDef = workflowDef;
    }

    public StartWorkflowRequest withWorkflowDef(WorkflowDef workflowDef) {
        this.workflowDef = workflowDef;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public StartWorkflowRequest withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
