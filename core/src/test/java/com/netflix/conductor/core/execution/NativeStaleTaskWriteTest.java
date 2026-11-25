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
package com.netflix.conductor.core.execution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.service.SchemaService;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.ConcurrentExecutionLimitDAO;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.ExecutionLockService;

import tools.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A worker's result and an async system task's run save a task's whole row without the workflow
 * lock. When two of them overlap (the SDK resends a result whose response timed out; a JOIN is
 * evaluated twice, out of order) the one that read the row first may save it last. With the marker
 * of the continuation that waits on the task kept on that row, the late save put back a marker the
 * continuation had already applied: 4 of 200 executions of fork { child | child | task } then join
 * were held as diverged.
 *
 * <p>Those saves stay as they are. The markers are on the workflow row instead, which only code
 * under the lock saves: a stale task save lands, as for any workflow, and cannot touch them.
 *
 * <p>The store keeps rows as the persistence modules do: a read returns a copy, a save replaces the
 * whole row.
 */
public class NativeStaleTaskWriteTest {

    private static final String WF = "wf-1";
    private static final String JOIN_ID = "id-fork_join";

    private final ObjectMapper json = new ObjectMapperProvider().getObjectMapper();
    private final Map<String, String> rows = new ConcurrentHashMap<>();
    private String workflowRow;
    private ExecutionDAO store;
    private ExecutionDAOFacade dao;
    private NativeContinuationService nativeService;
    private AsyncSystemTaskExecutor executor;

    // ------------------------------------------------------------------ the store

    private void save(TaskModel task) {
        rows.put(task.getTaskId(), json.writeValueAsString(task));
    }

    private TaskModel read(String taskId) {
        String row = rows.get(taskId);
        return row == null ? null : json.readValue(row, TaskModel.class);
    }

    private WorkflowModel readWorkflow() {
        WorkflowModel workflow = json.readValue(workflowRow, WorkflowModel.class);
        List<TaskModel> tasks = new ArrayList<>();
        for (String id : List.of("id-fork", "id-a", "id-b", JOIN_ID)) {
            tasks.add(read(id));
        }
        workflow.setTasks(tasks);
        return workflow;
    }

    private void saveWorkflow(WorkflowModel workflow) {
        WorkflowModel row = json.readValue(json.writeValueAsString(workflow), WorkflowModel.class);
        row.setTasks(new ArrayList<>());
        workflowRow = json.writeValueAsString(row);
    }

    private TaskContinuation markerOf(String taskId) {
        return readWorkflow().getNativeState().getMarkers().get(taskId);
    }

    /** What a continuation's completion saves, under the workflow lock: the workflow row. */
    private void applyContinuation(String taskId) {
        WorkflowModel workflow = readWorkflow();
        TaskContinuation marker = workflow.getNativeState().getMarkers().get(taskId);
        marker.setLeased(false);
        marker.setEndTime(100);
        marker.getRecorded().put("once-1", "value the code recorded");
        saveWorkflow(workflow);
        assertTrue(markerOf(taskId).isApplied());
    }

    /** The marker is as the continuation applied it, and nothing is offered again. */
    private void assertAppliedAsItWas(String taskId) {
        TaskContinuation marker = markerOf(taskId);
        assertTrue("not applied: " + marker, marker.isApplied());
        assertEquals(100, marker.getEndTime());
        assertEquals("value the code recorded", marker.getRecorded().get("once-1"));
        assertFalse(marker.isLeased());
        assertTrue(nativeService.repairAndWake(readWorkflow()).offers.isEmpty());
        // nothing of it on the task's row
        assertFalse(json.readTree(rows.get(taskId)).has("continuation"));
    }

    // ------------------------------------------------------------------ the execution

    private static WorkflowTask entry(String ref, String type, String lane, Integer continuation) {
        WorkflowTask t = new WorkflowTask();
        t.setName(ref);
        t.setTaskReferenceName(ref);
        t.setType(type);
        t.getMetadata().put("lane", lane);
        if (continuation != null) {
            t.getMetadata().put("continuation", continuation);
        }
        return t;
    }

    private static TaskModel task(WorkflowTask entry, String type, TaskModel.Status status) {
        TaskModel t = new TaskModel();
        t.setTaskId("id-" + entry.getTaskReferenceName());
        t.setReferenceTaskName(entry.getTaskReferenceName());
        t.setTaskType(type);
        t.setTaskDefName(entry.getName());
        t.setStatus(status);
        t.setWorkflowInstanceId(WF);
        t.setWorkflowTask(entry);
        t.setExecuted(true);
        return t;
    }

    /** A lane's only task: finished, and its continuation applied and ended the lane. */
    private static TaskModel laneTask(WorkflowTask entry, NativeState state) {
        TaskModel t = task(entry, "p1_step", TaskModel.Status.COMPLETED);
        TaskContinuation marker = new TaskContinuation();
        marker.setEndTime(10);
        marker.setDone(true);
        state.getMarkers().put(t.getTaskId(), marker);
        t.setEndTime(5);
        return t;
    }

