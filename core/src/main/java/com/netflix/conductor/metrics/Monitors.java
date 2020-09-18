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
package com.netflix.conductor.metrics;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.servo.monitor.BasicStopwatch;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.histogram.PercentileTimer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Viren
 *
 */
public class Monitors {

	private static final Registry registry = Spectator.globalRegistry();

	private static final Map<String, Map<Map<String, String>, Counter>> counters = new ConcurrentHashMap<>();

	private static final Map<String, Map<Map<String, String>, PercentileTimer>> timers = new ConcurrentHashMap<>();

	private static final Map<String, Map<Map<String, String>, Gauge>> gauges = new ConcurrentHashMap<>();

	public static final String classQualifier = "WorkflowMonitor";

	private Monitors() {
	}

	/**
	 *
	 * @param className Name of the class
	 * @param methodName Method name
	 *
	 */
	public static void error(String className, String methodName) {
		getCounter(className, "workflow_server_error", "methodName", methodName).increment();
	}

	public static Stopwatch start(String className, String name, String... additionalTags) {
		return start(getTimer(className, name, additionalTags));
	}

	/**
	 * Increment a counter that is used to measure the rate at which some event
	 * is occurring. Consider a simple queue, counters would be used to measure
	 * things like the rate at which items are being inserted and removed.
	 *
	 * @param className
	 * @param name
	 * @param additionalTags
	 */
	private static void counter(String className, String name, String... additionalTags) {
		getCounter(className, name, additionalTags).increment();
	}

	/**
	 * Set a gauge is a handle to get the current value. Typical examples for
	 * gauges would be the size of a queue or number of threads in the running
	 * state. Since gauges are sampled, there is no information about what might
	 * have occurred between samples.
	 *
	 * @param className
	 * @param name
	 * @param measurement
	 * @param additionalTags
	 */
	private static void gauge(String className, String name, long measurement, String... additionalTags) {
		getGauge(className, name, additionalTags).set(measurement);
	}

	private static Timer getTimer(String className, String name, String... additionalTags) {
		Map<String, String> tags = toMap(className, additionalTags);
		return timers.computeIfAbsent(name, s -> new ConcurrentHashMap<>()).computeIfAbsent(tags, t -> {
			Id id = registry.createId(name, tags);
			return PercentileTimer.get(registry, id);
		});
	}

	private static Counter getCounter(String className, String name, String... additionalTags) {
		Map<String, String> tags = toMap(className, additionalTags);

		return counters.computeIfAbsent(name, s -> new ConcurrentHashMap<>()).computeIfAbsent(tags, t -> {
			Id id = registry.createId(name, tags);
			return registry.counter(id);
		});
	}

	private static Gauge getGauge(String className, String name, String... additionalTags) {
		Map<String, String> tags = toMap(className, additionalTags);

		return gauges.computeIfAbsent(name, s -> new ConcurrentHashMap<>()).computeIfAbsent(tags, t -> {
			Id id = registry.createId(name, tags);
			return registry.gauge(id);
		});
	}

	private static Map<String, String> toMap(String className, String... additionalTags) {
		Map<String, String> tags = new HashMap<>();
		tags.put("class", className);
		for (int j = 0; j < additionalTags.length - 1; j++) {
			String tk = additionalTags[j];
			String tv = "" + additionalTags[j + 1];
			if(!tv.isEmpty()) {
				tags.put(tk, tv);
			}
			j++;
		}
		return tags;
	}

	private static Stopwatch start(Timer sm) {

		Stopwatch sw = new BasicStopwatch() {

			@Override
			public void stop() {
				super.stop();
				long duration = getDuration(TimeUnit.MILLISECONDS);
				sm.record(duration, TimeUnit.MILLISECONDS);
			}

		};
		sw.start();
		return sw;
	}

	public static void recordGauge(String name, long count, String... tags) {
		gauge(classQualifier, name, count, tags);
	}

