/*
 * Copyright 2022 Orkes, Inc.
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

import java.util.HashMap;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

public class Terminate extends Task<Terminate> {

    private static final String TERMINATION_STATUS_PARAMETER = "terminationStatus";

    private static final String TERMINATION_WORKFLOW_OUTPUT = "workflowOutput";

    private static final String TERMINATION_REASON_PARAMETER = "terminationReason";

    /**
     * Terminate the workflow and mark it as FAILED
     *
     * @param taskReferenceName
     * @param reason
     */
    public Terminate(String taskReferenceName, String reason) {
        this(taskReferenceName, Workflow.WorkflowStatus.FAILED, reason, new HashMap<>());
    }

    /**
     * Terminate the workflow with a specific terminate status
     *
     * @param taskReferenceName
     * @param terminationStatus
     * @param reason
     */
    public Terminate(
            String taskReferenceName, Workflow.WorkflowStatus terminationStatus, String reason) {
        this(taskReferenceName, terminationStatus, reason, new HashMap<>());
    }

    public Terminate(
            String taskReferenceName,
            Workflow.WorkflowStatus terminationStatus,
            String reason,
            Object workflowOutput) {
        super(taskReferenceName, TaskType.TERMINATE);

        input(TERMINATION_STATUS_PARAMETER, terminationStatus.name());
        input(TERMINATION_WORKFLOW_OUTPUT, workflowOutput);
        input(TERMINATION_REASON_PARAMETER, reason);
    }

    Terminate(WorkflowTask workflowTask) {
        super(workflowTask);
    }
}
