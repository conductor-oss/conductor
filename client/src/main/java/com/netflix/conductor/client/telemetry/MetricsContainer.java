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
package com.netflix.conductor.client.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;

import com.google.common.base.Joiner;

public class MetricsContainer {

    private static final String TASK_TYPE = "taskType";
    private static final String WORKFLOW_TYPE = "workflowType";
    private static final String WORKFLOW_VERSION = "version";
    private static final String EXCEPTION = "exception";
    private static final String ENTITY_NAME = "entityName";
    private static final String OPERATION = "operation";
    private static final String PAYLOAD_TYPE = "payload_type";

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
    private static final String TASK_RESULT_SIZE = "task_result_size";
    private static final String WORKFLOW_INPUT_SIZE = "workflow_input_size";
    private static final String EXTERNAL_PAYLOAD_USED = "external_payload_used";
    private static final String WORKFLOW_START_ERROR = "workflow_start_error";
    private static final String THREAD_UNCAUGHT_EXCEPTION = "thread_uncaught_exceptions";

    private static final Registry REGISTRY = Spectator.globalRegistry();
    private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();
    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> GAUGES = new ConcurrentHashMap<>();
    private static final String CLASS_NAME = MetricsContainer.class.getSimpleName();

    private MetricsContainer() {}

    public static Timer getPollTimer(String taskType) {
        return getTimer(TASK_POLL_TIME, TASK_TYPE, taskType);
    }

    public static Timer getExecutionTimer(String taskType) {
        return getTimer(TASK_EXECUTE_TIME, TASK_TYPE, taskType);
    }

    private static Timer getTimer(String name, String... additionalTags) {
        String key = CLASS_NAME + "." + name + "." + Joiner.on(",").join(additionalTags);
        return TIMERS.computeIfAbsent(
                key,
                k -> {
                    List<Tag> tagList = getTags(additionalTags);
                    tagList.add(new BasicTag("unit", TimeUnit.MILLISECONDS.name()));
                    return REGISTRY.timer(name, tagList);
                });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Tag> getTags(String[] additionalTags) {
        List<Tag> tagList = new ArrayList();
        tagList.add(new BasicTag("class", CLASS_NAME));
        for (int j = 0; j < additionalTags.length - 1; j++) {
            tagList.add(new BasicTag(additionalTags[j], additionalTags[j + 1]));
            j++;
        }
        return tagList;
    }

    private static void incrementCount(String name, String... additionalTags) {
        getCounter(name, additionalTags).increment();
    }

    private static Counter getCounter(String name, String... additionalTags) {
        String key = CLASS_NAME + "." + name + "." + Joiner.on(",").join(additionalTags);
        return COUNTERS.computeIfAbsent(
                key,
                k -> {
                    List<Tag> tags = getTags(additionalTags);
                    return REGISTRY.counter(name, tags);
                });
    }

    private static AtomicLong getGauge(String name, String... additionalTags) {
        String key = CLASS_NAME + "." + name + "." + Joiner.on(",").join(additionalTags);
        return GAUGES.computeIfAbsent(
                key,
                pollTimer -> {
                    Id id = REGISTRY.createId(name, getTags(additionalTags));
                    return PolledMeter.using(REGISTRY).withId(id).monitorValue(new AtomicLong(0));
                });
    }

    public static void incrementTaskExecutionQueueFullCount(String taskType) {
        incrementCount(TASK_EXECUTION_QUEUE_FULL, TASK_TYPE, taskType);
    }

    public static void incrementUncaughtExceptionCount() {
        incrementCount(THREAD_UNCAUGHT_EXCEPTION);
    }

    public static void incrementTaskPollErrorCount(String taskType, Exception e) {
        incrementCount(
                TASK_POLL_ERROR, TASK_TYPE, taskType, EXCEPTION, e.getClass().getSimpleName());
    }

    public static void incrementTaskPausedCount(String taskType) {
        incrementCount(TASK_PAUSED, TASK_TYPE, taskType);
    }

    public static void incrementTaskExecutionErrorCount(String taskType, Throwable e) {
        incrementCount(
                TASK_EXECUTE_ERROR, TASK_TYPE, taskType, EXCEPTION, e.getClass().getSimpleName());
    }

    public static void incrementTaskAckFailedCount(String taskType) {
        incrementCount(TASK_ACK_FAILED, TASK_TYPE, taskType);
    }

    public static void incrementTaskAckErrorCount(String taskType, Exception e) {
        incrementCount(
                TASK_ACK_ERROR, TASK_TYPE, taskType, EXCEPTION, e.getClass().getSimpleName());
    }

    public static void recordTaskResultPayloadSize(String taskType, long payloadSize) {
        getGauge(TASK_RESULT_SIZE, TASK_TYPE, taskType).getAndSet(payloadSize);
    }

    public static void incrementTaskUpdateErrorCount(String taskType, Throwable t) {
        incrementCount(
                TASK_UPDATE_ERROR, TASK_TYPE, taskType, EXCEPTION, t.getClass().getSimpleName());
    }

    public static void incrementTaskPollCount(String taskType, int taskCount) {
        getCounter(TASK_POLL_COUNTER, TASK_TYPE, taskType).increment(taskCount);
    }

    public static void recordWorkflowInputPayloadSize(
            String workflowType, String version, long payloadSize) {
        getGauge(WORKFLOW_INPUT_SIZE, WORKFLOW_TYPE, workflowType, WORKFLOW_VERSION, version)
                .getAndSet(payloadSize);
    }

    public static void incrementExternalPayloadUsedCount(
            String name, String operation, String payloadType) {
        incrementCount(
                EXTERNAL_PAYLOAD_USED,
                ENTITY_NAME,
                name,
                OPERATION,
                operation,
                PAYLOAD_TYPE,
                payloadType);
    }

    public static void incrementWorkflowStartErrorCount(String workflowType, Throwable t) {
        incrementCount(
                WORKFLOW_START_ERROR,
                WORKFLOW_TYPE,
                workflowType,
                EXCEPTION,
                t.getClass().getSimpleName());
    }
}
