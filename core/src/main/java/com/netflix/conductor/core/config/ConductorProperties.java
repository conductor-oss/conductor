/*
 * Copyright 2021 Netflix, Inc.
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
package com.netflix.conductor.core.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

@ConfigurationProperties("conductor.app")
public class ConductorProperties {

    /**
     * Name of the stack within which the app is running. e.g. devint, testintg, staging, prod etc.
     */
    private String stack = "test";

    /** The id with the app has been registered. */
    private String appId = "conductor";

    /** The maximum number of threads to be allocated to the executor service threadpool. */
    private int executorServiceMaxThreadCount = 50;

    /** The timeout duration to set when a workflow is pushed to the decider queue. */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration workflowOffsetTimeout = Duration.ofSeconds(30);

    /** The number of threads to use to do background sweep on active workflows. */
    private int sweeperThreadCount = Runtime.getRuntime().availableProcessors() * 2;

    /** The number of threads to configure the threadpool in the event processor. */
    private int eventProcessorThreadCount = 2;

    /** Used to enable/disable the indexing of messages within event payloads. */
    private boolean eventMessageIndexingEnabled = true;

    /** Used to enable/disable the indexing of event execution results. */
    private boolean eventExecutionIndexingEnabled = true;

    /** Used to enable/disable the workflow execution lock. */
    private boolean workflowExecutionLockEnabled = false;

    /** The time (in milliseconds) for which the lock is leased for. */
    private Duration lockLeaseTime = Duration.ofMillis(60000);

    /**
     * The time (in milliseconds) for which the thread will block in an attempt to acquire the lock.
     */
    private Duration lockTimeToTry = Duration.ofMillis(500);

    /**
     * The time (in seconds) that is used to consider if a worker is actively polling for a task.
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration activeWorkerLastPollTimeout = Duration.ofSeconds(10);

    /**
     * The time (in seconds) for which a task execution will be postponed if being rate limited or
     * concurrent execution limited.
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration taskExecutionPostponeDuration = Duration.ofSeconds(60);

    /** Used to enable/disable the indexing of task execution logs. */
    private boolean taskExecLogIndexingEnabled = true;

    /** Used to enable/disable asynchronous indexing to elasticsearch. */
    private boolean asyncIndexingEnabled = false;

    /** The number of threads to be used within the threadpool for system task workers. */
    private int systemTaskWorkerThreadCount = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * The interval (in seconds) after which a system task will be checked by the system task worker
     * for completion.
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration systemTaskWorkerCallbackDuration = Duration.ofSeconds(30);

    /**
     * The interval (in milliseconds) at which system task queues will be polled by the system task
     * workers.
     */
    private Duration systemTaskWorkerPollInterval = Duration.ofMillis(50);

    /** The namespace for the system task workers to provide instance level isolation. */
    private String systemTaskWorkerExecutionNamespace = "";

    /**
     * The number of threads to be used within the threadpool for system task workers in each
     * isolation group.
     */
    private int isolatedSystemTaskWorkerThreadCount = 1;

    /** The max number of system tasks to be polled in a single request. */
    private int systemTaskMaxPollCount = 1;

    /**
     * The duration of workflow execution which qualifies a workflow as a short-running workflow
     * when async indexing to elasticsearch is enabled.
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration asyncUpdateShortRunningWorkflowDuration = Duration.ofSeconds(30);

    /**
     * The delay with which short-running workflows will be updated in the elasticsearch index when
     * async indexing is enabled.
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration asyncUpdateDelay = Duration.ofSeconds(60);

    /**
     * Used to control the validation for owner email field as mandatory within workflow and task
     * definitions.
     */
    private boolean ownerEmailMandatory = true;

    /**
     * The number of threads to be usde in Scheduler used for polling events from multiple event
     * queues. By default, a thread count equal to the number of CPU cores is chosen.
     */
    private int eventQueueSchedulerPollThreadCount = Runtime.getRuntime().availableProcessors();

    /** The time interval (in milliseconds) at which the default event queues will be polled. */
    private Duration eventQueuePollInterval = Duration.ofMillis(100);

    /** The number of messages to be polled from a default event queue in a single operation. */
    private int eventQueuePollCount = 10;

    /** The timeout (in milliseconds) for the poll operation on the default event queue. */
    private Duration eventQueueLongPollTimeout = Duration.ofMillis(1000);

    /**
     * The threshold of the workflow input payload size in KB beyond which the payload will be
     * stored in {@link com.netflix.conductor.common.utils.ExternalPayloadStorage}.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize workflowInputPayloadSizeThreshold = DataSize.ofKilobytes(5120L);

    /**
     * The maximum threshold of the workflow input payload size in KB beyond which input will be
     * rejected and the workflow will be marked as FAILED.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize maxWorkflowInputPayloadSizeThreshold = DataSize.ofKilobytes(10240L);

    /**
     * The threshold of the workflow output payload size in KB beyond which the payload will be
     * stored in {@link com.netflix.conductor.common.utils.ExternalPayloadStorage}.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize workflowOutputPayloadSizeThreshold = DataSize.ofKilobytes(5120L);

    /**
     * The maximum threshold of the workflow output payload size in KB beyond which output will be
     * rejected and the workflow will be marked as FAILED.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize maxWorkflowOutputPayloadSizeThreshold = DataSize.ofKilobytes(10240L);

    /**
     * The threshold of the task input payload size in KB beyond which the payload will be stored in
     * {@link com.netflix.conductor.common.utils.ExternalPayloadStorage}.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize taskInputPayloadSizeThreshold = DataSize.ofKilobytes(3072L);

    /**
     * The maximum threshold of the task input payload size in KB beyond which the task input will
     * be rejected and the task will be marked as FAILED_WITH_TERMINAL_ERROR.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize maxTaskInputPayloadSizeThreshold = DataSize.ofKilobytes(10240L);

    /**
     * The threshold of the task output payload size in KB beyond which the payload will be stored
     * in {@link com.netflix.conductor.common.utils.ExternalPayloadStorage}.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize taskOutputPayloadSizeThreshold = DataSize.ofKilobytes(3072L);

    /**
     * The maximum threshold of the task output payload size in KB beyond which the task input will
     * be rejected and the task will be marked as FAILED_WITH_TERMINAL_ERROR.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize maxTaskOutputPayloadSizeThreshold = DataSize.ofKilobytes(10240L);

    /**
     * The maximum threshold of the workflow variables payload size in KB beyond which the task
     * changes will be rejected and the task will be marked as FAILED_WITH_TERMINAL_ERROR.
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize maxWorkflowVariablesPayloadSizeThreshold = DataSize.ofKilobytes(256L);

    public String getStack() {
        return stack;
    }

    public void setStack(String stack) {
        this.stack = stack;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public int getExecutorServiceMaxThreadCount() {
        return executorServiceMaxThreadCount;
    }

    public void setExecutorServiceMaxThreadCount(int executorServiceMaxThreadCount) {
        this.executorServiceMaxThreadCount = executorServiceMaxThreadCount;
    }

    public Duration getWorkflowOffsetTimeout() {
        return workflowOffsetTimeout;
    }

    public void setWorkflowOffsetTimeout(Duration workflowOffsetTimeout) {
        this.workflowOffsetTimeout = workflowOffsetTimeout;
    }

    public int getSweeperThreadCount() {
        return sweeperThreadCount;
    }

    public void setSweeperThreadCount(int sweeperThreadCount) {
        this.sweeperThreadCount = sweeperThreadCount;
    }

    public int getEventProcessorThreadCount() {
        return eventProcessorThreadCount;
    }

    public void setEventProcessorThreadCount(int eventProcessorThreadCount) {
        this.eventProcessorThreadCount = eventProcessorThreadCount;
    }

    public boolean isEventMessageIndexingEnabled() {
        return eventMessageIndexingEnabled;
    }

    public void setEventMessageIndexingEnabled(boolean eventMessageIndexingEnabled) {
        this.eventMessageIndexingEnabled = eventMessageIndexingEnabled;
    }

    public boolean isEventExecutionIndexingEnabled() {
        return eventExecutionIndexingEnabled;
    }

    public void setEventExecutionIndexingEnabled(boolean eventExecutionIndexingEnabled) {
        this.eventExecutionIndexingEnabled = eventExecutionIndexingEnabled;
    }

    public boolean isWorkflowExecutionLockEnabled() {
        return workflowExecutionLockEnabled;
    }

    public void setWorkflowExecutionLockEnabled(boolean workflowExecutionLockEnabled) {
        this.workflowExecutionLockEnabled = workflowExecutionLockEnabled;
    }

    public Duration getLockLeaseTime() {
        return lockLeaseTime;
    }

    public void setLockLeaseTime(Duration lockLeaseTime) {
        this.lockLeaseTime = lockLeaseTime;
    }

    public Duration getLockTimeToTry() {
        return lockTimeToTry;
    }

    public void setLockTimeToTry(Duration lockTimeToTry) {
        this.lockTimeToTry = lockTimeToTry;
    }

    public Duration getActiveWorkerLastPollTimeout() {
        return activeWorkerLastPollTimeout;
    }

    public void setActiveWorkerLastPollTimeout(Duration activeWorkerLastPollTimeout) {
        this.activeWorkerLastPollTimeout = activeWorkerLastPollTimeout;
    }

    public Duration getTaskExecutionPostponeDuration() {
        return taskExecutionPostponeDuration;
    }

    public void setTaskExecutionPostponeDuration(Duration taskExecutionPostponeDuration) {
        this.taskExecutionPostponeDuration = taskExecutionPostponeDuration;
    }

    public boolean isTaskExecLogIndexingEnabled() {
        return taskExecLogIndexingEnabled;
    }

    public void setTaskExecLogIndexingEnabled(boolean taskExecLogIndexingEnabled) {
        this.taskExecLogIndexingEnabled = taskExecLogIndexingEnabled;
    }

    public boolean isAsyncIndexingEnabled() {
        return asyncIndexingEnabled;
    }

    public void setAsyncIndexingEnabled(boolean asyncIndexingEnabled) {
        this.asyncIndexingEnabled = asyncIndexingEnabled;
    }

    public int getSystemTaskWorkerThreadCount() {
        return systemTaskWorkerThreadCount;
    }

    public void setSystemTaskWorkerThreadCount(int systemTaskWorkerThreadCount) {
        this.systemTaskWorkerThreadCount = systemTaskWorkerThreadCount;
    }

    public Duration getSystemTaskWorkerCallbackDuration() {
        return systemTaskWorkerCallbackDuration;
    }

    public void setSystemTaskWorkerCallbackDuration(Duration systemTaskWorkerCallbackDuration) {
        this.systemTaskWorkerCallbackDuration = systemTaskWorkerCallbackDuration;
    }

    public Duration getSystemTaskWorkerPollInterval() {
        return systemTaskWorkerPollInterval;
    }

    public void setSystemTaskWorkerPollInterval(Duration systemTaskWorkerPollInterval) {
        this.systemTaskWorkerPollInterval = systemTaskWorkerPollInterval;
    }

    public String getSystemTaskWorkerExecutionNamespace() {
        return systemTaskWorkerExecutionNamespace;
    }

    public void setSystemTaskWorkerExecutionNamespace(String systemTaskWorkerExecutionNamespace) {
        this.systemTaskWorkerExecutionNamespace = systemTaskWorkerExecutionNamespace;
    }

    public int getIsolatedSystemTaskWorkerThreadCount() {
        return isolatedSystemTaskWorkerThreadCount;
    }

    public void setIsolatedSystemTaskWorkerThreadCount(int isolatedSystemTaskWorkerThreadCount) {
        this.isolatedSystemTaskWorkerThreadCount = isolatedSystemTaskWorkerThreadCount;
    }

    public int getSystemTaskMaxPollCount() {
        return systemTaskMaxPollCount;
    }

    public void setSystemTaskMaxPollCount(int systemTaskMaxPollCount) {
        this.systemTaskMaxPollCount = systemTaskMaxPollCount;
    }

    public Duration getAsyncUpdateShortRunningWorkflowDuration() {
        return asyncUpdateShortRunningWorkflowDuration;
    }

    public void setAsyncUpdateShortRunningWorkflowDuration(
            Duration asyncUpdateShortRunningWorkflowDuration) {
        this.asyncUpdateShortRunningWorkflowDuration = asyncUpdateShortRunningWorkflowDuration;
    }

    public Duration getAsyncUpdateDelay() {
        return asyncUpdateDelay;
    }

    public void setAsyncUpdateDelay(Duration asyncUpdateDelay) {
        this.asyncUpdateDelay = asyncUpdateDelay;
    }

    public boolean isOwnerEmailMandatory() {
        return ownerEmailMandatory;
    }

    public void setOwnerEmailMandatory(boolean ownerEmailMandatory) {
        this.ownerEmailMandatory = ownerEmailMandatory;
    }

    public int getEventQueueSchedulerPollThreadCount() {
        return eventQueueSchedulerPollThreadCount;
    }

    public void setEventQueueSchedulerPollThreadCount(int eventQueueSchedulerPollThreadCount) {
        this.eventQueueSchedulerPollThreadCount = eventQueueSchedulerPollThreadCount;
    }

    public Duration getEventQueuePollInterval() {
        return eventQueuePollInterval;
    }

    public void setEventQueuePollInterval(Duration eventQueuePollInterval) {
        this.eventQueuePollInterval = eventQueuePollInterval;
    }

    public int getEventQueuePollCount() {
        return eventQueuePollCount;
    }

    public void setEventQueuePollCount(int eventQueuePollCount) {
        this.eventQueuePollCount = eventQueuePollCount;
    }

    public Duration getEventQueueLongPollTimeout() {
        return eventQueueLongPollTimeout;
    }

    public void setEventQueueLongPollTimeout(Duration eventQueueLongPollTimeout) {
        this.eventQueueLongPollTimeout = eventQueueLongPollTimeout;
    }

    public DataSize getWorkflowInputPayloadSizeThreshold() {
        return workflowInputPayloadSizeThreshold;
    }

    public void setWorkflowInputPayloadSizeThreshold(DataSize workflowInputPayloadSizeThreshold) {
        this.workflowInputPayloadSizeThreshold = workflowInputPayloadSizeThreshold;
    }

    public DataSize getMaxWorkflowInputPayloadSizeThreshold() {
        return maxWorkflowInputPayloadSizeThreshold;
    }

    public void setMaxWorkflowInputPayloadSizeThreshold(
            DataSize maxWorkflowInputPayloadSizeThreshold) {
        this.maxWorkflowInputPayloadSizeThreshold = maxWorkflowInputPayloadSizeThreshold;
    }

    public DataSize getWorkflowOutputPayloadSizeThreshold() {
        return workflowOutputPayloadSizeThreshold;
    }

    public void setWorkflowOutputPayloadSizeThreshold(DataSize workflowOutputPayloadSizeThreshold) {
        this.workflowOutputPayloadSizeThreshold = workflowOutputPayloadSizeThreshold;
    }

    public DataSize getMaxWorkflowOutputPayloadSizeThreshold() {
        return maxWorkflowOutputPayloadSizeThreshold;
    }

    public void setMaxWorkflowOutputPayloadSizeThreshold(
            DataSize maxWorkflowOutputPayloadSizeThreshold) {
        this.maxWorkflowOutputPayloadSizeThreshold = maxWorkflowOutputPayloadSizeThreshold;
    }

    public DataSize getTaskInputPayloadSizeThreshold() {
        return taskInputPayloadSizeThreshold;
    }

    public void setTaskInputPayloadSizeThreshold(DataSize taskInputPayloadSizeThreshold) {
        this.taskInputPayloadSizeThreshold = taskInputPayloadSizeThreshold;
    }

    public DataSize getMaxTaskInputPayloadSizeThreshold() {
        return maxTaskInputPayloadSizeThreshold;
    }

    public void setMaxTaskInputPayloadSizeThreshold(DataSize maxTaskInputPayloadSizeThreshold) {
        this.maxTaskInputPayloadSizeThreshold = maxTaskInputPayloadSizeThreshold;
    }

    public DataSize getTaskOutputPayloadSizeThreshold() {
        return taskOutputPayloadSizeThreshold;
    }

    public void setTaskOutputPayloadSizeThreshold(DataSize taskOutputPayloadSizeThreshold) {
        this.taskOutputPayloadSizeThreshold = taskOutputPayloadSizeThreshold;
    }

    public DataSize getMaxTaskOutputPayloadSizeThreshold() {
        return maxTaskOutputPayloadSizeThreshold;
    }

    public void setMaxTaskOutputPayloadSizeThreshold(DataSize maxTaskOutputPayloadSizeThreshold) {
        this.maxTaskOutputPayloadSizeThreshold = maxTaskOutputPayloadSizeThreshold;
    }

    public DataSize getMaxWorkflowVariablesPayloadSizeThreshold() {
        return maxWorkflowVariablesPayloadSizeThreshold;
    }

    public void setMaxWorkflowVariablesPayloadSizeThreshold(
            DataSize maxWorkflowVariablesPayloadSizeThreshold) {
        this.maxWorkflowVariablesPayloadSizeThreshold = maxWorkflowVariablesPayloadSizeThreshold;
    }

    /** @return Returns all the configurations in a map. */
    public Map<String, Object> getAll() {
        Map<String, Object> map = new HashMap<>();
        Properties props = System.getProperties();
        props.forEach((key, value) -> map.put(key.toString(), value));
        return map;
    }
}
