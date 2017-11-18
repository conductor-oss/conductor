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

	private static StatsConfig statsConfig = new StatsConfig.Builder().withPublishCount(true).withPublishMax(true).withPublishMean(true).withPublishMin(true).withPublishTotal(true).build();

	private static MonitorRegistry registry = DefaultMonitorRegistry.getInstance();

	private static ConcurrentHashMap<String, StatsTimer> monitors = new ConcurrentHashMap<>();

	private static ConcurrentHashMap<String, BasicCounter> errors = new ConcurrentHashMap<>();
	
	private static final String className = WorkflowTaskMetrics.class.getSimpleName(); 

	private WorkflowTaskMetrics() {

	}

	private static Stopwatch start(String name, String taskType) {
		return start(getTimer(name, "taskType", taskType));
	}
	
	private static StatsTimer getTimer(String name, String...additionalTags) {
		String key = className + "." + name + "." + Joiner.on(",").join(additionalTags);
		return monitors.computeIfAbsent(key, k -> {
			Builder cb = MonitorConfig.builder(name).withTag("class", className).withTag("unit", TimeUnit.MILLISECONDS.name());
			for(int j = 0; j < additionalTags.length-1; j++) {
				String tk = additionalTags[j];
				String tv = additionalTags[j+1];
				cb.withTag(tk, tv);
				j++;
			}
			MonitorConfig config = cb.build();
			
			StatsTimer sm = new StatsTimer(config, statsConfig);
			registry.register(sm);
			return sm;
		});
	}
	
	private static void counter(String name, String...additionalTags) {
		getCounter(name, additionalTags).increment();
	}

	private static BasicCounter getCounter(String name, String...additionalTags) {
		String key = className + "." + name + "."  + Joiner.on(",").join(additionalTags);
		return errors.computeIfAbsent(key, k -> {
			Builder cb = MonitorConfig.builder(name).withTag("class", className);
			for(int j = 0; j < additionalTags.length-1; j++) {
				String tk = additionalTags[j];
				String tv = additionalTags[j+1];
				cb.withTag(tk, tv);
				j++;
			}
			MonitorConfig config = cb.build();
			BasicCounter bc = new BasicCounter(config);
			registry.register(bc);
			return bc;
		});
	}

	private static Stopwatch start(StatsMonitor sm) {

		Stopwatch sw = new BasicStopwatch() {

			@Override
			public void stop() {
				super.stop();
				long duration = getDuration(TimeUnit.MILLISECONDS);
				sm.record(duration);
			}

		};
		sw.start();
		return sw;
	}

	public static void queueFull(String taskType) {
		counter("task_execution_queue_full", "taskType", taskType);
	}

	public static void pollingException(String taskType, Exception e) {
		counter("task_poll_error", "taskType", taskType, "exception", e.getClass().getSimpleName());
	}

	public static void paused(String taskType) {
		counter("task_poll_error", "taskType", taskType);		
	}

	public static void executionException(String taskType, Throwable e) {
		counter("task_execute_error", "taskType", taskType, "exception", e.getClass().getSimpleName());		
	}

	public static void ackFailed(String taskType) {
		counter("task_ack_failed", "taskType", taskType);
	}

	public static void ackException(String taskType, Exception e) {
		counter("task_ack_error", "taskType", taskType, "exception", e.getClass().getSimpleName());		
	}

	public static void updateTaskError(String taskType, Throwable t) {
		counter("task_update_error", "taskType", taskType, "exception", t.getClass().getSimpleName());		
	}
	
	public static void polled(String taskType) {
		counter("task_poll_counter", "taskType", taskType);		
	}

	public static Stopwatch executionTimer(String taskType) {
		return start("task_execute_time", taskType);
	}
	
	public static Stopwatch pollTimer(String taskType) {
		return start("task_poll_time", taskType);

	}
}
