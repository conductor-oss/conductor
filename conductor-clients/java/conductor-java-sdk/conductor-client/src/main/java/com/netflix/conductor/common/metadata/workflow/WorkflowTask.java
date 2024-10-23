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

/**
 * This is the task definition definied as part of the {@link WorkflowDef}. The tasks definied in
 * the Workflow definition are saved as part of {@link WorkflowDef#getTasks}
 */
public class WorkflowTask {

    public static class CacheConfig {

        private String key;

        private int ttlInSecond;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public int getTtlInSecond() {
            return ttlInSecond;
        }

        public void setTtlInSecond(int ttlInSecond) {
            this.ttlInSecond = ttlInSecond;
        }
    }

    private String name;

    private String taskReferenceName;

    private String description;

    private Map<String, Object> inputParameters = new HashMap<>();

    private String type = TaskType.SIMPLE.name();

    private String dynamicTaskNameParam;

    private String caseValueParam;

    private String caseExpression;

    private String scriptExpression;

    public static class WorkflowTaskList {

        public List<WorkflowTask> getTasks() {
            return tasks;
        }

        public void setTasks(List<WorkflowTask> tasks) {
            this.tasks = tasks;
        }

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

    private boolean optional = false;

    private TaskDef taskDefinition;

    private Boolean rateLimited;

    private List<String> defaultExclusiveJoinTask = new LinkedList<>();

    private Boolean asyncComplete = false;

    private String loopCondition;

    private List<WorkflowTask> loopOver = new LinkedList<>();

    private Integer retryCount;

    private String evaluatorType;

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

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the taskReferenceName
     */
    public String getTaskReferenceName() {
        return taskReferenceName;
    }

