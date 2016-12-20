/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.netflix.conductor.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.servo.monitor.BasicStopwatch;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.histogram.PercentileTimer;

/**
 * @author Viren
 *
 */
public class Monitors {

	private static Registry registry = Spectator.globalRegistry();

	private static Map<String, Map<Map<String, String>, Counter>> counters = new ConcurrentHashMap<>();

	private static Map<String, Map<Map<String, String>, PercentileTimer>> timers = new ConcurrentHashMap<>();

	private static Map<String, Map<Map<String, String>, AtomicLong>> gauges = new ConcurrentHashMap<>();

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
		getGauge(className, name, additionalTags).getAndSet(measurement);
	}

	public static Timer getTimer(String className, String name, String... additionalTags) {
		Map<String, String> tags = toMap(className, additionalTags);
		tags.put("unit", TimeUnit.SECONDS.name());
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

	private static AtomicLong getGauge(String className, String name, String... additionalTags) {
		Map<String, String> tags = toMap(className, additionalTags);

		return gauges.computeIfAbsent(name, s -> new ConcurrentHashMap<>()).computeIfAbsent(tags, t -> {
			Id id = registry.createId(name, tags);
			return registry.gauge(id, new AtomicLong(0));
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

	public static void recordQueueWaitTime(String taskType, long queueWaitTime) {
		getTimer(classQualifier, "task_queue_wait", "taskType", taskType).record(queueWaitTime, TimeUnit.MILLISECONDS);
	}

	public static void recordTaskExecutionTime(String taskType, long duration, boolean includesRetries, Task.Status status) {
		getTimer(classQualifier, "task_execution", "taskType", taskType, "includeRetries", "" + includesRetries, "status", status.name()).record(duration, TimeUnit.MILLISECONDS);
	}

	public static void recordTaskPoll(String taskType) {
		counter(classQualifier, "task_poll", "taskType", taskType);
	}

	public static void recordQueueDepth(String taskType, long size, String ownerApp) {
		gauge(classQualifier, "task_queue_depth", size, "taskType", taskType, "ownerApp", ownerApp);
	}

	public static void recordRunningWorkflows(long count, String name, String version, String ownerApp) {
		gauge(classQualifier, "workflow_running", count, "workflowName", name, "version", version, "ownerApp", ""+ownerApp);

	}

	public static void recordTaskTimeout(String taskType) {
		counter(classQualifier, "task_timeout", "taskType", taskType);
	}

	public static void recordWorkflowTermination(String workflowType, WorkflowStatus status) {
		counter(classQualifier, "workflow_failure", "workflowName", workflowType, "status", status.name());
	}

	public static void recordWorkflowStartError(String workflowType) {
		counter(classQualifier, "workflow_start_error", "workflowName", workflowType);
	}

	public static void recordUpdateConflict(String taskType, String workflowType, WorkflowStatus status) {
		counter(classQualifier, "task_update_conflict", "workflowName", workflowType, "taskType", taskType, "workflowStatus", status.name());
	}

	public static void recordUpdateConflict(String taskType, String workflowType, Status status) {
		counter(classQualifier, "task_update_conflict", "workflowName", workflowType, "taskType", taskType, "workflowStatus", status.name());
	}
}