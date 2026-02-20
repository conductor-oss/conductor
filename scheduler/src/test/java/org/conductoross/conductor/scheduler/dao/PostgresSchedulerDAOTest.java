/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.scheduler.dao;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.conductoross.conductor.scheduler.config.TestObjectMapperConfiguration;
import org.conductoross.conductor.scheduler.config.WorkflowSchedulerConfiguration;
import org.conductoross.conductor.scheduler.model.WorkflowSchedule;
import org.conductoross.conductor.scheduler.model.WorkflowScheduleExecution;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import static org.junit.Assert.*;

/**
 * Integration tests for {@link PostgresSchedulerDAO}.
 *
 * <p>Uses Testcontainers via the {@code jdbc:tc:postgresql:...} URL in {@code
 * application.properties} — no external database required.
 */
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            DataSourceAutoConfiguration.class,
            WorkflowSchedulerConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest
public class PostgresSchedulerDAOTest {

    @Autowired private SchedulerDAO schedulerDAO;

    @Autowired private DataSource dataSource;

    @Before
    public void cleanDb() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("TRUNCATE TABLE workflow_schedule CASCADE").executeUpdate();
            conn.prepareStatement("TRUNCATE TABLE workflow_schedule_execution CASCADE")
                    .executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Schedule CRUD
    // -------------------------------------------------------------------------

    @Test
    public void testSaveAndFindSchedule() {
        WorkflowSchedule schedule = buildSchedule("test-schedule", "my-workflow");
        schedulerDAO.updateSchedule(schedule);

        WorkflowSchedule found = schedulerDAO.findScheduleByName("test-schedule");

        assertNotNull(found);
        assertEquals("test-schedule", found.getName());
        assertEquals("my-workflow", found.getStartWorkflowRequest().getName());
        assertEquals("0 0 9 * * MON-FRI", found.getCronExpression());
        assertEquals("UTC", found.getZoneId());
    }

    @Test
    public void testFindScheduleByName_notFound_returnsNull() {
        WorkflowSchedule found = schedulerDAO.findScheduleByName("no-such-schedule");
        assertNull(found);
    }

    @Test
    public void testUpdateSchedule_upserts() {
        WorkflowSchedule schedule = buildSchedule("upsert-schedule", "workflow-v1");
        schedulerDAO.updateSchedule(schedule);

        // Update the cron expression
        schedule.setCronExpression("0 0 10 * * *");
        schedulerDAO.updateSchedule(schedule);

        WorkflowSchedule found = schedulerDAO.findScheduleByName("upsert-schedule");
        assertEquals("0 0 10 * * *", found.getCronExpression());
    }

    @Test
    public void testGetAllSchedules() {
        schedulerDAO.updateSchedule(buildSchedule("sched-a", "wf-a"));
        schedulerDAO.updateSchedule(buildSchedule("sched-b", "wf-b"));
        schedulerDAO.updateSchedule(buildSchedule("sched-c", "wf-c"));

        List<WorkflowSchedule> all = schedulerDAO.getAllSchedules();
        assertEquals(3, all.size());
    }

    @Test
    public void testFindAllSchedulesByWorkflow() {
        schedulerDAO.updateSchedule(buildSchedule("s1", "target-wf"));
        schedulerDAO.updateSchedule(buildSchedule("s2", "target-wf"));
        schedulerDAO.updateSchedule(buildSchedule("s3", "other-wf"));

        List<WorkflowSchedule> results = schedulerDAO.findAllSchedules("target-wf");
        assertEquals(2, results.size());
        assertTrue(
                results.stream()
                        .allMatch(s -> "target-wf".equals(s.getStartWorkflowRequest().getName())));
    }

    @Test
    public void testDeleteSchedule_removesScheduleAndExecutions() {
        schedulerDAO.updateSchedule(buildSchedule("to-delete", "some-wf"));

        WorkflowScheduleExecution exec = buildExecution("to-delete");
        schedulerDAO.saveExecutionRecord(exec);

        schedulerDAO.deleteWorkflowSchedule("to-delete");

        assertNull(schedulerDAO.findScheduleByName("to-delete"));
        // Execution should be cascade-deleted too
        assertNull(schedulerDAO.readExecutionRecord(exec.getExecutionId()));
    }

    // -------------------------------------------------------------------------
    // Execution tracking
    // -------------------------------------------------------------------------

    @Test
    public void testSaveAndReadExecutionRecord() {
        schedulerDAO.updateSchedule(buildSchedule("exec-test", "wf"));
        WorkflowScheduleExecution exec = buildExecution("exec-test");
        schedulerDAO.saveExecutionRecord(exec);

        WorkflowScheduleExecution found = schedulerDAO.readExecutionRecord(exec.getExecutionId());
        assertNotNull(found);
        assertEquals(exec.getExecutionId(), found.getExecutionId());
        assertEquals("exec-test", found.getScheduleName());
        assertEquals(WorkflowScheduleExecution.ExecutionState.POLLED, found.getState());
    }

