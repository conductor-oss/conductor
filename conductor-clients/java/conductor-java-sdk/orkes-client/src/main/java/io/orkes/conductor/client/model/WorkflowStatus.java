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

import java.util.HashMap;
import java.util.Map;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowStatus {

    private String correlationId = null;

    private Map<String, Object> output = null;

    public enum StatusEnum {
        RUNNING("RUNNING"),
        COMPLETED("COMPLETED"),
        FAILED("FAILED"),
        TIMED_OUT("TIMED_OUT"),
        TERMINATED("TERMINATED"),
        PAUSED("PAUSED");

        private final String value;

        StatusEnum(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        public static StatusEnum fromValue(String input) {
            for (StatusEnum b : StatusEnum.values()) {
                if (b.value.equals(input)) {
                    return b;
                }
            }
            return null;
        }
    }

    private StatusEnum status = null;

    private Map<String, Object> variables = null;

    private String workflowId = null;

    public WorkflowStatus correlationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public WorkflowStatus output(Map<String, Object> output) {
        this.output = output;
        return this;
    }

    public WorkflowStatus putOutputItem(String key, Object outputItem) {
        if (this.output == null) {
            this.output = new HashMap<>();
        }
        this.output.put(key, outputItem);
        return this;
    }

    public WorkflowStatus status(StatusEnum status) {
        this.status = status;
        return this;
    }

    public WorkflowStatus variables(Map<String, Object> variables) {
        this.variables = variables;
        return this;
    }

    public WorkflowStatus putVariablesItem(String key, Object variablesItem) {
        if (this.variables == null) {
            this.variables = new HashMap<>();
        }
        this.variables.put(key, variablesItem);
        return this;
    }

    public WorkflowStatus workflowId(String workflowId) {
        this.workflowId = workflowId;
        return this;
    }
}