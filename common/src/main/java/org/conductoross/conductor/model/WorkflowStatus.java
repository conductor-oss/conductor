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
package org.conductoross.conductor.model;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.run.Workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight summary of a workflow execution, returned by {@code GET
 * /workflow/{workflowId}/status}. Unlike {@link Workflow}, it omits the full task list and only
 * optionally includes output and variables, making it cheap to fetch when a caller just needs the
 * current status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStatus {

    private String workflowId;
    private String correlationId;
    private Map<String, Object> output;
    private Map<String, Object> variables;
    private Workflow.WorkflowStatus status;

    public WorkflowStatus(Workflow workflow, boolean includeOutput, boolean includeVariables) {
        this.workflowId = workflow.getWorkflowId();
        if (StringUtils.isNotEmpty(workflow.getCorrelationId())) {
            this.correlationId = workflow.getCorrelationId();
        }
        if (includeOutput) {
            this.output = workflow.getOutput();
        }
        if (includeVariables) {
            this.variables = workflow.getVariables();
        }
        this.status = workflow.getStatus();
    }
}
