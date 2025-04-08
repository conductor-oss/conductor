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
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
     * @param log Log line to be added
     * @return Instance of TaskResult
     */
    public TaskResult log(String log) {
        this.logs.add(new TaskExecLog(log));
        return this;
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