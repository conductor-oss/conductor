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

import java.util.ArrayList;
import java.util.List;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

public class DynamicFork extends Task<DynamicFork> {

    public static final String FORK_TASK_PARAM = "forkedTasks";

    public static final String FORK_TASK_INPUT_PARAM = "forkedTasksInputs";

    private String forkTasksParameter;

    private String forkTasksInputsParameter;

    private Join join;

    private SimpleTask forkPrepareTask;

    /**
     * Dynamic fork task that executes a set of tasks in parallel which are determined at run time.
     * Use cases: Based on the input, you want to fork N number of processes in parallel to be
     * executed. The number N is not pre-determined at the definition time and so a regular ForkJoin
     * cannot be used.
     *
     * @param taskReferenceName
     */
    public DynamicFork(
            String taskReferenceName, String forkTasksParameter, String forkTasksInputsParameter) {
        super(taskReferenceName, TaskType.FORK_JOIN_DYNAMIC);
        this.join = new Join(taskReferenceName + "_join");
        this.forkTasksParameter = forkTasksParameter;
        this.forkTasksInputsParameter = forkTasksInputsParameter;
        super.input(FORK_TASK_PARAM, forkTasksParameter);
        super.input(FORK_TASK_INPUT_PARAM, forkTasksInputsParameter);
    }

    /**
     * Dynamic fork task that executes a set of tasks in parallel which are determined at run time.
     * Use cases: Based on the input, you want to fork N number of processes in parallel to be
     * executed. The number N is not pre-determined at the definition time and so a regular ForkJoin
     * cannot be used.
     *
     * @param taskReferenceName
     * @param forkPrepareTask A Task that produces the output as {@link DynamicForkInput} to specify
     *     which tasks to fork.
     */
    public DynamicFork(String taskReferenceName, SimpleTask forkPrepareTask) {
        super(taskReferenceName, TaskType.FORK_JOIN_DYNAMIC);
        this.forkPrepareTask = forkPrepareTask;
        this.join = new Join(taskReferenceName + "_join");
        this.forkTasksParameter = forkPrepareTask.taskOutput.get(FORK_TASK_PARAM);
        this.forkTasksInputsParameter = forkPrepareTask.taskOutput.get(FORK_TASK_INPUT_PARAM);
        super.input(FORK_TASK_PARAM, forkTasksParameter);
        super.input(FORK_TASK_INPUT_PARAM, forkTasksInputsParameter);
    }

    DynamicFork(WorkflowTask workflowTask) {
        super(workflowTask);
        String nameOfParamForForkTask = workflowTask.getDynamicForkTasksParam();
        String nameOfParamForForkTaskInput = workflowTask.getDynamicForkTasksInputParamName();
        this.forkTasksParameter =
                (String) workflowTask.getInputParameters().get(nameOfParamForForkTask);
        this.forkTasksInputsParameter =
                (String) workflowTask.getInputParameters().get(nameOfParamForForkTaskInput);
    }

    public Join getJoin() {
        return join;
    }

    public String getForkTasksParameter() {
        return forkTasksParameter;
    }

    public String getForkTasksInputsParameter() {
        return forkTasksInputsParameter;
    }

    @Override
    public void updateWorkflowTask(WorkflowTask task) {
        task.setDynamicForkTasksParam("forkedTasks");
        task.setDynamicForkTasksInputParamName("forkedTasksInputs");
    }

    @Override
    protected List<WorkflowTask> getChildrenTasks() {
        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.addAll(join.getWorkflowDefTasks());
        return tasks;
    }

    @Override
    protected List<WorkflowTask> getParentTasks() {
        if (forkPrepareTask != null) {
            return List.of(forkPrepareTask.toWorkflowTask());
        }
        return List.of();
    }
}
