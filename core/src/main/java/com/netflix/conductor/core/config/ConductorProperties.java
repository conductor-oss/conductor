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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class ConductorProperties {

    /**
     * Current environment. e.g. test, prod
     */
    @Value("${environment:test}")
    private String environment;

    /**
     * name of the stack under which the app is running. e.g. devint, testintg, staging, prod etc.
     */
    @Value("${STACK:test}")
    private String stack;

    /**
     * APP_ID
     */
    @Value("${APP_ID:conductor}")
    private String appId;

    /**
     * maximum number of threads to be allocated to the executor service threadpool
     */
    @Value("${workflow.executor.service.max.threads:50}")
    private int executorServiceMaxThreads;

    /**
     * time frequency in seconds, at which the workflow sweeper should run to evaluate running workflows
     */
    @Value("${decider.sweep.frequency.seconds:30}")
    private int sweepFrequency;

    /**
     * when set to true, the sweep is disabled
     */
    @Value("${decider.sweep.disable:false}")
    private boolean disableSweep;

    @Value("${workflow.sweeper.thread.count:5}")
    private int sweeperThreadCount;

    /**
     * Number of threads to be used by the event processor
     */
    @Value("${workflow.event.processor.thread.count:2}")
    private int eventProcessorThreadCount;

    /**
     * when set to true, message from the event processing are indexed
     */
    @Value("${workflow.event.message.indexing.enabled:true}")
    private boolean eventMessageIndexingEnabled;

    /**
     * when set to true, event execution results are indexed
     */
    @Value("${workflow.event.execution.indexing.enabled:true}")
    private boolean eventExecutionIndexingEnabled;

    @Value("${workflow.decider.locking.enabled:false}")
    private boolean enableWorkflowExecutionLock;

    @Value("${workflow.locking.lease.time.ms:60000}")
    private long lockLeaseTimeMs;

    @Value("${workflow.locking.time.to.try.ms:500}")
    private long lockTimeToTryMs;

    @Value("${tasks.active.worker.lastpoll:10}")
    private int activeWorkerLastPollSecs;

    @Value("${task.queue.message.postponeSeconds:60}")
    private int queueTaskMessagePostponeSeconds;

    @Value("${task.requeue.timeout:60000}")
    private int taskRequeueTimeout;

    /**
     * if true(default), enables task execution log indexing
     */
    @Value("${workflow.taskExecLog.indexing.enabled:true}")
    private boolean taskExecLogIndexingEnabled;

    /**
     * when set to true, the indexing operation to elasticsearch will be performed asynchronously
     */
    @Value("${async.indexing.enabled:false}")
    private boolean enableAsyncIndexing;

    /**
     * the number of threads to be used within the threadpool for system task workers
     */
    @Value("${workflow.system.task.worker.thread.count:10}")
    private int systemTaskWorkerThreadCount;

    /**
     * the interval (in seconds) after which a system task will be checked for completion
     */
    @Value("${workflow.system.task.worker.callback.seconds:30}")
    private int systemTaskWorkerCallbackSeconds;

    /**
     * the interval (in seconds) at which system task queues will be polled by the system task workers
     */
    @Value("${workflow.system.task.worker.poll.interval:50}")
    private int systemTaskWorkerPollInterval;

    /**
     * the namespace for the system task workers to provide instance level isolation
     */
    @Value("${workflow.system.task.worker.executionNameSpace:}")
    private String systemTaskWorkerExecutionNamespace;

    /**
     * the number of threads to be used within the threadpool for system task workers in each isolation group
     */
    @Value("${workflow.isolated.system.task.worker.thread.count:1}")
    private int systemTaskWorkerIsolatedThreadCount;

    /**
     * the max number of system tasks to poll
     */
    @Value("${workflow.system.task.queue.pollCount:1}")
    private int systemTaskMaxPollCount;

    /**
     * when set to true, the background task workers executing async system tasks (eg HTTP) are disabled
     */
    @Value("${conductor.disable.async.workers:false}")
    private boolean disableAsyncWorkers;

    /**
     * the duration of workflow execution which qualifies a workflow as a short-running workflow for async updating to
     * the index
     */
    @Value("${async.update.short.workflow.duration.seconds:30}")
    private int asyncUpdateShortRunningWorkflowDuration;

    /**
     * the delay with which short-running workflows will be updated in the index
     */
    @Value("${async.update.delay.seconds:60}")
    private int asyncUpdateDelay;

    /**
     * true if owner email is mandatory for task definitions and workflow definitions
     */
    @Value("${workflow.owner.email.mandatory:true}")
    private boolean ownerEmailMandatory;

    /**
     * Configuration to enable {@link com.netflix.conductor.core.execution.WorkflowRepairService}, that tries to keep
     * ExecutionDAO and QueueDAO in sync, based on the task or workflow state.
     * <p>
     * This is disabled by default; To enable, the Queueing layer must implement QueueDAO.containsMessage method.
     */
    @Value("${workflow.repairservice.enabled:false}")
    private boolean workflowRepairServiceEnabled;

    /**
     * the number of threads to be use in Scheduler used for polling events from multiple event queues. By default, a
     * thread count equal to the number of CPU cores is chosen.
     */
    @Value("${workflow.event.queue.scheduler.poll.thread.count:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}")
    private int eventSchedulerPollThreadCount;

    @Value("${workflow.dyno.queues.pollingInterval:100}")
    private int eventQueuePollInterval;

    @Value("${workflow.dyno.queues.pollCount:10}")
    private int eventQueuePollCount;

    @Value("${workflow.dyno.queues.longPollTimeout:1000}")
    private int eventQueueLongPollTimeout;

    /**
     * The threshold of the workflow input payload size in KB beyond which the payload will be stored in {@link
     * com.netflix.conductor.common.utils.ExternalPayloadStorage}
     */
    @Value("${conductor.workflow.input.payload.threshold.kb:5120}")
    private Long workflowInputPayloadSizeThresholdKB;

    /**
     * The maximum threshold of the workflow input payload size in KB beyond which input will be rejected and the
     * workflow will be marked as FAILED
     */
    @Value("${conductor.max.workflow.input.payload.threshold.kb:10240}")
    private Long maxWorkflowInputPayloadSizeThresholdKB;

    /**
     * The threshold of the workflow output payload size in KB beyond which the payload will be stored in {@link
     * com.netflix.conductor.common.utils.ExternalPayloadStorage}
     */
    @Value("${conductor.workflow.output.payload.threshold.kb:5120}")
    private Long workflowOutputPayloadSizeThresholdKB;

    /**
     * The maximum threshold of the workflow output payload size in KB beyond which output will be rejected and the
     * workflow will be marked as FAILED
     */
    @Value("${conductor.max.workflow.output.payload.threshold.kb:10240}")
    private Long maxWorkflowOutputPayloadSizeThresholdKB;

    /**
     * The threshold of the task input payload size in KB beyond which the payload will be stored in {@link
     * com.netflix.conductor.common.utils.ExternalPayloadStorage}
     */
    @Value("${conductor.task.input.payload.threshold.kb:3072}")
    private Long taskInputPayloadSizeThresholdKB;

    /**
     * The maximum threshold of the task input payload size in KB beyond which the task input will be rejected and the
     * task will be marked as FAILED_WITH_TERMINAL_ERROR
     */
    @Value("${conductor.max.task.input.payload.threshold.kb:10240}")
    private Long maxTaskInputPayloadSizeThresholdKB;

    /**
     * The threshold of the task output payload size in KB beyond which the payload will be stored in {@link
     * com.netflix.conductor.common.utils.ExternalPayloadStorage}
     */
    @Value("${conductor.task.output.payload.threshold.kb:3072}")
    private Long taskOutputPayloadSizeThresholdKB;

    /**
     * The maximum threshold of the task output payload size in KB beyond which the task input will be rejected and the
     * task will be marked as FAILED_WITH_TERMINAL_ERROR
     */
    @Value("${conductor.max.task.output.payload.threshold.kb:10240}")
    private Long maxTaskOutputPayloadSizeThresholdKB;

    /**
     * The maximum threshold of the workflow variables payload size in KB beyond which the task changes will be rejected
     * and the task will be marked as FAILED_WITH_TERMINAL_ERROR
     */
    @Value("${conductor.max.workflow.variables.payload.threshold.kb:256}")
    private Long maxWorkflowVariablesPayloadSizeThresholdKB;

    public String getEnvironment() {
        return environment;
    }

    public String getStack() {
        return stack;
    }

    public String getAppId() {
        return appId;
    }

    public int getExecutorServiceMaxThreads() {
        return executorServiceMaxThreads;
    }

    public int getSweepFrequency() {
        return sweepFrequency;
    }

    public boolean disableSweep() {
        return disableSweep;
    }

    public int getSweeperThreadCount() {
        return sweeperThreadCount;
    }

    public int getEventProcessorThreadCount() {
        return eventProcessorThreadCount;
    }

    public boolean isEventMessageIndexingEnabled() {
        return eventMessageIndexingEnabled;
    }

    public boolean isEventExecutionIndexingEnabled() {
        return eventExecutionIndexingEnabled;
    }

    public boolean isWorkflowExecutionLockEnabled() {
        return enableWorkflowExecutionLock;
    }

    public long getLockLeaseTimeMs() {
        return lockLeaseTimeMs;
    }

    public long getLockTimeToTryMs() {
        return lockTimeToTryMs;
    }

    public int getActiveWorkerLastPollSecs() {
        return activeWorkerLastPollSecs;
    }

    public int getQueueTaskMessagePostponeSeconds() {
        return queueTaskMessagePostponeSeconds;
    }

    public int getTaskRequeueTimeout() {
        return taskRequeueTimeout;
    }

    public boolean isTaskExecLogIndexingEnabled() {
        return taskExecLogIndexingEnabled;
    }

    public boolean enableAsyncIndexing() {
        return enableAsyncIndexing;
    }

    public int getSystemTaskWorkerThreadCount() {
        return systemTaskWorkerThreadCount;
    }

    public int getSystemTaskWorkerCallbackSeconds() {
        return systemTaskWorkerCallbackSeconds;
    }

    public int getSystemTaskWorkerPollInterval() {
        return systemTaskWorkerPollInterval;
    }

    public String getSystemTaskWorkerExecutionNamespace() {
        return systemTaskWorkerExecutionNamespace;
    }

    public int getSystemTaskWorkerIsolatedThreadCount() {
        return systemTaskWorkerIsolatedThreadCount;
    }

    public int getSystemTaskMaxPollCount() {
        return systemTaskMaxPollCount;
    }

    public boolean disableAsyncWorkers() {
        return disableAsyncWorkers;
    }

    public int getAsyncUpdateShortRunningWorkflowDuration() {
        return asyncUpdateShortRunningWorkflowDuration;
    }

    public int getAsyncUpdateDelay() {
        return asyncUpdateDelay;
    }

    public boolean isOwnerEmailMandatory() {
        return ownerEmailMandatory;
    }

    public boolean isWorkflowRepairServiceEnabled() {
        return workflowRepairServiceEnabled;
    }

    public int getEventSchedulerPollThreadCount() {
        return eventSchedulerPollThreadCount;
    }

    public int getEventQueuePollInterval() {
        return eventQueuePollInterval;
    }

    public int getEventQueuePollCount() {
        return eventQueuePollCount;
    }

    public int getEventQueueLongPollTimeout() {
        return eventQueueLongPollTimeout;
    }

    public Long getWorkflowInputPayloadSizeThresholdKB() {
        return workflowInputPayloadSizeThresholdKB;
    }

    public Long getMaxWorkflowInputPayloadSizeThresholdKB() {
        return maxWorkflowInputPayloadSizeThresholdKB;
    }

    public Long getWorkflowOutputPayloadSizeThresholdKB() {
        return workflowOutputPayloadSizeThresholdKB;
    }

    public Long getMaxWorkflowOutputPayloadSizeThresholdKB() {
        return maxWorkflowOutputPayloadSizeThresholdKB;
    }

    public Long getTaskInputPayloadSizeThresholdKB() {
        return taskInputPayloadSizeThresholdKB;
    }

    public Long getMaxTaskInputPayloadSizeThresholdKB() {
        return maxTaskInputPayloadSizeThresholdKB;
    }

    public Long getTaskOutputPayloadSizeThresholdKB() {
        return taskOutputPayloadSizeThresholdKB;
    }

    public Long getMaxTaskOutputPayloadSizeThresholdKB() {
        return maxTaskOutputPayloadSizeThresholdKB;
    }

    public Long getMaxWorkflowVariablesPayloadSizeThresholdKB() {
        return maxWorkflowVariablesPayloadSizeThresholdKB;
    }

    //SBMTODO: is this needed (?)

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
