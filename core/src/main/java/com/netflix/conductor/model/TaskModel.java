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
package com.netflix.conductor.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.google.protobuf.Any;

public class TaskModel {

    public enum Status {
        IN_PROGRESS(false, true, true),
        CANCELED(true, false, false),
        FAILED(true, false, true),
        FAILED_WITH_TERMINAL_ERROR(true, false, false),
        COMPLETED(true, true, true),
        COMPLETED_WITH_ERRORS(true, true, true),
        SCHEDULED(false, true, true),
        TIMED_OUT(true, false, true),
        SKIPPED(true, true, false);

        private final boolean terminal;

        private final boolean successful;

        private final boolean retriable;

        Status(boolean terminal, boolean successful, boolean retriable) {
            this.terminal = terminal;
            this.successful = successful;
            this.retriable = retriable;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public boolean isRetriable() {
            return retriable;
        }
    }

    private String taskType;

    private Status status;

    private Map<String, Object> inputData = new HashMap<>();

    private String referenceTaskName;

    private int retryCount;

    private int seq;

    private String correlationId;

    private int pollCount;

    private String taskDefName;

    /** Time when the task was scheduled */
    private long scheduledTime;

    /** Time when the task was first polled */
    private long startTime;

    /** Time when the task completed executing */
    private long endTime;

    /** Time when the task was last updated */
    private long updateTime;

    private int startDelayInSeconds;

    private String retriedTaskId;

    private boolean retried;

    private boolean executed;

    private boolean callbackFromWorker = true;

    private long responseTimeoutSeconds;

    private String workflowInstanceId;

    private String workflowType;

    private String taskId;

    private String reasonForIncompletion;

    private long callbackAfterSeconds;

    private String workerId;

    private Map<String, Object> outputData = new HashMap<>();

    private WorkflowTask workflowTask;

    private String domain;

    private Any inputMessage;

    private Any outputMessage;

    // id 31 is reserved

    private int rateLimitPerFrequency;

    private int rateLimitFrequencyInSeconds;

    private String externalInputPayloadStoragePath;

    private String externalOutputPayloadStoragePath;

    private int workflowPriority;

    private String executionNameSpace;

    private String isolationGroupId;

    private int iteration;

    private String subWorkflowId;

    /**
     * Use to note that a sub workflow associated with SUB_WORKFLOW task has an action performed on
     * it directly.
     */
    private boolean subworkflowChanged;

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Map<String, Object> getInputData() {
        return inputData;
    }

    public void setInputData(Map<String, Object> inputData) {
        if (inputData == null) {
            inputData = new HashMap<>();
        }
        this.inputData = inputData;
    }

    public String getReferenceTaskName() {
        return referenceTaskName;
    }

