/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.common.metadata.workflow;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;

import lombok.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowTask {

    public static class CacheConfig {

        private String key;

        private int ttlInSecond;
    }

    private String name;

    private String taskReferenceName;

    private String description;

    private Map<String, Object> inputParameters = new HashMap<>();

    private String type = TaskType.SIMPLE.name();

    private String dynamicTaskNameParam;

    private String caseValueParam;

    /**
     * A javascript expression for decision cases. The result should be a scalar value that
     *     is used to decide the case branches.
     */
    private String caseExpression;

    private String scriptExpression;

    public static class WorkflowTaskList {

        private List<WorkflowTask> tasks;
    }

    // Populates for the tasks of the decision type
    private Map<String, List<WorkflowTask>> decisionCases = new LinkedHashMap<>();

    private String dynamicForkJoinTasksParam;

    private String dynamicForkTasksParam;

    private String dynamicForkTasksInputParamName;

    private List<WorkflowTask> defaultCase = new LinkedList<>();

    private List<List<WorkflowTask>> forkTasks = new LinkedList<>();

    private int // No. of seconds (at-least) to wait before starting a task.
    startDelay;

    private SubWorkflowParams subWorkflowParam;

    private List<String> joinOn = new LinkedList<>();

    private String sink;

    /**
     * If the task is optional. When set to true, the workflow execution continues even when
     *     the task is in failed status.
     */
    private boolean optional = false;

    private TaskDef taskDefinition;

    private Boolean rateLimited;

    private List<String> defaultExclusiveJoinTask = new LinkedList<>();

    /**
     * whether wait for an external event to complete the task, for EVENT and HTTP tasks
     */
    private Boolean asyncComplete = false;

    private String loopCondition;

    private List<WorkflowTask> loopOver = new LinkedList<>();

    private Integer retryCount;

    private String evaluatorType;

    /**
     * An evaluation expression for switch cases evaluated by corresponding evaluator. The
     *     result should be a scalar value that is used to decide the case branches.
     */
    private String expression;

    /*
    Map of events to be emitted when the task status changed.
    key can be comma separated values of the status changes prefixed with "on"<STATUS>
    */
    // @ProtoField(id = 29)
    private Map<String, List<StateChangeEvent>> onStateChange = new HashMap<>();

    private String joinStatus;

    private CacheConfig cacheConfig;

    private boolean permissive;

    // Adding to support Backward compatibility
    @Deprecated
    public void setWorkflowTaskType(TaskType type) {
        this.type = type.name();
    }

    public Boolean isAsyncComplete() {
        return asyncComplete;
    }

    public Boolean isRateLimited() {
        return rateLimited != null && rateLimited;
    }



    private Collection<List<WorkflowTask>> children() {
        Collection<List<WorkflowTask>> workflowTaskLists = new LinkedList<>();
        switch(TaskType.of(type)) {
            case DECISION:
            case SWITCH:
                workflowTaskLists.addAll(decisionCases.values());
                workflowTaskLists.add(defaultCase);
                break;
            case FORK_JOIN:
                workflowTaskLists.addAll(forkTasks);
                break;
            case DO_WHILE:
                workflowTaskLists.add(loopOver);
                break;
            default:
                break;
        }
        return workflowTaskLists;
    }

    public List<WorkflowTask> collectTasks() {
        List<WorkflowTask> tasks = new LinkedList<>();
        tasks.add(this);
        for (List<WorkflowTask> workflowTaskList : children()) {
            for (WorkflowTask workflowTask : workflowTaskList) {
                tasks.addAll(workflowTask.collectTasks());
            }
        }
        return tasks;
    }

    public WorkflowTask next(String taskReferenceName, WorkflowTask parent) {
        TaskType taskType = TaskType.of(type);
        switch(taskType) {
            case DO_WHILE:
            case DECISION:
            case SWITCH:
                for (List<WorkflowTask> workflowTasks : children()) {
                    Iterator<WorkflowTask> iterator = workflowTasks.iterator();
                    while (iterator.hasNext()) {
                        WorkflowTask task = iterator.next();
                        if (task.getTaskReferenceName().equals(taskReferenceName)) {
                            break;
                        }
                        WorkflowTask nextTask = task.next(taskReferenceName, this);
                        if (nextTask != null) {
                            return nextTask;
                        }
                        if (task.has(taskReferenceName)) {
                            break;
                        }
                    }
                    if (iterator.hasNext()) {
                        return iterator.next();
                    }
                }
                if (taskType == TaskType.DO_WHILE && this.has(taskReferenceName)) {
                    // come here means this is DO_WHILE task and `taskReferenceName` is the last
                    // task in
                    // this DO_WHILE task, because DO_WHILE task need to be executed to decide
                    // whether to
                    // schedule next iteration, so we just return the DO_WHILE task, and then ignore
                    // generating this task again in deciderService.getNextTask()
                    return this;
                }
                break;
            case FORK_JOIN:
                boolean found = false;
                for (List<WorkflowTask> workflowTasks : children()) {
                    Iterator<WorkflowTask> iterator = workflowTasks.iterator();
                    while (iterator.hasNext()) {
                        WorkflowTask task = iterator.next();
                        if (task.getTaskReferenceName().equals(taskReferenceName)) {
                            found = true;
                            break;
                        }
                        WorkflowTask nextTask = task.next(taskReferenceName, this);
                        if (nextTask != null) {
                            return nextTask;
                        }
                        if (task.has(taskReferenceName)) {
                            break;
                        }
                    }
                    if (iterator.hasNext()) {
                        return iterator.next();
                    }
                    if (found && parent != null) {
                        return parent.next(this.taskReferenceName, // we need to return join task... -- get my sibling from my
                        parent);
                        // parent..
                    }
                }
                break;
            case DYNAMIC:
            case TERMINATE:
            case SIMPLE:
                return null;
            default:
                break;
        }
        return null;
    }

    public boolean has(String taskReferenceName) {
        if (this.getTaskReferenceName().equals(taskReferenceName)) {
            return true;
        }
        switch(TaskType.of(type)) {
            case DECISION:
            case SWITCH:
            case DO_WHILE:
            case FORK_JOIN:
                for (List<WorkflowTask> childx : children()) {
                    for (WorkflowTask child : childx) {
                        if (child.has(taskReferenceName)) {
                            return true;
                        }
                    }
                }
                break;
            default:
                break;
        }
        return false;
    }

    public WorkflowTask get(String taskReferenceName) {
        if (this.getTaskReferenceName().equals(taskReferenceName)) {
            return this;
        }
        for (List<WorkflowTask> childx : children()) {
            for (WorkflowTask child : childx) {
                WorkflowTask found = child.get(taskReferenceName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public String toString() {
        return name + "/" + taskReferenceName;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorkflowTask that = (WorkflowTask) o;
        return Objects.equals(name, that.name) && Objects.equals(taskReferenceName, that.taskReferenceName);
    }

    public int hashCode() {
        return Objects.hash(name, taskReferenceName);
    }
}