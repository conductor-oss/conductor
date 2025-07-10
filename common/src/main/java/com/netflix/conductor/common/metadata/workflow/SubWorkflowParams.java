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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.common.utils.TaskUtils;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

@ProtoMessage
public class SubWorkflowParams {

    @ProtoField(id = 1)
    private String name;

    @ProtoField(id = 2)
    private Integer version;

    @ProtoField(id = 3)
    private Map<String, String> taskToDomain;

    // workaround as WorkflowDef cannot directly be used due to cyclic dependency issue in protobuf
    // imports
    @ProtoField(id = 4)
    private Object workflowDefinition;

    private String idempotencyKey;

    private IdempotencyStrategy idempotencyStrategy;

    // Priority of the sub workflow, not set inherits from the parent
    private Object priority;

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

    public Object getPriority() {
        return priority;
    }

    public void setPriority(Object priority) {
        this.priority = priority;
    }

    /**
     * @return the name
     */
    public String getName() {
        if (workflowDefinition != null) {
            if (workflowDefinition instanceof WorkflowDef) {
                return ((WorkflowDef) workflowDefinition).getName();
            }
        }
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the version
     */
    public Integer getVersion() {
        if (workflowDefinition != null) {
            if (workflowDefinition instanceof WorkflowDef) {
                return ((WorkflowDef) workflowDefinition).getVersion();
            }
        }
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * @return the taskToDomain
     */
    public Map<String, String> getTaskToDomain() {
        return taskToDomain;
    }

    /**
     * @param taskToDomain the taskToDomain to set
     */
    public void setTaskToDomain(Map<String, String> taskToDomain) {
        this.taskToDomain = taskToDomain;
    }

    /**
     * @return the workflowDefinition as an Object
     */
    @JsonGetter("workflowDefinition")
    public Object getWorkflowDefinition() {
        return workflowDefinition;
    }

    @Deprecated
    @JsonIgnore
    public void setWorkflowDef(WorkflowDef workflowDef) {
        this.setWorkflowDefinition(workflowDef);
    }

    @Deprecated
    @JsonIgnore
    public WorkflowDef getWorkflowDef() {
        return (WorkflowDef) workflowDefinition;
    }

    /**
     * @param workflowDef the workflowDefinition to set
     */
    @JsonSetter("workflowDefinition")
    public void setWorkflowDefinition(Object workflowDef) {
        if (workflowDef == null) {
            this.workflowDefinition = workflowDef;
        } else if (workflowDef instanceof WorkflowDef) {
            this.workflowDefinition = workflowDef;
        } else if (workflowDef instanceof String) {
            if (!(((String) workflowDef).startsWith("${"))
                    || !(((String) workflowDef).endsWith("}"))) {
                throw new IllegalArgumentException(
                        "workflowDefinition is a string, but not a valid DSL string");
            } else {
                this.workflowDefinition = workflowDef;
            }
        } else if (workflowDef instanceof LinkedHashMap) {
            this.workflowDefinition = TaskUtils.convertToWorkflowDef(workflowDef);
        } else {
            throw new IllegalArgumentException(
                    "workflowDefinition must be either null, or WorkflowDef, or a valid DSL string");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SubWorkflowParams that = (SubWorkflowParams) o;
        return Objects.equals(getName(), that.getName())
                && Objects.equals(getVersion(), that.getVersion())
                && Objects.equals(getTaskToDomain(), that.getTaskToDomain())
                && Objects.equals(getWorkflowDefinition(), that.getWorkflowDefinition());
    }
}