    @Before
    public void setUp() {
        WorkflowTask a = entry("a", TaskType.TASK_TYPE_SIMPLE, "fork_1.0", 1);
        WorkflowTask b = entry("b", TaskType.TASK_TYPE_SIMPLE, "fork_1.1", 1);
        WorkflowTask fork = entry("fork", TaskType.TASK_TYPE_FORK_JOIN, "main", null);
        fork.setForkTasks(List.of(List.of(a), List.of(b)));
        // the main lane waits at the join: its continuation main#2 runs when the join completes
        WorkflowTask joinEntry = entry("fork_join", TaskType.TASK_TYPE_JOIN, "main", 2);
        WorkflowDef def = new WorkflowDef();
        def.setName("p1_stress");
        def.setVersion(1);
        def.getMetadata().put("native", new HashMap<>(Map.of("continuationTimeoutSeconds", 30)));
        def.setTasks(new ArrayList<>(List.of(fork, joinEntry)));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(WF);
        workflow.setWorkflowDefinition(def);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        NativeState state = new NativeState();
        TaskContinuation bootstrap = new TaskContinuation();
        bootstrap.setEndTime(1);
        state.setBootstrap(bootstrap);
        workflow.setNativeState(state);

        save(task(fork, TaskType.TASK_TYPE_FORK, TaskModel.Status.COMPLETED));
        save(laneTask(a, state));
        save(laneTask(b, state));
        TaskModel join = task(joinEntry, TaskType.TASK_TYPE_JOIN, TaskModel.Status.IN_PROGRESS);
        join.setExecuted(false);
        state.getMarkers().put(JOIN_ID, new TaskContinuation()); // not run: the join is running
        join.getInputData().put("joinOn", List.of());
        save(join);
        workflowRow = json.writeValueAsString(workflow);

        store = mock(ExecutionDAO.class);
        when(store.getTask(anyString())).thenAnswer(i -> read(i.getArgument(0)));
        when(store.getWorkflow(anyString(), anyBoolean())).thenAnswer(i -> readWorkflow());
        doAnswer(
                        i -> {
                            save(i.getArgument(0));
                            return null;
                        })
                .when(store)
                .updateTask(any());
        ConductorProperties properties = new ConductorProperties();
        dao =
                new ExecutionDAOFacade(
                        store,
                        mock(QueueDAO.class),
                        mock(IndexDAO.class),
                        mock(RateLimitingDAO.class),
                        mock(ConcurrentExecutionLimitDAO.class),
                        mock(PollDataDAO.class),
                        json,
                        properties,
                        mock(ExternalPayloadStorageUtils.class));
        nativeService =
                new NativeContinuationService(
                        json,
                        properties,
                        new NativeFragmentValidator(
                                json,
                                properties,
                                mock(MetadataMapperService.class),
                                new HashMap<String, TaskMapper>()));

        QueueDAO queue = mock(QueueDAO.class);
        when(queue.setUnackTimeout(anyString(), anyString(), anyLong())).thenReturn(true);
        ParametersUtils parameters = mock(ParametersUtils.class);
        when(parameters.substituteSecrets(any())).thenAnswer(i -> i.getArgument(0));
        executor =
                new AsyncSystemTaskExecutor(
                        dao,
                        queue,
                        mock(MetadataDAO.class),
                        properties,
                        mock(WorkflowExecutor.class),
                        parameters);
    }

    /** The JOIN, with a pause between reading the execution and deciding, as a slow run has. */
    private static final class SlowJoin extends Join {
        final CountDownLatch read = new CountDownLatch(1);
        final CountDownLatch go = new CountDownLatch(1);

        SlowJoin() {
            super(new ConductorProperties());
        }

        @Override
        public boolean execute(
                WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
            read.countDown();
            try {
                go.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.execute(workflow, task, workflowExecutor);
        }
    }

    @Test
    public void aSlowJoinRunSavedLastLeavesTheMarkerItsContinuationApplied() throws Exception {
        // run A reads the JOIN, IN_PROGRESS, and is slow to finish
        SlowJoin slow = new SlowJoin();
        Thread runA = new Thread(() -> executor.execute(slow, JOIN_ID));
        runA.start();
        assertTrue(slow.read.await(10, TimeUnit.SECONDS));

        // run B of the same JOIN completes it, and main#2 applies
        executor.execute(new Join(new ConductorProperties()), JOIN_ID);
        assertEquals(TaskModel.Status.COMPLETED, read(JOIN_ID).getStatus());
        applyContinuation(JOIN_ID);

        // run A saves its copy of the task
        slow.go.countDown();
        runA.join(10_000);

        assertAppliedAsItWas(JOIN_ID);
    }

