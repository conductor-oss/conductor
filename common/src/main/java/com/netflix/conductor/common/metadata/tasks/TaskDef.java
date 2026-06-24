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

import com.netflix.conductor.annotations.protogen.ProtoEnum;
import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.common.constraints.OwnerEmailMandatoryConstraint;
import com.netflix.conductor.common.constraints.TaskTimeoutConstraint;
import com.netflix.conductor.common.metadata.Auditable;
import com.netflix.conductor.common.metadata.SchemaDef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ProtoMessage
@TaskTimeoutConstraint
@Valid
public class TaskDef extends Auditable {

    @ProtoEnum
    public enum TimeoutPolicy {
        RETRY,
        TIME_OUT_WF,
        ALERT_ONLY
    }

    @ProtoEnum
    public enum RetryLogic {
        FIXED,
        EXPONENTIAL_BACKOFF,
        LINEAR_BACKOFF
    }

    public static final int ONE_HOUR = 60 * 60;

    /** Unique name identifying the task. The name is unique across */
    @NotEmpty(message = "TaskDef name cannot be null or empty")
    @ProtoField(id = 1)
    private String name;

    @ProtoField(id = 2)
    private String description;

    @ProtoField(id = 3)
    @Min(value = 0, message = "TaskDef retryCount: {value} must be >= 0")
    private int retryCount = 3; // Default

    @ProtoField(id = 4)
    @NotNull
    private long timeoutSeconds;

    @ProtoField(id = 5)
    private List<String> inputKeys = new ArrayList<>();

    @ProtoField(id = 6)
    private List<String> outputKeys = new ArrayList<>();

    @ProtoField(id = 7)
    private TimeoutPolicy timeoutPolicy = TimeoutPolicy.TIME_OUT_WF;

    @ProtoField(id = 8)
    private RetryLogic retryLogic = RetryLogic.FIXED;

    @ProtoField(id = 9)
    private int retryDelaySeconds = 60;

    @ProtoField(id = 10)
    @Min(
            value = 1,
            message =
                    "TaskDef responseTimeoutSeconds: ${validatedValue} should be minimum {value} second")
    private long responseTimeoutSeconds = ONE_HOUR;

    @ProtoField(id = 11)
    private Integer concurrentExecLimit;

    @ProtoField(id = 12)
    private Map<String, Object> inputTemplate = new HashMap<>();

    // This field is deprecated, do not use id 13.
    //	@ProtoField(id = 13)
    //	private Integer rateLimitPerSecond;

    @ProtoField(id = 14)
    private Integer rateLimitPerFrequency;

    @ProtoField(id = 15)
    private Integer rateLimitFrequencyInSeconds;

    @ProtoField(id = 16)
    private String isolationGroupId;

    @ProtoField(id = 17)
    private String executionNameSpace;

    @ProtoField(id = 18)
    @OwnerEmailMandatoryConstraint
    private String ownerEmail;

    @ProtoField(id = 19)
    @Min(value = 0, message = "TaskDef pollTimeoutSeconds: {value} must be >= 0")
    private Integer pollTimeoutSeconds;

    @ProtoField(id = 20)
    @Min(value = 1, message = "Backoff scale factor. Applicable for LINEAR_BACKOFF")
    private Integer backoffScaleFactor = 1;

    /**
     * Maximum delay between retries in seconds. When set to a value greater than 0, the computed
     * delay for {@code EXPONENTIAL_BACKOFF} and {@code LINEAR_BACKOFF} retry logic will be capped
     * at this value. A value of 0 (the default) means no cap is applied.
     *
     * <p>Example: 20 retries with exponential backoff starting at 1 s and capped at 600 s will back
     * off as 1, 2, 4, 8, …, 600, 600, 600, … instead of growing unboundedly.
     */
    @ProtoField(id = 24)
    @Min(value = 0, message = "TaskDef maxRetryDelaySeconds: {value} must be >= 0")
    private int maxRetryDelaySeconds = 0;

    /**
     * Maximum jitter to add to the retry delay. On each retry a random value in {@code [0,
     * backoffJitterMs]} milliseconds is added to the computed delay, spreading retries across time
     * and preventing thundering-herd storms when many tasks fail simultaneously. A value of 0 (the
     * default) disables jitter.
     */
    @ProtoField(id = 25)
    @Min(value = 0, message = "TaskDef backoffJitterMs: {value} must be >= 0")
    private int backoffJitterMs = 0;

    @ProtoField(id = 21)
    private String baseType;

    @ProtoField(id = 22)
    @Min(value = 0, message = "TaskDef totalTimeoutSeconds: {value} must be >= 0")
    private long totalTimeoutSeconds;

    @ProtoField(id = 23)
    private boolean taskStatusListenerEnabled = true;

    @ProtoField(id = 26)
    private SchemaDef inputSchema;

    @ProtoField(id = 27)
    private SchemaDef outputSchema;

    @ProtoField(id = 28)
    private boolean enforceSchema;

    @ProtoField(id = 29)
    private Map<String, Object> metadata = new HashMap<>();

    @ProtoField(id = 30)
    private Integer version;

    public TaskDef() {}

    public TaskDef(String name) {
        this.name = name;
    }

    public TaskDef(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public TaskDef(String name, String description, int retryCount, long timeoutSeconds) {
        this.name = name;
        this.description = description;
        this.retryCount = retryCount;
        this.timeoutSeconds = timeoutSeconds;
    }

    public TaskDef(
            String name,
            String description,
            String ownerEmail,
            int retryCount,
            long timeoutSeconds,
            long responseTimeoutSeconds) {
        this.name = name;
        this.description = description;
        this.ownerEmail = ownerEmail;
        this.retryCount = retryCount;
        this.timeoutSeconds = timeoutSeconds;
        this.responseTimeoutSeconds = responseTimeoutSeconds;
    }

    public Integer getVersion() {
        return version == null ? 1 : version;
    }

    /**
     * @return rateLimitPerFrequency The max number of tasks that will be allowed to be executed per
     *     rateLimitFrequencyInSeconds.
     */
    public Integer getRateLimitPerFrequency() {
        return rateLimitPerFrequency == null ? 0 : rateLimitPerFrequency;
    }

    /**
     * @return rateLimitFrequencyInSeconds: The time bucket that is used to rate limit tasks based
     *     on {@link #getRateLimitPerFrequency()} If null or not set, then defaults to 1 second
     */
    public Integer getRateLimitFrequencyInSeconds() {
        return rateLimitFrequencyInSeconds == null ? 1 : rateLimitFrequencyInSeconds;
    }

    /**
     * @return concurrency limit
     */
    public int concurrencyLimit() {
        return concurrentExecLimit == null ? 0 : concurrentExecLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskDef that = (TaskDef) o;
        return Objects.equals(getName(), that.getName())
                && Objects.equals(getVersion(), that.getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getVersion());
    }

    @Override
    public String toString() {
        return name;
    }
}