    @Test
    public void testUpdateExecutionRecord_transitionToExecuted() {
        schedulerDAO.updateSchedule(buildSchedule("state-test", "wf"));
        WorkflowScheduleExecution exec = buildExecution("state-test");
        schedulerDAO.saveExecutionRecord(exec);

        exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
        exec.setWorkflowId("conductor-wf-123");
        schedulerDAO.saveExecutionRecord(exec);

        WorkflowScheduleExecution found = schedulerDAO.readExecutionRecord(exec.getExecutionId());
        assertEquals(WorkflowScheduleExecution.ExecutionState.EXECUTED, found.getState());
        assertEquals("conductor-wf-123", found.getWorkflowId());
    }

    @Test
    public void testRemoveExecutionRecord() {
        schedulerDAO.updateSchedule(buildSchedule("remove-exec", "wf"));
        WorkflowScheduleExecution exec = buildExecution("remove-exec");
        schedulerDAO.saveExecutionRecord(exec);

        schedulerDAO.removeExecutionRecord(exec.getExecutionId());

        assertNull(schedulerDAO.readExecutionRecord(exec.getExecutionId()));
    }

    @Test
    public void testGetPendingExecutionRecordIds() {
        schedulerDAO.updateSchedule(buildSchedule("pending-test", "wf"));

        WorkflowScheduleExecution polled1 = buildExecution("pending-test");
        WorkflowScheduleExecution polled2 = buildExecution("pending-test");
        WorkflowScheduleExecution executed = buildExecution("pending-test");
        executed.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);

        schedulerDAO.saveExecutionRecord(polled1);
        schedulerDAO.saveExecutionRecord(polled2);
        schedulerDAO.saveExecutionRecord(executed);

        List<String> pendingIds = schedulerDAO.getPendingExecutionRecordIds();
        assertEquals(2, pendingIds.size());
        assertTrue(pendingIds.contains(polled1.getExecutionId()));
        assertTrue(pendingIds.contains(polled2.getExecutionId()));
    }

    @Test
    public void testGetExecutionRecords_orderedByTimeDesc() {
        schedulerDAO.updateSchedule(buildSchedule("history-test", "wf"));

        for (int i = 0; i < 5; i++) {
            WorkflowScheduleExecution exec = buildExecution("history-test");
            exec.setExecutionTime((long) (1000 + i));
            exec.setState(WorkflowScheduleExecution.ExecutionState.EXECUTED);
            schedulerDAO.saveExecutionRecord(exec);
        }

        List<WorkflowScheduleExecution> records =
                schedulerDAO.getExecutionRecords("history-test", 3);
        assertEquals(3, records.size());
        // Most recent first
        assertTrue(records.get(0).getExecutionTime() >= records.get(1).getExecutionTime());
        assertTrue(records.get(1).getExecutionTime() >= records.get(2).getExecutionTime());
    }

    // -------------------------------------------------------------------------
    // Next-run time management
    // -------------------------------------------------------------------------

    @Test
    public void testSetAndGetNextRunTime() {
        schedulerDAO.updateSchedule(buildSchedule("next-run-test", "wf"));

        long epochMillis = System.currentTimeMillis() + 60_000;
        schedulerDAO.setNextRunTimeInEpoch("next-run-test", epochMillis);

        long retrieved = schedulerDAO.getNextRunTimeInEpoch("next-run-test");
        assertEquals(epochMillis, retrieved);
    }

    @Test
    public void testGetNextRunTime_notSet_returnsMinusOne() {
        schedulerDAO.updateSchedule(buildSchedule("no-next-run", "wf"));

        long result = schedulerDAO.getNextRunTimeInEpoch("no-next-run");
        assertEquals(-1L, result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkflowSchedule buildSchedule(String name, String workflowName) {
        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setName(workflowName);
        startReq.setVersion(1);

        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setName(name);
        schedule.setCronExpression("0 0 9 * * MON-FRI");
        schedule.setZoneId("UTC");
        schedule.setStartWorkflowRequest(startReq);
        schedule.setPaused(false);
        schedule.setCreateTime(System.currentTimeMillis());
        return schedule;
    }

    private WorkflowScheduleExecution buildExecution(String scheduleName) {
        WorkflowScheduleExecution exec = new WorkflowScheduleExecution();
        exec.setExecutionId(UUID.randomUUID().toString());
        exec.setScheduleName(scheduleName);
        exec.setScheduledTime(System.currentTimeMillis());
        exec.setExecutionTime(System.currentTimeMillis());
        exec.setState(WorkflowScheduleExecution.ExecutionState.POLLED);
        exec.setZoneId("UTC");
        return exec;
    }
}
