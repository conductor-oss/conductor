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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.execution.mapper.NoopTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import tools.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Continuations as markers on the tasks the code paused on. */
@SuppressWarnings("unchecked")
public class NativeContinuationServiceTest {

    private static final String CODE_TYPE = "kitchensink.v1.code";

    private ObjectMapper objectMapper;
    private ConductorProperties properties;
    private NativeContinuationService service;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapperProvider().getObjectMapper();
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        when(metadataDAO.getTaskDef(anyString()))
                .thenAnswer(invocation -> new TaskDef(invocation.getArgument(0)));
        Map<String, TaskMapper> mappers = new HashMap<>();
        for (String type :
                List.of("SIMPLE", "FORK_JOIN", "JOIN", "TERMINATE", "WAIT", "DO_WHILE", "SWITCH")) {
            mappers.put(type, new NoopTaskMapper());
        }
        properties = new ConductorProperties();
        service =
                new NativeContinuationService(
                        objectMapper, properties, new MetadataMapperService(metadataDAO), mappers);
    }

    // ------------------------------------------------------------------ fixtures

    private static WorkflowDef nativeDef(boolean handlers) {
        WorkflowDef def = new WorkflowDef();
        def.setName("kitchensink");
        def.setVersion(1);
        def.setSchemaVersion(2);
        Map<String, Object> meta = new HashMap<>();
        meta.put("protocol", 2);
        meta.put("codeType", CODE_TYPE);
        meta.put("continuationTimeoutSeconds", 30);
        meta.put("handlers", handlers);
        def.getMetadata().put("native", meta);
        return def;
    }

    private WorkflowModel freshWorkflow(boolean handlers) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setWorkflowDefinition(nativeDef(handlers));
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.getInput().put("mod", 5);
        service.initialize(workflow);
        return workflow;
    }

    /**
     * A fragment entry; {@code pauseLane} and {@code n} mark it as the entry the code paused on.
     */
    private static Map<String, Object> entry(
            String ref, String type, String lane, String pauseLane, int n) {
        Map<String, Object> e = new HashMap<>();
        e.put("name", ref);
        e.put("taskReferenceName", ref);
        e.put("type", type);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("lane", lane);
        if (pauseLane != null) {
            metadata.put("continuation", Map.of("lane", pauseLane, "n", n));
        }
        e.put("metadata", metadata);
        return e;
    }

    private static Map<String, Object> output(
            int n, long epoch, List<Map<String, Object>> fragment) {
        Map<String, Object> out = new HashMap<>();
        out.put("protocol", 2);
        out.put("n", n);
        out.put("epoch", epoch);
        out.put("fragment", fragment);
        return out;
    }

    private static TaskResult result(String taskId, Map<String, Object> output) {
        TaskResult result = new TaskResult();
        result.setWorkflowInstanceId("wf-1");
        result.setTaskId(taskId);
        result.setWorkerId("worker-a");
        result.setStatus(TaskResult.Status.COMPLETED);
        result.setOutputData(output);
        return result;
    }

    private static void lease(TaskContinuation marker, long epoch) {
        marker.setState(TaskContinuation.LEASED);
        marker.setEpoch(epoch);
        marker.setQueue(CODE_TYPE + ":worker-a");
        marker.setLeasedAt(System.currentTimeMillis());
    }

    /** A completed task the code paused on, with its marker leased. */
    /** Completes a continuation as the worker that holds the execution's slot. */
    private NativeContinuationService.Completion completeHoldingSlot(
            WorkflowModel workflow,
            TaskModel awaited,
            com.netflix.conductor.common.metadata.tasks.TaskResult result) {
        workflow.getNativeState()
                .setActiveContinuation(awaited != null ? awaited.getTaskId() : result.getTaskId());
        return service.complete(workflow, awaited, result);
    }

    private static TaskModel awaitedTask(
            WorkflowModel workflow, String ref, String lane, int n, long epoch) {
        WorkflowTask entry = new WorkflowTask();
        entry.setName(ref);
        entry.setTaskReferenceName(ref);
        entry.setType("SIMPLE");
        entry.getMetadata().put("lane", lane);
        workflow.getWorkflowDefinition().getTasks().add(entry);
        TaskModel task = new TaskModel();
        task.setTaskId("id-" + ref);
        task.setReferenceTaskName(ref);
        task.setTaskType("SIMPLE");
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setExecuted(true);
        task.setSeq(workflow.getTasks().size() + 1);
        task.setWorkflowTask(entry);
        task.setWorkflowInstanceId("wf-1");
        TaskContinuation marker = new TaskContinuation(lane, n);
        lease(marker, epoch);
        task.setContinuation(marker);
        workflow.getTasks().add(task);
        return task;
    }

    private static List<String> refs(List<WorkflowTask> tasks) {
        List<String> out = new ArrayList<>();
        tasks.forEach(t -> out.add(t.getTaskReferenceName()));
        return out;
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void initializeSetsUpTheBootstrapAndHandlerMarkers() {
        WorkflowModel workflow = freshWorkflow(true);
        NativeState state = workflow.getNativeState();
        assertEquals(CODE_TYPE, state.getCodeType());
        assertEquals(30, state.getContinuationTimeoutSeconds());
        assertTrue(state.getBootstrap().isRunnable());
        assertEquals(1, state.getBootstrap().getN());
        assertTrue(state.getHandlers().isWaiting());
        assertNull(freshWorkflow(false).getNativeState().getHandlers());
        assertTrue(workflow.getWorkflowDefinition().getTasks().isEmpty());
    }

    @Test
    public void theBootstrapCompletionAppendsTheFirstEntriesAndRecordsTheContinuation() {
        WorkflowModel workflow = freshWorkflow(false);
        TaskContinuation bootstrap = workflow.getNativeState().getBootstrap();
        lease(bootstrap, 1);
        Map<String, Object> out =
                output(1, 1, List.of(entry("task_1", "SIMPLE", "main", "main", 2)));
        out.put("recorded", Map.of("runId", "r-1"));
        workflow.getVariables().put("stage", "new"); // what the code saw when it ran
        out.put("variables", Map.of("stage", "classic"));
        out.put("resumedWith", Map.of("stage", "new"));

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, null, result("wf-1|main", out));

        assertFalse(completion.deferred);
        assertFalse(completion.reason, completion.finished);
        assertEquals(List.of("task_1"), refs(workflow.getWorkflowDefinition().getTasks()));
        assertTrue(bootstrap.isApplied());
        assertEquals(1, bootstrap.getStep());
        assertEquals("r-1", bootstrap.getRecorded().get("runId"));
        assertEquals("new", bootstrap.getResumedWith().get("stage"));
        assertEquals("classic", workflow.getVariables().get("stage"));
        assertEquals(CODE_TYPE + ":worker-a", completion.ackQueue);
        assertEquals("wf-1|main", completion.ackId);
        // the entry the code paused on carries the next marker for the mapper to copy
        assertEquals(
                Map.of("lane", "main", "n", 2),
                workflow.getWorkflowDefinition()
                        .getTasks()
                        .get(0)
                        .getMetadata()
                        .get("continuation"));
    }

    @Test
    public void aCompletionOnATaskInsertsAfterItAndReEvaluatesIt() {
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 3);

        NativeContinuationService.Completion completion =
                completeHoldingSlot(
                        workflow,
                        awaited,
                        result(
                                "id-task_1",
                                output(
                                        2,
                                        3,
                                        List.of(entry("task_2", "SIMPLE", "main", "main", 3)))));

        assertEquals(
                List.of("task_1", "task_2"), refs(workflow.getWorkflowDefinition().getTasks()));
        assertTrue(awaited.getContinuation().isApplied());
        assertFalse(awaited.isExecuted()); // the decider re-evaluates it and schedules task_2
        assertTrue(completion.touchedTasks.contains(awaited));
        assertEquals("id-task_1", completion.ackId);
    }

    @Test
    public void aFragmentAnchoredAfterASwitchLeavesTheCaseItPausedIn() {
        // the code paused on a task inside a switch case; its next call is anchored after the
        // switch (metadata.after), so it lands at the switch's level, not inside the case
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel awaited = awaitedTask(workflow, "task_a", "main", 2, 3);
        WorkflowTask inCase = workflow.getWorkflowDefinition().getTasks().remove(0);
        WorkflowTask sw = new WorkflowTask();
        sw.setName("route");
        sw.setTaskReferenceName("route");
        sw.setType("SWITCH");
        sw.getMetadata().put("lane", "main");
        sw.getDecisionCases().put("a", new ArrayList<>(List.of(inCase)));
        sw.getDecisionCases().put("b", new ArrayList<>());
        workflow.getWorkflowDefinition().getTasks().add(sw);

        Map<String, Object> next = entry("notify", "SIMPLE", "main", "main", 3);
        ((Map<String, Object>) next.get("metadata")).put("after", "route");
        completeHoldingSlot(workflow, awaited, result("id-task_a", output(2, 3, List.of(next))));

        assertEquals(List.of("route", "notify"), refs(workflow.getWorkflowDefinition().getTasks()));
        assertEquals(List.of("task_a"), refs(sw.getDecisionCases().get("a")));
        assertTrue(sw.getDecisionCases().get("b").isEmpty());

        // an anchor that names no entry is rejected
        TaskModel again = awaitedTask(workflow, "task_c", "main", 3, 4);
        Map<String, Object> bad = entry("later", "SIMPLE", "main", "main", 4);
        ((Map<String, Object>) bad.get("metadata")).put("after", "nowhere");
        NativeContinuationService.Completion completion =
                completeHoldingSlot(
                        workflow, again, result("id-task_c", output(3, 4, List.of(bad))));
        assertTrue(completion.finished);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aFragmentThatLeavesACaseKeepsWhatItDrewThereInTheCase() {
        // the code passed an if inside the case it paused in, then left the case: the if stays in
        // the case, after the task paused on, and the call after the switch goes after the switch
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel awaited = awaitedTask(workflow, "task_a", "main", 2, 3);
        WorkflowTask inCase = workflow.getWorkflowDefinition().getTasks().remove(0);
        WorkflowTask sw = new WorkflowTask();
        sw.setName("route");
        sw.setTaskReferenceName("route");
        sw.setType("SWITCH");
        sw.getMetadata().put("lane", "main");
        sw.getDecisionCases().put("a", new ArrayList<>(List.of(inCase)));
        sw.getDecisionCases().put("b", new ArrayList<>());
        workflow.getWorkflowDefinition().getTasks().add(sw);

        // an if the code went past without a call: a switch with the arm it took, no continuation
        Map<String, Object> passed = entry("check", "SWITCH", "main", null, 0);
        passed.put("evaluatorType", "native-code");
        passed.put("expression", "switchCaseValue");
        passed.put("inputParameters", Map.of("switchCaseValue", "false"));
        passed.put("decisionCases", Map.of("true", List.of()));
        passed.put("defaultCase", List.of());
        Map<String, Object> next = entry("notify", "SIMPLE", "main", "main", 3);
        ((Map<String, Object>) next.get("metadata")).put("after", "route");
        NativeContinuationService.Completion completion =
                completeHoldingSlot(
                        workflow,
                        awaited,
                        result("id-task_a", output(2, 3, List.of(passed, next))));

        assertFalse(completion.reason, completion.finished);

        assertEquals(List.of("route", "notify"), refs(workflow.getWorkflowDefinition().getTasks()));
        assertEquals(List.of("task_a", "check"), refs(sw.getDecisionCases().get("a")));
    }

    @Test
    public void oneContinuationOfAnExecutionIsOfferedAtATimeAndAPollWritesNothing() {
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel left = awaitedTask(workflow, "left", "fork_1.0", 1, 0);
        TaskModel right = awaitedTask(workflow, "right", "fork_1.1", 1, 0);
        left.getContinuation().setState(TaskContinuation.RUNNABLE);
        left.getContinuation().setQueuedAt(1);
        right.getContinuation().setState(TaskContinuation.RUNNABLE);
        right.getContinuation().setQueuedAt(2);
        com.netflix.conductor.dao.QueueDAO queue = mock(com.netflix.conductor.dao.QueueDAO.class);
        com.netflix.conductor.core.dal.ExecutionDAOFacade dao =
                mock(com.netflix.conductor.core.dal.ExecutionDAOFacade.class);
        when(dao.getTaskModel("id-left")).thenReturn(left);
        when(dao.getTaskModel("id-right")).thenReturn(right);
        when(dao.getWorkflowModel(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(workflow);
        service.setQueueDAO(queue);
        service.setExecutionDAOFacade(dao);

        // the decide offers the one that waited longest
        NativeContinuationService.Wakeups wakeups = service.repairAndWake(workflow);
        assertEquals(List.of("id-left"), offered(wakeups));
        assertTrue(left.getContinuation().isLeased());
        assertEquals(1, left.getContinuation().getEpoch());
        assertTrue(right.getContinuation().isRunnable());
        assertEquals(List.of("id-left"), offered(service.repairAndWake(workflow))); // held

        // the poll hands out the offer and writes nothing; anything else is a stale message
        assertTrue(
                service.lease(CODE_TYPE, "id-left", dao.getTaskModel("id-left"), "worker-a")
                        != null);
        assertEquals(
                null,
                service.lease(CODE_TYPE, "id-right", dao.getTaskModel("id-right"), "worker-b"));
        org.mockito.Mockito.verify(queue).remove(CODE_TYPE, "id-right");
        org.mockito.Mockito.verify(dao, org.mockito.Mockito.never()).updateWorkflow(any());
        org.mockito.Mockito.verify(dao, org.mockito.Mockito.never()).updateTask(any());

        // when the first one finishes, whatever the outcome, the next is offered
        TaskResult failed =
                result("id-left", output(1, left.getContinuation().getEpoch(), List.of()));
        failed.setStatus(TaskResult.Status.FAILED);
        service.complete(workflow, left, failed);
        assertEquals(TaskModel.Status.COMPLETED, left.getStatus()); // the task's own result stands
        assertEquals(List.of("id-right"), offered(service.repairAndWake(workflow)));
        assertTrue(
                service.lease(CODE_TYPE, "id-right", dao.getTaskModel("id-right"), "worker-b")
                        != null);
    }

    private static List<String> offered(NativeContinuationService.Wakeups wakeups) {
        return wakeups.offers.stream().map(NativeContinuationService.Offer::messageId).toList();
    }

    @Test
    public void aFinishedContinuationWaitsForTheHandlerLaneAndIsWokenAfterIt() {
        WorkflowMessageQueueDAO queue = mock(WorkflowMessageQueueDAO.class);
        when(queue.size("wf-1")).thenReturn(1L);
        service.setWorkflowMessageQueueDAO(queue);
        WorkflowModel workflow = freshWorkflow(true);
        TaskContinuation bootstrap = workflow.getNativeState().getBootstrap();
        lease(bootstrap, 1);
        Map<String, Object> out = output(1, 1, List.of());
        out.put("done", true);
        out.put("result", Map.of());
        completeHoldingSlot(workflow, null, result("wf-1|main", out));

        // the signal runs first: only the handler lane is woken
        assertEquals(List.of("wf-1|handlers"), offered(service.repairAndWake(workflow)));

        // the handler lane has settled and no message is left: the code's completion runs again
        when(queue.size("wf-1")).thenReturn(0L);
        workflow.getNativeState().getHandlers().setState(TaskContinuation.WAITING);
        assertEquals(List.of("wf-1|main"), offered(service.repairAndWake(workflow)));
    }

    @Test
    public void continuationsFollowTheWorkflowsTaskDomains() {
        com.netflix.conductor.core.dal.ExecutionDAOFacade dao =
                mock(com.netflix.conductor.core.dal.ExecutionDAOFacade.class);
        service.setExecutionDAOFacade(dao);
        when(dao.getTaskPollDataByDomain(CODE_TYPE, "build-1"))
                .thenReturn(
                        new com.netflix.conductor.common.metadata.tasks.PollData(
                                CODE_TYPE, "build-1", "w", System.currentTimeMillis()));
        WorkflowModel workflow = freshWorkflow(false);

        assertEquals(CODE_TYPE, service.queueName(workflow)); // no domains: the shared queue

        workflow.setTaskToDomain(new HashMap<>(Map.of(CODE_TYPE, "gone,build-1,NO_DOMAIN")));
        assertEquals("build-1:" + CODE_TYPE, service.queueName(workflow)); // first with a poller

        workflow.setTaskToDomain(new HashMap<>(Map.of(CODE_TYPE, "gone,NO_DOMAIN")));
        assertEquals(CODE_TYPE, service.queueName(workflow)); // nobody polling: falls back

        workflow.setTaskToDomain(new HashMap<>(Map.of("*", "build-1,NO_DOMAIN")));
        assertEquals("build-1:" + CODE_TYPE, service.queueName(workflow)); // the wildcard applies
    }

    @Test
    public void aStaleCompletionIsRefused() {
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 3);
        try {
            completeHoldingSlot(workflow, awaited, result("id-task_1", output(2, 2, List.of())));
            fail("an older epoch must be refused");
        } catch (ConflictException expected) {
            // superseded lease
        }
        awaited.getContinuation().setState(TaskContinuation.APPLIED);
        try {
            completeHoldingSlot(workflow, awaited, result("id-task_1", output(2, 3, List.of())));
            fail("an applied continuation must not apply twice");
        } catch (ConflictException expected) {
            // duplicate
        }
    }

    @Test
    public void aResultFromAWorkerThatLostTheSlotIsRefused() {
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 3);
        workflow.getNativeState().setActiveContinuation("another-continuation");
        try {
            service.complete(workflow, awaited, result("id-task_1", output(2, 3, List.of())));
            fail("a result computed before another continuation ran must be refused");
        } catch (ConflictException expected) {
            // the continuation runs again
        }
        assertEquals(TaskContinuation.LEASED, awaited.getContinuation().getState());
    }

    @Test
    public void theMainLaneReturningCompletesTheWorkflow() {
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 1);
        Map<String, Object> out = output(2, 1, List.of());
        out.put("done", true);
        out.put("result", Map.of("answer", 42));

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, awaited, result("id-task_1", out));

        // the decider completes the workflow (after anything the fragment appended has run)
        assertFalse(completion.reason, completion.finished);
        assertTrue(awaited.getContinuation().isDone());
        assertEquals(Map.of("answer", 42), awaited.getContinuation().getResult());
        assertFalse(awaited.isExecuted());
    }

    @Test
    public void theMainLaneThrowingFailsTheWorkflowWithTheCause() {
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel awaited = awaitedTask(workflow, "charge", "main", 2, 1);
        Map<String, Object> out = output(2, 1, List.of());
        out.put("done", true);
        out.put("error", Map.of("message", "task charge FAILED: over limit", "ref", "charge"));

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, awaited, result("id-charge", out));

        assertTrue(completion.finished);
        assertEquals("task charge FAILED: over limit", completion.reason);
        assertEquals(awaited, completion.causeTask);
    }

    private com.netflix.conductor.core.dal.ExecutionDAOFacade dao;

    private void useMockedStores(WorkflowModel workflow) {
        dao = mock(com.netflix.conductor.core.dal.ExecutionDAOFacade.class);
        when(dao.getWorkflowModel(anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(workflow);
        service.setQueueDAO(mock(com.netflix.conductor.dao.QueueDAO.class));
        service.setExecutionDAOFacade(dao);
    }

    /** What a decide and then a poll do: offer the next continuation, then hand it out. */
    private void offerAndPoll(WorkflowModel workflow, String messageId, String workerId) {
        assertEquals(List.of(messageId), offered(service.repairAndWake(workflow)));
        assertTrue(
                service.lease(CODE_TYPE, messageId, dao.getTaskModel(messageId), workerId) != null);
    }

    private TaskResult diverged(TaskContinuation marker) {
        TaskResult result =
                result("wf-1|main", output(marker.getN(), marker.getEpoch(), List.of()));
        result.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        result.setReasonForIncompletion("the code calls x but the recording has no such task");
        return result;
    }

    @Test
    public void aHoldLastsAcrossRetriesUntilTheCodeMatches() {
        WorkflowModel workflow = freshWorkflow(false);
        useMockedStores(workflow);
        TaskContinuation marker = workflow.getNativeState().getBootstrap();
        marker.setState(TaskContinuation.RUNNABLE);

        offerAndPoll(workflow, "wf-1|main", "worker-a");
        completeHoldingSlot(workflow, null, diverged(marker));
        long heldSince = marker.getQueuedAt();

        // the retry is still held while it runs, and the hold keeps its start
        offerAndPoll(workflow, "wf-1|main", "worker-a");
        assertTrue(marker.isLeased());
        assertTrue(marker.getDeferred().startsWith("diverged: "));
        completeHoldingSlot(workflow, null, diverged(marker));
        assertEquals(heldSince, marker.getQueuedAt());

        // a build that matches the recording ends the hold
        offerAndPoll(workflow, "wf-1|main", "worker-b");
        completeHoldingSlot(
                workflow,
                null,
                result("wf-1|main", output(marker.getN(), marker.getEpoch(), List.of())));
        assertTrue(marker.isApplied());
        assertEquals(null, marker.getDeferred());
    }

    @Test
    public void anOfferInAQueueNobodyPollsIsMovedToTheQueueWorkersPoll() {
        WorkflowModel workflow = freshWorkflow(false);
        useMockedStores(workflow);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel item = awaitedTask(workflow, "item", "fork_1.17", 1, 0);
        TaskContinuation marker = item.getContinuation();
        marker.setState(TaskContinuation.RUNNABLE);
        when(dao.getTaskModel("id-item")).thenReturn(item);

        offerAndPoll(workflow, "id-item", "worker-a");

        // within its lease, or in the queue resolved now, the offer stands
        long epoch = marker.getEpoch();
        workflow.getNativeState().setActiveUntil(System.currentTimeMillis() - 1);
        service.repairAndWake(workflow);
        assertEquals(epoch, marker.getEpoch());

        // offered in a domain whose workers went away: offered again where workers poll
        marker.setQueue("gone:" + CODE_TYPE);
        assertEquals(List.of("id-item"), offered(service.repairAndWake(workflow)));
        assertEquals(epoch + 1, marker.getEpoch());
        assertEquals(CODE_TYPE, marker.getQueue());
        assertTrue(
                service.lease(CODE_TYPE, "id-item", dao.getTaskModel("id-item"), "worker-b")
                        != null);
    }

    @Test
    public void aDivergedContinuationHoldsTheExecutionAndBacksOff() {
        WorkflowModel workflow = freshWorkflow(false);
        lease(workflow.getNativeState().getBootstrap(), 1);
        TaskResult result = result("wf-1|main", output(1, 1, List.of()));
        result.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        result.setReasonForIncompletion("the code calls x but the recording has no such task");

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, null, result);

        assertFalse(completion.finished); // running, for a rollback or a fixed build to continue
        assertTrue(completion.deferred);
        TaskContinuation marker = workflow.getNativeState().getBootstrap();
        assertTrue(marker.isRunnable());
        assertEquals(
                "diverged: the code calls x but the recording has no such task",
                marker.getDeferred());
        assertEquals(1, completion.retryDelaySeconds);

        // handed out more often, it waits longer, up to a minute
        lease(marker, 2);
        marker.setAttempts(4);
        result = result("wf-1|main", output(1, 2, List.of()));
        result.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        assertEquals(8, completeHoldingSlot(workflow, null, result).retryDelaySeconds);
        lease(marker, 3);
        marker.setAttempts(30);
        result = result("wf-1|main", output(1, 3, List.of()));
        result.setStatus(TaskResult.Status.FAILED);
        assertEquals(60, completeHoldingSlot(workflow, null, result).retryDelaySeconds);
    }

    @Test
    public void aWorkerErrorReleasesTheLeaseForAnotherRun() {
        WorkflowModel workflow = freshWorkflow(false);
        lease(workflow.getNativeState().getBootstrap(), 1);
        TaskResult result = result("wf-1|main", output(1, 1, List.of()));
        result.setStatus(TaskResult.Status.FAILED);

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, null, result);

        assertTrue(completion.deferred);
        assertTrue(workflow.getNativeState().getBootstrap().isRunnable());
    }

    @Test
    public void completionIsDeferredWhileMessagesArePending() {
        WorkflowMessageQueueDAO queue = mock(WorkflowMessageQueueDAO.class);
        when(queue.size("wf-1")).thenReturn(2L);
        service.setWorkflowMessageQueueDAO(queue);
        WorkflowModel workflow = freshWorkflow(true);
        lease(workflow.getNativeState().getBootstrap(), 1);
        Map<String, Object> out = output(1, 1, List.of());
        out.put("done", true);
        out.put("result", Map.of());

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, null, result("wf-1|main", out));

        assertTrue(completion.deferred);
        assertTrue(completion.reason.contains("2 message(s)"));
        assertTrue(completion.waitsForHandlers); // not offered again until that lane has run
        TaskContinuation bootstrap = workflow.getNativeState().getBootstrap();
        assertTrue(bootstrap.isRunnable());
        assertNull(bootstrap.getWorker());

        // a leased handler run also defers
        when(queue.size("wf-1")).thenReturn(0L);
        lease(bootstrap, 2);
        lease(workflow.getNativeState().getHandlers(), 1);
        completion =
                completeHoldingSlot(workflow, null, result("wf-1|main", output(1, 2, List.of())));
        assertFalse(completion.deferred); // not done: nothing to defer
        lease(bootstrap, 3);
        out.put("epoch", 3);
        completion = completeHoldingSlot(workflow, null, result("wf-1|main", out));
        assertTrue(completion.deferred);
        assertTrue(completion.reason.contains("handler run"));
    }

    @Test
    public void appliesOnlyChangedVariablesAndDefersOnAConflict() {
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getVariables().put("deposits", List.of("dep-1")); // written by a handler
        workflow.getVariables().put("stage", "a");
        lease(workflow.getNativeState().getBootstrap(), 1);
        Map<String, Object> out = output(1, 1, List.of());
        out.put("variables", Map.of("deposits", List.of(), "stage", "b"));
        out.put("resumedWith", Map.of("deposits", List.of(), "stage", "a"));

        completeHoldingSlot(workflow, null, result("wf-1|main", out));
        assertEquals(List.of("dep-1"), workflow.getVariables().get("deposits"));
        assertEquals("b", workflow.getVariables().get("stage"));

        // the same continuation changing a variable another completion changed since it read it
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 1);
        workflow.getVariables().put("stage", "c");
        Map<String, Object> conflicting = output(2, 1, List.of());
        conflicting.put("variables", Map.of("stage", "d"));
        conflicting.put("resumedWith", Map.of("stage", "b"));
        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, awaited, result("id-task_1", conflicting));
        assertTrue(completion.deferred);
        assertTrue(completion.reason.contains("stage"));
        assertEquals("c", workflow.getVariables().get("stage"));
    }

    @Test
    public void rejectsFragmentsThatBreakTheRules() {
        List<Map<String, Object>> bads = new ArrayList<>();
        bads.add(entry("t", "TERMINATE", "main", null, 0));
        bads.add(entry("t", "NO_SUCH_TYPE", "main", null, 0));
        bads.add(entry(CODE_TYPE, "SIMPLE", "main", null, 0)); // named after the code type
        bads.add(new HashMap<>(Map.of("name", "t", "taskReferenceName", "t", "type", "SIMPLE")));
        for (Map<String, Object> bad : bads) {
            WorkflowModel workflow = freshWorkflow(false);
            lease(workflow.getNativeState().getBootstrap(), 1);
            NativeContinuationService.Completion completion =
                    completeHoldingSlot(
                            workflow, null, result("wf-1|main", output(1, 1, List.of(bad))));
            assertTrue(bad.toString(), completion.finished);
            assertTrue(workflow.getWorkflowDefinition().getTasks().isEmpty());
        }
    }

    /** A SWITCH entry whose one case holds {@code children}. */
    private static Map<String, Object> switchOf(String ref, List<Map<String, Object>> children) {
        Map<String, Object> sw = entry(ref, "SWITCH", "main", null, 0);
        sw.put("evaluatorType", "native-code");
        sw.put("expression", "switchCaseValue");
        sw.put("inputParameters", Map.of("switchCaseValue", "a"));
        sw.put("decisionCases", Map.of("a", children));
        return sw;
    }

    @Test
    public void aSwitchTheServerWouldEvaluateIsRejected() {
        // a native workflow's switches are decided by its code
        WorkflowModel workflow = freshWorkflow(false);
        lease(workflow.getNativeState().getBootstrap(), 1);
        Map<String, Object> sw = switchOf("route", List.of(entry("a", "SIMPLE", "main", null, 0)));
        sw.put("evaluatorType", "graaljs");
        sw.put("expression", "$.region == 'eu' ? 'a' : 'b'");

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, null, result("wf-1|main", output(1, 1, List.of(sw))));

        assertTrue(completion.finished);
        assertTrue(
                completion.reason, completion.reason.contains("evaluatorType must be native-code"));
        assertTrue(workflow.getWorkflowDefinition().getTasks().isEmpty());
    }

    @Test
    public void nestedEntriesCountTowardsTheDefinitionLimit() {
        properties.getNativeWorkflow().setMaxDefinitionTasks(5);
        List<Map<String, Object>> children = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            children.add(entry("c" + i, "SIMPLE", "main", null, 0));
        }
        WorkflowModel workflow = freshWorkflow(false);
        lease(workflow.getNativeState().getBootstrap(), 1);
        NativeContinuationService.Completion completion =
                completeHoldingSlot(
                        workflow,
                        null,
                        result("wf-1|main", output(1, 1, List.of(switchOf("sw", children)))));
        assertTrue(completion.finished);
        assertTrue(
                completion.reason, completion.reason.contains("6 tasks, more than the limit of 5"));
        assertTrue(workflow.getWorkflowDefinition().getTasks().isEmpty());
    }

    @Test
    public void aRejectedFragmentChangesNothingAndARunMayAnchorOnAnEarlierRun() {
        // a run may go after an entry nested in an earlier run of the same fragment
        WorkflowModel workflow = freshWorkflow(false);
        lease(workflow.getNativeState().getBootstrap(), 1);
        Map<String, Object> after = entry("next", "SIMPLE", "main", "main", 2);
        ((Map<String, Object>) after.get("metadata")).put("after", "inner");
        Map<String, Object> sw = switchOf("sw", List.of(entry("inner", "SIMPLE", "main", null, 0)));
        NativeContinuationService.Completion ok =
                completeHoldingSlot(
                        workflow, null, result("wf-1|main", output(1, 1, List.of(sw, after))));
        assertFalse(ok.finished);
        assertEquals(
                List.of("inner", "next"),
                refs(
                        workflow.getWorkflowDefinition()
                                .getTasks()
                                .get(0)
                                .getDecisionCases()
                                .get("a")));

        // a run whose anchor is nowhere: nothing of the fragment, and no variable, is written
        WorkflowModel other = freshWorkflow(false);
        lease(other.getNativeState().getBootstrap(), 1);
        Map<String, Object> lost = entry("later", "SIMPLE", "main", "main", 2);
        ((Map<String, Object>) lost.get("metadata")).put("after", "nowhere");
        Map<String, Object> out =
                output(1, 1, List.of(entry("first", "SIMPLE", "main", null, 0), lost));
        out.put("variables", Map.of("x", 1));
        NativeContinuationService.Completion rejected =
                completeHoldingSlot(other, null, result("wf-1|main", out));
        assertTrue(rejected.finished);
        assertTrue(rejected.reason, rejected.reason.contains("nowhere"));
        assertTrue(other.getWorkflowDefinition().getTasks().isEmpty());
        assertTrue(other.getVariables().isEmpty());
    }

    @Test
    public void variablesOverTheLimitChangeNothing() {
        properties.setMaxWorkflowVariablesPayloadSizeThreshold(
                org.springframework.util.unit.DataSize.ofBytes(100));
        WorkflowModel workflow = freshWorkflow(false);
        lease(workflow.getNativeState().getBootstrap(), 1);
        Map<String, Object> out =
                output(1, 1, List.of(entry("first", "SIMPLE", "main", "main", 2)));
        out.put("variables", Map.of("big", "x".repeat(500)));
        NativeContinuationService.Completion rejected =
                completeHoldingSlot(workflow, null, result("wf-1|main", out));
        assertTrue(rejected.finished);
        assertTrue(rejected.reason, rejected.reason.contains("variables payload"));
        assertTrue(workflow.getWorkflowDefinition().getTasks().isEmpty());
        assertTrue(workflow.getVariables().isEmpty());
    }

    @Test
    public void anExceptionTheCodeThrewIsAFailedTaskWhereItThrew() {
        WorkflowModel workflow = freshWorkflow(false);
        TaskModel lookup = awaitedTask(workflow, "lookup", "main", 2, 3);
        Map<String, Object> thrown = entry("throw", "TERMINATE", "main", null, 0);
        thrown.put("name", "IllegalArgumentException");
        thrown.put(
                "inputParameters",
                Map.of("terminationStatus", "FAILED", "terminationReason", "no items"));
        Map<String, Object> out = output(2, 3, List.of(thrown));
        out.put("done", true);
        out.put(
                "error",
                Map.of(
                        "type",
                        "java.lang.IllegalArgumentException",
                        "message",
                        "IllegalArgumentException: no items",
                        "ref",
                        "throw"));

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, lookup, result("id-lookup", out));

        assertTrue(completion.finished);
        TaskModel cause = completion.causeTask;
        assertEquals("throw", cause.getReferenceTaskName());
        assertEquals("TERMINATE", cause.getTaskType());
        assertEquals("IllegalArgumentException", cause.getTaskDefName());
        assertEquals(TaskModel.Status.FAILED, cause.getStatus());
        assertEquals("IllegalArgumentException: no items", cause.getReasonForIncompletion());
        assertTrue(workflow.getTasks().contains(cause));
        assertEquals(
                "throw", workflow.getWorkflowDefinition().getTasks().get(1).getTaskReferenceName());
        assertEquals(
                "the task before it stays as it ran",
                TaskModel.Status.COMPLETED,
                lookup.getStatus());
    }

    @Test
    public void anUncaughtTaskFailureShowsItsTaskFailed() {
        WorkflowModel workflow = freshWorkflow(false);
        TaskModel charge = awaitedTask(workflow, "charge", "main", 2, 3);
        charge.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
        charge.getOutputData().put("conductor.failedStatus", "FAILED_WITH_TERMINAL_ERROR");
        Map<String, Object> out = output(2, 3, List.of());
        out.put("done", true);
        out.put(
                "error",
                Map.of("type", "TaskFailure", "message", "task charge FAILED", "ref", "charge"));

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, charge, result("id-charge", out));

        assertSame(charge, completion.causeTask);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, charge.getStatus());
    }

    @Test
    public void handlerCompletionsCarryRepliesSeenAndReleases() {
        WorkflowModel workflow = freshWorkflow(true);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskContinuation handlers = workflow.getNativeState().getHandlers();
        handlers.setN(4);
        lease(handlers, 7);
        // a condition wait the handler found satisfied
        TaskModel wait = new TaskModel();
        wait.setTaskId("id-condition");
        wait.setReferenceTaskName("condition");
        wait.setTaskType("WAIT");
        wait.setStatus(TaskModel.Status.IN_PROGRESS);
        wait.setSeq(5);
        workflow.getTasks().add(wait);
        Map<String, Object> out = output(4, 7, List.of());
        workflow.getVariables().put("notes", List.of());
        out.put("variables", Map.of("notes", List.of("first")));
        out.put("resumedWith", Map.of("notes", List.of()));
        out.put("replies", Map.of("m-1", Map.of("value", 1, "at", 5L)));
        out.put("seen", List.of("m-1"));
        out.put(
                "releases",
                List.of(Map.of("ref", "condition", "output", Map.of("satisfied", true))));

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, null, result("wf-1|handlers", out));

        assertFalse(completion.deferred);
        assertEquals(List.of("first"), workflow.getVariables().get("notes"));
        Map<?, ?> reply = (Map<?, ?>) workflow.getNativeState().getReplies().get("m-1");
        assertEquals(1, ((Number) reply.get("value")).intValue());
        assertEquals(5, ((Number) reply.get("at")).intValue());
        assertEquals(List.of("m-1"), workflow.getNativeState().getSeen());
        assertEquals(TaskModel.Status.COMPLETED, wait.getStatus());
        assertFalse(wait.isExecuted());
        assertTrue(completion.touchedTasks.contains(wait));
        // the lane is ready for its next run
        assertEquals(5, workflow.getNativeState().getHandlers().getN());
        assertTrue(workflow.getNativeState().getHandlers().isWaiting());
    }

    @Test
    public void markersOnEvaluatedTasksWakeAndQueuedMessagesWakeHandlers() {
        WorkflowModel workflow = freshWorkflow(true);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 0);
        awaited.getContinuation().setState(TaskContinuation.WAITING);
        awaited.setExecuted(false);
        assertFalse(offered(service.repairAndWake(workflow)).contains("id-task_1"));
        assertTrue(awaited.getContinuation().isWaiting()); // not evaluated yet

        awaited.setExecuted(true); // the decider evaluated it
        TaskModel forgotten = awaitedTask(workflow, "task_2", "fork_1.0", 1, 0);
        forgotten.getContinuation().setState(TaskContinuation.WAITING);
        WorkflowMessageQueueDAO queue = mock(WorkflowMessageQueueDAO.class);
        when(queue.size("wf-1")).thenReturn(1L);
        service.setWorkflowMessageQueueDAO(queue);

        NativeContinuationService.Wakeups wakeups = service.repairAndWake(workflow);

        assertTrue(awaited.getContinuation().isLeased()); // offered
        assertTrue(forgotten.getContinuation().isRunnable());
        assertTrue(wakeups.touchedTasks.contains(forgotten));
        assertTrue(wakeups.workflowTouched);
        assertTrue(workflow.getNativeState().getHandlers().isRunnable());
        // one continuation is offered at a time: the one that waited longest
        assertEquals(List.of("id-task_1"), offered(wakeups));
    }

    @Test
    public void lanesSettledReadsTheMarkers() {
        WorkflowModel workflow = freshWorkflow(false);
        TaskModel a = awaitedTask(workflow, "a1", "fork_1.0", 2, 1);
        TaskModel b = awaitedTask(workflow, "b1", "fork_1.1", 2, 1);
        b.setContinuation(null); // the lane ended with this task without pausing

        a.getContinuation().setState(TaskContinuation.WAITING);
        assertFalse(NativeContinuationService.lanesSettled(workflow, List.of("a1", "b1")));
        a.getContinuation().setState(TaskContinuation.LEASED);
        assertFalse(NativeContinuationService.lanesSettled(workflow, List.of("a1", "b1")));
        a.getContinuation().setState(TaskContinuation.APPLIED); // appended more: not the tail
        assertFalse(NativeContinuationService.lanesSettled(workflow, List.of("a1", "b1")));
        a.getContinuation().setDone(true);
        assertTrue(NativeContinuationService.lanesSettled(workflow, List.of("a1", "b1")));
        a.setStatus(TaskModel.Status.IN_PROGRESS);
        assertFalse(NativeContinuationService.lanesSettled(workflow, List.of("a1", "b1")));
        assertFalse(NativeContinuationService.lanesSettled(workflow, List.of("missing")));
    }

    /**
     * A task that ended at {@code endedAt}, whose continuation was applied at {@code appliedAt}.
     */
    private static TaskModel ran(
            WorkflowModel workflow, String ref, String lane, int n, long endedAt, long appliedAt) {
        TaskModel task = awaitedTask(workflow, ref, lane, n, 1);
        task.setEndTime(endedAt);
        if (appliedAt > 0) {
            task.getContinuation().setState(TaskContinuation.APPLIED);
            task.getContinuation().setAppliedAt(appliedAt);
        }
        return task;
    }

    private static void appendedBy(TaskModel task, String marker) {
        task.getWorkflowTask().getMetadata().put(NativeContinuationService.APPENDED_BY, marker);
    }

    @Test
    public void rerunUndoesWhatTheCodeDidAfterTheTask() {
        WorkflowModel workflow = freshWorkflow(false);
        workflow.getNativeState().getBootstrap().setState(TaskContinuation.APPLIED);
        TaskModel t1 = ran(workflow, "task_1", "main", 2, 100, 110);
        appendedBy(t1, "main#1");
        TaskModel t2 = ran(workflow, "task_2", "main", 3, 200, 210);
        appendedBy(t2, "main#2");
        TaskModel t3 = ran(workflow, "task_3", "main", 4, 0, 0);
        appendedBy(t3, "main#3");

        NativeContinuationService.RerunPlan plan = service.planRerun(workflow, t2);

        assertEquals(Set.of("id-task_3"), plan.dropTaskIds());
        assertEquals(Set.of("task_3"), plan.dropEntries());
        assertEquals(Set.of("main#3"), plan.resetMarkers());
        workflow.getTasks().remove(t3);
        List<TaskModel> touched = new ArrayList<>();
        service.adjustRerun(workflow, t2, plan, touched);
        assertEquals(
                List.of("task_1", "task_2"), refs(workflow.getWorkflowDefinition().getTasks()));
        assertTrue(t2.getContinuation().isWaiting()); // runs again when task_2 completes again
        assertTrue(t1.getContinuation().isApplied()); // ran before task_2 ended: nothing to redo
        assertTrue(workflow.getNativeState().getBootstrap().isApplied());
    }

    /**
     * fork(x | y), join, after: the bootstrap appended the fork, both branches and the join; the
     * join's continuation appended after.
     */
    private static List<TaskModel> forked(WorkflowModel workflow) {
        TaskModel x = ran(workflow, "x", "a", 2, 100, 105);
        TaskModel y = ran(workflow, "y", "b", 2, 150, 160);
        TaskModel join = ran(workflow, "fork_join", "main", 2, 170, 175);
        join.setTaskType(TaskType.TASK_TYPE_JOIN);
        TaskModel after = ran(workflow, "after", "main", 3, 0, 0);
        appendedBy(after, "main#2");
        List<WorkflowTask> top = workflow.getWorkflowDefinition().getTasks();
        WorkflowTask fork = new WorkflowTask();
        fork.setName("fork");
        fork.setTaskReferenceName("fork");
        fork.setType("FORK_JOIN");
        fork.getMetadata().put(NativeContinuationService.APPENDED_BY, "main#1");
        fork.setForkTasks(
                new ArrayList<>(
                        List.of(
                                new ArrayList<>(List.of(top.get(0))),
                                new ArrayList<>(List.of(top.get(1))))));
        WorkflowTask joinEntry = top.get(2);
        joinEntry.setType("JOIN");
        joinEntry.setJoinOn(List.of("x", "y"));
        joinEntry.getMetadata().put(NativeContinuationService.APPENDED_BY, "main#1");
        WorkflowTask afterEntry = top.get(3);
        top.clear();
        top.addAll(List.of(fork, joinEntry, afterEntry));
        return List.of(x, y, join, after);
    }

    @Test
    public void rerunFromABranchKeepsWhatTheOtherBranchDidBeforeTheTaskEnded() {
        WorkflowModel workflow = freshWorkflow(false);
        List<TaskModel> tasks = forked(workflow);
        TaskModel x = tasks.get(0);
        TaskModel y = tasks.get(1);
        TaskModel join = tasks.get(2);

        NativeContinuationService.RerunPlan plan = service.planRerun(workflow, x);

        // x ended at 100: y's continuation (160) and the join's (175) may have seen it
        assertEquals(Set.of("a#2", "b#2", "main#2"), plan.resetMarkers());
        assertEquals(Set.of("id-after"), plan.dropTaskIds());
        assertEquals(Set.of("after"), plan.dropEntries());
        workflow.getTasks().remove(tasks.get(3));
        List<TaskModel> touched = new ArrayList<>();
        service.adjustRerun(workflow, x, plan, touched);
        assertEquals(
                List.of("fork", "fork_join"), refs(workflow.getWorkflowDefinition().getTasks()));
        assertTrue(x.getContinuation().isWaiting());
        assertTrue(y.getContinuation().isWaiting()); // its task stays; its code runs again
        assertTrue(join.getContinuation().isWaiting());
        assertTrue(touched.containsAll(List.of(y, join)));

        // from y, which ended at 150: x's continuation ran before that and stays
        WorkflowModel second = freshWorkflow(false);
        List<TaskModel> again = forked(second);
        plan = service.planRerun(second, again.get(1));
        assertEquals(Set.of("b#2", "main#2"), plan.resetMarkers());
    }

    @Test
    public void rerunAfterASwitchKeepsItsCasesAndTheCaseThatRan() {
        WorkflowModel workflow = freshWorkflow(false);
        TaskModel route = ran(workflow, "route", "main", 2, 50, 0);
        route.setContinuation(null);
        appendedBy(route, "main#1");
        TaskModel inCase = ran(workflow, "in_case", "main", 2, 100, 110);
        appendedBy(inCase, "main#1");
        TaskModel after = ran(workflow, "after", "main", 3, 200, 0);
        appendedBy(after, "main#2");
        List<WorkflowTask> top = workflow.getWorkflowDefinition().getTasks();
        WorkflowTask decision = top.get(0);
        decision.setType("SWITCH");
        WorkflowTask drawn = new WorkflowTask();
        drawn.setName("other");
        drawn.setTaskReferenceName("other_if_y");
        drawn.setType("SIMPLE");
        drawn.getMetadata().put("preview", true);
        decision.setDecisionCases(
                new java.util.LinkedHashMap<>(
                        Map.of(
                                "x",
                                new ArrayList<>(List.of(top.remove(1))),
                                "y",
                                new ArrayList<>(List.of(drawn)))));

        NativeContinuationService.RerunPlan plan = service.planRerun(workflow, after);
        service.adjustRerun(workflow, after, plan, new ArrayList<>());

        assertTrue(plan.dropTaskIds().isEmpty());
        assertEquals(List.of("other_if_y"), refs(decision.getDecisionCases().get("y")));
        assertTrue(inCase.getContinuation().isApplied());
        assertTrue(after.getContinuation().isWaiting());
    }

    @Test
    public void rerunFromATaskSchedulesWhatFollowsItInItsListAgain() {
        WorkflowModel workflow = freshWorkflow(false);
        // one continuation appended both: f1 does not pause, so f2 follows it in the same list
        TaskModel f1 = ran(workflow, "f1", "main", 2, 100, 0);
        f1.setContinuation(null);
        appendedBy(f1, "main#1");
        TaskModel f2 = ran(workflow, "f2", "main", 2, 120, 0);
        appendedBy(f2, "main#1");

        NativeContinuationService.RerunPlan plan = service.planRerun(workflow, f1);

        assertEquals(Set.of("id-f2"), plan.dropTaskIds()); // scheduled again from its entry
        assertTrue(plan.dropEntries().isEmpty());
    }

    @Test
    public void mapperCopiesTheMarkerOntoScheduledTasks() {
        WorkflowTask entry = new WorkflowTask();
        entry.setName("task_1");
        entry.setTaskReferenceName("task_1");
        entry.setType(TaskType.TASK_TYPE_SIMPLE);
        entry.getMetadata().put("lane", "main");
        entry.getMetadata().put("continuation", Map.of("lane", "main", "n", 2));
        WorkflowModel workflow = freshWorkflow(false);
        TaskModel task =
                com.netflix.conductor.core.execution.mapper.TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withWorkflowTask(entry)
                        .withTaskId("t-1")
                        .build()
                        .createTaskModel();
        assertEquals("main", task.getContinuation().getLane());
        assertEquals(2, task.getContinuation().getN());
        assertTrue(task.getContinuation().isWaiting());
    }
}
