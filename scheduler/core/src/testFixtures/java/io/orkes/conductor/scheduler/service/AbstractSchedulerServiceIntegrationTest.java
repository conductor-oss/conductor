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

import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.service.WorkflowService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.dao.scheduler.SchedulerDAO;
import io.orkes.conductor.scheduler.config.SchedulerProperties;
import io.orkes.conductor.scheduler.model.WorkflowSchedule;
import io.orkes.conductor.scheduler.model.WorkflowScheduleModel;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link SchedulerService} using a real {@link SchedulerDAO} (from a concrete
 * subclass) and mocked collaborators (WorkflowService, QueueDAO, etc.).
 *
 * <p>Subclasses provide the wired {@link SchedulerDAO} and {@link DataSource} via Spring. This
 * abstract class handles DB cleanup between tests and constructs a real {@link SchedulerService}
 * instance using {@link MockExecutor} so no background threads are started.
 *
 * <p>Every concrete subclass (Postgres, MySQL) automatically inherits all tests here.
 */
public abstract class AbstractSchedulerServiceIntegrationTest {

    protected abstract SchedulerDAO dao();

    protected abstract DataSource dataSource();

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    protected WorkflowService workflowService;
    protected MockQueueDao queueDAO;
    protected SchedulerService service;
    protected SchedulerTimeProvider timeProvider;

    @Before
    public final void setUpService() throws Exception {
        try (Connection conn = dataSource().getConnection()) {
            conn.prepareStatement("DELETE FROM scheduler_execution").executeUpdate();
            conn.prepareStatement("DELETE FROM scheduler").executeUpdate();
            conn.prepareStatement("DELETE FROM workflow_scheduled_executions").executeUpdate();
        }
        workflowService = mock(WorkflowService.class);
        timeProvider = mock(SchedulerTimeProvider.class);
        queueDAO = new MockQueueDao();

        SchedulerProperties props = new SchedulerProperties();
        props.setArchivalThreadCount(1);
        props.setPollingThreadCount(1);
        props.setPollBatchSize(10);
        props.setPollingInterval(100);

        service =
                new SchedulerService(
                        mock(SchedulerArchivalDAO.class),
                        dao(),
                        workflowService,
                        queueDAO,
                        new MockExecutor(),
                        Optional.empty(),
                        props,
                        timeProvider,
                        mock(Lock.class),
                        objectMapper);
    }

    // =========================================================================
    // a. Create-time preservation on update
    // =========================================================================

    @Test
    public void testCreateOrUpdate_createTimePreservedOnUpdate() throws Exception {
        String scheduleName = "create-time-" + UUID.randomUUID();
        // Thursday, August 26, 2021 5:46:40 PM UTC
        long fixedTime = 1630000000000L;
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(fixedTime));

        WorkflowSchedule schedule = buildSchedule(scheduleName, "ct-wf");
        WorkflowSchedule saved1 = service.createOrUpdateWorkflowSchedule(schedule);
        long createTime1 = saved1.getCreateTime();
        assertTrue("createTime must be set", createTime1 > 0);

