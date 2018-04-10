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
package com.netflix.conductor.client.task;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Joiner;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.MonitorRegistry;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.BasicStopwatch;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.MonitorConfig.Builder;
import com.netflix.servo.monitor.StatsMonitor;
import com.netflix.servo.monitor.StatsTimer;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.stats.StatsConfig;

/**
 * @author Viren
 *
 */
public class WorkflowTaskMetrics {

	private static final String TASK_TYPE = "taskType";
	private static final String EXCEPTION = "exception";

	private static final String TASK_EXECUTION_QUEUE_FULL = "task_execution_queue_full";
	private static final String TASK_POLL_ERROR = "task_poll_error";
	private static final String TASK_PAUSED = "task_paused";
	private static final String TASK_EXECUTE_ERROR = "task_execute_error";
	private static final String TASK_ACK_FAILED = "task_ack_failed";
	private static final String TASK_ACK_ERROR = "task_ack_error";
	private static final String TASK_UPDATE_ERROR = "task_update_error";
	private static final String TASK_POLL_COUNTER = "task_poll_counter";
	private static final String TASK_EXECUTE_TIME = "task_execute_time";
	private static final String TASK_POLL_TIME = "task_poll_time";

	private static StatsConfig statsConfig = new StatsConfig.Builder().withPublishCount(true).withPublishMax(true).withPublishMean(true).withPublishMin(true).withPublishTotal(true).build();

	private static MonitorRegistry registry = DefaultMonitorRegistry.getInstance();

	private static ConcurrentHashMap<String, StatsTimer> monitors = new ConcurrentHashMap<>();

	private static ConcurrentHashMap<String, BasicCounter> errors = new ConcurrentHashMap<>();

	private static final String className = WorkflowTaskMetrics.class.getSimpleName();

	private WorkflowTaskMetrics() {

	}

	private static Stopwatch startTimer(String name, String taskType) {
		return start(getTimer(name, TASK_TYPE, taskType));
	}

	private static StatsTimer getTimer(String name, String...additionalTags) {
		String key = className + "." + name + "." + Joiner.on(",").join(additionalTags);
		return monitors.computeIfAbsent(key, k -> {
			Builder builder = MonitorConfig.builder(name)
                    .withTag("class", className)
                    .withTag("unit", TimeUnit.MILLISECONDS.name());
			for(int j = 0; j < additionalTags.length-1; j++) {
				String tagKey = additionalTags[j];
				String tagValue = additionalTags[j+1];
				builder.withTag(tagKey, tagValue);
				j++;
			}
			MonitorConfig config = builder.build();

			StatsTimer statsTimer = new StatsTimer(config, statsConfig);
			registry.register(statsTimer);
			return statsTimer;
		});
	}

	private static void incrementCount(String name, String...additionalTags) {
		getCounter(name, additionalTags).increment();
	}

	private static BasicCounter getCounter(String name, String...additionalTags) {
		String key = className + "." + name + "."  + Joiner.on(",").join(additionalTags);
		return errors.computeIfAbsent(key, k -> {
			Builder builder = MonitorConfig.builder(name).withTag("class", className);
			for(int j = 0; j < additionalTags.length-1; j++) {
				String tagKey = additionalTags[j];
				String tagValue = additionalTags[j+1];
				builder.withTag(tagKey, tagValue);
				j++;
			}
			MonitorConfig config = builder.build();
			BasicCounter basicCounter = new BasicCounter(config);
			registry.register(basicCounter);
			return basicCounter;
		});
	}

	private static Stopwatch start(StatsMonitor statsMonitor) {
		Stopwatch stopwatch = new BasicStopwatch() {
			@Override
			public void stop() {
				super.stop();
				long duration = getDuration(TimeUnit.MILLISECONDS);
				statsMonitor.record(duration);
			}
		};
		stopwatch.start();
		return stopwatch;
	}

	public static void taskExecutionQueueFullCounter(String taskType) {
		incrementCount(TASK_EXECUTION_QUEUE_FULL, TASK_TYPE, taskType);
	}

	public static void taskPollErrorCounter(String taskType, Exception e) {
		incrementCount(TASK_POLL_ERROR, TASK_TYPE, taskType, EXCEPTION, e.getClass().getSimpleName());
	}

	public static void taskPausedCounter(String taskType) {
		incrementCount(TASK_PAUSED, TASK_TYPE, taskType);
	}

	public static void taskExecutionErrorCounter(String taskType, Throwable e) {
		incrementCount(TASK_EXECUTE_ERROR, TASK_TYPE, taskType, EXCEPTION, e.getClass().getSimpleName());
	}

	public static void taskAckFailedCounter(String taskType) {
		incrementCount(TASK_ACK_FAILED, TASK_TYPE, taskType);
	}

	public static void taskAckErrorCounter(String taskType, Exception e) {
		incrementCount(TASK_ACK_ERROR, TASK_TYPE, taskType, EXCEPTION, e.getClass().getSimpleName());
	}

	public static void taskUpdateErrorCounter(String taskType, Throwable t) {
		incrementCount(TASK_UPDATE_ERROR, TASK_TYPE, taskType, EXCEPTION, t.getClass().getSimpleName());
	}

	public static void taskPollCounter(String taskType) {
		incrementCount(TASK_POLL_COUNTER, TASK_TYPE, taskType);
	}

	public static Stopwatch startExecutionTimer(String taskType) {
		return startTimer(TASK_EXECUTE_TIME, taskType);
	}

	public static Stopwatch startPollTimer(String taskType) {
		return startTimer(TASK_POLL_TIME, taskType);

	}
}