    /**
     * @param taskReferenceName the taskReferenceName to set
     */
    public void setTaskReferenceName(String taskReferenceName) {
        this.taskReferenceName = taskReferenceName;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the inputParameters
     */
    public Map<String, Object> getInputParameters() {
        return inputParameters;
    }

    /**
     * @param inputParameters the inputParameters to set
     */
    public void setInputParameters(Map<String, Object> inputParameters) {
        this.inputParameters = inputParameters;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    public void setWorkflowTaskType(TaskType type) {
        this.type = type.name();
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the decisionCases
     */
    public Map<String, List<WorkflowTask>> getDecisionCases() {
        return decisionCases;
    }

    /**
     * @param decisionCases the decisionCases to set
     */
    public void setDecisionCases(Map<String, List<WorkflowTask>> decisionCases) {
        this.decisionCases = decisionCases;
    }

    /**
     * @return the defaultCase
     */
    public List<WorkflowTask> getDefaultCase() {
        return defaultCase;
    }

    /**
     * @param defaultCase the defaultCase to set
     */
    public void setDefaultCase(List<WorkflowTask> defaultCase) {
        this.defaultCase = defaultCase;
    }

    /**
     * @return the forkTasks
     */
    public List<List<WorkflowTask>> getForkTasks() {
        return forkTasks;
    }

    /**
     * @param forkTasks the forkTasks to set
     */
    public void setForkTasks(List<List<WorkflowTask>> forkTasks) {
        this.forkTasks = forkTasks;
    }

    /**
     * @return the startDelay in seconds
     */
    public int getStartDelay() {
        return startDelay;
    }

    /**
     * @param startDelay the startDelay to set
     */
    public void setStartDelay(int startDelay) {
        this.startDelay = startDelay;
    }

    /**
     * @return the retryCount
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * @param retryCount the retryCount to set
     */
    public void setRetryCount(final Integer retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * @return the dynamicTaskNameParam
     */
    public String getDynamicTaskNameParam() {
        return dynamicTaskNameParam;
    }

    /**
     * @param dynamicTaskNameParam the dynamicTaskNameParam to set to be used by DYNAMIC tasks
     */
    public void setDynamicTaskNameParam(String dynamicTaskNameParam) {
        this.dynamicTaskNameParam = dynamicTaskNameParam;
    }

    /**
     * @deprecated Use {@link WorkflowTask#getEvaluatorType()} and {@link
     *     WorkflowTask#getExpression()} combination.
     * @return the caseValueParam
     */
    @Deprecated
    public String getCaseValueParam() {
        return caseValueParam;
    }

    @Deprecated
    public String getDynamicForkJoinTasksParam() {
        return dynamicForkJoinTasksParam;
    }

    @Deprecated
    public void setDynamicForkJoinTasksParam(String dynamicForkJoinTasksParam) {
        this.dynamicForkJoinTasksParam = dynamicForkJoinTasksParam;
    }

    public String getDynamicForkTasksParam() {
        return dynamicForkTasksParam;
    }

    public void setDynamicForkTasksParam(String dynamicForkTasksParam) {
        this.dynamicForkTasksParam = dynamicForkTasksParam;
    }

    public String getDynamicForkTasksInputParamName() {
        return dynamicForkTasksInputParamName;
    }

    public void setDynamicForkTasksInputParamName(String dynamicForkTasksInputParamName) {
        this.dynamicForkTasksInputParamName = dynamicForkTasksInputParamName;
    }

    /**
     * @param caseValueParam the caseValueParam to set
     * @deprecated Use {@link WorkflowTask#getEvaluatorType()} and {@link
     *     WorkflowTask#getExpression()} combination.
     */
    @Deprecated
    public void setCaseValueParam(String caseValueParam) {
        this.caseValueParam = caseValueParam;
    }

    /**
     * @return A javascript expression for decision cases. The result should be a scalar value that
     *     is used to decide the case branches.
     * @see #getDecisionCases()
     * @deprecated Use {@link WorkflowTask#getEvaluatorType()} and {@link
     *     WorkflowTask#getExpression()} combination.
     */
    @Deprecated
    public String getCaseExpression() {
        return caseExpression;
    }

    /**
     * @param caseExpression A javascript expression for decision cases. The result should be a
     *     scalar value that is used to decide the case branches.
     * @deprecated Use {@link WorkflowTask#getEvaluatorType()} and {@link
     *     WorkflowTask#getExpression()} combination.
     */
    @Deprecated
    public void setCaseExpression(String caseExpression) {
        this.caseExpression = caseExpression;
    }

    public String getScriptExpression() {
        return scriptExpression;
    }

    public void setScriptExpression(String expression) {
        this.scriptExpression = expression;
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public void setCacheConfig(CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    /**
     * @return the subWorkflow
     */
    public SubWorkflowParams getSubWorkflowParam() {
        return subWorkflowParam;
    }

    /**
     * @param subWorkflow the subWorkflowParam to set
     */
    public void setSubWorkflowParam(SubWorkflowParams subWorkflow) {
        this.subWorkflowParam = subWorkflow;
    }

    /**
     * @return the joinOn
     */
    public List<String> getJoinOn() {
        return joinOn;
    }

    /**
     * @param joinOn the joinOn to set
     */
    public void setJoinOn(List<String> joinOn) {
        this.joinOn = joinOn;
    }

    /**
     * @return the loopCondition
     */
    public String getLoopCondition() {
        return loopCondition;
    }

    /**
     * @param loopCondition the expression to set
     */
    public void setLoopCondition(String loopCondition) {
        this.loopCondition = loopCondition;
    }

    /**
     * @return the loopOver
     */
    public List<WorkflowTask> getLoopOver() {
        return loopOver;
    }

    /**
     * @param loopOver the loopOver to set
     */
    public void setLoopOver(List<WorkflowTask> loopOver) {
        this.loopOver = loopOver;
    }

    /**
     * @return Sink value for the EVENT type of task
     */
    public String getSink() {
        return sink;
    }

    /**
     * @param sink Name of the sink
     */
    public void setSink(String sink) {
        this.sink = sink;
    }

    /**
     * @return whether wait for an external event to complete the task, for EVENT and HTTP tasks
     */
    public Boolean isAsyncComplete() {
        return asyncComplete;
    }

    public void setAsyncComplete(Boolean asyncComplete) {
        this.asyncComplete = asyncComplete;
    }

    /**
     * @return If the task is optional. When set to true, the workflow execution continues even when
     *     the task is in failed status.
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * @return Task definition associated to the Workflow Task
     */
    public TaskDef getTaskDefinition() {
        return taskDefinition;
    }

    /**
     * @param taskDefinition Task definition
     */
    public void setTaskDefinition(TaskDef taskDefinition) {
        this.taskDefinition = taskDefinition;
    }

    /**
     * @param optional when set to true, the task is marked as optional
     */
    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public Boolean getRateLimited() {
        return rateLimited;
    }

    public void setRateLimited(Boolean rateLimited) {
        this.rateLimited = rateLimited;
    }

    public Boolean isRateLimited() {
        return rateLimited != null && rateLimited;
    }

    public List<String> getDefaultExclusiveJoinTask() {
        return defaultExclusiveJoinTask;
    }

    public void setDefaultExclusiveJoinTask(List<String> defaultExclusiveJoinTask) {
        this.defaultExclusiveJoinTask = defaultExclusiveJoinTask;
    }

    /**
     * @return the evaluatorType
     */
    public String getEvaluatorType() {
        return evaluatorType;
    }

    /**
     * @param evaluatorType the evaluatorType to set
     */
    public void setEvaluatorType(String evaluatorType) {
        this.evaluatorType = evaluatorType;
    }

    /**
     * @return An evaluation expression for switch cases evaluated by corresponding evaluator. The
     *     result should be a scalar value that is used to decide the case branches.
     * @see #getDecisionCases()
     */
    public String getExpression() {
        return expression;
    }

    /**
     * @param expression the expression to set
     */
    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getJoinStatus() {
        return joinStatus;
    }

    public void setJoinStatus(String joinStatus) {
        this.joinStatus = joinStatus;
    }

    public boolean isPermissive() {
        return permissive;
    }

    public void setPermissive(boolean permissive) {
        this.permissive = permissive;
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

    public Map<String, List<StateChangeEvent>> getOnStateChange() {
        return onStateChange;
    }

    public void setOnStateChange(Map<String, List<StateChangeEvent>> onStateChange) {
        this.onStateChange = onStateChange;
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
