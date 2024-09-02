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
package com.netflix.conductor.common.metadata.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

public class Task {

    public enum Status {

        IN_PROGRESS(false, true, true),
        CANCELED(true, false, false),
        FAILED(true, false, true),
        FAILED_WITH_TERMINAL_ERROR(true, false, // No retries even if retries are configured, the task and the related
                false),
        // workflow should be terminated
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

    /**
     * Time when the task was scheduled
     */
    private long scheduledTime;

    /**
     * Time when the task was first polled
     */
    private long startTime;

    /**
     * Time when the task completed executing
     */
    private long endTime;

    /**
     * Time when the task was last updated
     */
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

    // If the task is an event associated with a parent task, the id of the parent task
    private String parentTaskId;

    public Task() {
    }

    /**
     * @return Type of the task
     * @see TaskType
     */
    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    /**
     * @return Status of the task
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @param status Status of the task
     */
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

    /**
     * @return the referenceTaskName
     */
    public String getReferenceTaskName() {
        return referenceTaskName;
    }

    /**
     * @param referenceTaskName the referenceTaskName to set
     */
    public void setReferenceTaskName(String referenceTaskName) {
        this.referenceTaskName = referenceTaskName;
    }

    /**
     * @return the correlationId
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * @param correlationId the correlationId to set
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * @return the retryCount
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * @param retryCount the retryCount to set
     */
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    /**
     * @return the scheduledTime
     */
    public long getScheduledTime() {
        return scheduledTime;
    }

    /**
     * @param scheduledTime the scheduledTime to set
     */
    public void setScheduledTime(long scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    /**
     * @return the startTime
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * @param startTime the startTime to set
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * @return the endTime
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * @param endTime the endTime to set
     */
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    /**
     * @return the startDelayInSeconds
     */
    public int getStartDelayInSeconds() {
        return startDelayInSeconds;
    }

    /**
     * @param startDelayInSeconds the startDelayInSeconds to set
     */
    public void setStartDelayInSeconds(int startDelayInSeconds) {
        this.startDelayInSeconds = startDelayInSeconds;
    }

    /**
     * @return the retriedTaskId
     */
    public String getRetriedTaskId() {
        return retriedTaskId;
    }

    /**
     * @param retriedTaskId the retriedTaskId to set
     */
    public void setRetriedTaskId(String retriedTaskId) {
        this.retriedTaskId = retriedTaskId;
    }

    /**
     * @return the seq
     */
    public int getSeq() {
        return seq;
    }

    /**
     * @param seq the seq to set
     */
    public void setSeq(int seq) {
        this.seq = seq;
    }

    /**
     * @return the updateTime
     */
    public long getUpdateTime() {
        return updateTime;
    }

    /**
     * @param updateTime the updateTime to set
     */
    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * @return the queueWaitTime
     */
    public long getQueueWaitTime() {
        if (this.startTime > 0 && this.scheduledTime > 0) {
            if (this.updateTime > 0 && getCallbackAfterSeconds() > 0) {
                long waitTime = System.currentTimeMillis() - (this.updateTime + (getCallbackAfterSeconds() * 1000));
                return waitTime > 0 ? waitTime : 0;
            } else {
                return this.startTime - this.scheduledTime;
            }
        }
        return 0L;
    }

    /**
     * @return True if the task has been retried after failure
     */
    public boolean isRetried() {
        return retried;
    }

    /**
     * @param retried the retried to set
     */
    public void setRetried(boolean retried) {
        this.retried = retried;
    }

    /**
     * @return True if the task has completed its lifecycle within conductor (from start to
     * completion to being updated in the datastore)
     */
    public boolean isExecuted() {
        return executed;
    }

    /**
     * @param executed the executed value to set
     */
    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    /**
     * @return No. of times task has been polled
     */
    public int getPollCount() {
        return pollCount;
    }

    public void setPollCount(int pollCount) {
        this.pollCount = pollCount;
    }

    public void incrementPollCount() {
        ++this.pollCount;
    }

    public boolean isCallbackFromWorker() {
        return callbackFromWorker;
    }

    public void setCallbackFromWorker(boolean callbackFromWorker) {
        this.callbackFromWorker = callbackFromWorker;
    }

    /**
     * @return Name of the task definition
     */
    public String getTaskDefName() {
        if (taskDefName == null || "".equals(taskDefName)) {
            taskDefName = taskType;
        }
        return taskDefName;
    }

    /**
     * @param taskDefName Name of the task definition
     */
    public void setTaskDefName(String taskDefName) {
        this.taskDefName = taskDefName;
    }

    /**
     * @return the timeout for task to send response. After this timeout, the task will be re-queued
     */
    public long getResponseTimeoutSeconds() {
        return responseTimeoutSeconds;
    }

    /**
     * @param responseTimeoutSeconds - timeout for task to send response. After this timeout, the
     *                               task will be re-queued
     */
    public void setResponseTimeoutSeconds(long responseTimeoutSeconds) {
        this.responseTimeoutSeconds = responseTimeoutSeconds;
    }

    /**
     * @return the workflowInstanceId
     */
    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    /**
     * @param workflowInstanceId the workflowInstanceId to set
     */
    public void setWorkflowInstanceId(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    /**
     * @param workflowType the name of the workflow
     * @return the task object with the workflow type set
     */
    public com.netflix.conductor.common.metadata.tasks.Task setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
        return this;
    }

    /**
     * @return the taskId
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * @param taskId the taskId to set
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * @return the reasonForIncompletion
     */
    public String getReasonForIncompletion() {
        return reasonForIncompletion;
    }

    /**
     * @param reasonForIncompletion the reasonForIncompletion to set
     */
    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = StringUtils.substring(reasonForIncompletion, 0, 500);
    }