	public static void recordQueueWaitTime(String taskType, long queueWaitTime) {
		getTimer(classQualifier, "task_queue_wait", "taskType", taskType).record(queueWaitTime, TimeUnit.MILLISECONDS);
	}

	public static void recordTaskExecutionTime(String taskType, long duration, boolean includesRetries, Task.Status status) {
		getTimer(classQualifier, "task_execution", "taskType", taskType, "includeRetries", "" + includesRetries, "status", status.name()).record(duration, TimeUnit.MILLISECONDS);
	}

	public static void recordTaskPollError(String taskType, String domain, String exception) {
		counter(classQualifier, "task_poll_error", "taskType", taskType, "domain", domain, "exception", exception);
	}

	public static void recordTaskPoll(String taskType) {
		counter(classQualifier, "task_poll", "taskType", taskType);
	}

	public static void recordTaskPollCount(String taskType, String domain, int count) {
		getCounter(classQualifier, "task_poll_count", "taskType", taskType, "domain", domain).increment(count);
	}

	public static void recordQueueDepth(String taskType, long size, String ownerApp) {
		gauge(classQualifier, "task_queue_depth", size, "taskType", taskType, "ownerApp", ""+ownerApp);
	}

	public static void recordTaskInProgress(String taskType, long size, String ownerApp) {
		gauge(classQualifier, "task_in_progress", size, "taskType", taskType, "ownerApp", ""+ownerApp);
	}

	public static void recordRunningWorkflows(long count, String name, String version, String ownerApp) {
		gauge(classQualifier, "workflow_running", count, "workflowName", name, "version", version, "ownerApp", ""+ownerApp);
	}

	public static void recordTaskTimeout(String taskType) {
		counter(classQualifier, "task_timeout", "taskType", taskType);
	}

	public static void recordTaskResponseTimeout(String taskType) {
		counter(classQualifier, "task_response_timeout", "taskType", taskType);
	}

	public static void recordTaskPendingTime(String taskType, String workflowType, long duration) {
		gauge(classQualifier, "task_pending_time", duration, "workflowName", workflowType, "taskType", taskType);
	}

	public static void recordWorkflowTermination(String workflowType, WorkflowStatus status, String ownerApp) {
		counter(classQualifier, "workflow_failure", "workflowName", workflowType, "status", status.name(), "ownerApp", ""+ownerApp);
	}

	public static void recordWorkflowStartError(String workflowType, String ownerApp) {
		counter(classQualifier, "workflow_start_error", "workflowName", workflowType, "ownerApp", ""+ownerApp);
	}

	public static void recordUpdateConflict(String taskType, String workflowType, WorkflowStatus status) {
		counter(classQualifier, "task_update_conflict", "workflowName", workflowType, "taskType", taskType, "workflowStatus", status.name());
	}

	public static void recordUpdateConflict(String taskType, String workflowType, Status status) {
		counter(classQualifier, "task_update_conflict", "workflowName", workflowType, "taskType", taskType, "taskStatus", status.name());
	}

	public static void recordTaskUpdateError(String taskType, String workflowType) {
		counter(classQualifier, "task_update_error", "workflowName", workflowType, "taskType", taskType);
	}

	public static void recordWorkflowCompletion(String workflowType, long duration, String ownerApp) {
		getTimer(classQualifier, "workflow_execution", "workflowName", workflowType, "ownerApp", ""+ownerApp).record(duration, TimeUnit.MILLISECONDS);
	}

	public static void recordTaskRateLimited(String taskDefName, int limit) {
		gauge(classQualifier, "task_rate_limited", limit, "taskType", taskDefName);
	}

	public static void recordTaskConcurrentExecutionLimited(String taskDefName, int limit) {
		gauge(classQualifier, "task_concurrent_execution_limited", limit, "taskType", taskDefName);
	}

	public static void recordEventQueueMessagesProcessed(String queueType, String queueName, int count) {
		getCounter(classQualifier, "event_queue_messages_processed", "queueType", queueType, "queueName", queueName).increment(count);
	}

	public static void recordObservableQMessageReceivedErrors(String queueType) {
		counter(classQualifier, "observable_queue_error", "queueType", queueType);
	}

