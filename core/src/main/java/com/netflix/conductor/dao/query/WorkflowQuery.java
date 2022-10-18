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
import com.netflix.conductor.model.WorkflowModel;

import java.util.Map;

public class WorkflowQuery {

    private final String workflowId;

    private final PayloadType payloadType;

    private final WorkflowModel workflowModel;

    private final Map<String, Object> payload;

    private WorkflowQuery(Builder builder) {
        this.workflowId = builder.workflowId;
        this.payloadType = builder.payloadType;
        this.workflowModel = builder.workflowModel;
        this.payload = builder.payload;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public static class Builder {
        private String workflowId;
        private PayloadType payloadType;
        private WorkflowModel workflowModel;
        private Map<String, Object> payload;

        public Builder(String workflowId, PayloadType payloadType) {
            this.workflowId = workflowId;
            this.payloadType = payloadType;
        }

        public Builder withWorkflowModel(WorkflowModel workflowModel) {
            this.workflowModel = workflowModel;
            return this;
        }

        public Builder withPayload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public WorkflowQuery build() {
            return new WorkflowQuery(this);
        }
    }
}
