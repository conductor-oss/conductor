/*
 * Copyright 2020 Netflix, Inc.
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

import java.util.Map;
import java.util.Objects;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.Preconditions;

@ProtoMessage
public class SubWorkflowParams {

    @ProtoField(id = 1)
    @NotNull(message = "SubWorkflowParams name cannot be null")
    @NotEmpty(message = "SubWorkflowParams name cannot be empty")
    private String name;

    @ProtoField(id = 2)
    private Integer version;

    @ProtoField(id = 3)
    private Map<String, String> taskToDomain;

    // workaround as WorkflowDef cannot directly be used due to cyclic dependency issue in protobuf
    // imports
    @ProtoField(id = 4)
    private Object workflowDefinition;

    /** @return the name */
    public String getName() {
        if (workflowDefinition != null) {
            return getWorkflowDef().getName();
        } else {
            return name;
        }
    }

    /** @param name the name to set */
    public void setName(String name) {
        this.name = name;
    }

    /** @return the version */
    public Integer getVersion() {
        if (workflowDefinition != null) {
            return getWorkflowDef().getVersion();
        } else {
            return version;
        }
    }

    /** @param version the version to set */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /** @return the taskToDomain */
    public Map<String, String> getTaskToDomain() {
        return taskToDomain;
    }

    /** @param taskToDomain the taskToDomain to set */
    public void setTaskToDomain(Map<String, String> taskToDomain) {
        this.taskToDomain = taskToDomain;
    }

    /** @return the workflowDefinition as an Object */
    public Object getWorkflowDefinition() {
        return workflowDefinition;
    }

    /** @return the workflowDefinition as a WorkflowDef */
    @JsonGetter("workflowDefinition")
    public WorkflowDef getWorkflowDef() {
        return (WorkflowDef) workflowDefinition;
    }

    /** @param workflowDef the workflowDefinition to set */
    public void setWorkflowDefinition(Object workflowDef) {
        Preconditions.checkArgument(
                workflowDef == null || workflowDef instanceof WorkflowDef,
                "workflowDefinition must be either null or WorkflowDef");
        this.workflowDefinition = workflowDef;
    }

    /** @param workflowDef the workflowDefinition to set */
    @JsonSetter("workflowDefinition")
    public void setWorkflowDef(WorkflowDef workflowDef) {
        this.workflowDefinition = workflowDef;
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
