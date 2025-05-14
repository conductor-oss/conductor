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

import com.netflix.conductor.common.utils.TaskUtils;

import lombok.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SubWorkflowParams {

    private String name;

    private Integer version;

    private Map<String, String> taskToDomain;

    // workaround as WorkflowDef cannot directly be used due to cyclic dependency issue in protobuf
    // imports
    private Object workflowDefinition;

    private String idempotencyKey;

    private IdempotencyStrategy idempotencyStrategy;

    // Priority of the sub workflow, not set inherits from the parent
    private Object priority;

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
     * @return the workflowDefinition as an Object
     */
    public Object getWorkflowDefinition() {
        return workflowDefinition;
    }

    @Deprecated
    public void setWorkflowDef(WorkflowDef workflowDef) {
        this.setWorkflowDefinition(workflowDef);
    }

    @Deprecated
    public WorkflowDef getWorkflowDef() {
        return (WorkflowDef) workflowDefinition;
    }

    /** 
     * @param workflowDef the workflowDefinition to set
     */
    public void setWorkflowDefinition(Object workflowDef) {
        if (workflowDef == null) {
            this.workflowDefinition = null;
        } else if (workflowDef instanceof WorkflowDef) {
            this.workflowDefinition = workflowDef;
        } else if (workflowDef instanceof String) {
            if (!(((String) workflowDef).startsWith("${")) || !(((String) workflowDef).endsWith("}"))) {
                throw new IllegalArgumentException("workflowDefinition is a string, but not a valid DSL string");
            } else {
                this.workflowDefinition = workflowDef;
            }
        } else if (workflowDef instanceof LinkedHashMap) {
            this.workflowDefinition = TaskUtils.convertToWorkflowDef(workflowDef);
        } else {
            throw new IllegalArgumentException("workflowDefinition must be either null, or WorkflowDef, or a valid DSL string");
        }
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SubWorkflowParams that = (SubWorkflowParams) o;
        return Objects.equals(getName(), that.getName()) && Objects.equals(getVersion(), that.getVersion()) && Objects.equals(getTaskToDomain(), that.getTaskToDomain()) && Objects.equals(getWorkflowDefinition(), that.getWorkflowDefinition());
    }
}