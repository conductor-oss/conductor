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

import java.util.List;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SWITCH;

/** {@link Switch} task is a replacement for now deprecated {@link Decision} task. */
@Component(TASK_TYPE_SWITCH)
public class Switch extends WorkflowSystemTask {

    public Switch() {
        super(TASK_TYPE_SWITCH);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }

    /**
     * Returns the terminal task reference for a Switch task.
     *
     * <p>For Switch tasks, the terminal task is the last task in the executed branch (chosen case).
     * This method looks at the selectedCase from the task output and finds the last task in that
     * branch. If the last task is itself a container task (like another Switch), it recursively
     * resolves to find the actual terminal task.
     *
     * @param workflow the workflow model
     * @param task the Switch task model
     * @return the reference name of the terminal task in the executed branch
     */
    @Override
    public String getTerminalTaskRef(
            WorkflowModel workflow, TaskModel task, SystemTaskRegistry systemTaskRegistry) {
        WorkflowTask workflowTask = task.getWorkflowTask();
        if (workflowTask == null) {
            return task.getReferenceTaskName();
        }

        // Get the selected case from the Switch task's output
        Object selectedCaseObj = task.getOutputData().get("selectedCase");
        if (selectedCaseObj == null) {
            return task.getReferenceTaskName();
        }
        String selectedCase = selectedCaseObj.toString();

        // Get the tasks for the selected case
        List<WorkflowTask> caseTasks = workflowTask.getDecisionCases().get(selectedCase);
        if (caseTasks == null || caseTasks.isEmpty()) {
            caseTasks = workflowTask.getDefaultCase();
        }

        if (caseTasks == null || caseTasks.isEmpty()) {
            return task.getReferenceTaskName();
        }

        // Get the last task definition in the branch
        WorkflowTask lastTaskDef = caseTasks.get(caseTasks.size() - 1);

        // Recursively resolve if the last task is also a container task
        return resolveTerminalTaskRef(workflow, lastTaskDef, systemTaskRegistry);
    }

    /**
     * Recursively resolves a workflow task definition to find the actual terminal task reference.
     * Uses polymorphic resolution via SystemTaskRegistry - no hardcoded type checks.
     */
    protected String resolveTerminalTaskRef(
            WorkflowModel workflow, WorkflowTask taskDef, SystemTaskRegistry systemTaskRegistry) {
        if (taskDef == null) {
            return null;
        }

        String taskRefName = taskDef.getTaskReferenceName();
        TaskModel executedTask = workflow.getTaskByRefName(taskRefName);

        // If the task hasn't executed yet or isn't terminal, return the task reference
        // as-is
        if (executedTask == null || !executedTask.getStatus().isTerminal()) {
            return taskRefName;
        }

        // Use polymorphic resolution via the registry - no type checks needed
        String taskType = executedTask.getTaskType();
        if (systemTaskRegistry.isSystemTask(taskType)) {
            WorkflowSystemTask systemTask = systemTaskRegistry.get(taskType);
            return systemTask.getTerminalTaskRef(workflow, executedTask, systemTaskRegistry);
        }

        // Non-system tasks return their own reference
        return taskRefName;
    }
}
