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

import java.util.Arrays;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

public class Join extends Task<Join> {

    private final String[] joinOn;

    /**
     * @param taskReferenceName
     * @param joinOn List of task reference names to join on
     */
    public Join(String taskReferenceName, String... joinOn) {
        super(taskReferenceName, TaskType.JOIN);
        this.joinOn = joinOn;
    }

    Join(WorkflowTask workflowTask) {
        super(workflowTask);
        this.joinOn = workflowTask.getJoinOn().toArray(new String[0]);
    }

    @Override
    protected void updateWorkflowTask(WorkflowTask workflowTask) {
        workflowTask.setJoinOn(Arrays.asList(joinOn));
    }

    public String[] getJoinOn() {
        return joinOn;
    }
}
