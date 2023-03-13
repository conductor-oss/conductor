/*
 * Copyright 2023 Netflix, Inc.
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
package com.netflix.conductor.common.run;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

public class WorkflowTestRequest extends StartWorkflowRequest {

    // Map of task reference name to mock output for the task
    private Map<String, List<TaskMock>> taskRefToMockOutput = new HashMap<>();

    // If there are sub-workflows inside the workflow
    // The map of task reference name to the mock for the sub-workflow
    private Map<String, WorkflowTestRequest> subWorkflowTestRequest = new HashMap<>();

    public static class TaskMock {
        private TaskResult.Status status = TaskResult.Status.COMPLETED;
        private Map<String, Object> output;
        private long executionTime; // Time in millis for the execution of the task.  Useful for
        // simulating timeout conditions
        private long queueWaitTime; // Time in millis for the wait time in the queue.

        public TaskMock() {}

        public TaskMock(TaskResult.Status status, Map<String, Object> output) {
            this.status = status;
            this.output = output;
        }

        public TaskResult.Status getStatus() {
            return status;
        }

        public void setStatus(TaskResult.Status status) {
            this.status = status;
        }

        public Map<String, Object> getOutput() {
            return output;
        }

        public void setOutput(Map<String, Object> output) {
            this.output = output;
        }

        public long getExecutionTime() {
            return executionTime;
        }

        public void setExecutionTime(long executionTime) {
            this.executionTime = executionTime;
        }

        public long getQueueWaitTime() {
            return queueWaitTime;
        }

        public void setQueueWaitTime(long queueWaitTime) {
            this.queueWaitTime = queueWaitTime;
        }
    }

    public Map<String, List<TaskMock>> getTaskRefToMockOutput() {
        return taskRefToMockOutput;
    }

    public void setTaskRefToMockOutput(Map<String, List<TaskMock>> taskRefToMockOutput) {
        this.taskRefToMockOutput = taskRefToMockOutput;
    }

    public Map<String, WorkflowTestRequest> getSubWorkflowTestRequest() {
        return subWorkflowTestRequest;
    }

    public void setSubWorkflowTestRequest(Map<String, WorkflowTestRequest> subWorkflowTestRequest) {
        this.subWorkflowTestRequest = subWorkflowTestRequest;
    }
}
