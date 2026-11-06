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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.service.WorkflowService;

import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.health.RedisMonitor;
import io.orkes.conductor.scheduler.config.SchedulerProperties;
import io.orkes.conductor.scheduler.listener.ScheduleChangeListenerStub;
import io.orkes.conductor.scheduler.model.WorkflowSchedule;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * In OSS a schedule's queue message id is simply its name ({@code getQueueMsgId()} returns {@code
 * getName()}), but {@code getOrgAndPayload} still strips a five character {@code "org:"} prefix
 * whenever the fifth character of the id happens to be a colon. A schedule named {@code prod:daily}
 * is therefore looked up as {@code daily} and dropped as "deleted".
 */
class SchedulerServiceNamePrefixTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private final InMemorySchedulerDAO schedulerDAO = new InMemorySchedulerDAO();
    private final MockQueueDao queueDAO = new MockQueueDao();
    private final Lock lock = Mockito.mock(Lock.class);
    private final WorkflowService workflowService = Mockito.mock(WorkflowService.class);
    private final MockExecutor executor = new MockExecutor();
    private final RedisMonitor redisMonitor = Mockito.mock(RedisMonitor.class);
    private final SchedulerProperties properties = new SchedulerProperties();
    private final SchedulerTimeProvider timeProvider = Mockito.mock(SchedulerTimeProvider.class);

    @BeforeEach
    void setUp() {
        queueDAO.clearCounter();
        queueDAO.clearData();
        schedulerDAO.clear();
        properties.setArchivalThreadCount(1);
        properties.setPollingThreadCount(1);
        properties.setPollBatchSize(1);
        properties.setPollingInterval(100);
        when(redisMonitor.isMemoryCritical()).thenReturn(false);
        when(redisMonitor.getUsagePercentage()).thenReturn(10);
    }

    /**
     * Creates the schedule through the public API and then runs one polling cycle at a time that is
     * one second short of the next run, which is the window in which the scheduler is expected to
     * fire the workflow.
     */
    private void createScheduleAndPoll(String name) {
        SchedulerService service =
                new SchedulerService(
                        Mockito.mock(SchedulerArchivalDAO.class),
                        schedulerDAO,
                        workflowService,
                        queueDAO,
                        executor,
                        Optional.of(redisMonitor),
                        properties,
                        timeProvider,
                        lock,
                        objectMapper,
                        new ScheduleChangeListenerStub());
        service.start();

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName(name);
        schedule.setCronExpression("@daily");
        schedule.setZoneId("UTC");
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName("test_workflow");
        schedule.setStartWorkflowRequest(request);

        // Thursday, August 26, 2021 5:46:40 PM — the moment the schedule is created
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.createOrUpdateWorkflowSchedule(schedule);
        Mockito.reset(timeProvider);

        // 23:59:59.500 the same day — the next run (midnight) is less than a second away
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630022399500L));
        executor.getExecutorServiceMainQueuePoll(1).getCommand().run();
        Mockito.reset(timeProvider);
    }

    @Test
    @DisplayName("a schedule whose name has ':' as its fifth character still runs")
    void scheduleNameWithColonAtPrefixPositionStillRuns() {
        createScheduleAndPoll("prod:daily");

        verify(workflowService, times(1)).startWorkflow(any(StartWorkflowRequest.class));
    }

    @Test
    @DisplayName("a schedule whose name has no colon is unaffected")
    void scheduleNameWithoutColonRuns() {
        createScheduleAndPoll("proddaily");

        verify(workflowService, times(1)).startWorkflow(any(StartWorkflowRequest.class));
    }

    private ZonedDateTime utcTime(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("UTC"));
    }
}