	public static void recordEventQueueMessagesHandled(String queueType, String queueName) {
		counter(classQualifier, "event_queue_messages_handled", "queueType", queueType, "queueName", queueName);
	}

	public static void recordEventQueueMessagesError(String queueType, String queueName) {
		counter(classQualifier, "event_queue_messages_error", "queueType", queueType, "queueName", queueName);
	}

	public static void recordEventExecutionSuccess(String event, String handler, String action) {
		counter(classQualifier, "event_execution_success", "event", event, "handler", handler, "action", action);
	}

	public static void recordEventExecutionError(String event, String handler, String action, String exceptionClazz) {
		counter(classQualifier, "event_execution_error", "event", event, "handler", handler, "action", action, "exception", exceptionClazz);
	}

	public static void recordEventActionError(String action, String entityName, String event) {
		counter(classQualifier, "event_action_error", "action", action, "entityName", entityName, "event", event);
	}

	public static void recordDaoRequests(String dao, String action, String taskType, String workflowType) {
		counter(classQualifier, "dao_requests", "dao", dao, "action", action, "taskType", taskType, "workflowType", workflowType);
	}

	public static void recordDaoEventRequests(String dao, String action, String event) {
		counter(classQualifier, "dao_requests", "dao", dao, "action", action, "event", event);
	}

	public static void recordDaoPayloadSize(String dao, String action, int size) {
	    gauge(classQualifier, "dao_payload_size", size, "dao", dao, "action", action);
    }

	public static void recordDaoPayloadSize(String dao, String action, String taskType, String workflowType, int size) {
		gauge(classQualifier, "dao_payload_size", size, "dao", dao, "action", action, "taskType", taskType, "workflowType", workflowType);
	}

	public static void recordExternalPayloadStorageUsage(String name, String operation, String payloadType) {
		counter(classQualifier, "external_payload_storage_usage", "name", name, "operation", operation, "payloadType", payloadType);
	}

	public static void recordDaoError(String dao, String action) {
		counter(classQualifier, "dao_errors", "dao", dao, "action", action);
	}

	public static void recordAckTaskError(String taskType) {
		counter(classQualifier, "task_ack_error", "taskType", taskType);
	}

	public static void recordESIndexTime(String action, String docType, long val) {
		getTimer(Monitors.classQualifier, action, "docType", docType).record(val, TimeUnit.MILLISECONDS);
	}

	public static void recordWorkerQueueSize(String queueType, int val) {
		gauge(Monitors.classQualifier, "indexing_worker_queue", val, "queueType", queueType);
	}

	public static void recordDiscardedIndexingCount(String queueType) {
		counter(Monitors.classQualifier, "discarded_index_count", "queueType", queueType);
	}

	public static void recordAcquireLockUnsuccessful() {
		counter(classQualifier, "acquire_lock_unsuccessful");
	}

	public static void recordAcquireLockFailure(String exceptionClassName) {
		counter(classQualifier, "acquire_lock_failure", "exceptionType", exceptionClassName);
	}

	public static void recordWorkflowArchived(String workflowType, WorkflowStatus status) {
		counter(classQualifier, "workflow_archived", "workflowName", workflowType, "workflowStatus", status.name());
	}

	public static void recordArchivalDelayQueueSize(int val) {
		gauge(classQualifier, "workflow_archival_delay_queue_size", val);
	}
	public static void recordDiscardedArchivalCount() {
		counter(classQualifier, "discarded_archival_count");
	}

	public static void recordSystemTaskWorkerPollingLimited(String queueName) {
		counter(classQualifier, "system_task_worker_polling_limited", "queueName", queueName);
	}

	public static void recordEventQueuePollSize(String queueType, int val) {
		gauge(Monitors.classQualifier, "event_queue_poll", val, "queueType", queueType);
	}

	public static void recordQueueMessageRepushFromRepairService(String queueName) {
		counter(classQualifier, "queue_message_repushed", "queueName", queueName);
	}
}
