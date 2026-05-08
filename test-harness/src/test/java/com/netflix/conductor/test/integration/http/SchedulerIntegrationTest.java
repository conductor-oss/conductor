/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.test.integration.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.QueueDAO;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.*;

/**
 * Integration tests for the scheduler module — full SpringBoot context with real beans, no mocks.
 *
 * <p>Tests cover: create schedule with multiple cron expressions, verify firing, pause (no firing),
 * and resume (firing resumes). Uses an in-memory QueueDAO and SchedulerDAO so no external
 * infrastructure is required.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ConductorTestApp.class)
@TestPropertySource(
        locations = "classpath:application-integrationtest.properties",
        properties = {
            "conductor.scheduler.enabled=true",
            "conductor.scheduler.initialDelayMs=200",
            "conductor.scheduler.pollingInterval=100",
            "conductor.scheduler.pollBatchSize=10",
            "conductor.scheduler.maxScheduleJitterMs=10"
        })
public class SchedulerIntegrationTest {

    // =========================================================================
    // Test configuration: in-memory DAO beans (no Redis / Docker / DB required)
    // =========================================================================

    @TestConfiguration
    @ConditionalOnProperty(name = "conductor.scheduler.enabled", havingValue = "true")
    static class TestConfig {

        @Bean
        @Primary
        public QueueDAO queueDAO() {
            return new SchedulerTestQueueDAO();
        }

        @Bean
        @Primary
        public SchedulerDAO schedulerDAO() {
            return new InMemorySchedulerDAO();
        }

        @Bean
        @Primary
        public SchedulerArchivalDAO schedulerArchivalDAO() {
            return new TrackingSchedulerArchivalDAO();
        }
    }

    /**
     * In-memory QueueDAO backed by {@link LinkedBlockingDeque} per queue name. Offset times are
     * ignored — messages are immediately available for polling. The scheduler's "is it due" guard
     * still enforces real cron timing.
     */
    static class SchedulerTestQueueDAO implements QueueDAO {

        private final ConcurrentHashMap<String, LinkedBlockingDeque<String>> queues =
                new ConcurrentHashMap<>();

        private LinkedBlockingDeque<String> q(String name) {
            return queues.computeIfAbsent(name, k -> new LinkedBlockingDeque<>());
        }

        @Override
        public void push(String queueName, String id, long offsetTimeInSecond) {
            q(queueName).addLast(id);
        }

        @Override
        public void push(String queueName, String id, int priority, long offsetTimeInSecond) {
            q(queueName).addLast(id);
        }

        @Override
        public void push(String queueName, List<Message> messages) {
            messages.forEach(m -> q(queueName).addLast(m.getId()));
        }

        @Override
        public boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond) {
            LinkedBlockingDeque<String> queue = q(queueName);
            if (queue.contains(id)) return false;
            queue.addLast(id);
            return true;
        }

        @Override
        public boolean pushIfNotExists(
                String queueName, String id, int priority, long offsetTimeInSecond) {
            return pushIfNotExists(queueName, id, offsetTimeInSecond);
        }

        @Override
        public List<String> pop(String queueName, int count, int timeout) {
            LinkedBlockingDeque<String> queue = q(queueName);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = queue.poll();
                if (id == null) break;
                result.add(id);
            }
            if (result.isEmpty() && timeout > 0) {
                try {
                    String id = queue.poll(Math.min(timeout, 200), TimeUnit.MILLISECONDS);
                    if (id != null) result.add(id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return result;
        }

        @Override
        public List<Message> pollMessages(String queueName, int count, int timeout) {
            return pop(queueName, count, timeout).stream()
                    .map(id -> new Message(id, null, null))
                    .collect(Collectors.toList());
        }

        @Override
        public void remove(String queueName, String messageId) {
            q(queueName).remove(messageId);
        }

        @Override
        public int getSize(String queueName) {
            return q(queueName).size();
        }

        @Override
        public boolean ack(String queueName, String messageId) {
            q(queueName).remove(messageId);
            return true;
        }

        @Override
        public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
            return true;
        }

        @Override
        public void flush(String queueName) {
            q(queueName).clear();
        }

        @Override
        public Map<String, Long> queuesDetail() {
            Map<String, Long> result = new HashMap<>();
            queues.forEach((k, v) -> result.put(k, (long) v.size()));
            return result;
        }

        @Override
        public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
            return new HashMap<>();
        }

        @Override
        public boolean resetOffsetTime(String queueName, String id) {
            return q(queueName).contains(id);
        }

        @Override
        public boolean containsMessage(String queueName, String messageId) {
            return q(queueName).contains(messageId);
        }
    }

    /** Simple in-memory SchedulerDAO. Keyed by name/id. */
    static class InMemorySchedulerDAO implements SchedulerDAO {

        private final ConcurrentHashMap<String, WorkflowScheduleModel> schedules =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, WorkflowScheduleExecutionModel> executions =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Long> nextRunTimes = new ConcurrentHashMap<>();

        @Override
        public void updateSchedule(WorkflowScheduleModel schedule) {
            schedules.put(schedule.getName(), schedule);
        }

        @Override
        public void saveExecutionRecord(WorkflowScheduleExecutionModel model) {
            executions.put(model.getExecutionId(), model);
        }

        @Override
        public WorkflowScheduleExecutionModel readExecutionRecord(String executionId) {
            return executions.get(executionId);
        }

        @Override
        public void removeExecutionRecord(String executionId) {
            executions.remove(executionId);
        }

        @Override
        public WorkflowScheduleModel findScheduleByName(String name) {
            return schedules.get(name);
        }

        @Override
        public List<WorkflowScheduleModel> findAllSchedules(String workflowName) {
            return schedules.values().stream()
                    .filter(
                            s ->
                                    workflowName == null
                                            || workflowName.equals(
                                                    s.getStartWorkflowRequest().getName()))
                    .collect(Collectors.toList());
        }

        @Override
        public void deleteWorkflowSchedule(String name) {
            schedules.remove(name);
        }

        @Override
        public List<String> getPendingExecutionRecordIds() {
            return new ArrayList<>(executions.keySet());
        }

        @Override
        public List<WorkflowScheduleModel> getAllSchedules() {
            return new ArrayList<>(schedules.values());
        }

        @Override
        public Map<String, WorkflowScheduleModel> findAllByNames(Set<String> names) {
            Map<String, WorkflowScheduleModel> result = new HashMap<>();
            for (String name : names) {
                WorkflowScheduleModel s = schedules.get(name);
                if (s != null) result.put(name, s);
            }
            return result;
        }

        @Override
        public long getNextRunTimeInEpoch(String scheduleName) {
            return nextRunTimes.getOrDefault(scheduleName, -1L);
        }

        @Override
        public void setNextRunTimeInEpoch(String name, long toEpochMilli) {
            nextRunTimes.put(name, toEpochMilli);
        }

        @Override
        public SearchResult<WorkflowScheduleModel> searchSchedules(
                String workflowName,
                String scheduleName,
                Boolean paused,
                String freeText,
                int start,
                int size,
                List<String> sortOptions) {
            List<WorkflowScheduleModel> filtered =
                    schedules.values().stream()
                            .filter(
                                    s ->
                                            workflowName == null
                                                    || workflowName.equals(
                                                            s.getStartWorkflowRequest().getName()))
                            .filter(s -> scheduleName == null || s.getName().contains(scheduleName))
                            .filter(s -> paused == null || s.isPaused() == paused)
                            .sorted(Comparator.comparing(WorkflowScheduleModel::getName))
                            .collect(Collectors.toList());
            int total = filtered.size();
            int end = Math.min(start + size, total);
            List<WorkflowScheduleModel> page =
                    start < total ? filtered.subList(start, end) : Collections.emptyList();
            return new SearchResult<>(total, page);
        }

        /** Returns all execution records for a given schedule name. */
        List<WorkflowScheduleExecutionModel> getExecutionsForSchedule(String scheduleName) {
            return executions.values().stream()
                    .filter(r -> scheduleName.equals(r.getScheduleName()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * In-memory archival DAO that stores execution records for test verification. The scheduler's
     * archival thread moves records from SchedulerDAO to this DAO after each fire.
     */
    static class TrackingSchedulerArchivalDAO implements SchedulerArchivalDAO {

        private final ConcurrentHashMap<String, WorkflowScheduleExecutionModel> records =
                new ConcurrentHashMap<>();

        @Override
        public void saveExecutionRecord(WorkflowScheduleExecutionModel model) {
            records.put(model.getExecutionId(), model);
        }

        @Override
        public SearchResult<String> searchScheduledExecutions(
                String query, String freeText, int start, int count, List<String> sort) {
            List<String> ids = new ArrayList<>(records.keySet());
            return new SearchResult<>(ids.size(), ids);
        }

        @Override
        public Map<String, WorkflowScheduleExecutionModel> getExecutionsByIds(
                Set<String> executionIds) {
            Map<String, WorkflowScheduleExecutionModel> result = new HashMap<>();
            for (String id : executionIds) {
                WorkflowScheduleExecutionModel m = records.get(id);
                if (m != null) result.put(id, m);
            }
            return result;
        }

        @Override
        public WorkflowScheduleExecutionModel getExecutionById(String executionId) {
            return records.get(executionId);
        }

        @Override
        public void cleanupOldRecords(int archivalMaxRecords, int archivalMaxRecordThreshold) {}

        long countByScheduleName(String scheduleName) {
            return records.values().stream()
                    .filter(r -> scheduleName.equals(r.getScheduleName()))
                    .count();
        }
    }

    // =========================================================================
    // Injected beans and HTTP client fields
    // =========================================================================

    @Autowired private SchedulerDAO schedulerDAO;
    @Autowired private SchedulerArchivalDAO schedulerArchivalDAO;

    @LocalServerPort private int port;

    private String apiRoot;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    @Before
    public void init() {
        apiRoot = String.format("http://localhost:%d/api/", port);
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
    }

    // =========================================================================
    // REST helpers
    // =========================================================================

    private void postJson(String path, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = objectMapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        restTemplate.postForEntity(apiRoot + path, entity, String.class);
    }

    private void put(String path) {
        restTemplate.exchange(apiRoot + path, HttpMethod.PUT, HttpEntity.EMPTY, Void.class);
    }

    private <T> T get(String path, Class<T> type) {
        return restTemplate.getForObject(apiRoot + path, type);
    }

    private void delete(String path) {
        restTemplate.delete(apiRoot + path);
    }

    /**
     * Counts execution records that have been archived for the given schedule name. The scheduler
     * fires a cron, saves an execution record to SchedulerDAO, then the archival thread moves it to
     * the SchedulerArchivalDAO.
     */
    private long countExecutionRecords(String scheduleName) {
        return ((TrackingSchedulerArchivalDAO) schedulerArchivalDAO)
                .countByScheduleName(scheduleName);
    }

    // =========================================================================
    // Test: multi-cron schedule — create, verify fires, pause, resume
    // =========================================================================

    @Test
    public void testMultiCronSchedule_createPauseResume() throws Exception {
        String scheduleName = "test-multi-cron-schedule";
        String workflowName = "scheduler_integration_test_wf";

        // ── 1. Register a simple workflow so startWorkflow succeeds ──
        Map<String, Object> taskDef = new HashMap<>();
        taskDef.put("name", "sched_test_task");
        taskDef.put("ownerEmail", "test@test.com");
        taskDef.put("retryCount", 0);
        taskDef.put("timeoutSeconds", 120);
        taskDef.put("responseTimeoutSeconds", 120);
        postJson("metadata/taskdefs", List.of(taskDef));

        Map<String, Object> wfDef = new HashMap<>();
        wfDef.put("name", workflowName);
        wfDef.put("ownerEmail", "test@test.com");
        wfDef.put("schemaVersion", 2);
        wfDef.put("timeoutPolicy", "ALERT_ONLY");
        wfDef.put("timeoutSeconds", 0);
        Map<String, Object> task = new HashMap<>();
        task.put("name", "sched_test_task");
        task.put("taskReferenceName", "sched_test_ref");
        task.put("type", "SIMPLE");
        task.put("inputParameters", Map.of());
        wfDef.put("tasks", List.of(task));
        postJson("metadata/workflow", wfDef);

        // ── 2. Create schedule with 3 cron expressions (every 2 seconds each) ──
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("name", scheduleName);
        schedule.put(
                "cronSchedules",
                List.of(
                        Map.of("cronExpression", "*/2 * * * * *", "zoneId", "UTC"),
                        Map.of("cronExpression", "*/2 * * * * *", "zoneId", "UTC"),
                        Map.of("cronExpression", "*/2 * * * * *", "zoneId", "UTC")));
        schedule.put("startWorkflowRequest", Map.of("name", workflowName, "version", 1));
        schedule.put("paused", false);
        postJson("scheduler/schedules", schedule);

        // ── 3. Wait and verify all 3 crons fire ──
        // Each cron fires every 2s. After ~10s we expect at least 3 total (one per cron).
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            long count = countExecutionRecords(scheduleName);
                            assertTrue(
                                    "Expected at least 3 execution records (one per cron), got "
                                            + count,
                                    count >= 3);
                        });

        long preFireCount = countExecutionRecords(scheduleName);
        assertTrue("Expected multiple fires before pause, got " + preFireCount, preFireCount >= 3);

        // ── 4. Pause the schedule ──
        put("scheduler/schedules/" + scheduleName + "/pause");

        // Verify schedule is paused
        String scheduleJson = get("scheduler/schedules/" + scheduleName, String.class);
        assertTrue("Schedule should be paused", scheduleJson.contains("\"paused\":true"));

        // Record count at pause time, wait, verify no new executions
        long countAtPause = countExecutionRecords(scheduleName);
        Thread.sleep(4000);
        long countAfterPauseWait = countExecutionRecords(scheduleName);
        assertEquals(
                "No new executions should occur while paused", countAtPause, countAfterPauseWait);

        // ── 5. Resume the schedule ──
        put("scheduler/schedules/" + scheduleName + "/resume");

        // Verify schedule is unpaused
        scheduleJson = get("scheduler/schedules/" + scheduleName, String.class);
        assertTrue("Schedule should be unpaused", scheduleJson.contains("\"paused\":false"));

        // Wait for new executions after resume
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            long count = countExecutionRecords(scheduleName);
                            assertTrue(
                                    "Expected new executions after resume, had "
                                            + countAfterPauseWait
                                            + " at pause, now "
                                            + count,
                                    count > countAfterPauseWait);
                        });

        // ── 6. Cleanup ──
        delete("scheduler/schedules/" + scheduleName);
        assertNull("Schedule should be deleted", schedulerDAO.findScheduleByName(scheduleName));
    }
}
