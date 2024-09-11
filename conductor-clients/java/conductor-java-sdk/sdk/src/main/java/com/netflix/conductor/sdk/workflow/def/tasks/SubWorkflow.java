/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.sdk.workflow.def.tasks;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;

public class SubWorkflow extends Task<SubWorkflow> {

    private ConductorWorkflow conductorWorkflow;

    private String workflowName;

    private Integer workflowVersion;

    /**
     * Start a workflow as a sub-workflow
     *
     * @param taskReferenceName
     * @param workflowName
     * @param workflowVersion
     */
    public SubWorkflow(String taskReferenceName, String workflowName, Integer workflowVersion) {
        super(taskReferenceName, TaskType.SUB_WORKFLOW);
        this.workflowName = workflowName;
        this.workflowVersion = workflowVersion;
    }

    /**
     * Start a workflow as a sub-workflow
     *
     * @param taskReferenceName
     * @param conductorWorkflow
     */
    public SubWorkflow(String taskReferenceName, ConductorWorkflow conductorWorkflow) {
        super(taskReferenceName, TaskType.SUB_WORKFLOW);
        this.conductorWorkflow = conductorWorkflow;
    }

    SubWorkflow(WorkflowTask workflowTask) {
        super(workflowTask);
        SubWorkflowParams subworkflowParam = workflowTask.getSubWorkflowParam();
        this.workflowName = subworkflowParam.getName();
        this.workflowVersion = subworkflowParam.getVersion();
        if (subworkflowParam.getWorkflowDefinition() != null
                && subworkflowParam.getWorkflowDefinition() instanceof WorkflowDef) {
            this.conductorWorkflow =
                    ConductorWorkflow.fromWorkflowDef(
                            (WorkflowDef) subworkflowParam.getWorkflowDefinition());
        }
    }

    public ConductorWorkflow getConductorWorkflow() {
        return conductorWorkflow;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public int getWorkflowVersion() {
        return workflowVersion;
    }

    @Override
    protected void updateWorkflowTask(WorkflowTask workflowTask) {
        SubWorkflowParams subWorkflowParam = new SubWorkflowParams();

        if (conductorWorkflow != null) {
            subWorkflowParam.setWorkflowDefinition(conductorWorkflow.toWorkflowDef());
        } else {
            subWorkflowParam.setName(workflowName);
            subWorkflowParam.setVersion(workflowVersion);
        }
        workflowTask.setSubWorkflowParam(subWorkflowParam);
    }
}
