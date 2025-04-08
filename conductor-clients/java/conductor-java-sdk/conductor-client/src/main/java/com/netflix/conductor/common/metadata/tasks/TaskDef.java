/*
 * Copyright 2021 Conductor Authors.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.netflix.conductor.common.metadata.Auditable;
import com.netflix.conductor.common.metadata.SchemaDef;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskDef extends Auditable {

    public enum TimeoutPolicy {

        RETRY, TIME_OUT_WF, ALERT_ONLY
    }

    public enum RetryLogic {

        FIXED, EXPONENTIAL_BACKOFF, LINEAR_BACKOFF
    }

    public static final int ONE_HOUR = 60 * 60;

    /**
     * Unique name identifying the task. The name is unique across
     */
    private String name;

    private String description;

    private int // Default
    retryCount = 3;

    private long timeoutSeconds;

    private List<String> inputKeys = new ArrayList<>();

    private List<String> outputKeys = new ArrayList<>();

    private TimeoutPolicy timeoutPolicy = TimeoutPolicy.TIME_OUT_WF;

    private RetryLogic retryLogic = RetryLogic.FIXED;

    private int retryDelaySeconds = 60;

    private long responseTimeoutSeconds = ONE_HOUR;

    private Integer concurrentExecLimit;

    private Map<String, Object> inputTemplate = new HashMap<>();

    // This field is deprecated, do not use id 13.
    //	@ProtoField(id = 13)
    //	private Integer rateLimitPerSecond;
    private Integer rateLimitPerFrequency;

    private Integer rateLimitFrequencyInSeconds;

    private String isolationGroupId;

    private String executionNameSpace;

    private String ownerEmail;

    private Integer pollTimeoutSeconds;

    private Integer backoffScaleFactor = 1;

    private String baseType;

    private SchemaDef inputSchema;

    private SchemaDef outputSchema;

    private boolean enforceSchema;

    public int concurrencyLimit() {
        return concurrentExecLimit == null ? 0 : concurrentExecLimit;
    }

    public String toString() {
        return name;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskDef taskDef = (TaskDef) o;
        return getRetryCount() == taskDef.getRetryCount() && getTimeoutSeconds() == taskDef.getTimeoutSeconds() && getRetryDelaySeconds() == taskDef.getRetryDelaySeconds() && getBackoffScaleFactor() == taskDef.getBackoffScaleFactor() && getResponseTimeoutSeconds() == taskDef.getResponseTimeoutSeconds() && Objects.equals(getName(), taskDef.getName()) && Objects.equals(getDescription(), taskDef.getDescription()) && Objects.equals(getInputKeys(), taskDef.getInputKeys()) && Objects.equals(getOutputKeys(), taskDef.getOutputKeys()) && getTimeoutPolicy() == taskDef.getTimeoutPolicy() && getRetryLogic() == taskDef.getRetryLogic() && Objects.equals(getConcurrentExecLimit(), taskDef.getConcurrentExecLimit()) && Objects.equals(getRateLimitPerFrequency(), taskDef.getRateLimitPerFrequency()) && Objects.equals(getInputTemplate(), taskDef.getInputTemplate()) && Objects.equals(getIsolationGroupId(), taskDef.getIsolationGroupId()) && Objects.equals(getExecutionNameSpace(), taskDef.getExecutionNameSpace()) && Objects.equals(getOwnerEmail(), taskDef.getOwnerEmail()) && Objects.equals(getBaseType(), taskDef.getBaseType()) && Objects.equals(getInputSchema(), taskDef.getInputSchema()) && Objects.equals(getOutputSchema(), taskDef.getOutputSchema());
    }

    public int hashCode() {
        return Objects.hash(getName(), getDescription(), getRetryCount(), getTimeoutSeconds(), getInputKeys(), getOutputKeys(), getTimeoutPolicy(), getRetryLogic(), getRetryDelaySeconds(), getBackoffScaleFactor(), getResponseTimeoutSeconds(), getConcurrentExecLimit(), getRateLimitPerFrequency(), getInputTemplate(), getIsolationGroupId(), getExecutionNameSpace(), getOwnerEmail(), getBaseType(), getInputSchema(), getOutputSchema());
    }
}