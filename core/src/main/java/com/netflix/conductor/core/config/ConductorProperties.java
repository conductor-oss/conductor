/*
 * Copyright 2020 Netflix, Inc.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.app")
public class ConductorProperties {

    /**
     * Name of the stack within which the app is running. e.g. devint, testintg, staging, prod etc.
     */
    private String stack = "test";

    /**
     * The id with the app has been registered.
     */
    private String appId = "conductor";

    /**
     * The maximum number of threads to be allocated to the executor service threadpool.
     */
    private int executorServiceMaxThreadCount = 50;

    /**
     * The frequency in seconds, at which the workflow sweeper should run to evaluate running workflows.
     */
    private int sweepFrequencySeconds = 30;

    /**
     * Used to enable/disable the workflow sweeper.
     */
    private boolean sweepDisabled = false;

    /**
     * The number of threads to configure the threadpool in the workflow sweeper.
     */
    private int sweeperThreadCount = 5;

    /**
     * The number of threads to configure the threadpool in the event processor.
     */
    private int eventProcessorThreadCount = 2;

    /**
     * Used to enable/disable the indexing of messages within event payloads.
     */
    private boolean eventMessageIndexingEnabled = true;

    /**
     * Used to enable/disable the indexing of event execution results.
     */
    private boolean eventExecutionIndexingEnabled = true;

    /**
     * Used to enable/disable the workflow execution lock.
     */
    private boolean workflowExecutionLockEnabled = false;

    /**
     * The time (in milliseconds) for which the lock is leased for.
     */
    private long lockLeaseTimeMs = 60000;

    /**
     * The time (in milliseconds) for which the thread will block in an attempt to acquire the lock.
     */
    private long lockTimeToTryMs = 500;

    /**
     * The time (in seconds) that is used to consider if a worker is actively polling for a task.
     */
    private int activeWorkerLastPollSecs = 10;

    /**
     * The time (in seconds) for which a task execution will be postponed if being rate limited or concurrent execution
     * limited.
     */
    private int taskExecutionPostponeSeconds = 60;

    /**
     * Used to enable/disable the indexing of task execution logs.
     */
    private boolean taskExecLogIndexingEnabled = true;

    /**
     * Used to enable/disable asynchronous indexing to elasticsearch.
     */
    private boolean asyncIndexingEnabled = false;

    /**
     * The number of threads to be used within the threadpool for system task workers.
     */
    private int systemTaskWorkerThreadCount = 10;

    /**
     * The interval (in seconds) after which a system task will be checked by the system task worker for completion.
     */
    private int systemTaskWorkerCallbackSeconds = 30;

    /**
     * The interval (in seconds) at which system task queues will be polled by the system task workers.
     */
    private int systemTaskWorkerPollInterval = 50;

    /**
     * The namespace for the system task workers to provide instance level isolation.
     */
    private String systemTaskWorkerExecutionNamespace = "";

    /**
     * The number of threads to be used within the threadpool for system task workers in each isolation group.
     */
    private int isolatedSystemTaskWorkerThreadCount = 1;

    /**
     * The max number of system tasks to be polled in a single request.
     */
    private int systemTaskMaxPollCount = 1;

    /**
     * Used to enable/disable the system task workers that execute async system tasks like HTTP, etc.
     */
    private boolean systemTaskWorkersDisabled = false;

    /**
     * The duration of workflow execution which qualifies a workflow as a short-running workflow when async indexing to
     * elasticsearch is enabled.
     */
    private int asyncUpdateShortRunningWorkflowDuration = 30;

    /**
     * The delay with which short-running workflows will be updated in the elasticsearch index when async indexing is
     * enabled.
     */
    private int asyncUpdateDelay = 60;

    /**
     * Used to control the validation for owner email field as mandatory within workflow and task definitions.
     */
    private boolean ownerEmailMandatory = true;

    /**
     * Configuration to enable {@link com.netflix.conductor.core.execution.WorkflowRepairService}, that tries to keep
     * ExecutionDAO and QueueDAO in sync, based on the task or workflow state.
     * <p>
     * This is disabled by default; To enable, the Queueing layer must implement QueueDAO.containsMessage method.
     */
    private boolean workflowRepairServiceEnabled = false;

    /**
     * The number of threads to be usde in Scheduler used for polling events from multiple event queues. By default, a
     * thread count equal to the number of CPU cores is chosen.
     */
    private int eventQueueSchedulerPollThreadCount = Runtime.getRuntime().availableProcessors();

    /**
     * The time interval (in milliseconds) at which the default event queues will be polled.
     */
    private int eventQueuePollIntervalMs = 100;

    /**
     * The number of messages to be polled from a default event queue in a single operation.
     */
    private int eventQueuePollCount = 10;

    /**
     * The timeout (in milliseconds) for the poll operation on the default event queue.
     */
    private int eventQueueLongPollTimeout = 1000;

    /**
     * The threshold of the workflow input payload size in KB beyond which the payload will be stored in {@link
     * com.netflix.conductor.common.utils.ExternalPayloadStorage}.
     */
    private Long workflowInputPayloadSizeThresholdKB = 5120L;

    /**
     * The maximum threshold of the workflow input payload size in KB beyond which input will be rejected and the
     * workflow will be marked as FAILED.
     */
    private Long maxWorkflowInputPayloadSizeThresholdKB = 10240L;

    /**
     * The threshold of the workflow output payload size in KB beyond which the payload will be stored in {@link
     * com.netflix.conductor.common.utils.ExternalPayloadStorage}.
     */
    private Long workflowOutputPayloadSizeThresholdKB = 5120L;

    /**
     * The maximum threshold of the workflow output payload size in KB beyond which output will be rejected and the
     * workflow will be marked as FAILED.
     */
    private Long maxWorkflowOutputPayloadSizeThresholdKB = 10240L;

    /**
     * The threshold of the task input payload size in KB beyond which the payload will be stored in {@link
     * com.netflix.conductor.common.utils.ExternalPayloadStorage}.
     */
    private Long taskInputPayloadSizeThresholdKB = 3072L;

    /**
     * The maximum threshold of the task input payload size in KB beyond which the task input will be rejected and the
     * task will be marked as FAILED_WITH_TERMINAL_ERROR.
     */
    private Long maxTaskInputPayloadSizeThresholdKB = 10240L;

    /**
     * The threshold of the task output payload size in KB beyond which the payload will be stored in {@link
     * com.netflix.conductor.common.utils.ExternalPayloadStorage}.
     */
    private Long taskOutputPayloadSizeThresholdKB = 3072L;

    /**
     * The maximum threshold of the task output payload size in KB beyond which the task input will be rejected and the
     * task will be marked as FAILED_WITH_TERMINAL_ERROR.
     */
    private Long maxTaskOutputPayloadSizeThresholdKB = 10240L;

    /**
     * The maximum threshold of the workflow variables payload size in KB beyond which the task changes will be rejected
     * and the task will be marked as FAILED_WITH_TERMINAL_ERROR.
     */
    private Long maxWorkflowVariablesPayloadSizeThresholdKB = 256L;

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

    public int getSweepFrequencySeconds() {
        return sweepFrequencySeconds;
    }

    public void setSweepFrequencySeconds(int sweepFrequencySeconds) {
        this.sweepFrequencySeconds = sweepFrequencySeconds;
    }

    public boolean isSweepDisabled() {
        return sweepDisabled;
    }

    public void setSweepDisabled(boolean sweepDisabled) {
        this.sweepDisabled = sweepDisabled;
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

    public long getLockLeaseTimeMs() {
        return lockLeaseTimeMs;
    }

    public void setLockLeaseTimeMs(long lockLeaseTimeMs) {
        this.lockLeaseTimeMs = lockLeaseTimeMs;
    }

    public long getLockTimeToTryMs() {
        return lockTimeToTryMs;
    }

    public void setLockTimeToTryMs(long lockTimeToTryMs) {
        this.lockTimeToTryMs = lockTimeToTryMs;
    }

    public int getActiveWorkerLastPollSecs() {
        return activeWorkerLastPollSecs;
    }

    public void setActiveWorkerLastPollSecs(int activeWorkerLastPollSecs) {
        this.activeWorkerLastPollSecs = activeWorkerLastPollSecs;
    }

    public int getTaskExecutionPostponeSeconds() {
        return taskExecutionPostponeSeconds;
    }

    public void setTaskExecutionPostponeSeconds(int taskExecutionPostponeSeconds) {
        this.taskExecutionPostponeSeconds = taskExecutionPostponeSeconds;
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

    public int getSystemTaskWorkerCallbackSeconds() {
        return systemTaskWorkerCallbackSeconds;
    }

    public void setSystemTaskWorkerCallbackSeconds(int systemTaskWorkerCallbackSeconds) {
        this.systemTaskWorkerCallbackSeconds = systemTaskWorkerCallbackSeconds;
    }

    public int getSystemTaskWorkerPollInterval() {
        return systemTaskWorkerPollInterval;
    }

    public void setSystemTaskWorkerPollInterval(int systemTaskWorkerPollInterval) {
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

    public boolean isSystemTaskWorkersDisabled() {
        return systemTaskWorkersDisabled;
    }

    public void setSystemTaskWorkersDisabled(boolean systemTaskWorkersDisabled) {
        this.systemTaskWorkersDisabled = systemTaskWorkersDisabled;
    }

    public int getAsyncUpdateShortRunningWorkflowDuration() {
        return asyncUpdateShortRunningWorkflowDuration;
    }

    public void setAsyncUpdateShortRunningWorkflowDuration(int asyncUpdateShortRunningWorkflowDuration) {
        this.asyncUpdateShortRunningWorkflowDuration = asyncUpdateShortRunningWorkflowDuration;
    }

    public int getAsyncUpdateDelay() {
        return asyncUpdateDelay;
    }

    public void setAsyncUpdateDelay(int asyncUpdateDelay) {
        this.asyncUpdateDelay = asyncUpdateDelay;
    }

    public boolean isOwnerEmailMandatory() {
        return ownerEmailMandatory;
    }

    public void setOwnerEmailMandatory(boolean ownerEmailMandatory) {
        this.ownerEmailMandatory = ownerEmailMandatory;
    }

    public boolean isWorkflowRepairServiceEnabled() {
        return workflowRepairServiceEnabled;
    }

    public void setWorkflowRepairServiceEnabled(boolean workflowRepairServiceEnabled) {
        this.workflowRepairServiceEnabled = workflowRepairServiceEnabled;
    }

    public int getEventQueueSchedulerPollThreadCount() {
        return eventQueueSchedulerPollThreadCount;
    }

    public void setEventQueueSchedulerPollThreadCount(int eventQueueSchedulerPollThreadCount) {
        this.eventQueueSchedulerPollThreadCount = eventQueueSchedulerPollThreadCount;
    }

    public int getEventQueuePollIntervalMs() {
        return eventQueuePollIntervalMs;
    }

    public void setEventQueuePollIntervalMs(int eventQueuePollIntervalMs) {
        this.eventQueuePollIntervalMs = eventQueuePollIntervalMs;
    }

    public int getEventQueuePollCount() {
        return eventQueuePollCount;
    }

    public void setEventQueuePollCount(int eventQueuePollCount) {
        this.eventQueuePollCount = eventQueuePollCount;
    }

    public int getEventQueueLongPollTimeout() {
        return eventQueueLongPollTimeout;
    }

    public void setEventQueueLongPollTimeout(int eventQueueLongPollTimeout) {
        this.eventQueueLongPollTimeout = eventQueueLongPollTimeout;
    }

    public Long getWorkflowInputPayloadSizeThresholdKB() {
        return workflowInputPayloadSizeThresholdKB;
    }

    public void setWorkflowInputPayloadSizeThresholdKB(Long workflowInputPayloadSizeThresholdKB) {
        this.workflowInputPayloadSizeThresholdKB = workflowInputPayloadSizeThresholdKB;
    }

    public Long getMaxWorkflowInputPayloadSizeThresholdKB() {
        return maxWorkflowInputPayloadSizeThresholdKB;
    }

    public void setMaxWorkflowInputPayloadSizeThresholdKB(Long maxWorkflowInputPayloadSizeThresholdKB) {
        this.maxWorkflowInputPayloadSizeThresholdKB = maxWorkflowInputPayloadSizeThresholdKB;
    }

    public Long getWorkflowOutputPayloadSizeThresholdKB() {
        return workflowOutputPayloadSizeThresholdKB;
    }

    public void setWorkflowOutputPayloadSizeThresholdKB(Long workflowOutputPayloadSizeThresholdKB) {
        this.workflowOutputPayloadSizeThresholdKB = workflowOutputPayloadSizeThresholdKB;
    }

    public Long getMaxWorkflowOutputPayloadSizeThresholdKB() {
        return maxWorkflowOutputPayloadSizeThresholdKB;
    }

    public void setMaxWorkflowOutputPayloadSizeThresholdKB(Long maxWorkflowOutputPayloadSizeThresholdKB) {
        this.maxWorkflowOutputPayloadSizeThresholdKB = maxWorkflowOutputPayloadSizeThresholdKB;
    }

    public Long getTaskInputPayloadSizeThresholdKB() {
        return taskInputPayloadSizeThresholdKB;
    }

    public void setTaskInputPayloadSizeThresholdKB(Long taskInputPayloadSizeThresholdKB) {
        this.taskInputPayloadSizeThresholdKB = taskInputPayloadSizeThresholdKB;
    }

    public Long getMaxTaskInputPayloadSizeThresholdKB() {
        return maxTaskInputPayloadSizeThresholdKB;
    }

    public void setMaxTaskInputPayloadSizeThresholdKB(Long maxTaskInputPayloadSizeThresholdKB) {
        this.maxTaskInputPayloadSizeThresholdKB = maxTaskInputPayloadSizeThresholdKB;
    }

    public Long getTaskOutputPayloadSizeThresholdKB() {
        return taskOutputPayloadSizeThresholdKB;
    }

    public void setTaskOutputPayloadSizeThresholdKB(Long taskOutputPayloadSizeThresholdKB) {
        this.taskOutputPayloadSizeThresholdKB = taskOutputPayloadSizeThresholdKB;
    }

    public Long getMaxTaskOutputPayloadSizeThresholdKB() {
        return maxTaskOutputPayloadSizeThresholdKB;
    }

    public void setMaxTaskOutputPayloadSizeThresholdKB(Long maxTaskOutputPayloadSizeThresholdKB) {
        this.maxTaskOutputPayloadSizeThresholdKB = maxTaskOutputPayloadSizeThresholdKB;
    }

    public Long getMaxWorkflowVariablesPayloadSizeThresholdKB() {
        return maxWorkflowVariablesPayloadSizeThresholdKB;
    }

    public void setMaxWorkflowVariablesPayloadSizeThresholdKB(Long maxWorkflowVariablesPayloadSizeThresholdKB) {
        this.maxWorkflowVariablesPayloadSizeThresholdKB = maxWorkflowVariablesPayloadSizeThresholdKB;
    }

    /**
     * @return Returns all the configurations in a map.
     */
    public Map<String, Object> getAll() {
        Map<String, Object> map = new HashMap<>();
        Properties props = System.getProperties();
        props.forEach((key, value) -> map.put(key.toString(), value));
        return map;
    }
}
