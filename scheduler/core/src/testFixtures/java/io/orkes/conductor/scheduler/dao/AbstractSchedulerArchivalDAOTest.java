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
package io.orkes.conductor.scheduler.dao;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;

import io.orkes.conductor.dao.archive.SchedulerArchivalDAO;
import io.orkes.conductor.scheduler.model.WorkflowScheduleExecutionModel;

import static org.junit.Assert.*;

/**
 * Shared contract test suite for all {@link SchedulerArchivalDAO} implementations.
 *
 * <p>Every persistence module subclasses this and provides the wired DAO via {@link
 * #archivalDao()}. Subclasses are responsible for clearing state between tests (typically in a
 * {@code @Before} method).
 *
 * <p><b>Test categories:</b>
 *
 * <ol>
 *   <li>Save and retrieve — single and batch lookups
 *   <li>Round-trip fidelity — all model fields survive serialization
 *   <li>Search — by schedule name, free text, wildcard, pagination
 *   <li>Cleanup — threshold-based record pruning
 *   <li>Edge cases — empty/null inputs
 * </ol>
 */
public abstract class AbstractSchedulerArchivalDAOTest {

    protected static final String ORG_ID = "0000";

    /** Returns the archival DAO under test. */
    protected abstract SchedulerArchivalDAO archivalDao();

    // =========================================================================
    // 1. Save and retrieve
    // =========================================================================

    @Test
    public void testSaveAndGetById() {
        WorkflowScheduleExecutionModel model = buildExecution("sched-1", "exec-1");
        archivalDao().saveExecutionRecord(model);

        WorkflowScheduleExecutionModel found = archivalDao().getExecutionById(ORG_ID, "exec-1");
        assertNotNull(found);
        assertEquals("exec-1", found.getExecutionId());
        assertEquals("sched-1", found.getScheduleName());
        assertEquals("test-wf", found.getWorkflowName());
        assertEquals(WorkflowScheduleExecutionModel.State.EXECUTED, found.getState());
    }

    @Test
    public void testGetById_notFound_returnsNull() {
        assertNull(archivalDao().getExecutionById(ORG_ID, "no-such-id"));
    }

    @Test
    public void testSaveAndGetByIds() {
        archivalDao().saveExecutionRecord(buildExecution("sched-1", "exec-a"));
        archivalDao().saveExecutionRecord(buildExecution("sched-1", "exec-b"));
        archivalDao().saveExecutionRecord(buildExecution("sched-2", "exec-c"));

        Map<String, WorkflowScheduleExecutionModel> result =
                archivalDao().getExecutionsByIds(ORG_ID, Set.of("exec-a", "exec-c", "no-such"));
        assertEquals(2, result.size());
        assertTrue(result.containsKey("exec-a"));
        assertTrue(result.containsKey("exec-c"));
    }

    @Test
    public void testGetByIds_emptySet_returnsEmpty() {
        assertTrue(archivalDao().getExecutionsByIds(ORG_ID, Set.of()).isEmpty());
    }

    @Test
    public void testGetByIds_nullSet_returnsEmpty() {
        assertTrue(archivalDao().getExecutionsByIds(ORG_ID, null).isEmpty());
    }

    @Test
    public void testSaveExecutionRecord_upsert() {
        WorkflowScheduleExecutionModel model = buildExecution("upsert-sched", "upsert-exec");
        archivalDao().saveExecutionRecord(model);

        model.setReason("updated reason");
        model.setState(WorkflowScheduleExecutionModel.State.FAILED);
        archivalDao().saveExecutionRecord(model);

        WorkflowScheduleExecutionModel found =
                archivalDao().getExecutionById(ORG_ID, "upsert-exec");
        assertNotNull(found);
        assertEquals("updated reason", found.getReason());
        assertEquals(WorkflowScheduleExecutionModel.State.FAILED, found.getState());
    }

    // =========================================================================
    // 2. Round-trip fidelity
    // =========================================================================

    @Test
    public void testRoundTrip_allFields() {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName("my-wf");
        req.setVersion(3);

        WorkflowScheduleExecutionModel model = new WorkflowScheduleExecutionModel();
        model.setExecutionId("rt-exec");
        model.setScheduleName("rt-sched");
        model.setWorkflowName("my-wf");
        model.setWorkflowId("wf-instance-789");
        model.setReason("Timeout exceeded");
        model.setStackTrace("java.lang.RuntimeException: Timeout\n\tat Foo.bar(Foo.java:42)");
        model.setState(WorkflowScheduleExecutionModel.State.FAILED);
        model.setScheduledTime(1000000L);
        model.setExecutionTime(1000500L);
        model.setStartWorkflowRequest(req);

        archivalDao().saveExecutionRecord(model);

        WorkflowScheduleExecutionModel found = archivalDao().getExecutionById(ORG_ID, "rt-exec");
        assertNotNull(found);
        assertEquals("rt-sched", found.getScheduleName());
        assertEquals("my-wf", found.getWorkflowName());
        assertEquals("wf-instance-789", found.getWorkflowId());
        assertEquals("Timeout exceeded", found.getReason());
        assertTrue(found.getStackTrace().contains("RuntimeException"));
        assertEquals(WorkflowScheduleExecutionModel.State.FAILED, found.getState());
        assertEquals(Long.valueOf(1000000L), found.getScheduledTime());
        assertEquals(Long.valueOf(1000500L), found.getExecutionTime());
        assertNotNull(found.getStartWorkflowRequest());
        assertEquals("my-wf", found.getStartWorkflowRequest().getName());
        assertEquals(Integer.valueOf(3), found.getStartWorkflowRequest().getVersion());
    }