        // Advance time by 5 seconds for the update
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(fixedTime + 5000));
        WorkflowSchedule saved2 = service.createOrUpdateWorkflowSchedule(schedule);

        WorkflowScheduleModel found = dao().findScheduleByName(scheduleName);
        assertNotNull(found);
        assertEquals(
                "createTime must not change on subsequent saves",
                createTime1,
                found.getCreateTime().longValue());
        assertTrue(
                "updatedTime must be later than createTime after an update",
                found.getUpdatedTime() > createTime1);
    }

    // =========================================================================
    // b. nextRunTime stored and retrievable
    // =========================================================================

    @Test
    public void testCreateOrUpdate_nextRunTimeStoredAndRetrievable() throws Exception {
        String scheduleName = "next-run-stored-" + UUID.randomUUID();
        // Thursday, August 26, 2021 5:46:40 PM UTC
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));

        WorkflowSchedule schedule = buildSchedule(scheduleName, "nrt-wf");
        service.createOrUpdateWorkflowSchedule(schedule);

        long stored = dao().getNextRunTimeInEpoch(scheduleName);
        assertTrue("DAO-stored next-run time must be positive after create", stored > 0);
    }

    // =========================================================================
    // c. handleSchedules creates execution record and fires workflow
    // =========================================================================

    @Test
    public void testHandleSchedules_createsExecutionRecordAndFiresWorkflow() throws Exception {
        String scheduleName = "handle-exec-" + UUID.randomUUID();
        // Thursday, August 26, 2021 17:46:40 UTC
        long createTime = 1630000000000L;
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(createTime));

        WorkflowSchedule schedule = buildSchedule(scheduleName, "handle-wf");
        service.createOrUpdateWorkflowSchedule(schedule);

        // Move nextRunTime to the past so the schedule is due
        long pastTime = System.currentTimeMillis() - 5000;
        dao().setNextRunTimeInEpoch(scheduleName, pastTime);

        // Advance time to "now" so the schedule fires
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(System.currentTimeMillis()));
        when(workflowService.startWorkflow(any())).thenReturn("wf-test-id");

        // handleSchedules is package-private (@VisibleForTesting) — accessible from same package
        service.handleSchedules(List.of(scheduleName));

        verify(workflowService, times(1)).startWorkflow(any(StartWorkflowRequest.class));

        // Verify execution record was saved via the archival queue push
        // (handleSchedules pushes to the archival queue after saving the execution record)
        assertNotNull(
                "Archival queue should have a message",
                queueDAO.dummyQueues.get(
                        SchedulerService.CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME));
        assertFalse(
                "Archival queue should not be empty",
                queueDAO.dummyQueues
                        .get(SchedulerService.CONDUCTOR_SYSTEM_SCHEDULER_ARCHIVAL_QUEUE_NAME)
                        .isEmpty());
    }

    // =========================================================================
    // d. handleSchedules advances next-run pointer
    // =========================================================================

    @Test
    public void testHandleSchedules_advancesNextRunPointer() throws Exception {
        String scheduleName = "pointer-advance-" + UUID.randomUUID();
        long createTime = 1630000000000L;
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(createTime));

        WorkflowSchedule schedule = buildSchedule(scheduleName, "ptr-wf");
        service.createOrUpdateWorkflowSchedule(schedule);

        // Set nextRunTime to past
        long pastTime = System.currentTimeMillis() - 5000;
        dao().setNextRunTimeInEpoch(scheduleName, pastTime);

        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(System.currentTimeMillis()));
        when(workflowService.startWorkflow(any())).thenReturn("wf-id");

        service.handleSchedules(List.of(scheduleName));

        long newNextRun = dao().getNextRunTimeInEpoch(scheduleName);
        assertTrue(
                "Next-run pointer must be advanced to a future time after handling",
                newNextRun > System.currentTimeMillis() - 1000);
    }

    // =========================================================================
    // e. Delete schedule
    // =========================================================================

    @Test
    public void testDeleteSchedule_removesScheduleFromDAO() throws Exception {
        String scheduleName = "delete-test-" + UUID.randomUUID();
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));

        WorkflowSchedule schedule = buildSchedule(scheduleName, "del-wf");
        service.createOrUpdateWorkflowSchedule(schedule);
        assertNotNull(dao().findScheduleByName(scheduleName));

        service.deleteSchedule(scheduleName);
        assertNull(
                "Schedule must be removed after deletion", dao().findScheduleByName(scheduleName));
    }

    // =========================================================================
    // f. Pause and resume schedule
    // =========================================================================

    @Test
    public void testPauseAndResumeSchedule() throws Exception {
        String scheduleName = "pause-resume-" + UUID.randomUUID();
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));

        WorkflowSchedule schedule = buildSchedule(scheduleName, "pr-wf");
        service.createOrUpdateWorkflowSchedule(schedule);

        WorkflowScheduleModel found = dao().findScheduleByName(scheduleName);
        assertFalse("Schedule should not be paused after creation", found.isPaused());

        service.pauseSchedule(scheduleName);
        found = dao().findScheduleByName(scheduleName);
        assertTrue("Schedule must be paused after pauseSchedule()", found.isPaused());

        service.resumeSchedule(scheduleName);
        found = dao().findScheduleByName(scheduleName);
        assertFalse("Schedule must be unpaused after resumeSchedule()", found.isPaused());
    }

    @Test(expected = NotFoundException.class)
    public void testPauseSchedule_throwsOnMissingSchedule() {
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));
        service.pauseSchedule("nonexistent-schedule-" + UUID.randomUUID());
    }

    // =========================================================================
    // g. Search schedules
    // =========================================================================

    @Test
    public void testSearchSchedules_returnsMatchingResults() throws Exception {
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));

        String prefix = "search-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        for (int i = 0; i < 3; i++) {
            service.createOrUpdateWorkflowSchedule(buildSchedule(prefix + i, "search-wf"));
        }
        // Create one with a different workflow name
        service.createOrUpdateWorkflowSchedule(buildSchedule(prefix + "other", "different-wf"));

        var result = service.searchSchedules("search-wf", null, null, null, 0, 10, null);
        assertTrue(
                "Search for 'search-wf' should return at least 3 results",
                result.getTotalHits() >= 3);

        var allResult = service.searchSchedules(null, null, null, null, 0, 100, null);
        assertTrue(
                "Search with no filters should return at least 4 results",
                allResult.getTotalHits() >= 4);
    }

    // =========================================================================
    // h. handleSchedules with paused schedule does not fire
    // =========================================================================

    @Test
    public void testHandleSchedules_pausedScheduleDoesNotFire() throws Exception {
        String scheduleName = "paused-no-fire-" + UUID.randomUUID();
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));

        WorkflowSchedule schedule = buildSchedule(scheduleName, "paused-wf");
        service.createOrUpdateWorkflowSchedule(schedule);

        // Pause it
        service.pauseSchedule(scheduleName);

        // Set nextRunTime to the past
        dao().setNextRunTimeInEpoch(scheduleName, System.currentTimeMillis() - 5000);
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(System.currentTimeMillis()));

        service.handleSchedules(List.of(scheduleName));

        verify(workflowService, never()).startWorkflow(any());
    }

    // =========================================================================
    // i. handleSchedules with deleted schedule is a no-op
    // =========================================================================

    @Test
    public void testHandleSchedules_deletedScheduleIsNoOp() throws Exception {
        when(timeProvider.getUtcTime(any())).thenReturn(utcTime(1630000000000L));

        // Pass a schedule name that doesn't exist in the DAO
        String ghostName = "ghost-" + UUID.randomUUID();
        service.handleSchedules(List.of(ghostName));

        verify(workflowService, never()).startWorkflow(any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ZonedDateTime utcTime(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("UTC"));
    }

    protected WorkflowSchedule buildSchedule(String name, String workflowName) {
        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setName(workflowName);
        startReq.setVersion(1);

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName(name);
        schedule.setCronExpression("0 * * * * *"); // every minute
        schedule.setZoneId("UTC");
        schedule.setStartWorkflowRequest(startReq);
        schedule.setPaused(false);
        return schedule;
    }
}
