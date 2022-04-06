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
import java.util.Collections;
import java.util.List;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

public class DoWhile extends Task<DoWhile> {

    private String loopCondition;

    private List<Task<?>> loopTasks = new ArrayList<>();

    /**
     * Execute tasks in a loop determined by the condition set using condition parameter. The loop
     * will continue till the condition is true
     *
     * @param taskReferenceName
     * @param condition Javascript that evaluates to a boolean value
     * @param tasks
     */
    public DoWhile(String taskReferenceName, String condition, Task<?>... tasks) {
        super(taskReferenceName, TaskType.DO_WHILE);
        Collections.addAll(this.loopTasks, tasks);
        this.loopCondition = condition;
    }

    /**
     * Similar to a for loop, run tasks for N times
     *
     * @param taskReferenceName
     * @param loopCount
     * @param tasks
     */
    public DoWhile(String taskReferenceName, int loopCount, Task<?>... tasks) {
        super(taskReferenceName, TaskType.DO_WHILE);
        Collections.addAll(this.loopTasks, tasks);
        this.loopCondition = getForLoopCondition(loopCount);
    }

    DoWhile(WorkflowTask workflowTask) {
        super(workflowTask);
        this.loopCondition = workflowTask.getLoopCondition();
        for (WorkflowTask task : workflowTask.getLoopOver()) {
            Task<?> loopTask = TaskRegistry.getTask(task);
            this.loopTasks.add(loopTask);
        }
    }

    public DoWhile loopOver(Task<?>... tasks) {
        for (Task<?> task : tasks) {
            this.loopTasks.add(task);
        }
        return this;
    }

    private String getForLoopCondition(int loopCount) {
        return "if ( $."
                + getTaskReferenceName()
                + "['iteration'] < "
                + loopCount
                + ") { true; } else { false; }";
    }

    public String getLoopCondition() {
        return loopCondition;
    }

    public List<? extends Task> getLoopTasks() {
        return loopTasks;
    }

    @Override
    public void updateWorkflowTask(WorkflowTask workflowTask) {
        workflowTask.setLoopCondition(loopCondition);

        List<WorkflowTask> loopWorkflowTasks = new ArrayList<>();
        for (Task task : this.loopTasks) {
            loopWorkflowTasks.addAll(task.getWorkflowDefTasks());
        }
        workflowTask.setLoopOver(loopWorkflowTasks);
    }
}
