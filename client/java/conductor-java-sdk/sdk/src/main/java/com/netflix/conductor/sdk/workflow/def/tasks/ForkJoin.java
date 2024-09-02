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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/** ForkJoin task */
public class ForkJoin extends Task<ForkJoin> {

    private Join join;

    private final Task[][] forkedTasks;

    /**
     * execute task specified in the forkedTasks parameter in parallel.
     *
     * <p>forkedTask is a two-dimensional list that executes the outermost list in parallel and list
     * within that is executed sequentially.
     *
     * <p>e.g. [[task1, task2],[task3, task4],[task5]] are executed as:
     *
     * <pre>
     *                    ---------------
     *                    |     fork    |
     *                    ---------------
     *                    |       |     |
     *                    |       |     |
     *                  task1  task3  task5
     *                  task2  task4    |
     *                    |      |      |
     *                 ---------------------
     *                 |       join        |
     *                 ---------------------
     * </pre>
     *
     * <p>This method automatically adds a join that waits for all the *last* tasks in the fork
     * (e.g. task2, task4 and task5 in the above example) to be completed.*
     *
     * <p>Use join method @see {@link ForkJoin#joinOn(String...)} to override this behavior (note:
     * not a common scenario)
     *
     * @param taskReferenceName unique task reference name
     * @param forkedTasks List of tasks to be executed in parallel
     */
    public ForkJoin(String taskReferenceName, Task<?>[]... forkedTasks) {
        super(taskReferenceName, TaskType.FORK_JOIN);
        this.forkedTasks = forkedTasks;
    }

    ForkJoin(WorkflowTask workflowTask) {
        super(workflowTask);
        int size = workflowTask.getForkTasks().size();
        this.forkedTasks = new Task[size][];
        int i = 0;
        for (List<WorkflowTask> forkTasks : workflowTask.getForkTasks()) {
            Task[] tasks = new Task[forkTasks.size()];
            for (int j = 0; j < forkTasks.size(); j++) {
                WorkflowTask forkWorkflowTask = forkTasks.get(j);
                Task task = TaskRegistry.getTask(forkWorkflowTask);
                tasks[j] = task;
            }
            this.forkedTasks[i++] = tasks;
        }
    }

    public ForkJoin joinOn(String... joinOn) {
        this.join = new Join(getTaskReferenceName() + "_join", joinOn);
        return this;
    }

    @Override
    protected List<WorkflowTask> getChildrenTasks() {
        WorkflowTask fork = toWorkflowTask();

        WorkflowTask joinWorkflowTask = null;
        if (this.join != null) {
            List<WorkflowTask> joinTasks = this.join.getWorkflowDefTasks();
            joinWorkflowTask = joinTasks.get(0);
        } else {
            joinWorkflowTask = new WorkflowTask();
            joinWorkflowTask.setWorkflowTaskType(TaskType.JOIN);
            joinWorkflowTask.setTaskReferenceName(getTaskReferenceName() + "_join");
            joinWorkflowTask.setName(joinWorkflowTask.getTaskReferenceName());
            joinWorkflowTask.setJoinOn(fork.getJoinOn());
        }
        return Arrays.asList(joinWorkflowTask);
    }

    @Override
    public void updateWorkflowTask(WorkflowTask fork) {
        List<String> joinOnTaskRefNames = new ArrayList<>();
        List<List<WorkflowTask>> forkTasks = new ArrayList<>();

        for (Task<?>[] forkedTaskList : forkedTasks) {
            List<WorkflowTask> forkedWorkflowTasks = new ArrayList<>();
            for (Task<?> baseWorkflowTask : forkedTaskList) {
                forkedWorkflowTasks.addAll(baseWorkflowTask.getWorkflowDefTasks());
            }
            forkTasks.add(forkedWorkflowTasks);
            joinOnTaskRefNames.add(
                    forkedWorkflowTasks.get(forkedWorkflowTasks.size() - 1).getTaskReferenceName());
        }
        if (this.join != null) {
            fork.setJoinOn(List.of(this.join.getJoinOn()));
        } else {
            fork.setJoinOn(joinOnTaskRefNames);
        }

        fork.setForkTasks(forkTasks);
    }

    public Task[][] getForkedTasks() {
        return forkedTasks;
    }
}