    /**
     * A run that loaded the execution before the last lane settled finds the JOIN not done and
     * saves it IN_PROGRESS after a later run completed it. The JOIN is evaluated again, as for any
     * workflow; its continuation, which already applied, does not run again.
     */
    @Test
    public void aJoinEvaluatedOutOfOrderDoesNotRunItsContinuationAgain() throws Exception {
        // lane fork_1.1's task finished, but its continuation has not run yet: not settled
        WorkflowModel workflow = readWorkflow();
        workflow.getNativeState().getMarkers().put("id-b", new TaskContinuation());
        saveWorkflow(workflow);

        // run A loads the execution now and is slow to finish
        SlowJoin slow = new SlowJoin();
        Thread runA = new Thread(() -> executor.execute(slow, JOIN_ID));
        runA.start();
        assertTrue(slow.read.await(10, TimeUnit.SECONDS));

        // fork_1.1#1 applies and ends its lane
        workflow = readWorkflow();
        TaskContinuation settled = workflow.getNativeState().getMarkers().get("id-b");
        settled.setEndTime(10);
        settled.setDone(true);
        saveWorkflow(workflow);

        // run B completes the JOIN, and main#2 applies
        executor.execute(new Join(new ConductorProperties()), JOIN_ID);
        assertEquals(TaskModel.Status.COMPLETED, read(JOIN_ID).getStatus());
        applyContinuation(JOIN_ID);

        // run A finishes with what it loaded: the JOIN back IN_PROGRESS
        slow.go.countDown();
        runA.join(10_000);
        assertEquals(TaskModel.Status.IN_PROGRESS, read(JOIN_ID).getStatus());
        assertAppliedAsItWas(JOIN_ID);

        // the JOIN is evaluated again and completes; its continuation stays applied
        executor.execute(new Join(new ConductorProperties()), JOIN_ID);
        assertEquals(TaskModel.Status.COMPLETED, read(JOIN_ID).getStatus());
        assertAppliedAsItWas(JOIN_ID);
    }

    /**
     * The same, through a worker's result: the SDK resends an update whose response did not come
     * back within its read timeout (10 s by default, under SQLite's 15 s busy timeout), so two
     * deliveries of one result can be on the server at once.
     */
    @Test
    public void aResentWorkerResultSavedLastLeavesTheMarkerItsContinuationApplied()
            throws Exception {
        // lane fork_1.0 waits on a SIMPLE task a: running, its continuation fork_1.0#1 not run
        TaskModel a = read("id-a");
        a.setStatus(TaskModel.Status.IN_PROGRESS);
        a.setEndTime(0);
        save(a);
        WorkflowModel workflow = readWorkflow();
        workflow.getNativeState().getMarkers().put("id-a", new TaskContinuation());
        saveWorkflow(workflow);

        CountDownLatch readA = new CountDownLatch(1);
        CountDownLatch goA = new CountDownLatch(1);
        when(store.getTask(anyString()))
                .thenAnswer(
                        i -> {
                            TaskModel row = read(i.getArgument(0));
                            if ("runA".equals(Thread.currentThread().getName())
                                    && readA.getCount() > 0) {
                                readA.countDown();
                                goA.await(10, TimeUnit.SECONDS);
                            }
                            return row;
                        });
        ConductorProperties properties = mock(ConductorProperties.class);
        when(properties.getLockLeaseTime()).thenReturn(Duration.ofSeconds(30));
        WorkflowExecutorOps weo =
                new WorkflowExecutorOps(
                        mock(DeciderService.class),
                        mock(MetadataDAO.class),
                        mock(QueueDAO.class),
                        mock(MetadataMapperService.class),
                        mock(WorkflowStatusListener.class),
                        mock(TaskStatusListener.class),
                        dao,
                        properties,
                        mock(ExecutionLockService.class),
                        mock(SystemTaskRegistry.class),
                        mock(ParametersUtils.class),
                        mock(IDGenerator.class),
                        Optional.empty(),
                        mock(SchemaService.class),
                        mock(NativeContinuationService.class),
                        WorkflowDriversFixture.standardOnly());
        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId(WF);
        result.setTaskId("id-a");
        result.setStatus(TaskResult.Status.COMPLETED);
        result.getOutputData().put("result", 1);

        // delivery A reads the task, running, and is slow to save
        Thread runA = new Thread(() -> weo.updateTask(result), "runA");
        runA.start();
        assertTrue(readA.await(10, TimeUnit.SECONDS));

        // delivery B, the resend, completes it, and fork_1.0#1 applies
        weo.updateTask(result);
        assertEquals(TaskModel.Status.COMPLETED, read("id-a").getStatus());
        applyContinuation("id-a");

        // delivery A saves its copy of the task
        goA.countDown();
        runA.join(10_000);

        assertAppliedAsItWas("id-a");
    }
}
