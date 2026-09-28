/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.common.metadata.agent;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InjectTaskRequest {
    private String taskDefName;
    private String referenceTaskName;
    private String type; // "SIMPLE" or "SUB_WORKFLOW"
    private Map<String, Object> inputData;
    private String status; // expected: "IN_PROGRESS" (informational only)
    private SubWorkflowParam subWorkflowParam;

    @Data
    @NoArgsConstructor
    public static class SubWorkflowParam {
        private String name;
        private Integer version;
        private String executionId; // pre-created tracking execution ID
    }
}
