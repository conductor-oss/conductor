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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.lang3.StringUtils;

/**
 * Result of the task execution.
 */
public class TaskResult {

    public enum Status {

        IN_PROGRESS, FAILED, FAILED_WITH_TERMINAL_ERROR, COMPLETED
    }

    private String workflowInstanceId;

    private String taskId;

    private String reasonForIncompletion;

    private long callbackAfterSeconds;

    private String workerId;

    private Status status;

    private Map<String, Object> outputData = new HashMap<>();

    private List<TaskExecLog> logs = new CopyOnWriteArrayList<>();

    private String externalOutputPayloadStoragePath;

    private String subWorkflowId;

    private boolean extendLease;

    public TaskResult(Task task) {
        this.workflowInstanceId = task.getWorkflowInstanceId();
        this.taskId = task.getTaskId();
        this.reasonForIncompletion = task.getReasonForIncompletion();
        this.callbackAfterSeconds = task.getCallbackAfterSeconds();
        this.workerId = task.getWorkerId();
        this.outputData = task.getOutputData();
        this.externalOutputPayloadStoragePath = task.getExternalOutputPayloadStoragePath();
        this.subWorkflowId = task.getSubWorkflowId();
        switch(task.getStatus()) {
            case CANCELED:
            case COMPLETED_WITH_ERRORS:
            case TIMED_OUT:
            case SKIPPED:
                this.status = Status.FAILED;
                break;
            case SCHEDULED:
                this.status = Status.IN_PROGRESS;
                break;
            default:
                this.status = Status.valueOf(task.getStatus().name());
                break;
        }
    }

    public TaskResult() {
    }

    /**
     * @return Workflow instance id for which the task result is produced
     */
    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
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
        this.reasonForIncompletion = StringUtils.substring(reasonForIncompletion, 0, 500);
    }

    public long getCallbackAfterSeconds() {
        return callbackAfterSeconds;
    }

    /**
     * When set to non-zero values, the task remains in the queue for the specified seconds before
     * sent back to the worker when polled. Useful for the long running task, where the task is
     * updated as IN_PROGRESS and should not be polled out of the queue for a specified amount of
     * time. (delayed queue implementation)
     *
     * @param callbackAfterSeconds Amount of time in seconds the task should be held in the queue
     *     before giving it to a polling worker.
     */
    public void setCallbackAfterSeconds(long callbackAfterSeconds) {
        this.callbackAfterSeconds = callbackAfterSeconds;
    }

    public String getWorkerId() {
        return workerId;
    }

    /**
     * @param workerId a free form string identifying the worker host. Could be hostname, IP Address
     *     or any other meaningful identifier that can help identify the host/process which executed
     *     the task, in case of troubleshooting.
     */
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    /**
     * @return the status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @param status Status of the task
     *     <p><b>IN_PROGRESS</b>: Use this for long running tasks, indicating the task is still in
     *     progress and should be checked again at a later time. e.g. the worker checks the status
     *     of the job in the DB, while the job is being executed by another process.
     *     <p><b>FAILED, FAILED_WITH_TERMINAL_ERROR, COMPLETED</b>: Terminal statuses for the task.
     *     Use FAILED_WITH_TERMINAL_ERROR when you do not want the task to be retried.
     * @see #setCallbackAfterSeconds(long)
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    public Map<String, Object> getOutputData() {
        return outputData;
    }

    /**
     * @param outputData output data to be set for the task execution result
     */
    public void setOutputData(Map<String, Object> outputData) {
        this.outputData = outputData;
    }

    /**
     * Adds output
     *
     * @param key output field
     * @param value value
     * @return current instance
     */
    public TaskResult addOutputData(String key, Object value) {
        this.outputData.put(key, value);
        return this;
    }

    /**
     * @return Task execution logs
     */
    public List<TaskExecLog> getLogs() {
        return logs;
    }

    /**
     * @param logs Task execution logs
     */
    public void setLogs(List<TaskExecLog> logs) {
        this.logs = logs;
    }

    /**
     * @param log Log line to be added
     * @return Instance of TaskResult
     */
    public TaskResult log(String log) {
        this.logs.add(new TaskExecLog(log));
        return this;
    }

    /**
     * @return the path where the task output is stored in external storage
     */
    public String getExternalOutputPayloadStoragePath() {
        return externalOutputPayloadStoragePath;
    }

    /**
     * @param externalOutputPayloadStoragePath path in the external storage where the task output is
     *     stored
     */
    public void setExternalOutputPayloadStoragePath(String externalOutputPayloadStoragePath) {
        this.externalOutputPayloadStoragePath = externalOutputPayloadStoragePath;
    }

    public String getSubWorkflowId() {
        return subWorkflowId;
    }

    public void setSubWorkflowId(String subWorkflowId) {
        this.subWorkflowId = subWorkflowId;
    }

    public boolean isExtendLease() {
        return extendLease;
    }

    public void setExtendLease(boolean extendLease) {
        this.extendLease = extendLease;
    }

    public String toString() {
        return "TaskResult{" + "workflowInstanceId='" + workflowInstanceId + '\'' + ", taskId='" + taskId + '\'' + ", reasonForIncompletion='" + reasonForIncompletion + '\'' + ", callbackAfterSeconds=" + callbackAfterSeconds + ", workerId='" + workerId + '\'' + ", status=" + status + ", outputData=" + outputData + ", logs=" + logs + ", externalOutputPayloadStoragePath='" + externalOutputPayloadStoragePath + '\'' + ", subWorkflowId='" + subWorkflowId + '\'' + ", extendLease='" + extendLease + '\'' + '}';
    }

    public static TaskResult complete() {
        return newTaskResult(Status.COMPLETED);
    }

    public static TaskResult failed() {
        return newTaskResult(Status.FAILED);
    }

    public static TaskResult failed(String failureReason) {
        TaskResult result = newTaskResult(Status.FAILED);
        result.setReasonForIncompletion(failureReason);
        return result;
    }

    public static TaskResult inProgress() {
        return newTaskResult(Status.IN_PROGRESS);
    }

    public static TaskResult newTaskResult(Status status) {
        TaskResult result = new TaskResult();
        result.setStatus(status);
        return result;
    }
}
