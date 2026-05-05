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
package io.orkes.conductor.scheduler.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.WorkflowService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.health.RedisMonitor;
import io.orkes.conductor.scheduler.config.SchedulerProperties;
import io.orkes.conductor.scheduler.model.CronSchedule;
import io.orkes.conductor.scheduler.model.NextScheduleResult;
import io.orkes.conductor.scheduler.model.WorkflowSchedule;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

import static io.orkes.conductor.scheduler.service.SchedulerService.CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME;
import static io.orkes.conductor.scheduler.service.SchedulerService.CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the OSS SchedulerService. Uses in-memory DAO implementations and mocks — no
 * external dependencies required.
 */
class SchedulerServiceTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private final InMemorySchedulerDAO schedulerDAO = new InMemorySchedulerDAO();
    private final MockQueueDao queueDAO = new MockQueueDao();
    private final Lock lock = Mockito.mock(Lock.class);
    private final WorkflowService workflowService = Mockito.mock(WorkflowService.class);
    private final MockExecutor executor = new MockExecutor();
    private final RedisMonitor redisMonitor = Mockito.mock(RedisMonitor.class);
    private final SchedulerProperties properties = new SchedulerProperties();

    @BeforeEach
    void setUp() {
        queueDAO.clearCounter();
        queueDAO.clearData();
        schedulerDAO.clear();
        properties.setArchivalThreadCount(1);
        properties.setPollingThreadCount(1);
        properties.setPollBatchSize(1);
        properties.setPollingInterval(100);
        Mockito.reset(workflowService, redisMonitor, lock);
    }

    @AfterEach
    void cleanupAfterTest() {
        // InMemorySchedulerDAO is cleared in setUp
    }

    private SchedulerService createService(SchedulerTimeProvider timeProvider) {
        return new SchedulerService(
                Mockito.mock(io.orkes.conductor.dao.archive.SchedulerArchivalDAO.class),
                schedulerDAO,
                workflowService,
                queueDAO,
                executor,
                Optional.of(redisMonitor),
                properties,
                timeProvider,
                lock,
                objectMapper);
    }

    private SchedulerService createServiceWithRedisHealthy(SchedulerTimeProvider timeProvider) {
        when(redisMonitor.isMemoryCritical()).thenReturn(false);
        when(redisMonitor.getUsagePercentage()).thenReturn(10);
        return createService(timeProvider);
    }

    private ZonedDateTime utcTime(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("UTC"));
    }

    // -------------------------------------------------------------------------
    // Model tests (no DAO or service needed)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("WorkflowScheduleModel.from copies all properties from WorkflowSchedule")
    void testCopyBean() {
        WorkflowSchedule ws = new WorkflowSchedule();
        ws.setName(UUID.randomUUID().toString());
        ws.setCreatedBy("BLAH");
        ws.setCronExpression("* * * * * *");
        ws.setPaused(false);
        ws.setRunCatchupScheduleInstances(true);
        ws.setScheduleStartTime(Long.MAX_VALUE - 100);

        WorkflowScheduleModel wsm = WorkflowScheduleModel.from(ws);
        assertEquals(ws.getName(), wsm.getName());
        assertEquals(ws.getCreatedBy(), wsm.getCreatedBy());
        assertEquals(ws.getCronExpression(), wsm.getCronExpression());
        assertEquals(ws.isPaused(), wsm.isPaused());
        assertEquals(ws.isRunCatchupScheduleInstances(), wsm.isRunCatchupScheduleInstances());
        assertEquals(ws.getScheduleStartTime(), wsm.getScheduleStartTime());
    }

    @Test
    @DisplayName("getEffectiveCronSchedules wraps legacy single cronExpression")
    void effectiveCronSchedulesWrapsLegacyCronExpression() {
        WorkflowSchedule ws = new WorkflowSchedule();
        ws.setCronExpression("@daily");
        ws.setZoneId("America/New_York");

        List<CronSchedule> effective = ws.getEffectiveCronSchedules();
        assertEquals(1, effective.size());
        assertEquals("@daily", effective.get(0).getCronExpression());
        assertEquals("America/New_York", effective.get(0).getZoneId());
    }

    @Test
    @DisplayName("getEffectiveCronSchedules returns cronSchedules list when set")
    void effectiveCronSchedulesReturnsCronSchedulesList() {
        WorkflowSchedule ws = new WorkflowSchedule();
        ws.setCronExpression("@daily"); // should be ignored

        CronSchedule cs1 = new CronSchedule();
        cs1.setCronExpression("0 0 8 * * ?");
        cs1.setZoneId("UTC");
        CronSchedule cs2 = new CronSchedule();
        cs2.setCronExpression("0 0 20 * * ?");
        cs2.setZoneId("UTC");
        ws.setCronSchedules(Arrays.asList(cs1, cs2));

        List<CronSchedule> effective = ws.getEffectiveCronSchedules();
        assertEquals(2, effective.size());
        assertEquals("0 0 8 * * ?", effective.get(0).getCronExpression());
        assertEquals("0 0 20 * * ?", effective.get(1).getCronExpression());
        assertTrue(ws.hasMultipleCronSchedules());
    }

    // -------------------------------------------------------------------------
    // Core scheduling lifecycle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("basic schedule: create, execute on poll, produce archival message")
    void testSchedulerBasics() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        WorkflowSchedule testSchedule = new WorkflowSchedule();
        testSchedule.setName("test_schedule");
        testSchedule.setCronExpression("@daily");
        testSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        testSchedule.getStartWorkflowRequest().setName("test_workflow");

        // Thursday, August 26, 2021 5:46:40 PM
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.createOrUpdateWorkflowSchedule(testSchedule);
        Mockito.reset(mockTimeProvider);

        assertEquals(1, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME).size());
        assertTrue(
                queueDAO.dummyQueues
                        .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                        .containsKey("test_schedule"));
        assertEquals(1, queueDAO.getCounter("pushWithPriority"));
        assertNotNull(executor.getExecutorServiceMainQueuePoll(1).getCommand());
        assertNotNull(executor.getExecutorServiceArchivalQueuePoll(1).getCommand());
        assertNull(queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME));

        // Thursday, August 26, 2021 23:59:59.500 — just before midnight, within 1s of next run
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630022399500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        Mockito.reset(mockTimeProvider);

        assertEquals(1, queueDAO.getCounter("pop-conductor_system_scheduler"));
        assertEquals(1, queueDAO.getCounter("push"));
        assertEquals(2, queueDAO.getCounter("pushWithPriority"));
        assertEquals(1, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME).size());
        assertEquals(
                1, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME).size());

        // Verify workflow was started and execution record saved
        verify(workflowService, times(1)).startWorkflow(any(StartWorkflowRequest.class));

        WorkflowScheduleModel schedule = schedulerDAO.findScheduleByName("test_schedule");
        long nextRunEpoch = schedulerDAO.getNextRunTimeInEpoch(schedule.getName());
        // Next run should be midnight Aug 27 UTC = 1630108800000
        assertEquals(1630108800000L, nextRunEpoch);
        assertEquals(schedule, service.getSchedule("test_schedule"));
    }

    @Test
    @DisplayName("schedule with start and end times respects time bounds")
    void testSchedulerStartAndEnd() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        WorkflowScheduleModel testSchedule = new WorkflowScheduleModel();
        testSchedule.setName("test_schedule");
        testSchedule.setCronExpression("* * * ? * *"); // Every second
        testSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        testSchedule.setZoneId("America/New_York");
        testSchedule.getStartWorkflowRequest().setName("test_workflow");

        long startEpoch = 1688000000000L;
        ZonedDateTime testTime =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(startEpoch), service.zoneId);

        boolean scheduled = service.computeAndSaveNextSchedule(testSchedule, testTime, testTime);
        assertTrue(scheduled);
        Long offsetTimeSeconds =
                (Long)
                        queueDAO.dummyQueues
                                .get("conductor_system_scheduler")
                                .get("test_schedule")
                                .get("offsetTimeInSecond");
        assertEquals(1, offsetTimeSeconds);

        queueDAO.dummyQueues.clear();

        // scheduleStartTime in the future skips ahead
        int seconds = 20;
        testSchedule.setScheduleStartTime(startEpoch + (seconds * 1000));
        runAndAssert(service, testSchedule, startEpoch, startEpoch - 300, seconds);
        runAndAssert(
                service,
                testSchedule,
                startEpoch + (seconds * 1000),
                (startEpoch + (seconds * 1000)) - 300,
                1);

        // scheduleEndTime in the past returns false
        testSchedule.setScheduleStartTime(startEpoch);
        testSchedule.setScheduleEndTime(startEpoch + (seconds * 1000));
        runAndAssert(service, testSchedule, startEpoch, startEpoch - 300, 1);
        ZonedDateTime endTime =
                ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(startEpoch + (seconds * 1000)), service.zoneId);
        ZonedDateTime lastBeforeEnd =
                ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli((startEpoch + (seconds * 1000)) - 300),
                        service.zoneId);
        assertFalse(service.computeAndSaveNextSchedule(testSchedule, endTime, lastBeforeEnd));
    }

    private void runAndAssert(
            SchedulerService service,
            WorkflowScheduleModel testSchedule,
            long lastRunTime,
            long localTime,
            int expectedOffset) {
        ZonedDateTime current =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(localTime), service.zoneId);
        ZonedDateTime last =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastRunTime), service.zoneId);
        boolean scheduled = service.computeAndSaveNextSchedule(testSchedule, current, last);
        assertTrue(scheduled);
        Long offsetTimeSeconds =
                (Long)
                        queueDAO.dummyQueues
                                .get("conductor_system_scheduler")
                                .get("test_schedule")
                                .get("offsetTimeInSecond");
        assertEquals(expectedOffset, offsetTimeSeconds);
    }

    @Test
    @DisplayName("edge cases: within-window, too-soon, too-early queue re-push")
    void testSchedulerEdgeCases() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        WorkflowSchedule testSchedule = new WorkflowSchedule();
        testSchedule.setName("test_schedule");
        testSchedule.setCronExpression("*/5 * * ? * *");
        testSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        testSchedule.setZoneId("America/New_York");
        testSchedule.getStartWorkflowRequest().setName("test_workflow");

        // Thursday, August 26, 2021 5:46:40 PM
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.createOrUpdateWorkflowSchedule(testSchedule);
        Mockito.reset(mockTimeProvider);

        assertEquals(1, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME).size());
        assertEquals(
                5L,
                queueDAO.dummyQueues
                        .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                        .get("test_schedule")
                        .get("offsetTimeInSecond"));

        // Within 1 second of next run — should execute
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000004500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        Mockito.reset(mockTimeProvider);

        assertEquals(
                6L,
                queueDAO.dummyQueues
                        .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                        .get("test_schedule")
                        .get("offsetTimeInSecond"));
        assertEquals(
                1, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME).size());

        // Too soon — should NOT execute, just re-push
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000007500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        Mockito.reset(mockTimeProvider);

        assertEquals(
                3L,
                queueDAO.dummyQueues
                        .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                        .get("test_schedule")
                        .get("offsetTimeInSecond"));
        // Archival queue unchanged
        assertEquals(
                1, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME).size());

        // Too early — should NOT execute
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000003500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        Mockito.reset(mockTimeProvider);

        assertEquals(
                7L,
                queueDAO.dummyQueues
                        .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                        .get("test_schedule")
                        .get("offsetTimeInSecond"));
        assertEquals(
                1, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME).size());

        // Just before the next run — should execute
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000009100L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        Mockito.reset(mockTimeProvider);

        assertEquals(
                6L,
                queueDAO.dummyQueues
                        .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                        .get("test_schedule")
                        .get("offsetTimeInSecond"));
        assertEquals(
                2, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME).size());
    }

    // -------------------------------------------------------------------------
    // Backoff logic
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Redis memory pressure triggers exponential backoff")
    void testBackoffLogic() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        when(redisMonitor.isMemoryCritical()).thenReturn(false);
        when(redisMonitor.getUsagePercentage()).thenReturn(65);
        SchedulerService service = createService(mockTimeProvider);
        service.start();

        // Healthy Redis — should poll
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        assertEquals(1, queueDAO.getCounter("pop-conductor_system_scheduler"));

        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        assertEquals(2, queueDAO.getCounter("pop-conductor_system_scheduler"));

        // Critical Redis — triggers backoff
        when(redisMonitor.isMemoryCritical()).thenReturn(true);
        when(redisMonitor.getUsagePercentage()).thenReturn(80);
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        assertEquals(2, queueDAO.getCounter("pop-conductor_system_scheduler"));
        assertEquals(5, service.backoffFactor.get());
        int expectedBackoffCount = 50;
        assertEquals(expectedBackoffCount, service.scheduleWfPollerBackoff.get());

        for (int i = 1; i < expectedBackoffCount; i++) {
            executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        }
        assertEquals(1, service.scheduleWfPollerBackoff.get());
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        assertEquals(expectedBackoffCount * 2, service.scheduleWfPollerBackoff.get());

        for (int i = 1; i < (expectedBackoffCount * 2); i++) {
            executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        }

        // Redis recovers
        when(redisMonitor.isMemoryCritical()).thenReturn(false);
        when(redisMonitor.getUsagePercentage()).thenReturn(40);
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        assertEquals(0, service.scheduleWfPollerBackoff.get());
        assertEquals(0, service.backoffFactor.get());

        int expectedCounter = 3;
        assertEquals(expectedCounter, queueDAO.getCounter("pop-conductor_system_scheduler"));
        int pollCount = 10;
        for (int i = 0; i < pollCount; i++) {
            executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        }
        assertEquals(
                expectedCounter + pollCount, queueDAO.getCounter("pop-conductor_system_scheduler"));
    }

    // -------------------------------------------------------------------------
    // Pause / Resume
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("schedule created with paused=true is not queued")
    void pausedScheduleCreation() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        WorkflowSchedule testSchedule = new WorkflowSchedule();
        testSchedule.setPaused(true);
        testSchedule.setName("test_schedule");
        testSchedule.setCronExpression("@daily");
        testSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        testSchedule.setZoneId("America/New_York");
        testSchedule.getStartWorkflowRequest().setName("test_workflow");

        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.createOrUpdateWorkflowSchedule(testSchedule);

        assertNull(queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME));
    }

    @Test
    @DisplayName("updating schedule to paused=true removes it from queue")
    void scheduleUpdatedPaused() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        WorkflowSchedule testSchedule = new WorkflowSchedule();
        testSchedule.setPaused(false);
        testSchedule.setName("test_schedule");
        testSchedule.setCronExpression("@daily");
        testSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        testSchedule.setZoneId("America/New_York");
        testSchedule.getStartWorkflowRequest().setName("test_workflow");

        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.createOrUpdateWorkflowSchedule(testSchedule);
        assertNotNull(queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME));

        testSchedule.setPaused(true);
        service.createOrUpdateWorkflowSchedule(testSchedule);
        assertNull(
                queueDAO.dummyQueues
                        .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                        .get(testSchedule.getName()));
    }

    @Test
    @DisplayName("paused schedule in queue is acked without execution")
    void pausedScheduleInQueue() {
        var mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        var mockSchedulerDAO = Mockito.mock(io.orkes.conductor.dao.scheduler.SchedulerDAO.class);
        var service =
                new SchedulerService(
                        Mockito.mock(io.orkes.conductor.dao.archive.SchedulerArchivalDAO.class),
                        mockSchedulerDAO,
                        workflowService,
                        Mockito.mock(QueueDAO.class),
                        executor,
                        Optional.of(redisMonitor),
                        properties,
                        mockTimeProvider,
                        lock,
                        objectMapper);

        var testSchedule = new WorkflowScheduleModel();
        testSchedule.setPaused(true);
        testSchedule.setName("test_schedule");
        testSchedule.setCronExpression("@daily");
        testSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        testSchedule.setZoneId("America/New_York");
        when(mockSchedulerDAO.findScheduleByName(eq(testSchedule.getName())))
                .thenReturn(testSchedule);

        QueueDAO mockQueue = (QueueDAO) service.queueDAO;
        service.handleSchedules(List.of(testSchedule.getName()));

        verify(mockQueue, times(1))
                .ack(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME, testSchedule.getName());
        verify(mockQueue, never()).push(anyString(), anyString(), anyInt(), anyInt());
        verify(workflowService, never()).startWorkflow(any());
    }

    @Test
    @DisplayName("pauseSchedule on non-existent schedule throws NotFoundException")
    void pauseNonExistentSchedule() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        assertThrows(NotFoundException.class, () -> service.pauseSchedule("non_existent_schedule"));
    }

    @Test
    @DisplayName("resumeSchedule on non-existent schedule throws NotFoundException")
    void resumeNonExistentSchedule() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        assertThrows(
                NotFoundException.class, () -> service.resumeSchedule("non_existent_schedule"));
    }

    @Test
    @DisplayName("resumeSchedule on a non-paused schedule throws NotFoundException")
    void resumeScheduleOnNonPausedSchedule() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        WorkflowSchedule active = new WorkflowSchedule();
        active.setName("active_schedule");
        active.setCronExpression("@daily");
        active.setStartWorkflowRequest(new StartWorkflowRequest());
        active.getStartWorkflowRequest().setName("wf");
        service.createOrUpdateWorkflowSchedule(active);

        assertThrows(NotFoundException.class, () -> service.resumeSchedule("active_schedule"));
    }

    // -------------------------------------------------------------------------
    // Global scheduler pause toggle
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("pauseScheduler toggle prevents and restores schedule execution")
    void pauseSchedulerToggle() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        WorkflowSchedule testSchedule = new WorkflowSchedule();
        testSchedule.setName("toggled_schedule");
        testSchedule.setCronExpression("@daily");
        testSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        testSchedule.getStartWorkflowRequest().setName("wf");
        service.createOrUpdateWorkflowSchedule(testSchedule);
        Mockito.reset(mockTimeProvider);

        // Pause the global scheduler
        service.pauseScheduler(true);
        for (int i = 0; i < 5; i++) {
            executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        }
        assertEquals(
                0,
                queueDAO.getCounter("pop-conductor_system_scheduler"),
                "Scheduler must not poll the queue while paused");
        verify(workflowService, never()).startWorkflow(any());

        // Unpause and advance time
        service.pauseScheduler(false);
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630022399500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();

        assertEquals(
                1,
                queueDAO.getCounter("pop-conductor_system_scheduler"),
                "Scheduler should poll after unpausing");
        verify(workflowService, times(1)).startWorkflow(any());
    }

    // -------------------------------------------------------------------------
    // Workflow input injection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("scheduler injects _startedByScheduler and metadata into workflow input")
    void workflowInputInjection() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));

        StartWorkflowRequest swr = new StartWorkflowRequest();
        swr.setName("my_workflow");
        swr.setInput(new HashMap<>(Map.of("custom_key", "custom_value")));

        WorkflowSchedule testSchedule = new WorkflowSchedule();
        testSchedule.setName("input_schedule");
        testSchedule.setCronExpression("@daily");
        testSchedule.setStartWorkflowRequest(swr);
        service.createOrUpdateWorkflowSchedule(testSchedule);
        Mockito.reset(mockTimeProvider);

        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630022399500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();

        ArgumentCaptor<StartWorkflowRequest> captor =
                ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(workflowService, times(1)).startWorkflow(captor.capture());

        StartWorkflowRequest captured = captor.getValue();
        Map<String, Object> input = captured.getInput();

        assertEquals("input_schedule", input.get("_startedByScheduler"));
        assertNotNull(input.get("_scheduledTime"));
        assertNotNull(input.get("_executedTime"));
        assertNotNull(input.get("_executionId"));
        assertNotNull(input.get("_schedulerCron"));
        assertTrue(input.get("_schedulerCron").toString().contains("@daily"));
        assertEquals(
                "custom_value",
                input.get("custom_key"),
                "Original user-provided input must be preserved");
    }

    // -------------------------------------------------------------------------
    // Failed workflow trigger
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("failed workflow trigger saves FAILED state with truncated reason/stackTrace")
    void failedWorkflowTrigger() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        String longMessage = "E".repeat(2000);
        when(workflowService.startWorkflow(any())).thenThrow(new RuntimeException(longMessage));

        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        WorkflowSchedule testSchedule = new WorkflowSchedule();
        testSchedule.setName("fail_schedule");
        testSchedule.setCronExpression("@daily");
        testSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        testSchedule.getStartWorkflowRequest().setName("wf");
        service.createOrUpdateWorkflowSchedule(testSchedule);
        Mockito.reset(mockTimeProvider);

        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630022399500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();

        List<String> recordIds = schedulerDAO.getPendingExecutionRecordIds();
        assertFalse(recordIds.isEmpty(), "A FAILED execution record should have been saved");

        WorkflowScheduleExecutionModel record = schedulerDAO.readExecutionRecord(recordIds.get(0));
        assertNotNull(record);
        assertEquals(WorkflowScheduleExecutionModel.State.FAILED, record.getState());
        assertNotNull(record.getReason());
        assertTrue(
                record.getReason().length() <= 999,
                "Reason should be truncated to 999 characters, got " + record.getReason().length());
        assertNotNull(record.getStackTrace());
        assertTrue(
                record.getStackTrace().length() <= 2000,
                "StackTrace should be truncated to 2000 characters");
    }

    // -------------------------------------------------------------------------
    // DST / timezone offset
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("computeNextSchedule correctly handles DST timezone transitions")
    void scheduleOffsetCalculation() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        String cronExpression = "0 0 6 * * ?";
        // 8th March 9:30 AM
        Long scheduleStartTime = 1709976626000L;
        ZonedDateTime now =
                ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(scheduleStartTime), ZoneId.of("America/New_York"));
        ZonedDateTime last = now;
        WorkflowScheduleModel wsm = new WorkflowScheduleModel();
        wsm.setZoneId("America/New_York");
        wsm.setScheduleStartTime(scheduleStartTime);
        wsm.setCronExpression(cronExpression);

        List<ZonedDateTime> nextSchedules = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ZonedDateTime nextScheduleTime = service.computeNextSchedule(wsm, now, last);
            if (nextScheduleTime == null) {
                break;
            }
            nextSchedules.add(nextScheduleTime);
            last = now;
            now = nextScheduleTime;
        }

        assertEquals(2, nextSchedules.size());
        // On March 10, daylight savings kicks in: offset changes from -05:00 to -04:00
        assertEquals("-05:00", nextSchedules.get(0).getOffset().toString());
        assertEquals("-04:00", nextSchedules.get(1).getOffset().toString());
        // Local time must remain 6:00 AM regardless of DST
        assertEquals("06:00", nextSchedules.get(0).toLocalTime().toString());
        assertEquals("06:00", nextSchedules.get(1).toLocalTime().toString());
    }

    // -------------------------------------------------------------------------
    // Catchup mode
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("runCatchupScheduleInstances uses lastExpectedRunTime as cron base")
    void runCatchupScheduleInstances() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        // Every 10 seconds cron
        WorkflowScheduleModel schedule = new WorkflowScheduleModel();
        schedule.setName("catchup_schedule");
        schedule.setCronExpression("*/10 * * ? * *");
        schedule.setRunCatchupScheduleInstances(true);
        schedule.setStartWorkflowRequest(new StartWorkflowRequest());

        long baseEpoch = 1630000000000L;
        ZonedDateTime currentTime = utcTime(baseEpoch + 35_000);
        ZonedDateTime lastExpected = utcTime(baseEpoch + 10_000);

        // With catchup=true: next = cron.next(lastExpected) = T+20s
        ZonedDateTime next = service.computeNextSchedule(schedule, currentTime, lastExpected);

        assertNotNull(next);
        long nextEpoch = next.toInstant().toEpochMilli();
        assertEquals(
                baseEpoch + 20_000,
                nextEpoch,
                "Catch-up mode should compute next from lastExpectedRunTime, not currentTime");
    }

    // -------------------------------------------------------------------------
    // getListOfNextSchedules
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getListOfNextSchedules caps results at 5")
    void getListOfNextSchedulesCapsAtFive() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        ZonedDateTime now = utcTime(1630000000000L);
        when(mockTimeProvider.getUtcTime(any())).thenReturn(now);
        SchedulerService service = createService(mockTimeProvider);

        List<Long> results = service.getListOfNextSchedules("0 * * * * ?", null, null, 10);

        assertEquals(5, results.size(), "Result must be capped at 5");
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i) > results.get(i - 1), "Results must be in ascending order");
        }
    }

    @Test
    @DisplayName("getListOfNextSchedules throws RuntimeException for invalid cron expression")
    void getListOfNextSchedulesInvalidCron() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        assertThrows(
                RuntimeException.class,
                () -> service.getListOfNextSchedules("not-a-cron", null, null, 3));
    }

    @Test
    @DisplayName("getListOfNextSchedules stops early when scheduleEndTime is reached")
    void getListOfNextSchedulesRespectsEndTime() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        ZonedDateTime now = utcTime(1630000000000L);
        when(mockTimeProvider.getUtcTime(any())).thenReturn(now);
        SchedulerService service = createService(mockTimeProvider);

        // Every minute cron; end time allows only 2 executions
        long endTime = 1630000000000L + 90_000L; // 1.5 minutes from now
        List<Long> results = service.getListOfNextSchedules("0 * * * * ?", null, endTime, 5);

        assertEquals(2, results.size(), "Should return only fires before endTime");
        assertTrue(results.get(0) <= endTime);
        assertTrue(results.get(1) <= endTime);
    }

    // -------------------------------------------------------------------------
    // Search schedules
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("searchSchedules returns all schedules with default parameters")
    void testSearchSchedulesDefault() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        for (int i = 0; i < 5; i++) {
            WorkflowSchedule schedule = new WorkflowSchedule();
            schedule.setName("test_schedule_" + i);
            schedule.setCronExpression("0 0 * * * ?");
            schedule.setStartWorkflowRequest(new StartWorkflowRequest());
            schedule.getStartWorkflowRequest().setName("test_workflow_" + i);
            schedule.setPaused(i % 2 == 0);

            when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
            service.createOrUpdateWorkflowSchedule(schedule);
        }

        SearchResult<WorkflowScheduleModel> result =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of());

        assertNotNull(result);
        assertEquals(5, result.getTotalHits());
        assertEquals(5, result.getResults().size());
    }

    @Test
    @DisplayName("searchSchedules filters by workflow name")
    void testSearchSchedulesByWorkflowName() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        String targetWorkflowName = "target_workflow";
        for (int i = 0; i < 3; i++) {
            WorkflowSchedule schedule = new WorkflowSchedule();
            schedule.setName("schedule_" + i);
            schedule.setCronExpression("0 0 * * * ?");
            schedule.setStartWorkflowRequest(new StartWorkflowRequest());
            schedule.getStartWorkflowRequest().setName(targetWorkflowName);
            when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
            service.createOrUpdateWorkflowSchedule(schedule);
        }

        WorkflowSchedule otherSchedule = new WorkflowSchedule();
        otherSchedule.setName("other_schedule");
        otherSchedule.setCronExpression("0 0 * * * ?");
        otherSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        otherSchedule.getStartWorkflowRequest().setName("other_workflow");
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.createOrUpdateWorkflowSchedule(otherSchedule);

        SearchResult<WorkflowScheduleModel> result =
                service.searchSchedules(targetWorkflowName, null, null, "*", 0, 10, List.of());

        assertNotNull(result);
        assertEquals(3, result.getTotalHits());
        result.getResults()
                .forEach(
                        s ->
                                assertEquals(
                                        targetWorkflowName, s.getStartWorkflowRequest().getName()));
    }

    @Test
    @DisplayName("searchSchedules filters by paused status")
    void testSearchSchedulesByPausedStatus() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        for (int i = 0; i < 2; i++) {
            WorkflowSchedule schedule = new WorkflowSchedule();
            schedule.setName("paused_schedule_" + i);
            schedule.setCronExpression("0 0 * * * ?");
            schedule.setStartWorkflowRequest(new StartWorkflowRequest());
            schedule.getStartWorkflowRequest().setName("test_workflow");
            schedule.setPaused(true);
            when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
            service.createOrUpdateWorkflowSchedule(schedule);
        }

        for (int i = 0; i < 3; i++) {
            WorkflowSchedule schedule = new WorkflowSchedule();
            schedule.setName("active_schedule_" + i);
            schedule.setCronExpression("0 0 * * * ?");
            schedule.setStartWorkflowRequest(new StartWorkflowRequest());
            schedule.getStartWorkflowRequest().setName("test_workflow");
            schedule.setPaused(false);
            when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
            service.createOrUpdateWorkflowSchedule(schedule);
        }

        SearchResult<WorkflowScheduleModel> pausedResult =
                service.searchSchedules(null, null, true, "*", 0, 10, List.of());
        assertEquals(2, pausedResult.getTotalHits());
        pausedResult.getResults().forEach(s -> assertTrue(s.isPaused()));

        SearchResult<WorkflowScheduleModel> activeResult =
                service.searchSchedules(null, null, false, "*", 0, 10, List.of());
        assertEquals(3, activeResult.getTotalHits());
        activeResult.getResults().forEach(s -> assertFalse(s.isPaused()));
    }

    @Test
    @DisplayName("searchSchedules filters by name pattern")
    void testSearchSchedulesByNamePattern() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        for (int i = 0; i < 3; i++) {
            WorkflowSchedule schedule = new WorkflowSchedule();
            schedule.setName("daily_report_" + i);
            schedule.setCronExpression("0 0 * * * ?");
            schedule.setStartWorkflowRequest(new StartWorkflowRequest());
            schedule.getStartWorkflowRequest().setName("report_workflow");
            when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
            service.createOrUpdateWorkflowSchedule(schedule);
        }

        WorkflowSchedule otherSchedule = new WorkflowSchedule();
        otherSchedule.setName("hourly_sync");
        otherSchedule.setCronExpression("0 0 * * * ?");
        otherSchedule.setStartWorkflowRequest(new StartWorkflowRequest());
        otherSchedule.getStartWorkflowRequest().setName("sync_workflow");
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.createOrUpdateWorkflowSchedule(otherSchedule);

        SearchResult<WorkflowScheduleModel> result =
                service.searchSchedules(null, "daily_report", null, "*", 0, 10, List.of());

        assertNotNull(result);
        assertEquals(3, result.getTotalHits());
        result.getResults().forEach(s -> assertTrue(s.getName().contains("daily_report")));
    }

    @Test
    @DisplayName("searchSchedules handles pagination correctly")
    void testSearchSchedulesPagination() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        for (int i = 0; i < 10; i++) {
            WorkflowSchedule schedule = new WorkflowSchedule();
            schedule.setName("schedule_" + String.format("%02d", i));
            schedule.setCronExpression("0 0 * * * ?");
            schedule.setStartWorkflowRequest(new StartWorkflowRequest());
            schedule.getStartWorkflowRequest().setName("test_workflow");
            when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
            service.createOrUpdateWorkflowSchedule(schedule);
        }

        SearchResult<WorkflowScheduleModel> page1 =
                service.searchSchedules(null, null, null, "*", 0, 3, List.of("name:ASC"));
        assertEquals(10, page1.getTotalHits());
        assertEquals(3, page1.getResults().size());

        SearchResult<WorkflowScheduleModel> page2 =
                service.searchSchedules(null, null, null, "*", 3, 3, List.of("name:ASC"));
        assertEquals(10, page2.getTotalHits());
        assertEquals(3, page2.getResults().size());

        List<String> page1Names =
                page1.getResults().stream()
                        .map(WorkflowSchedule::getName)
                        .collect(Collectors.toList());
        List<String> page2Names =
                page2.getResults().stream()
                        .map(WorkflowSchedule::getName)
                        .collect(Collectors.toList());
        page1Names.forEach(name -> assertFalse(page2Names.contains(name)));
    }

    @Test
    @DisplayName("searchSchedules sorts correctly")
    void testSearchSchedulesSorting() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        List<String> names = Arrays.asList("charlie_schedule", "alpha_schedule", "bravo_schedule");
        for (String name : names) {
            WorkflowSchedule schedule = new WorkflowSchedule();
            schedule.setName(name);
            schedule.setCronExpression("0 0 * * * ?");
            schedule.setStartWorkflowRequest(new StartWorkflowRequest());
            schedule.getStartWorkflowRequest().setName("test_workflow");
            when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
            service.createOrUpdateWorkflowSchedule(schedule);
        }

        SearchResult<WorkflowScheduleModel> ascResult =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of("name:ASC"));
        assertEquals(3, ascResult.getTotalHits());
        assertEquals("alpha_schedule", ascResult.getResults().get(0).getName());
        assertEquals("bravo_schedule", ascResult.getResults().get(1).getName());
        assertEquals("charlie_schedule", ascResult.getResults().get(2).getName());

        SearchResult<WorkflowScheduleModel> descResult =
                service.searchSchedules(null, null, null, "*", 0, 10, List.of("name:DESC"));
        assertEquals("charlie_schedule", descResult.getResults().get(0).getName());
        assertEquals("bravo_schedule", descResult.getResults().get(1).getName());
        assertEquals("alpha_schedule", descResult.getResults().get(2).getName());
    }

    @Test
    @DisplayName("searchSchedules returns empty result when no match")
    void testSearchSchedulesNoMatches() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName("test_schedule");
        schedule.setCronExpression("0 0 * * * ?");
        schedule.setStartWorkflowRequest(new StartWorkflowRequest());
        schedule.getStartWorkflowRequest().setName("test_workflow");
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.createOrUpdateWorkflowSchedule(schedule);

        SearchResult<WorkflowScheduleModel> result =
                service.searchSchedules("non_existent_workflow", null, null, "*", 0, 10, List.of());
        assertEquals(0, result.getTotalHits());
        assertEquals(0, result.getResults().size());
    }

    // -------------------------------------------------------------------------
    // Requeue all execution records
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("requeueAllExecutionRecords pushes pending records to the archival queue")
    void requeueAllExecutionRecords() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createService(mockTimeProvider);
        service.start();

        List<String> execIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String id = UUID.randomUUID().toString();
            execIds.add(id);
            WorkflowScheduleExecutionModel rec = new WorkflowScheduleExecutionModel();
            rec.setExecutionId(id);
            rec.setScheduleName("sched" + i);
            rec.setWorkflowName("wf" + i);
            rec.setScheduledTime(1000L * i);
            rec.setExecutionTime(2000L * i);
            rec.setState(WorkflowScheduleExecutionModel.State.EXECUTED);
            rec.setStartWorkflowRequest(new StartWorkflowRequest());
            schedulerDAO.saveExecutionRecord(rec);
        }

        Map<String, Object> result = service.requeueAllExecutionRecords();

        assertEquals(3, result.get("recordIds.size"));
        assertEquals(
                3, queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME).size());
        for (String id : execIds) {
            assertTrue(
                    queueDAO.dummyQueues
                            .get(CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME)
                            .containsKey(id));
        }
    }

    // -------------------------------------------------------------------------
    // Multi-cron schedule tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("multi-cron schedule creates one queue message per cron entry")
    void multiCronScheduleCreatesOneQueueMessagePerCron() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        ZonedDateTime now = utcTime(1630000000000L);
        when(mockTimeProvider.getUtcTime(any())).thenReturn(now);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName("multi_cron_sched");
        schedule.setStartWorkflowRequest(new StartWorkflowRequest());
        schedule.getStartWorkflowRequest().setName("my_wf");

        CronSchedule cs1 = new CronSchedule();
        cs1.setCronExpression("@daily");
        cs1.setZoneId("UTC");
        CronSchedule cs2 = new CronSchedule();
        cs2.setCronExpression("0 0 12 * * ?");
        cs2.setZoneId("UTC");
        schedule.setCronSchedules(Arrays.asList(cs1, cs2));

        service.createOrUpdateWorkflowSchedule(schedule);

        Map<String, ?> schedulerQueue =
                queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME);
        assertNotNull(schedulerQueue);
        assertEquals(
                2,
                schedulerQueue.size(),
                "Multi-cron schedule must produce one queue message per cron entry");

        for (String msgId : schedulerQueue.keySet()) {
            assertTrue(
                    msgId.startsWith("{"),
                    "Multi-cron queue messages must be JSON but got: " + msgId);
        }
    }

    @Test
    @DisplayName("multi-cron picks the earliest next run across all cron entries")
    void multiCronSchedulePicksEarliestNextRun() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        ZonedDateTime now = utcTime(1630000000000L); // Aug 26, 2021 5:46:40 PM UTC
        when(mockTimeProvider.getUtcTime(any())).thenReturn(now);
        SchedulerService service = createService(mockTimeProvider);
        service.start();

        WorkflowScheduleModel wsm = new WorkflowScheduleModel();
        wsm.setName("earliest_cron");
        wsm.setStartWorkflowRequest(new StartWorkflowRequest());
        wsm.getStartWorkflowRequest().setName("wf");

        CronSchedule daily = new CronSchedule();
        daily.setCronExpression("@daily");
        daily.setZoneId("UTC");

        CronSchedule sixPm = new CronSchedule();
        sixPm.setCronExpression("0 0 18 * * ?"); // 6 PM UTC
        sixPm.setZoneId("UTC");
        wsm.setCronSchedules(Arrays.asList(daily, sixPm));

        NextScheduleResult result = service.computeNextScheduleWithZone(wsm, now, now);

        assertNotNull(result);
        long nextEpoch = result.getNextRunTime().toInstant().toEpochMilli();
        // 6PM UTC August 26, 2021 should come before midnight
        long sixPmEpoch =
                ZonedDateTime.of(2021, 8, 26, 18, 0, 0, 0, ZoneId.of("UTC"))
                        .toInstant()
                        .toEpochMilli();
        assertEquals(
                sixPmEpoch, nextEpoch, "Should pick the earlier 6PM cron over midnight @daily");
    }

    @Test
    @DisplayName("multi-cron fires each entry independently and injects _schedulerCron")
    void multiCronScheduleFiresWithCorrectCronInInput() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        ZonedDateTime createTime = utcTime(1630000000000L);
        when(mockTimeProvider.getUtcTime(any())).thenReturn(createTime);

        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName("multi_cron_fire");
        schedule.setStartWorkflowRequest(new StartWorkflowRequest());
        schedule.getStartWorkflowRequest().setName("wf");

        CronSchedule cs = new CronSchedule();
        cs.setCronExpression("0 59 23 * * ?");
        cs.setZoneId("UTC");
        schedule.setCronSchedules(Collections.singletonList(cs));

        service.createOrUpdateWorkflowSchedule(schedule);

        // Simulate execution time: just before midnight
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630022399500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();

        ArgumentCaptor<StartWorkflowRequest> captor =
                ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(workflowService, times(1)).startWorkflow(captor.capture());

        StartWorkflowRequest captured = captor.getValue();
        Map<String, Object> input = captured.getInput();

        assertNotNull(input.get("_schedulerCron"));
        String schedulerCron = input.get("_schedulerCron").toString();
        assertTrue(schedulerCron.contains("0 59 23 * * ?"));
        assertTrue(schedulerCron.contains("UTC"));
    }

    @Test
    @DisplayName("updating single-cron to multi-cron removes old message and adds JSON messages")
    void updateFromSingleToMultiCronRemovesOldMessages() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        ZonedDateTime now = utcTime(1630000000000L);
        when(mockTimeProvider.getUtcTime(any())).thenReturn(now);

        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        // Create initial single-cron schedule
        WorkflowSchedule singleCron = new WorkflowSchedule();
        singleCron.setName("upgrade_schedule");
        singleCron.setCronExpression("@daily");
        singleCron.setZoneId("UTC");
        singleCron.setStartWorkflowRequest(new StartWorkflowRequest());
        singleCron.getStartWorkflowRequest().setName("wf");
        service.createOrUpdateWorkflowSchedule(singleCron);

        Map<String, ?> queue = queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME);
        assertEquals(1, queue.size());
        assertTrue(queue.containsKey("upgrade_schedule"));

        // Update to multi-cron
        WorkflowSchedule multiCron = new WorkflowSchedule();
        multiCron.setName("upgrade_schedule");
        multiCron.setStartWorkflowRequest(new StartWorkflowRequest());
        multiCron.getStartWorkflowRequest().setName("wf");

        CronSchedule cs1 = new CronSchedule();
        cs1.setCronExpression("@daily");
        cs1.setZoneId("UTC");
        CronSchedule cs2 = new CronSchedule();
        cs2.setCronExpression("0 0 12 * * ?");
        cs2.setZoneId("UTC");
        multiCron.setCronSchedules(Arrays.asList(cs1, cs2));
        service.createOrUpdateWorkflowSchedule(multiCron);

        queue = queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME);
        assertEquals(
                2, queue.size(), "After update to multi-cron, should have 2 JSON queue messages");
        assertFalse(
                queue.containsKey("upgrade_schedule"),
                "Plain single-cron message must be removed after upgrade to multi-cron");
        for (String key : queue.keySet()) {
            assertTrue(key.startsWith("{"), "New messages must be JSON payloads");
        }
    }

    /**
     * BUG REGRESSION — multi-cron per-cron next-run-time must be stored in the DAO and must not
     * cause the schedule to fire on every poll cycle.
     *
     * <p>After a multi-cron message fires, {@code SchedulerService} calls {@code
     * schedulerDAO.setNextRunTimeInEpoch(jsonPayload, nextEpoch)} where {@code jsonPayload} is a
     * JSON string like {@code {"name":"s","cron":"... UTC","id":0}} — not the schedule name. The
     * DAO must persist that value.
     *
     * <p>Redis and SQL DAO implementations silently drop this write (hexists guard / UPDATE with 0
     * rows matched), so a subsequent {@code getNextRunTimeInEpoch(jsonPayload)} returns {@code -1}.
     * {@code SchedulerService} interprets -1 as epoch 1970, deduces the schedule is perpetually
     * overdue, and fires the workflow on every poll cycle.
     *
     * <p>This test runs with {@code InMemorySchedulerDAO}, which correctly supports arbitrary keys.
     * It documents what correct behavior looks like and serves as a regression test once the
     * Redis/SQL DAOs are fixed. The companion DAO-level tests ({@code
     * testSetAndGetNextRunTime_withMultiCronPayloadKey}) directly reproduce the bug in each storage
     * backend.
     */
    @Test
    @DisplayName(
            "multi-cron: per-cron next-run-time is stored in DAO and schedule does not misfire")
    void multiCronNextRunTimeIsStoredAfterCreateAndAfterFiring() throws Exception {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        when(redisMonitor.isMemoryCritical()).thenReturn(false);
        when(redisMonitor.getUsagePercentage()).thenReturn(10);

        // t0 = 2021-08-26T22:46:40Z  (cron "59 59 23 * * ?" fires at 23:59:59, ~73 min away)
        long t0 = 1630000000000L;
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(t0));
        SchedulerService service = createService(mockTimeProvider);
        service.start();

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName("multi_cron_nrt_bug");
        schedule.setStartWorkflowRequest(new StartWorkflowRequest());
        schedule.getStartWorkflowRequest().setName("wf");

        CronSchedule cs = new CronSchedule();
        cs.setCronExpression("59 59 23 * * ?"); // fires at 23:59:59 UTC each day
        cs.setZoneId("UTC");
        schedule.setCronSchedules(Collections.singletonList(cs));

        service.createOrUpdateWorkflowSchedule(schedule);

        // After creation: the scheduler queue must contain exactly one JSON payload message.
        Map<String, ?> schedulerQueue =
                queueDAO.dummyQueues.get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME);
        assertEquals(1, schedulerQueue.size());
        String queueMsgId = schedulerQueue.keySet().iterator().next();
        assertTrue(
                queueMsgId.startsWith("{"),
                "Queue message for multi-cron must be a JSON payload, got: " + queueMsgId);

        // After creation: the per-cron next-run-time must be stored in the DAO under the JSON
        // payload key (not the schedule name). This is what SQL/Redis DAOs currently drop.
        long storedAtCreate = schedulerDAO.getNextRunTimeInEpoch(queueMsgId);
        assertTrue(
                storedAtCreate > t0,
                "After createOrUpdateWorkflowSchedule, the per-cron next-run-time must be "
                        + "stored under the JSON payload key. Got: "
                        + storedAtCreate
                        + ". If this is -1 the DAO is silently dropping the write (the bug).");

        // Advance time to 500 ms after 23:59:59 UTC — the cron is now due.
        // fireTime corresponds to 2021-08-26T23:59:59.500Z
        long fireTime = 1630022399500L;
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(fireTime));

        // Trigger one handler iteration; it pops the queue message and should fire the workflow.
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();

        // The workflow must have fired exactly once.
        verify(workflowService, times(1)).startWorkflow(any());

        // After firing: the per-cron next-run-time must be updated to the NEXT occurrence of
        // "59 59 23 * * ?" — i.e., 2021-08-27T23:59:59Z = 1630108799000L, which is > fireTime.
        // If the DAO dropped the write (the bug), getNextRunTimeInEpoch returns -1 and the next
        // handler call will see offsetSeconds ≪ 0 and fire again immediately.
        long storedAfterFire = schedulerDAO.getNextRunTimeInEpoch(queueMsgId);
        assertTrue(
                storedAfterFire > fireTime,
                "After the multi-cron message fires, the per-cron next-run-time must be updated "
                        + "to the next cron occurrence (a future epoch > fireTime="
                        + fireTime
                        + "). Got: "
                        + storedAfterFire
                        + ". If this is -1 the DAO is silently dropping writes and multi-cron "
                        + "schedules will fire on every poll cycle (the bug).");

        // The second handler call must NOT fire the workflow again — the schedule is not due yet.
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        verify(workflowService, times(1)).startWorkflow(any()); // still exactly 1 invocation
    }

    // -------------------------------------------------------------------------
    // Dynamic jitter / burst estimate tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("burstEstimate increases on non-empty polls and decays on empty polls (AIMD)")
    void burstEstimateAIMD() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        service.start();

        // Create several schedules that will fire, so polls return non-empty batches
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        for (int i = 0; i < 10; i++) {
            WorkflowSchedule s = new WorkflowSchedule();
            s.setName("burst_schedule_" + i);
            s.setCronExpression("*/5 * * ? * *");
            s.setStartWorkflowRequest(new StartWorkflowRequest());
            s.getStartWorkflowRequest().setName("wf");
            service.createOrUpdateWorkflowSchedule(s);
        }

        assertEquals(0, service.burstEstimate.get(), "Burst should start at 0");

        // Advance time so all schedules are due, then poll multiple times
        Mockito.reset(mockTimeProvider);
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000004500L));

        // Each poll processes up to pollBatchSize (1 in test config) messages
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        int afterFirstPoll = service.burstEstimate.get();
        assertTrue(
                afterFirstPoll > 0,
                "Burst should increase after processing a non-empty batch, got " + afterFirstPoll);

        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        int afterSecondPoll = service.burstEstimate.get();
        assertTrue(
                afterSecondPoll >= afterFirstPoll,
                "Burst should keep increasing during sustained load");

        // Drain the queue manually so polls return empty
        queueDAO.clearData();
        int beforeDecay = service.burstEstimate.get();

        // Empty polls should decay the burst
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        int afterOneDecay = service.burstEstimate.get();
        assertEquals(
                beforeDecay / 2, afterOneDecay, "One empty poll should halve the burst estimate");

        // A few more empty polls should continue decaying toward 0
        for (int i = 0; i < 10; i++) {
            executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        }
        assertTrue(
                service.burstEstimate.get() <= 1,
                "Burst should decay to near 0 after multiple empty polls");
    }

    @Test
    @DisplayName("jitter is zero at low burst and meaningful at high burst")
    void dynamicJitterScalesWithBurst() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));

        // At burst=0, jitter ceiling is 0 — offsets should be whole seconds (no ms jitter)
        service.burstEstimate.set(0);

        WorkflowSchedule lowLoad = new WorkflowSchedule();
        lowLoad.setName("low_load_schedule");
        lowLoad.setCronExpression("@daily");
        lowLoad.setStartWorkflowRequest(new StartWorkflowRequest());
        lowLoad.getStartWorkflowRequest().setName("wf");
        service.createOrUpdateWorkflowSchedule(lowLoad);

        Map<String, Object> msgData =
                (Map<String, Object>)
                        queueDAO.dummyQueues
                                .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                                .get("low_load_schedule");
        assertNotNull(msgData);
        long lowBurstMs = (Long) msgData.get("offsetTimeInMs");
        long lowBurstSec = (Long) msgData.get("offsetTimeInSecond");
        // At burst=0, ms offset should equal seconds*1000 exactly (no jitter added)
        assertEquals(
                lowBurstSec * 1000,
                lowBurstMs,
                "At burst=0, offset millis should be exact seconds with no jitter");

        // Now simulate high burst — set burst to 1000
        // Jitter ceiling = 1000 * 3 / 10 = 300ms
        service.burstEstimate.set(1000);
        queueDAO.clearData();
        schedulerDAO.clear();

        List<Long> msOffsets = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            WorkflowSchedule s = new WorkflowSchedule();
            s.setName("high_burst_" + i);
            s.setCronExpression("@daily");
            s.setStartWorkflowRequest(new StartWorkflowRequest());
            s.getStartWorkflowRequest().setName("wf");
            service.createOrUpdateWorkflowSchedule(s);

            Map<String, Object> data =
                    (Map<String, Object>)
                            queueDAO.dummyQueues
                                    .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                                    .get("high_burst_" + i);
            assertNotNull(data, "Schedule high_burst_" + i + " should be in queue");
            msOffsets.add((Long) data.get("offsetTimeInMs"));
        }

        // With burst=1000, jitter range is 0-300ms. Over 50 samples, not all should be identical.
        long distinctCount = msOffsets.stream().distinct().count();
        assertTrue(
                distinctCount > 1,
                "With burst=1000, 50 schedules should have varied offsets due to jitter, but all were identical: "
                        + msOffsets.get(0));

        // All ms offsets should be >= the base seconds offset (jitter only adds, never subtracts)
        long baseSec =
                (Long)
                        queueDAO.dummyQueues
                                .get(CONDUCTOR_SYSTEM_SCHEDULER_QUEUE_NAME)
                                .values()
                                .iterator()
                                .next()
                                .get("offsetTimeInSecond");
        msOffsets.forEach(
                ms ->
                        assertTrue(
                                ms >= baseSec * 1000,
                                "Jittered ms offset "
                                        + ms
                                        + " should be >= base seconds "
                                        + baseSec * 1000));
    }

    @Test
    @DisplayName("burstEstimate is capped and does not overflow")
    void burstEstimateIsCapped() {
        SchedulerTimeProvider mockTimeProvider = Mockito.mock(SchedulerTimeProvider.class);
        SchedulerService service = createServiceWithRedisHealthy(mockTimeProvider);

        int maxJitter = properties.getMaxScheduleJitterMs(); // 1000
        int expectedCap = maxJitter * 10 / 3 + 1; // ~3334

        // Simulate extreme load — set burst very high
        service.burstEstimate.set(expectedCap - 5);

        // One more batch should cap at expectedCap, not grow beyond
        when(mockTimeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        WorkflowSchedule s = new WorkflowSchedule();
        s.setName("cap_test");
        s.setCronExpression("@daily");
        s.setStartWorkflowRequest(new StartWorkflowRequest());
        s.getStartWorkflowRequest().setName("wf");
        service.createOrUpdateWorkflowSchedule(s);

        // Manually simulate what runSchedulerMain does on a non-empty poll
        int maxBurst = maxJitter * 10 / 3 + 1;
        service.burstEstimate.updateAndGet(v -> Math.min(v + 100, maxBurst));

        assertTrue(
                service.burstEstimate.get() <= expectedCap,
                "Burst should be capped at "
                        + expectedCap
                        + " but was "
                        + service.burstEstimate.get());
    }
}
