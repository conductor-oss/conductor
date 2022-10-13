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
package com.netflix.conductor.dao.query;

import com.netflix.conductor.dao.ExecutionDAO.PayloadType;

public class TaskQuery {

    private final String workflowId;
    private final String taskReferenceName;
    private final String taskId;
    private final PayloadType payloadType;

    private TaskQuery(Builder builder) {
        this.workflowId = builder.workflowId;
        this.taskReferenceName = builder.taskReferenceName;
        this.taskId = builder.taskId;
        this.payloadType = builder.payloadType;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getTaskReferenceName() {
        return taskReferenceName;
    }

    public String getTaskId() {
        return taskId;
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public static class Builder {
        private String workflowId;
        private String taskReferenceName;
        private String taskId;
        private PayloadType payloadType;

        public Builder(String workflowId, String taskReferenceName) {
            this.workflowId = workflowId;
            this.taskReferenceName = taskReferenceName;
        }

        public Builder withTaskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder withPayloadType(PayloadType payloadType) {
            this.payloadType = payloadType;
            return this;
        }

        public TaskQuery build() {
            return new TaskQuery(this);
        }
    }
}
