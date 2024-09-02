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
package io.orkes.conductor.client.util;

import java.util.List;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

public class WorkflowUtil {
    public static WorkflowDef getWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(Commons.WORKFLOW_NAME);
        workflowDef.setVersion(Commons.WORKFLOW_VERSION);
        workflowDef.setOwnerEmail(Commons.OWNER_EMAIL);
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(Commons.TASK_NAME);
        workflowTask.setTaskReferenceName(Commons.TASK_NAME);
        workflowDef.setTasks(List.of(workflowTask));
        return workflowDef;
    }
}