    // =========================================================================
    // 3. Search
    // =========================================================================

    @Test
    public void testSearch_byScheduleName() {
        archivalDao().saveExecutionRecord(buildExecution("sched-a", "e1"));
        archivalDao().saveExecutionRecord(buildExecution("sched-a", "e2"));
        archivalDao().saveExecutionRecord(buildExecution("sched-b", "e3"));

        SearchResult<String> result =
                archivalDao().searchScheduledExecutions(ORG_ID, "sched-a", null, 0, 10, null);
        assertEquals(2, result.getTotalHits());
        assertTrue(result.getResults().contains("e1"));
        assertTrue(result.getResults().contains("e2"));
    }

    @Test
    public void testSearch_freeText() {
        archivalDao().saveExecutionRecord(buildExecution("alpha-schedule", "e-alpha"));
        archivalDao().saveExecutionRecord(buildExecution("beta-schedule", "e-beta"));

        SearchResult<String> result =
                archivalDao().searchScheduledExecutions(ORG_ID, null, "alpha", 0, 10, null);
        assertEquals(1, result.getTotalHits());
        assertEquals("e-alpha", result.getResults().get(0));
    }

    @Test
    public void testSearch_wildcard_returnsAll() {
        archivalDao().saveExecutionRecord(buildExecution("sched-1", "e1"));
        archivalDao().saveExecutionRecord(buildExecution("sched-2", "e2"));

        SearchResult<String> result =
                archivalDao().searchScheduledExecutions(ORG_ID, null, "*", 0, 10, null);
        assertEquals(2, result.getTotalHits());
    }

    @Test
    public void testSearch_pagination() {
        for (int i = 0; i < 5; i++) {
            WorkflowScheduleExecutionModel exec =
                    buildExecution("page-sched", "page-" + UUID.randomUUID());
            exec.setScheduledTime(System.currentTimeMillis() + i * 1000L);
            archivalDao().saveExecutionRecord(exec);
        }

        SearchResult<String> page1 =
                archivalDao().searchScheduledExecutions(ORG_ID, "page-sched", null, 0, 2, null);
        assertEquals(5, page1.getTotalHits());
        assertEquals(2, page1.getResults().size());

        SearchResult<String> page2 =
                archivalDao().searchScheduledExecutions(ORG_ID, "page-sched", null, 2, 2, null);
        assertEquals(5, page2.getTotalHits());
        assertEquals(2, page2.getResults().size());
    }

    @Test
    public void testSearch_noResults_returnsEmpty() {
        SearchResult<String> result =
                archivalDao().searchScheduledExecutions(ORG_ID, "nonexistent", null, 0, 10, null);
        assertEquals(0, result.getTotalHits());
        assertTrue(result.getResults().isEmpty());
    }

    // =========================================================================
    // 4. Cleanup
    // =========================================================================

    @Test
    public void testCleanupOldRecords() {
        for (int i = 0; i < 10; i++) {
            WorkflowScheduleExecutionModel exec = buildExecution("cleanup-sched", "cleanup-" + i);
            exec.setScheduledTime(1000000L + i * 1000L);
            archivalDao().saveExecutionRecord(exec);
        }

        // Keep only 3 records, threshold at 5 (count=10 > threshold=5, so cleanup triggers)
        archivalDao().cleanupOldRecords(3, 5);

        SearchResult<String> result =
                archivalDao().searchScheduledExecutions(ORG_ID, "cleanup-sched", null, 0, 20, null);
        assertEquals(3, result.getTotalHits());
    }

    @Test
    public void testCleanupOldRecords_belowThreshold_noOp() {
        for (int i = 0; i < 3; i++) {
            WorkflowScheduleExecutionModel exec = buildExecution("noclean-sched", "noclean-" + i);
            exec.setScheduledTime(1000000L + i * 1000L);
            archivalDao().saveExecutionRecord(exec);
        }

        // Threshold is 5, count is 3 — should not clean up
        archivalDao().cleanupOldRecords(2, 5);

        SearchResult<String> result =
                archivalDao().searchScheduledExecutions(ORG_ID, "noclean-sched", null, 0, 20, null);
        assertEquals(3, result.getTotalHits());
    }

    // =========================================================================
    // 5. Volume
    // =========================================================================

    @Test
    public void testVolume_manyRecordsSameSchedule() {
        int count = 50;
        for (int i = 0; i < count; i++) {
            WorkflowScheduleExecutionModel exec = buildExecution("volume-sched", "vol-" + i);
            exec.setScheduledTime(1000000L + i * 1000L);
            archivalDao().saveExecutionRecord(exec);
        }

        SearchResult<String> result =
                archivalDao().searchScheduledExecutions(ORG_ID, "volume-sched", null, 0, 100, null);
        assertEquals(count, result.getTotalHits());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    protected WorkflowScheduleExecutionModel buildExecution(
            String scheduleName, String executionId) {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName("test-wf");
        req.setVersion(1);

        WorkflowScheduleExecutionModel model = new WorkflowScheduleExecutionModel();
        model.setExecutionId(executionId);
        model.setScheduleName(scheduleName);
        model.setWorkflowName("test-wf");
        model.setWorkflowId("wf-" + executionId);
        model.setState(WorkflowScheduleExecutionModel.State.EXECUTED);
        model.setScheduledTime(System.currentTimeMillis());
        model.setExecutionTime(System.currentTimeMillis());
        model.setStartWorkflowRequest(req);
        return model;
    }
}