    /**
     * @return the callbackAfterSeconds
     */
    public long getCallbackAfterSeconds() {
        return callbackAfterSeconds;
    }

    /**
     * @param callbackAfterSeconds the callbackAfterSeconds to set
     */
    public void setCallbackAfterSeconds(long callbackAfterSeconds) {
        this.callbackAfterSeconds = callbackAfterSeconds;
    }

    /**
     * @return the workerId
     */
    public String getWorkerId() {
        return workerId;
    }

    /**
     * @param workerId the workerId to set
     */
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    /**
     * @return the outputData
     */
    public Map<String, Object> getOutputData() {
        return outputData;
    }

    /**
     * @param outputData the outputData to set
     */
    public void setOutputData(Map<String, Object> outputData) {
        if (outputData == null) {
            outputData = new HashMap<>();
        }
        this.outputData = outputData;
    }

    /**
     * @return Workflow Task definition
     */
    public WorkflowTask getWorkflowTask() {
        return workflowTask;
    }

    /**
     * @param workflowTask Task definition
     */
    public void setWorkflowTask(WorkflowTask workflowTask) {
        this.workflowTask = workflowTask;
    }

    /**
     * @return the domain
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @param domain the Domain
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    /**
     * @return {@link Optional} containing the task definition if available
     */
    public Optional<TaskDef> getTaskDefinition() {
        return Optional.ofNullable(this.getWorkflowTask()).map(WorkflowTask::getTaskDefinition);
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

    /**
     * @return the external storage path for the task input payload
     */
    public String getExternalInputPayloadStoragePath() {
        return externalInputPayloadStoragePath;
    }

    /**
     * @param externalInputPayloadStoragePath the external storage path where the task input payload
     *                                        is stored
     */
    public void setExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
    }

    /**
     * @return the external storage path for the task output payload
     */
    public String getExternalOutputPayloadStoragePath() {
        return externalOutputPayloadStoragePath;
    }

    /**
     * @param externalOutputPayloadStoragePath the external storage path where the task output
     *                                         payload is stored
     */
    public void setExternalOutputPayloadStoragePath(String externalOutputPayloadStoragePath) {
        this.externalOutputPayloadStoragePath = externalOutputPayloadStoragePath;
    }

    public void setIsolationGroupId(String isolationGroupId) {
        this.isolationGroupId = isolationGroupId;
    }

    public String getIsolationGroupId() {
        return isolationGroupId;
    }

    public String getExecutionNameSpace() {
        return executionNameSpace;
    }

    public void setExecutionNameSpace(String executionNameSpace) {
        this.executionNameSpace = executionNameSpace;
    }

    /**
     * @return the iteration
     */
    public int getIteration() {
        return iteration;
    }

    /**
     * @param iteration iteration
     */
    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public boolean isLoopOverTask() {
        return iteration > 0;
    }

    /**
     * @return the priority defined on workflow
     */
    public int getWorkflowPriority() {
        return workflowPriority;
    }

