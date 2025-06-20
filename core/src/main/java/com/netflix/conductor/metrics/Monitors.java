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
package com.netflix.conductor.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.contribs.metrics.MetricsCollector;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Monitors {
    public static final String NO_DOMAIN = "NO_DOMAIN";

    private static final MeterRegistry registry = MetricsCollector.getMeterRegistry();

    private static final double[] percentiles = new double[] {0.5, 0.75, 0.90, 0.95, 0.99};
    private static final Map<String, AtomicDouble> gauges = new ConcurrentHashMap<>();
    private static final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private static final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private static final Map<String, DistributionSummary> distributionSummaries =
            new ConcurrentHashMap<>();

    private Monitors() {}

    public static Counter getCounter(String name, String... tags) {
        String key = name + Arrays.toString(tags);
        return counters.computeIfAbsent(
                key, s -> Counter.builder(name).tags(toTags(tags)).register(registry));
    }

    public static Timer getTimer(String name, String... tags) {
        String key = name + Arrays.toString(tags);
        return timers.computeIfAbsent(
                key,
                s ->
                        Timer.builder(name)
                                .tags(toTags(tags))
                                .publishPercentiles(percentiles)
                                .register(registry));
    }

    public static DistributionSummary distributionSummary(String name, String... tags) {
        String key = name + Arrays.toString(tags);
        return distributionSummaries.computeIfAbsent(
                key,
                s ->
                        DistributionSummary.builder(name)
                                .tags(toTags(tags))
                                .publishPercentileHistogram()
                                .register(registry));
    }

    public static AtomicDouble gauge(String name, String... tags) {
        String key = name + Arrays.toString(tags);

        return gauges.computeIfAbsent(
                key,
                s -> {
                    AtomicDouble value = new AtomicDouble(0);
                    Gauge.builder(name, () -> value).tags(toTags(tags)).register(registry);
                    return value;
                });
    }

    private static Iterable<Tag> toTags(String... kv) {
        List<Tag> tags = new ArrayList<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            String key = kv[i];
            String value = kv[i + 1];
            if (key == null || value == null) {
                continue;
            }
            Tag tag = new ImmutableTag(key, value);
            tags.add(tag);
        }
        return tags;
    }

    /**
     * Increment a counter that is used to measure the rate at which some event is occurring.
     * Consider a simple queue, counters would be used to measure things like the rate at which
     * items are being inserted and removed.
     *
     * @param name
     * @param additionalTags
     */
    private static void counter(String name, String... additionalTags) {
        getCounter(name, additionalTags).increment();
    }

    /**
     * Set a gauge is a handle to get the current value. Typical examples for gauges would be the
     * size of a queue or number of threads in the running state. Since gauges are sampled, there is
     * no information about what might have occurred between samples.
     *
     * @param name
     * @param measurement
     * @param additionalTags
     */
    private static void gauge(String name, long measurement, String... additionalTags) {
        gauge(name, additionalTags).set(measurement);
    }

    /**
     * @param className Name of the class
     * @param methodName Method name
     */
    public static void error(String className, String methodName) {
        getCounter("workflow_server_error", "class", className, "methodName", methodName)
                .increment();
    }

    public static void recordGauge(String name, long count) {
        gauge(name, count);
    }

    public static void recordQueueWaitTime(String taskType, long queueWaitTime) {
        getTimer("task_queue_wait", "taskType", taskType)
                .record(queueWaitTime, TimeUnit.MILLISECONDS);
    }

    public static void recordTaskExecutionTime(
            String taskType, long duration, boolean includesRetries, TaskModel.Status status) {
        getTimer(
                        "task_execution",
                        "taskType",
                        taskType,
                        "includeRetries",
                        "" + includesRetries,
                        "status",
                        status.name())
                .record(duration, TimeUnit.MILLISECONDS);
    }

    public static void recordWorkflowDecisionTime(long duration) {
        getTimer("workflow_decision").record(duration, TimeUnit.MILLISECONDS);
    }

    public static void recordTaskPollError(String taskType, String exception) {
        recordTaskPollError(taskType, NO_DOMAIN, exception);
    }

    public static void recordTaskPollError(String taskType, String domain, String exception) {
        counter("task_poll_error", "taskType", taskType, "domain", domain, "exception", exception);
    }

    public static void recordTaskPoll(String taskType) {
        counter("task_poll", "taskType", taskType);
    }

    public static void recordTaskPollCount(String taskType, int count) {
        recordTaskPollCount(taskType, NO_DOMAIN, count);
    }

    public static void recordTaskPollCount(String taskType, String domain, int count) {
        getCounter("task_poll_count", "taskType", taskType, "domain", "" + domain).increment(count);
    }

    public static void recordQueueDepth(String taskType, long size, String ownerApp) {
        gauge(
                "task_queue_depth",
                size,
                "taskType",
                taskType,
                "ownerApp",
                StringUtils.defaultIfBlank(ownerApp, "unknown"));
    }

    public static void recordEventQueueDepth(String queueType, long size) {
        gauge("event_queue_depth", size, "queueType", queueType);
    }

    public static void recordTaskInProgress(String taskType, long size, String ownerApp) {
        gauge(
                "task_in_progress",
                size,
                "taskType",
                taskType,
                "ownerApp",
                StringUtils.defaultIfBlank(ownerApp, "unknown"));
    }

    public static void recordRunningWorkflows(long count, String name, String ownerApp) {
        gauge(
                "workflow_running",
                count,
                "workflowName",
                name,
                "ownerApp",
                StringUtils.defaultIfBlank(ownerApp, "unknown"));
    }

    public static void recordNumTasksInWorkflow(long count, String name, String version) {
        distributionSummary("tasks_in_workflow", "workflowName", name, "version", version)
                .record(count);
    }

    public static void recordTaskTimeout(String taskType) {
        counter("task_timeout", "taskType", taskType);
    }

    public static void recordTaskResponseTimeout(String taskType) {
        counter("task_response_timeout", "taskType", taskType);
    }

    public static void recordTaskPendingTime(String taskType, String workflowType, long duration) {
        gauge("task_pending_time", duration, "workflowName", workflowType, "taskType", taskType);
    }

    public static void recordWorkflowTermination(
            String workflowType, WorkflowModel.Status status, String ownerApp) {
        counter(
                "workflow_failure",
                "workflowName",
                workflowType,
                "status",
                status.name(),
                "ownerApp",
                StringUtils.defaultIfBlank(ownerApp, "unknown"));
    }

    public static void recordWorkflowStartSuccess(
            String workflowType, String version, String ownerApp) {
        counter(
                "workflow_start_success",
                "workflowName",
                workflowType,
                "version",
                version,
                "ownerApp",
                StringUtils.defaultIfBlank(ownerApp, "unknown"));
    }

    public static void recordWorkflowStartError(String workflowType, String ownerApp) {
        counter(
                "workflow_start_error",
                "workflowName",
                workflowType,
                "ownerApp",
                StringUtils.defaultIfBlank(ownerApp, "unknown"));
    }

    public static void recordUpdateConflict(
            String taskType, String workflowType, WorkflowModel.Status status) {
        counter(
                "task_update_conflict",
                "workflowName",
                workflowType,
                "taskType",
                taskType,
                "workflowStatus",
                status.name());
    }

    public static void recordUpdateConflict(
            String taskType, String workflowType, TaskModel.Status status) {
        counter(
                "task_update_conflict",
                "workflowName",
                workflowType,
                "taskType",
                taskType,
                "taskStatus",
                status.name());
    }

    public static void recordTaskUpdateError(String taskType, String workflowType) {
        counter("task_update_error", "workflowName", workflowType, "taskType", taskType);
    }

    public static void recordTaskExtendLeaseError(String taskType, String workflowType) {
        counter("task_extendLease_error", "workflowName", workflowType, "taskType", taskType);
    }

    public static void recordTaskQueueOpError(String taskType, String workflowType) {
        counter("task_queue_op_error", "workflowName", workflowType, "taskType", taskType);
    }

    public static void recordWorkflowCompletion(
            String workflowType, long duration, String ownerApp) {
        getTimer(
                        "workflow_execution",
                        "workflowName",
                        workflowType,
                        "ownerApp",
                        StringUtils.defaultIfBlank(ownerApp, "unknown"))
                .record(duration, TimeUnit.MILLISECONDS);
    }

    public static void recordUnackTime(String workflowType, long duration) {
        getTimer("workflow_unack", "workflowName", workflowType)
                .record(duration, TimeUnit.MILLISECONDS);
    }

    public static void recordTaskRateLimited(String taskDefName, int limit) {
        gauge("task_rate_limited", limit, "taskType", taskDefName);
    }

    public static void recordTaskConcurrentExecutionLimited(String taskDefName, int limit) {
        gauge("task_concurrent_execution_limited", limit, "taskType", taskDefName);
    }

    public static void recordEventQueueMessagesProcessed(
            String queueType, String queueName, int count) {
        getCounter("event_queue_messages_processed", "queueType", queueType, "queueName", queueName)
                .increment(count);
    }

    public static void recordObservableQMessageReceivedErrors(String queueType) {
        counter("observable_queue_error", "queueType", queueType);
    }

    public static void recordEventQueueMessagesHandled(String queueType, String queueName) {
        counter("event_queue_messages_handled", "queueType", queueType, "queueName", queueName);
    }

    public static void recordEventQueueMessagesError(String queueType, String queueName) {
        counter("event_queue_messages_error", "queueType", queueType, "queueName", queueName);
    }

    public static void recordEventExecutionSuccess(String event, String handler, String action) {
        counter("event_execution_success", "event", event, "handler", handler, "action", action);
    }

    public static void recordEventExecutionError(
            String event, String handler, String action, String exceptionClazz) {
        counter(
                "event_execution_error",
                "event",
                event,
                "handler",
                handler,
                "action",
                action,
                "exception",
                exceptionClazz);
    }

    public static void recordEventActionError(String action, String entityName, String event) {
        counter("event_action_error", "action", action, "entityName", entityName, "event", event);
    }

    public static void recordDaoRequests(
            String dao, String action, String taskType, String workflowType) {
        counter(
                "dao_requests",
                "dao",
                dao,
                "action",
                action,
                "taskType",
                StringUtils.defaultIfBlank(taskType, "unknown"),
                "workflowType",
                StringUtils.defaultIfBlank(workflowType, "unknown"));
    }

    public static void recordDaoEventRequests(String dao, String action, String event) {
        counter("dao_event_requests", "dao", dao, "action", action, "event", event);
    }

    public static void recordDaoPayloadSize(
            String dao, String action, String taskType, String workflowType, int size) {
        gauge(
                "dao_payload_size",
                size,
                "dao",
                dao,
                "action",
                action,
                "taskType",
                StringUtils.defaultIfBlank(taskType, "unknown"),
                "workflowType",
                StringUtils.defaultIfBlank(workflowType, "unknown"));
    }

    public static void recordExternalPayloadStorageUsage(
            String name, String operation, String payloadType) {
        counter(
                "external_payload_storage_usage",
                "name",
                name,
                "operation",
                operation,
                "payloadType",
                payloadType);
    }

    public static void recordDaoError(String dao, String action) {
        counter("dao_errors", "dao", dao, "action", action);
    }

    public static void recordAckTaskError(String taskType) {
        counter("task_ack_error", "taskType", taskType);
    }

    public static void recordESIndexTime(String action, String docType, long val) {
        getTimer(action, "docType", docType).record(val, TimeUnit.MILLISECONDS);
    }

    public static void recordWorkerQueueSize(String queueType, int val) {
        gauge("indexing_worker_queue", val, "queueType", queueType);
    }

    public static void recordDiscardedIndexingCount(String queueType) {
        counter("discarded_index_count", "queueType", queueType);
    }

    public static void recordAcquireLockUnsuccessful() {
        counter("acquire_lock_unsuccessful");
    }

    public static void recordAcquireLockFailure(String exceptionClassName) {
        counter("acquire_lock_failure", "exceptionType", exceptionClassName);
    }

    public static void recordWorkflowArchived(String workflowType, WorkflowModel.Status status) {
        counter("workflow_archived", "workflowName", workflowType, "workflowStatus", status.name());
    }

    public static void recordArchivalDelayQueueSize(int val) {
        gauge("workflow_archival_delay_queue_size", val);
    }

    public static void recordDiscardedArchivalCount() {
        counter("discarded_archival_count");
    }

    public static void recordSystemTaskWorkerPollingLimited(String queueName) {
        counter("system_task_worker_polling_limited", "queueName", queueName);
    }

    public static void recordEventQueuePollSize(String queueType, int val) {
        gauge("event_queue_poll", val, "queueType", queueType);
    }

    public static void recordQueueMessageRepushFromRepairService(String queueName) {
        counter("queue_message_repushed", "queueName", queueName);
    }

    public static void recordTaskExecLogSize(int val) {
        gauge("task_exec_log_size", val);
    }
}
