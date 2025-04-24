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

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    @Deprecated
    private String idempotencyKey;

    @Deprecated
    private IdempotencyStrategy idempotencyStrategy;

    public StartWorkflowRequest withName(String name) {
        this.name = name;
        return this;
    }

    public StartWorkflowRequest withVersion(Integer version) {
        this.version = version;
        return this;
    }

    public StartWorkflowRequest withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public StartWorkflowRequest withExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
        return this;
    }

    public StartWorkflowRequest withPriority(Integer priority) {
        this.priority = priority;
        return this;
    }

    public StartWorkflowRequest withInput(Map<String, Object> input) {
        this.input = input;
        return this;
    }

    public StartWorkflowRequest withTaskToDomain(Map<String, String> taskToDomain) {
        this.taskToDomain = taskToDomain;
        return this;
    }

    public StartWorkflowRequest withWorkflowDef(WorkflowDef workflowDef) {
        this.workflowDef = workflowDef;
        return this;
    }

    public StartWorkflowRequest withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}