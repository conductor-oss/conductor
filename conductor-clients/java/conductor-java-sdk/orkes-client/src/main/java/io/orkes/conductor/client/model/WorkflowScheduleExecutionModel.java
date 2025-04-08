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
package io.orkes.conductor.client.model;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowScheduleExecutionModel {

    private String executionId = null;

    private Long executionTime = null;

    private String reason = null;

    private String scheduleName = null;

    private Long scheduledTime = null;

    private String stackTrace = null;

    private StartWorkflowRequest startWorkflowRequest = null;

    public enum StateEnum {
        POLLED("POLLED"),
        FAILED("FAILED"),
        EXECUTED("EXECUTED");

        private final String value;

        StateEnum(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        public static StateEnum fromValue(String input) {
            for (StateEnum b : StateEnum.values()) {
                if (b.value.equals(input)) {
                    return b;
                }
            }
            return null;
        }
    }

    private StateEnum state = null;

    private String workflowId = null;

    private String workflowName = null;

}