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
package com.netflix.conductor.core.execution.tasks;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_INLINE_WORKFLOW;

import java.util.Map;

@Slf4j
@Component(TASK_TYPE_INLINE_WORKFLOW)
public class InlineWorkflow extends WorkflowSystemTask {

    private final ParametersUtils parametersUtils;
    public InlineWorkflow(ParametersUtils parametersUtils) {
        super(TASK_TYPE_INLINE_WORKFLOW);
        this.parametersUtils = parametersUtils;
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {

        boolean allDone = true;
        for (WorkflowTask workflowTask : task.getWorkflowTask().getInlineWorkflow().getTasks()) {
            String refName = workflowTask.getTaskReferenceName();
            TaskModel taskInWf = workflow.getTaskByRefName(refName);
            if (taskInWf != null && !taskInWf.getStatus().isTerminal()) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            task.setStatus(TaskModel.Status.COMPLETED);
            TaskDef taskDef = task.getTaskDefinition().orElse(null);
            Map<String, Object> output = parametersUtils.getTaskInputV2(task.getWorkflowTask()
                .getInlineWorkflow()
                .getOutputParameters(), workflow, task.getTaskId(), taskDef);
            task.setOutputData(output);
            return true;
        }
        return false;
    }
}