    /**
     * @param workflowPriority Priority defined for workflow
     */
    public void setWorkflowPriority(int workflowPriority) {
        this.workflowPriority = workflowPriority;
    }

    public boolean isSubworkflowChanged() {
        return subworkflowChanged;
    }

    public void setSubworkflowChanged(boolean subworkflowChanged) {
        this.subworkflowChanged = subworkflowChanged;
    }

    public String getSubWorkflowId() {
        // For backwards compatibility
        if (StringUtils.isNotBlank(subWorkflowId)) {
            return subWorkflowId;
        } else {
            return this.getOutputData() != null && this.getOutputData().get("subWorkflowId") != null ? (String) this.getOutputData().get("subWorkflowId") : this.getInputData() != null ? (String) this.getInputData().get("subWorkflowId") : null;
        }
    }

    public void setSubWorkflowId(String subWorkflowId) {
        this.subWorkflowId = subWorkflowId;
        // For backwards compatibility
        if (this.getOutputData() != null && this.getOutputData().containsKey("subWorkflowId")) {
            this.getOutputData().put("subWorkflowId", subWorkflowId);
        }
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public Task copy() {
        Task copy = new Task();
        copy.setCallbackAfterSeconds(callbackAfterSeconds);
        copy.setCallbackFromWorker(callbackFromWorker);
        copy.setCorrelationId(correlationId);
        copy.setInputData(inputData);
        copy.setOutputData(outputData);
        copy.setReferenceTaskName(referenceTaskName);
        copy.setStartDelayInSeconds(startDelayInSeconds);
        copy.setTaskDefName(taskDefName);
        copy.setTaskType(taskType);
        copy.setWorkflowInstanceId(workflowInstanceId);
        copy.setWorkflowType(workflowType);
        copy.setResponseTimeoutSeconds(responseTimeoutSeconds);
        copy.setStatus(status);
        copy.setRetryCount(retryCount);
        copy.setPollCount(pollCount);
        copy.setTaskId(taskId);
        copy.setWorkflowTask(workflowTask);
        copy.setDomain(domain);
        copy.setRateLimitPerFrequency(rateLimitPerFrequency);
        copy.setRateLimitFrequencyInSeconds(rateLimitFrequencyInSeconds);
        copy.setExternalInputPayloadStoragePath(externalInputPayloadStoragePath);
        copy.setExternalOutputPayloadStoragePath(externalOutputPayloadStoragePath);
        copy.setWorkflowPriority(workflowPriority);
        copy.setIteration(iteration);
        copy.setExecutionNameSpace(executionNameSpace);
        copy.setIsolationGroupId(isolationGroupId);
        copy.setSubWorkflowId(getSubWorkflowId());
        copy.setSubworkflowChanged(subworkflowChanged);
        copy.setParentTaskId(parentTaskId);
        return copy;
    }

    /**
     * @return a deep copy of the task instance To be used inside copy Workflow method to provide a
     * valid deep copied object. Note: This does not copy the following fields:
     * <ul>
     *   <li>retried
     *   <li>updateTime
     *   <li>retriedTaskId
     * </ul>
     */
    public Task deepCopy() {
        Task deepCopy = copy();
        deepCopy.setStartTime(startTime);
        deepCopy.setScheduledTime(scheduledTime);
        deepCopy.setEndTime(endTime);
        deepCopy.setWorkerId(workerId);
        deepCopy.setReasonForIncompletion(reasonForIncompletion);
        deepCopy.setSeq(seq);
        deepCopy.setParentTaskId(parentTaskId);
        return deepCopy;
    }

    public String toString() {
        return "Task{" + "taskType='" + taskType + '\'' + ", status=" + status + ", inputData=" + inputData + ", referenceTaskName='" + referenceTaskName + '\'' + ", retryCount=" + retryCount + ", seq=" + seq + ", correlationId='" + correlationId + '\'' + ", pollCount=" + pollCount + ", taskDefName='" + taskDefName + '\'' + ", scheduledTime=" + scheduledTime + ", startTime=" + startTime + ", endTime=" + endTime + ", updateTime=" + updateTime + ", startDelayInSeconds=" + startDelayInSeconds + ", retriedTaskId='" + retriedTaskId + '\'' + ", retried=" + retried + ", executed=" + executed + ", callbackFromWorker=" + callbackFromWorker + ", responseTimeoutSeconds=" + responseTimeoutSeconds + ", workflowInstanceId='" + workflowInstanceId + '\'' + ", workflowType='" + workflowType + '\'' + ", taskId='" + taskId + '\'' + ", reasonForIncompletion='" + reasonForIncompletion + '\'' + ", callbackAfterSeconds=" + callbackAfterSeconds + ", workerId='" + workerId + '\'' + ", outputData=" + outputData + ", workflowTask=" + workflowTask + ", domain='" + domain + '\'' + ", rateLimitPerFrequency=" + rateLimitPerFrequency + ", rateLimitFrequencyInSeconds=" + rateLimitFrequencyInSeconds + ", workflowPriority=" + workflowPriority + ", externalInputPayloadStoragePath='" + externalInputPayloadStoragePath + '\'' + ", externalOutputPayloadStoragePath='" + externalOutputPayloadStoragePath + '\'' + ", isolationGroupId='" + isolationGroupId + '\'' + ", executionNameSpace='" + executionNameSpace + '\'' + ", subworkflowChanged='" + subworkflowChanged + '\'' + '}';
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Task task = (Task) o;
        return getRetryCount() == task.getRetryCount() && getSeq() == task.getSeq() && getPollCount() == task.getPollCount() && getScheduledTime() == task.getScheduledTime() && getStartTime() == task.getStartTime() && getEndTime() == task.getEndTime() && getUpdateTime() == task.getUpdateTime() && getStartDelayInSeconds() == task.getStartDelayInSeconds() && isRetried() == task.isRetried() && isExecuted() == task.isExecuted() && isCallbackFromWorker() == task.isCallbackFromWorker() && getResponseTimeoutSeconds() == task.getResponseTimeoutSeconds() && getCallbackAfterSeconds() == task.getCallbackAfterSeconds() && getRateLimitPerFrequency() == task.getRateLimitPerFrequency() && getRateLimitFrequencyInSeconds() == task.getRateLimitFrequencyInSeconds() && Objects.equals(getTaskType(), task.getTaskType()) && getStatus() == task.getStatus() && getIteration() == task.getIteration() && getWorkflowPriority() == task.getWorkflowPriority() && Objects.equals(getInputData(), task.getInputData()) && Objects.equals(getReferenceTaskName(), task.getReferenceTaskName()) && Objects.equals(getCorrelationId(), task.getCorrelationId()) && Objects.equals(getTaskDefName(), task.getTaskDefName()) && Objects.equals(getRetriedTaskId(), task.getRetriedTaskId()) && Objects.equals(getWorkflowInstanceId(), task.getWorkflowInstanceId()) && Objects.equals(getWorkflowType(), task.getWorkflowType()) && Objects.equals(getTaskId(), task.getTaskId()) && Objects.equals(getReasonForIncompletion(), task.getReasonForIncompletion()) && Objects.equals(getWorkerId(), task.getWorkerId()) && Objects.equals(getOutputData(), task.getOutputData()) && Objects.equals(getWorkflowTask(), task.getWorkflowTask()) && Objects.equals(getDomain(), task.getDomain()) && Objects.equals(getExternalInputPayloadStoragePath(), task.getExternalInputPayloadStoragePath()) && Objects.equals(getExternalOutputPayloadStoragePath(), task.getExternalOutputPayloadStoragePath()) && Objects.equals(getIsolationGroupId(), task.getIsolationGroupId()) && Objects.equals(getExecutionNameSpace(), task.getExecutionNameSpace()) && Objects.equals(getParentTaskId(), task.getParentTaskId());
    }

    public int hashCode() {
        return Objects.hash(getTaskType(), getStatus(), getInputData(), getReferenceTaskName(), getWorkflowPriority(), getRetryCount(), getSeq(), getCorrelationId(), getPollCount(), getTaskDefName(), getScheduledTime(), getStartTime(), getEndTime(), getUpdateTime(), getStartDelayInSeconds(), getRetriedTaskId(), isRetried(), isExecuted(), isCallbackFromWorker(), getResponseTimeoutSeconds(), getWorkflowInstanceId(), getWorkflowType(), getTaskId(), getReasonForIncompletion(), getCallbackAfterSeconds(), getWorkerId(), getOutputData(), getWorkflowTask(), getDomain(), getRateLimitPerFrequency(), getRateLimitFrequencyInSeconds(), getExternalInputPayloadStoragePath(), getExternalOutputPayloadStoragePath(), getIsolationGroupId(), getExecutionNameSpace(), getParentTaskId());
    }
}
