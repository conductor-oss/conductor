/*
 * Copyright 2022 Netflix, Inc.
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
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.sdk.workflow.def.WorkflowBuilder;

public class SetVariable extends Task<SetVariable> {
    /**
     * Sets the value of the variable in workflow. Used for workflow state management. Workflow
     * state is a Map that is initialized using @see {@link WorkflowBuilder#variables(Object)}
     *
     * @param taskReferenceName Use input methods to set the variable values
     */
    public SetVariable(String taskReferenceName) {
        super(taskReferenceName, TaskType.SET_VARIABLE);
    }

    SetVariable(WorkflowTask workflowTask) {
        super(workflowTask);
    }
}
