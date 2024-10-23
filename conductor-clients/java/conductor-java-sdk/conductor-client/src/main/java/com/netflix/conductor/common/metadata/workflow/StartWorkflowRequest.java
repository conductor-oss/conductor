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

public class StartWorkflowRequest {

    private String name;

    private Integer version;

    private String correlationId;

    private Map<String, Object> input = new HashMap<>();

    private Map<String, String> taskToDomain = new HashMap<>();

    private WorkflowDef workflowDef;

    private String externalInputPayloadStoragePath;

    private Integer priority = 0;

    private String createdBy;

    private String idempotencyKey;

    private IdempotencyStrategy idempotencyStrategy;

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

    public StartWorkflowRequest withExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
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
