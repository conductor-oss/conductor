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
package com.netflix.conductor.common.metadata.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import lombok.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    /**
     *  True if the task has been retried after failure
     */
    private boolean retried;

    /**
     *  True if the task has completed its lifecycle within conductor (from start to
     * completion to being updated in the datastore)
     */
    private boolean executed;

    private boolean callbackFromWorker = true;

    /**
     * the timeout for task to send response. After this timeout, the task will be re-queued
     */
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

    private long firstStartTime;

    public void setInputData(Map<String, Object> inputData) {
        if (inputData == null) {
            inputData = new HashMap<>();
        }
        this.inputData = inputData;
    }

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

    public void incrementPollCount() {
        ++this.pollCount;
    }

    public String getTaskDefName() {
        if (taskDefName == null || "".equals(taskDefName)) {
            taskDefName = taskType;
        }
        return taskDefName;
    }

    /**
     * @param workflowType the name of the workflow
     * @return the task object with the workflow type set
     */
    public com.netflix.conductor.common.metadata.tasks.Task setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
        return this;
    }

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = StringUtils.substring(reasonForIncompletion, 0, 500);
    }

    public void setOutputData(Map<String, Object> outputData) {
        if (outputData == null) {
            outputData = new HashMap<>();
        }
        this.outputData = outputData;
    }

    public Optional<TaskDef> getTaskDefinition() {
        return Optional.ofNullable(this.getWorkflowTask()).map(WorkflowTask::getTaskDefinition);
    }

    public boolean isLoopOverTask() {
        return iteration > 0;
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
        copy.setFirstStartTime(firstStartTime);
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
        return "Task{" + "taskType='" + taskType + '\'' + ", status=" + status + ", inputData=" + inputData + ", referenceTaskName='" + referenceTaskName + '\'' + ", retryCount=" + retryCount + ", seq=" + seq + ", correlationId='" + correlationId + '\'' + ", pollCount=" + pollCount + ", taskDefName='" + taskDefName + '\'' + ", scheduledTime=" + scheduledTime + ", startTime=" + startTime + ", endTime=" + endTime + ", updateTime=" + updateTime + ", startDelayInSeconds=" + startDelayInSeconds + ", retriedTaskId='" + retriedTaskId + '\'' + ", retried=" + retried + ", executed=" + executed + ", callbackFromWorker=" + callbackFromWorker + ", responseTimeoutSeconds=" + responseTimeoutSeconds + ", workflowInstanceId='" + workflowInstanceId + '\'' + ", workflowType='" + workflowType + '\'' + ", taskId='" + taskId + '\'' + ", reasonForIncompletion='" + reasonForIncompletion + '\'' + ", callbackAfterSeconds=" + callbackAfterSeconds + ", workerId='" + workerId + '\'' + ", outputData=" + outputData + ", workflowTask=" + workflowTask + ", domain='" + domain + '\'' + ", rateLimitPerFrequency=" + rateLimitPerFrequency + ", rateLimitFrequencyInSeconds=" + rateLimitFrequencyInSeconds + ", workflowPriority=" + workflowPriority + ", externalInputPayloadStoragePath='" + externalInputPayloadStoragePath + '\'' + ", externalOutputPayloadStoragePath='" + externalOutputPayloadStoragePath + '\'' + ", isolationGroupId='" + isolationGroupId + '\'' + ", executionNameSpace='" + executionNameSpace + '\'' + ", subworkflowChanged='" + subworkflowChanged + '\'' + ", firstStartTime='" + firstStartTime + '\'' + '}';
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Task task = (Task) o;
        return getRetryCount() == task.getRetryCount() && getSeq() == task.getSeq() && getPollCount() == task.getPollCount() && getScheduledTime() == task.getScheduledTime() && getStartTime() == task.getStartTime() && getEndTime() == task.getEndTime() && getUpdateTime() == task.getUpdateTime() && getStartDelayInSeconds() == task.getStartDelayInSeconds() && isRetried() == task.isRetried() && isExecuted() == task.isExecuted() && isCallbackFromWorker() == task.isCallbackFromWorker() && getResponseTimeoutSeconds() == task.getResponseTimeoutSeconds() && getCallbackAfterSeconds() == task.getCallbackAfterSeconds() && getRateLimitPerFrequency() == task.getRateLimitPerFrequency() && getRateLimitFrequencyInSeconds() == task.getRateLimitFrequencyInSeconds() && Objects.equals(getTaskType(), task.getTaskType()) && getStatus() == task.getStatus() && getIteration() == task.getIteration() && getWorkflowPriority() == task.getWorkflowPriority() && Objects.equals(getInputData(), task.getInputData()) && Objects.equals(getReferenceTaskName(), task.getReferenceTaskName()) && Objects.equals(getCorrelationId(), task.getCorrelationId()) && Objects.equals(getTaskDefName(), task.getTaskDefName()) && Objects.equals(getRetriedTaskId(), task.getRetriedTaskId()) && Objects.equals(getWorkflowInstanceId(), task.getWorkflowInstanceId()) && Objects.equals(getWorkflowType(), task.getWorkflowType()) && Objects.equals(getTaskId(), task.getTaskId()) && Objects.equals(getReasonForIncompletion(), task.getReasonForIncompletion()) && Objects.equals(getWorkerId(), task.getWorkerId()) && Objects.equals(getOutputData(), task.getOutputData()) && Objects.equals(getWorkflowTask(), task.getWorkflowTask()) && Objects.equals(getDomain(), task.getDomain()) && Objects.equals(getExternalInputPayloadStoragePath(), task.getExternalInputPayloadStoragePath()) && Objects.equals(getExternalOutputPayloadStoragePath(), task.getExternalOutputPayloadStoragePath()) && Objects.equals(getIsolationGroupId(), task.getIsolationGroupId()) && Objects.equals(getExecutionNameSpace(), task.getExecutionNameSpace()) && Objects.equals(getParentTaskId(), task.getParentTaskId()) && Objects.equals(getFirstStartTime(), task.getFirstStartTime());
    }

    public int hashCode() {
        return Objects.hash(getTaskType(), getStatus(), getInputData(), getReferenceTaskName(), getWorkflowPriority(), getRetryCount(), getSeq(), getCorrelationId(), getPollCount(), getTaskDefName(), getScheduledTime(), getStartTime(), getEndTime(), getUpdateTime(), getStartDelayInSeconds(), getRetriedTaskId(), isRetried(), isExecuted(), isCallbackFromWorker(), getResponseTimeoutSeconds(), getWorkflowInstanceId(), getWorkflowType(), getTaskId(), getReasonForIncompletion(), getCallbackAfterSeconds(), getWorkerId(), getOutputData(), getWorkflowTask(), getDomain(), getRateLimitPerFrequency(), getRateLimitFrequencyInSeconds(), getExternalInputPayloadStoragePath(), getExternalOutputPayloadStoragePath(), getIsolationGroupId(), getExecutionNameSpace(), getParentTaskId(), getFirstStartTime());
    }
}