    public void setReferenceTaskName(String referenceTaskName) {
        this.referenceTaskName = referenceTaskName;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public int getPollCount() {
        return pollCount;
    }

    public void setPollCount(int pollCount) {
        this.pollCount = pollCount;
    }

    public String getTaskDefName() {
        if (taskDefName == null || "".equals(taskDefName)) {
            taskDefName = taskType;
        }
        return taskDefName;
    }

    public void setTaskDefName(String taskDefName) {
        this.taskDefName = taskDefName;
    }

    public long getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(long scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public int getStartDelayInSeconds() {
        return startDelayInSeconds;
    }

    public void setStartDelayInSeconds(int startDelayInSeconds) {
        this.startDelayInSeconds = startDelayInSeconds;
    }

    public String getRetriedTaskId() {
        return retriedTaskId;
    }

    public void setRetriedTaskId(String retriedTaskId) {
        this.retriedTaskId = retriedTaskId;
    }

    public boolean isRetried() {
        return retried;
    }

    public void setRetried(boolean retried) {
        this.retried = retried;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public boolean isCallbackFromWorker() {
        return callbackFromWorker;
    }

    public void setCallbackFromWorker(boolean callbackFromWorker) {
        this.callbackFromWorker = callbackFromWorker;
    }

    public long getResponseTimeoutSeconds() {
        return responseTimeoutSeconds;
    }

    public void setResponseTimeoutSeconds(long responseTimeoutSeconds) {
        this.responseTimeoutSeconds = responseTimeoutSeconds;
    }

    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getReasonForIncompletion() {
        return reasonForIncompletion;
    }

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = reasonForIncompletion;
    }

    public long getCallbackAfterSeconds() {
        return callbackAfterSeconds;
    }

    public void setCallbackAfterSeconds(long callbackAfterSeconds) {
        this.callbackAfterSeconds = callbackAfterSeconds;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public Map<String, Object> getOutputData() {
        return outputData;
    }

    public void setOutputData(Map<String, Object> outputData) {
        if (outputData == null) {
            outputData = new HashMap<>();
        }
        this.outputData = outputData;
    }

    public WorkflowTask getWorkflowTask() {
        return workflowTask;
    }

    public void setWorkflowTask(WorkflowTask workflowTask) {
        this.workflowTask = workflowTask;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Any getInputMessage() {
        return inputMessage;
    }

    public void setInputMessage(Any inputMessage) {
        this.inputMessage = inputMessage;
    }

    public Any getOutputMessage() {
        return outputMessage;
    }

    public void setOutputMessage(Any outputMessage) {
        this.outputMessage = outputMessage;
    }

    public int getRateLimitPerFrequency() {
        return rateLimitPerFrequency;
    }

    public void setRateLimitPerFrequency(int rateLimitPerFrequency) {
        this.rateLimitPerFrequency = rateLimitPerFrequency;
    }

    public int getRateLimitFrequencyInSeconds() {
        return rateLimitFrequencyInSeconds;
    }

    public void setRateLimitFrequencyInSeconds(int rateLimitFrequencyInSeconds) {
        this.rateLimitFrequencyInSeconds = rateLimitFrequencyInSeconds;
    }

    public String getExternalInputPayloadStoragePath() {
        return externalInputPayloadStoragePath;
    }

    public void setExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
    }

    public String getExternalOutputPayloadStoragePath() {
        return externalOutputPayloadStoragePath;
    }

    public void setExternalOutputPayloadStoragePath(String externalOutputPayloadStoragePath) {
        this.externalOutputPayloadStoragePath = externalOutputPayloadStoragePath;
    }

    public int getWorkflowPriority() {
        return workflowPriority;
    }

    public void setWorkflowPriority(int workflowPriority) {
        this.workflowPriority = workflowPriority;
    }

    public String getExecutionNameSpace() {
        return executionNameSpace;
    }

    public void setExecutionNameSpace(String executionNameSpace) {
        this.executionNameSpace = executionNameSpace;
    }

    public String getIsolationGroupId() {
        return isolationGroupId;
    }

    public void setIsolationGroupId(String isolationGroupId) {
        this.isolationGroupId = isolationGroupId;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public String getSubWorkflowId() {
        // For backwards compatibility
        if (StringUtils.isNotBlank(subWorkflowId)) {
            return subWorkflowId;
        } else {
            return this.getOutputData() != null && this.getOutputData().get("subWorkflowId") != null
                    ? (String) this.getOutputData().get("subWorkflowId")
                    : this.getInputData() != null
                            ? (String) this.getInputData().get("subWorkflowId")
                            : null;
        }
    }

    public void setSubWorkflowId(String subWorkflowId) {
        this.subWorkflowId = subWorkflowId;
        // For backwards compatibility
        if (this.getOutputData() != null && this.getOutputData().containsKey("subWorkflowId")) {
            this.getOutputData().put("subWorkflowId", subWorkflowId);
        }
    }

    public boolean isSubworkflowChanged() {
        return subworkflowChanged;
    }

    public void setSubworkflowChanged(boolean subworkflowChanged) {
        this.subworkflowChanged = subworkflowChanged;
    }

    public void incrementPollCount() {
        ++this.pollCount;
    }

    /** @return {@link Optional} containing the task definition if available */
    public Optional<TaskDef> getTaskDefinition() {
        return Optional.ofNullable(this.getWorkflowTask()).map(WorkflowTask::getTaskDefinition);
    }

    public boolean isLoopOverTask() {
        return iteration > 0;
    }

    /** @return the queueWaitTime */
    public long getQueueWaitTime() {
        if (this.startTime > 0 && this.scheduledTime > 0) {
            if (this.updateTime > 0 && getCallbackAfterSeconds() > 0) {
                long waitTime =
                        System.currentTimeMillis()
                                - (this.updateTime + (getCallbackAfterSeconds() * 1000));
                return waitTime > 0 ? waitTime : 0;
            } else {
                return this.startTime - this.scheduledTime;
            }
        }
        return 0L;
    }

    /** @return a deep copy of the task instance */
    public TaskModel copy() {
        TaskModel copy = new TaskModel();
        BeanUtils.copyProperties(this, copy);
        return copy;
    }

    @Override
    public String toString() {
        return "TaskModel{"
                + "taskType='"
                + taskType
                + '\''
                + ", status="
                + status
                + ", inputData="
                + inputData
                + ", referenceTaskName='"
                + referenceTaskName
                + '\''
                + ", retryCount="
                + retryCount
                + ", seq="
                + seq
                + ", correlationId='"
                + correlationId
                + '\''
                + ", pollCount="
                + pollCount
                + ", taskDefName='"
                + taskDefName
                + '\''
                + ", scheduledTime="
                + scheduledTime
                + ", startTime="
                + startTime
                + ", endTime="
                + endTime
                + ", updateTime="
                + updateTime
                + ", startDelayInSeconds="
                + startDelayInSeconds
                + ", retriedTaskId='"
                + retriedTaskId
                + '\''
                + ", retried="
                + retried
                + ", executed="
                + executed
                + ", callbackFromWorker="
                + callbackFromWorker
                + ", responseTimeoutSeconds="
                + responseTimeoutSeconds
                + ", workflowInstanceId='"
                + workflowInstanceId
                + '\''
                + ", workflowType='"
                + workflowType
                + '\''
                + ", taskId='"
                + taskId
                + '\''
                + ", reasonForIncompletion='"
                + reasonForIncompletion
                + '\''
                + ", callbackAfterSeconds="
                + callbackAfterSeconds
                + ", workerId='"
                + workerId
                + '\''
                + ", outputData="
                + outputData
                + ", workflowTask="
                + workflowTask
                + ", domain='"
                + domain
                + '\''
                + ", inputMessage="
                + inputMessage
                + ", outputMessage="
                + outputMessage
                + ", rateLimitPerFrequency="
                + rateLimitPerFrequency
                + ", rateLimitFrequencyInSeconds="
                + rateLimitFrequencyInSeconds
                + ", externalInputPayloadStoragePath='"
                + externalInputPayloadStoragePath
                + '\''
                + ", externalOutputPayloadStoragePath='"
                + externalOutputPayloadStoragePath
                + '\''
                + ", workflowPriority="
                + workflowPriority
                + ", executionNameSpace='"
                + executionNameSpace
                + '\''
                + ", isolationGroupId='"
                + isolationGroupId
                + '\''
                + ", iteration="
                + iteration
                + ", subWorkflowId='"
                + subWorkflowId
                + '\''
                + ", subworkflowChanged="
                + subworkflowChanged
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskModel taskModel = (TaskModel) o;
        return getRetryCount() == taskModel.getRetryCount()
                && getSeq() == taskModel.getSeq()
                && getPollCount() == taskModel.getPollCount()
                && getScheduledTime() == taskModel.getScheduledTime()
                && getStartTime() == taskModel.getStartTime()
                && getEndTime() == taskModel.getEndTime()
                && getUpdateTime() == taskModel.getUpdateTime()
                && getStartDelayInSeconds() == taskModel.getStartDelayInSeconds()
                && isRetried() == taskModel.isRetried()
                && isExecuted() == taskModel.isExecuted()
                && isCallbackFromWorker() == taskModel.isCallbackFromWorker()
                && getResponseTimeoutSeconds() == taskModel.getResponseTimeoutSeconds()
                && getCallbackAfterSeconds() == taskModel.getCallbackAfterSeconds()
                && getRateLimitPerFrequency() == taskModel.getRateLimitPerFrequency()
                && getRateLimitFrequencyInSeconds() == taskModel.getRateLimitFrequencyInSeconds()
                && getWorkflowPriority() == taskModel.getWorkflowPriority()
                && getIteration() == taskModel.getIteration()
                && isSubworkflowChanged() == taskModel.isSubworkflowChanged()
                && Objects.equals(getTaskType(), taskModel.getTaskType())
                && getStatus() == taskModel.getStatus()
                && Objects.equals(getInputData(), taskModel.getInputData())
                && Objects.equals(getReferenceTaskName(), taskModel.getReferenceTaskName())
                && Objects.equals(getCorrelationId(), taskModel.getCorrelationId())
                && Objects.equals(getTaskDefName(), taskModel.getTaskDefName())
                && Objects.equals(getRetriedTaskId(), taskModel.getRetriedTaskId())
                && Objects.equals(getWorkflowInstanceId(), taskModel.getWorkflowInstanceId())
                && Objects.equals(getWorkflowType(), taskModel.getWorkflowType())
                && Objects.equals(getTaskId(), taskModel.getTaskId())
                && Objects.equals(getReasonForIncompletion(), taskModel.getReasonForIncompletion())
                && Objects.equals(getWorkerId(), taskModel.getWorkerId())
                && Objects.equals(getOutputData(), taskModel.getOutputData())
                && Objects.equals(getWorkflowTask(), taskModel.getWorkflowTask())
                && Objects.equals(getDomain(), taskModel.getDomain())
                && Objects.equals(getInputMessage(), taskModel.getInputMessage())
                && Objects.equals(getOutputMessage(), taskModel.getOutputMessage())
                && Objects.equals(
                        getExternalInputPayloadStoragePath(),
                        taskModel.getExternalInputPayloadStoragePath())
                && Objects.equals(
                        getExternalOutputPayloadStoragePath(),
                        taskModel.getExternalOutputPayloadStoragePath())
                && Objects.equals(getExecutionNameSpace(), taskModel.getExecutionNameSpace())
                && Objects.equals(getIsolationGroupId(), taskModel.getIsolationGroupId())
                && Objects.equals(getSubWorkflowId(), taskModel.getSubWorkflowId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getTaskType(),
                getStatus(),
                getInputData(),
                getReferenceTaskName(),
                getRetryCount(),
                getSeq(),
                getCorrelationId(),
                getPollCount(),
                getTaskDefName(),
                getScheduledTime(),
                getStartTime(),
                getEndTime(),
                getUpdateTime(),
                getStartDelayInSeconds(),
                getRetriedTaskId(),
                isRetried(),
                isExecuted(),
                isCallbackFromWorker(),
                getResponseTimeoutSeconds(),
                getWorkflowInstanceId(),
                getWorkflowType(),
                getTaskId(),
                getReasonForIncompletion(),
                getCallbackAfterSeconds(),
                getWorkerId(),
                getOutputData(),
                getWorkflowTask(),
                getDomain(),
                getInputMessage(),
                getOutputMessage(),
                getRateLimitPerFrequency(),
                getRateLimitFrequencyInSeconds(),
                getExternalInputPayloadStoragePath(),
                getExternalOutputPayloadStoragePath(),
                getWorkflowPriority(),
                getExecutionNameSpace(),
                getIsolationGroupId(),
                getIteration(),
                getSubWorkflowId(),
                isSubworkflowChanged());
    }
}
