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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.util.unit.DataSize;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.execution.mapper.NoopTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
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
        metadata.put("lane", pauseLane != null ? pauseLane : lane);
        if (pauseLane != null) {
            metadata.put("continuation", n); // the lane's n-th continuation follows this task
        }
        e.put("metadata", metadata);
        return e;
    }

    private static Map<String, Object> output(
            int n, int retryCount, List<Map<String, Object>> fragment) {
        Map<String, Object> out = new HashMap<>();
        out.put("protocol", 2);
        out.put("n", n);
        out.put("retryCount", retryCount);
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

    private static void lease(TaskContinuation marker, int retryCount) {
        marker.setLeased(true);
        marker.setRetryCount(retryCount);
    }

    /** Completes a continuation as the worker that holds its lease, the execution's slot. */
    private NativeContinuationService.Completion completeHoldingSlot(
            WorkflowModel workflow, TaskModel awaited, TaskResult result) {
        return service.complete(workflow, awaited, result);
    }

    /** A completed task the code paused on, with its marker leased. */
    private static TaskModel awaitedTask(
            WorkflowModel workflow, String ref, String lane, int n, int retryCount) {
        WorkflowTask entry = new WorkflowTask();
        entry.setName(ref);
        entry.setTaskReferenceName(ref);
        entry.setType("SIMPLE");
        entry.getMetadata().put("lane", lane);
        entry.getMetadata().put("continuation", n);
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
        TaskContinuation marker = new TaskContinuation();
        lease(marker, retryCount);
        task.setContinuation(marker);
        workflow.getTasks().add(task);
        return task;
    }

    /** As a continuation that applied. */
    private static void applied(TaskContinuation marker) {
        marker.setLeased(false);
        marker.setEndTime(1);
    }

    /** A continuation not yet run: neither leased nor applied. */
    private static void assertNotRun(TaskContinuation marker) {
        assertFalse(marker.toString(), marker.isLeased());
        assertFalse(marker.toString(), marker.isApplied());
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
        assertEquals(CODE_TYPE, NativeContinuationService.codeType(workflow));
        assertNotRun(state.getBootstrap());
        assertNotRun(state.getHandlers());
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
        assertTrue(bootstrap.getEndTime() > 0);
        assertEquals("r-1", bootstrap.getRecorded().get("runId"));
        assertEquals("new", bootstrap.getResumedWith().get("stage"));
        assertEquals("classic", workflow.getVariables().get("stage"));
        assertEquals(CODE_TYPE, completion.ackQueue); // the code type's queue: no domains
        assertEquals("wf-1|main", completion.ackId);
        // the entry the code paused on carries the next continuation's number for the mapper
        assertEquals(
                2,
                workflow.getWorkflowDefinition()
                        .getTasks()
                        .get(0)
                        .getMetadata()
                        .get("continuation"));
    }

    @Test
    public void aCompletionOnATaskInsertsAfterItAndReEvaluatesIt() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
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
        applied(workflow.getNativeState().getBootstrap());
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
        applied(workflow.getNativeState().getBootstrap());
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
        applied(workflow.getNativeState().getBootstrap());
        TaskModel left = awaitedTask(workflow, "left", "fork_1.0", 1, 0);
        TaskModel right = awaitedTask(workflow, "right", "fork_1.1", 1, 0);
        left.getContinuation().setLeased(false);
        left.setEndTime(1);
        right.getContinuation().setLeased(false);
        right.setEndTime(2);
        QueueDAO queue = mock(QueueDAO.class);
        ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);
        when(dao.getTaskModel("id-left")).thenReturn(left);
        when(dao.getTaskModel("id-right")).thenReturn(right);
        when(dao.getWorkflowModel(anyString(), ArgumentMatchers.anyBoolean())).thenReturn(workflow);
        service.setQueueDAO(queue);
        service.setExecutionDAOFacade(dao);

        // the decide offers the one that waited longest
        NativeContinuationService.Wakeups wakeups = service.repairAndWake(workflow);
        assertEquals(List.of("id-left"), offered(wakeups));
        assertTrue(left.getContinuation().isLeased());
        assertEquals(0, left.getContinuation().getRetryCount()); // a first offer
        assertNotRun(right.getContinuation());
        assertEquals(List.of("id-left"), offered(service.repairAndWake(workflow))); // held

        // the poll hands out the offer and writes nothing; anything else is a stale message
        assertTrue(
                service.lease(CODE_TYPE, "id-left", dao.getTaskModel("id-left"), "worker-a")
                        != null);
        assertEquals(
                null,
                service.lease(CODE_TYPE, "id-right", dao.getTaskModel("id-right"), "worker-b"));
        Mockito.verify(queue).remove(CODE_TYPE, "id-right");
        Mockito.verify(dao, Mockito.never()).updateWorkflow(any());
        Mockito.verify(dao, Mockito.never()).updateTask(any());

        // a worker error: it keeps its turn, the order replay follows is the order tasks ended
        TaskResult failed =
                result("id-left", output(1, left.getContinuation().getRetryCount(), List.of()));
        failed.setStatus(TaskResult.Status.FAILED);
        service.complete(workflow, left, failed);
        assertEquals(TaskModel.Status.COMPLETED, left.getStatus()); // the task's own result stands
        assertEquals(List.of("id-left"), offered(service.repairAndWake(workflow)));

        // once it applied, the next is offered
        service.complete(
                workflow,
                left,
                result("id-left", output(1, left.getContinuation().getRetryCount(), List.of())));
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
        workflow.getNativeState().setHandlers(TaskContinuation.handlerRun(2));
        assertEquals(List.of("wf-1|main"), offered(service.repairAndWake(workflow)));
    }

    @Test
    public void theHandlerLaneGoesBeforeACodeContinuationThatWaitedLonger() {
        WorkflowMessageQueueDAO queue = mock(WorkflowMessageQueueDAO.class);
        when(queue.size("wf-1")).thenReturn(1L);
        service.setWorkflowMessageQueueDAO(queue);
        WorkflowModel workflow = freshWorkflow(true);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);
        task.getContinuation().setLeased(false);
        task.setEndTime(1); // runnable long before the message came

        // messages are handled before the code continues
        assertEquals(List.of("wf-1|handlers"), offered(service.repairAndWake(workflow)));
        assertNotRun(task.getContinuation());
    }

    @Test
    public void continuationsIgnoreTaskDomains() {
        WorkflowModel workflow = freshWorkflow(false);
        assertEquals(CODE_TYPE, service.queueName(workflow));

        // a run started with domains for its tasks, or for every type, still continues in the
        // shared queue, which every host of the code polls
        workflow.setTaskToDomain(new HashMap<>(Map.of(CODE_TYPE, "build-1", "*", "blue")));
        assertEquals(CODE_TYPE, service.queueName(workflow));
    }

    @Test
    public void aStaleCompletionIsRefused() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 3);
        try {
            completeHoldingSlot(workflow, awaited, result("id-task_1", output(2, 2, List.of())));
            fail("an earlier attempt must be refused");
        } catch (ConflictException expected) {
            // superseded lease
        }
        applied(awaited.getContinuation());
        try {
            completeHoldingSlot(workflow, awaited, result("id-task_1", output(2, 3, List.of())));
            fail("an applied continuation must not apply twice");
        } catch (ConflictException expected) {
            // duplicate
        }
    }

    @Test
    public void aCompletionMustNameTheRetryCountOfTheOfferItAnswers() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 3);
        TaskContinuation marker = awaited.getContinuation();
        List<Map<String, Object>> fragment = List.of(entry("task_2", "SIMPLE", "main", "main", 3));
        Map<String, Object> unnamed = output(2, 3, fragment);
        unnamed.remove("retryCount");

        // an earlier attempt, a later one, or none named: refused, and nothing is written
        for (Map<String, Object> out :
                List.of(output(2, 2, fragment), output(2, 4, fragment), unnamed)) {
            try {
                completeHoldingSlot(workflow, awaited, result("id-task_1", out));
                fail("retryCount " + out.get("retryCount") + " does not answer the offer at 3");
            } catch (ConflictException expected) {
                // not the current attempt
            }
            assertTrue(marker.isLeased());
            assertEquals(3, marker.getRetryCount());
            assertFalse(marker.isApplied());
            assertEquals(List.of("task_1"), refs(workflow.getWorkflowDefinition().getTasks()));
        }

        completeHoldingSlot(workflow, awaited, result("id-task_1", output(2, 3, fragment)));
        assertTrue(marker.isApplied());
        assertEquals(
                List.of("task_1", "task_2"), refs(workflow.getWorkflowDefinition().getTasks()));
    }

    @Test
    public void aDeferredAttemptCannotApplyOnceItsRetryIsOffered() {
        WorkflowModel workflow = freshWorkflow(false);
        useMockedStores(workflow);
        TaskContinuation bootstrap = workflow.getNativeState().getBootstrap();
        bootstrap.setLeased(false);
        List<Map<String, Object>> fragment = List.of(entry("task_1", "SIMPLE", "main", "main", 2));

        // worker A's attempt fails; the server, not the worker, counts the retry
        offerAndPoll(workflow, "wf-1|main", "worker-a");
        TaskResult failed = result("wf-1|main", output(1, 0, List.of()));
        failed.setStatus(TaskResult.Status.FAILED);
        assertTrue(completeHoldingSlot(workflow, null, failed).deferred);
        assertEquals(1, bootstrap.getRetryCount());

        // the retry goes to worker B; a late completion of A's attempt, at 0, is refused
        offerAndPoll(workflow, "wf-1|main", "worker-b");
        try {
            completeHoldingSlot(workflow, null, result("wf-1|main", output(1, 0, fragment)));
            fail("the attempt at 0 was superseded by the retry at 1");
        } catch (ConflictException expected) {
            // superseded
        }
        assertTrue(bootstrap.isLeased());
        assertTrue(workflow.getWorkflowDefinition().getTasks().isEmpty());

        // B's completion names the retry and applies
        completeHoldingSlot(workflow, null, result("wf-1|main", output(1, 1, fragment)));
        assertTrue(bootstrap.isApplied());
        assertEquals(List.of("task_1"), refs(workflow.getWorkflowDefinition().getTasks()));
    }

    @Test
    public void appliedTimesKeepIncreasingWithinARunWhateverTheClockSays() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
        // applied on a server whose clock was a minute ahead
        long ahead = System.currentTimeMillis() + 60_000;
        TaskModel earlier = awaitedTask(workflow, "task_1", "main", 2, 1);
        applied(earlier.getContinuation());
        earlier.getContinuation().setEndTime(ahead);
        TaskModel awaited = awaitedTask(workflow, "task_2", "fork_1.0", 1, 1);

        completeHoldingSlot(workflow, awaited, result("id-task_2", output(1, 1, List.of())));

        // replay follows these times: the later apply must sort after the earlier one
        assertEquals(ahead + 1, awaited.getContinuation().getEndTime());
    }

    @Test
    public void aCompletionOfAnOfferMadeBeforeARerunIsRefusedAfterTheTaskIsOfferedAgain() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 1);
        // a rerun from the task: same task id, a fresh marker, then offered again
        task.setContinuation(task.getContinuation().reset());
        task.getContinuation().setLeased(false);
        service.offer(workflow, task.getContinuation(), task.getTaskId(), 0);
        assertEquals(2, task.getContinuation().getRetryCount()); // the rerun is another attempt

        // the worker that held the offer from before the rerun reports late
        try {
            service.complete(workflow, task, result("id-task_1", output(2, 1, List.of())));
            fail("a completion of an offer made before the rerun must be refused");
        } catch (ConflictException expected) {
            // the continuation runs again under the new offer
        }
        assertTrue(task.getContinuation().isLeased());
    }

    @Test
    public void aRestartIsARetryOfTheLanesWithoutATask() {
        WorkflowModel workflow = freshWorkflow(true);
        workflow.getNativeState().getBootstrap().setRetryCount(4);
        workflow.getNativeState().getHandlers().setN(3);
        workflow.getNativeState().getHandlers().setRetryCount(2);

        service.initialize(workflow); // a restart: <workflowId>|main and |handlers stay the same

        TaskContinuation bootstrap = workflow.getNativeState().getBootstrap();
        assertNotRun(bootstrap);
        assertEquals(5, bootstrap.getRetryCount());
        assertEquals(3, workflow.getNativeState().getHandlers().getN());
        assertEquals(3, workflow.getNativeState().getHandlers().getRetryCount());
        assertNotRun(workflow.getNativeState().getHandlers());
    }

    @Test
    public void whileAContinuationIsLeasedNoOtherIsOffered() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel leased = awaitedTask(workflow, "task_1", "main", 2, 3);
        TaskModel other = awaitedTask(workflow, "task_2", "fork_1.0", 1, 0);
        other.getContinuation().setLeased(false);

        // the leased marker is the slot: its message is pushed again, under the same offer
        assertEquals(List.of("id-task_1"), offered(service.repairAndWake(workflow)));
        assertEquals(3, leased.getContinuation().getRetryCount());
        assertNotRun(other.getContinuation());

        // once it applied, the other is offered
        applied(leased.getContinuation());
        assertEquals(List.of("id-task_2"), offered(service.repairAndWake(workflow)));
        assertTrue(other.getContinuation().isLeased());
    }

    @Test
    public void theMainLaneReturningCompletesTheWorkflow() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 1);
        Map<String, Object> out = output(2, 1, List.of());
        out.put("done", true);
        out.put("result", Map.of("answer", 42));

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, awaited, result("id-task_1", out));

        // the decider completes the workflow (after anything the fragment appended has run)
        assertFalse(completion.reason, completion.finished);
        assertTrue(awaited.getContinuation().isDone());
        assertEquals(Map.of("answer", 42), workflow.getOutput()); // what the code returned
        assertFalse(awaited.isExecuted());
    }

    @Test
    public void theMainLaneThrowingFailsTheWorkflowWithTheCause() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel awaited = awaitedTask(workflow, "charge", "main", 2, 1);
        Map<String, Object> out = output(2, 1, List.of());
        out.put("done", true);
        out.put("error", Map.of("message", "task charge FAILED: over limit", "ref", "charge"));

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, awaited, result("id-charge", out));

        assertTrue(completion.finished);
        assertEquals("task charge FAILED: over limit", completion.reason);
        assertEquals(awaited, completion.causeTask);
        // saved with the marker: until the run is ended, it reads as failed, not returned
        assertEquals("task charge FAILED: over limit", workflow.getReasonForIncompletion());
        assertTrue(awaited.getContinuation().isDone());
    }

    private ExecutionDAOFacade dao;

    private void useMockedStores(WorkflowModel workflow) {
        dao = mock(ExecutionDAOFacade.class);
        when(dao.getWorkflowModel(anyString(), ArgumentMatchers.anyBoolean())).thenReturn(workflow);
        service.setQueueDAO(mock(QueueDAO.class));
        service.setExecutionDAOFacade(dao);
    }

    /** What a decide and then a poll do: offer the next continuation, then hand it out. */
    private void offerAndPoll(WorkflowModel workflow, String messageId, String workerId) {
        assertEquals(List.of(messageId), offered(service.repairAndWake(workflow)));
        assertTrue(
                service.lease(CODE_TYPE, messageId, dao.getTaskModel(messageId), workerId) != null);
    }

    private TaskResult diverged(TaskContinuation marker) {
        TaskResult result = result("wf-1|main", output(1, marker.getRetryCount(), List.of()));
        result.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        result.setReasonForIncompletion("the code calls x but the recording has no such task");
        return result;
    }

    @Test
    public void aHoldLastsAcrossRetriesUntilTheCodeMatches() {
        WorkflowModel workflow = freshWorkflow(false);
        useMockedStores(workflow);
        TaskContinuation marker = workflow.getNativeState().getBootstrap();
        marker.setLeased(false);

        offerAndPoll(workflow, "wf-1|main", "worker-a");
        completeHoldingSlot(workflow, null, diverged(marker));

        // the retry is still held while it runs: the run says why
        offerAndPoll(workflow, "wf-1|main", "worker-a");
        assertTrue(marker.isLeased());
        assertTrue(workflow.getReasonForIncompletion().startsWith("diverged: "));
        completeHoldingSlot(workflow, null, diverged(marker));

        // a build that matches the recording ends the hold
        offerAndPoll(workflow, "wf-1|main", "worker-b");
        completeHoldingSlot(
                workflow, null, result("wf-1|main", output(1, marker.getRetryCount(), List.of())));
        assertTrue(marker.isApplied());
        assertEquals(null, workflow.getReasonForIncompletion());
    }

    @Test
    public void aLeasedOfferIsPushedAgainAtTheSameRetryCount() {
        WorkflowModel workflow = freshWorkflow(false);
        useMockedStores(workflow);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel item = awaitedTask(workflow, "item", "fork_1.17", 1, 0);
        TaskContinuation marker = item.getContinuation();
        marker.setLeased(false);
        when(dao.getTaskModel("id-item")).thenReturn(item);

        offerAndPoll(workflow, "id-item", "worker-a");

        // the push repairs a lost message, the queue redelivers the rest
        int retryCount = marker.getRetryCount();
        assertEquals(List.of("id-item"), offered(service.repairAndWake(workflow)));
        assertEquals(retryCount, marker.getRetryCount());
    }

    @Test
    public void aDivergedContinuationHoldsTheExecutionAndBacksOff() {
        WorkflowModel workflow = freshWorkflow(false);
        lease(workflow.getNativeState().getBootstrap(), 0);
        TaskResult result = result("wf-1|main", output(1, 0, List.of()));
        result.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        result.setReasonForIncompletion("the code calls x but the recording has no such task");

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, null, result);

        assertFalse(completion.finished); // running, for a rollback or a fixed build to continue
        assertTrue(completion.deferred);
        TaskContinuation marker = workflow.getNativeState().getBootstrap();
        assertNotRun(marker);
        assertEquals(
                "diverged: the code calls x but the recording has no such task",
                workflow.getReasonForIncompletion());
        assertEquals(1, completion.retryDelaySeconds);
        assertEquals(1, marker.getRetryCount()); // the next offer is a retry

        // retried more often, it waits longer, up to a minute
        lease(marker, 3);
        result = result("wf-1|main", output(1, 3, List.of()));
        result.setStatus(TaskResult.Status.FAILED_WITH_TERMINAL_ERROR);
        assertEquals(8, completeHoldingSlot(workflow, null, result).retryDelaySeconds);
        lease(marker, 30);
        result = result("wf-1|main", output(1, 30, List.of()));
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
        assertNotRun(workflow.getNativeState().getBootstrap());
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
        assertNotRun(bootstrap);

        // a leased handler run also defers
        when(queue.size("wf-1")).thenReturn(0L);
        lease(bootstrap, 2);
        lease(workflow.getNativeState().getHandlers(), 1);
        completion =
                completeHoldingSlot(workflow, null, result("wf-1|main", output(1, 2, List.of())));
        assertFalse(completion.deferred); // not done: nothing to defer
        lease(bootstrap, 3);
        out.put("retryCount", 3);
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
        properties.setMaxWorkflowVariablesPayloadSizeThreshold(DataSize.ofBytes(100));
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
        applied(workflow.getNativeState().getBootstrap());
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
        assertEquals(TaskModel.Status.COMPLETED, wait.getStatus());
        assertFalse(wait.isExecuted());
        assertTrue(completion.touchedTasks.contains(wait));
        // the lane is ready for its next run
        assertEquals(5, workflow.getNativeState().getHandlers().getN());
        assertNotRun(workflow.getNativeState().getHandlers());
    }

    @Test
    public void aContinuationRunsOnceItsTaskIsEvaluatedAndQueuedMessagesGoFirst() {
        WorkflowModel workflow = freshWorkflow(true);
        applied(workflow.getNativeState().getBootstrap());
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 0);
        awaited.getContinuation().setLeased(false);
        awaited.setExecuted(false);
        assertEquals(List.of(), offered(service.repairAndWake(workflow))); // not evaluated yet
        assertNotRun(awaited.getContinuation());

        awaited.setExecuted(true); // the decider evaluated it
        WorkflowMessageQueueDAO queue = mock(WorkflowMessageQueueDAO.class);
        when(queue.size("wf-1")).thenReturn(1L);
        service.setWorkflowMessageQueueDAO(queue);

        // one continuation is offered at a time: the queued message is handled first
        NativeContinuationService.Wakeups wakeups = service.repairAndWake(workflow);
        assertTrue(wakeups.workflowTouched);
        assertTrue(workflow.getNativeState().getHandlers().isLeased());
        assertEquals(List.of("wf-1|handlers"), offered(wakeups));
        assertNotRun(awaited.getContinuation());

        // the handler run applied and no message is left: the code continues
        workflow.getNativeState().setHandlers(TaskContinuation.handlerRun(2));
        when(queue.size("wf-1")).thenReturn(0L);
        wakeups = service.repairAndWake(workflow);
        assertEquals(List.of("id-task_1"), offered(wakeups));
        assertTrue(wakeups.touchedTasks.contains(awaited));
    }

    @Test
    public void lanesSettledReadsTheMarkers() {
        WorkflowModel workflow = freshWorkflow(false);
        TaskModel a = awaitedTask(workflow, "a1", "fork_1.0", 2, 1);
        TaskModel b = awaitedTask(workflow, "b1", "fork_1.1", 2, 1);
        b.setContinuation(null); // the lane ended with this task without pausing

        a.getContinuation().setLeased(false);
        assertFalse(NativeContinuationService.lanesSettled(workflow, List.of("a1", "b1")));
        a.getContinuation().setLeased(true);
        assertFalse(NativeContinuationService.lanesSettled(workflow, List.of("a1", "b1")));
        applied(a.getContinuation()); // appended more: not the tail
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
            applied(task.getContinuation());
            task.getContinuation().setEndTime(appliedAt);
        }
        return task;
    }

    private static void appendedBy(TaskModel task, String marker) {
        task.getWorkflowTask().getMetadata().put(NativeContinuationService.APPENDED_BY, marker);
    }

    @Test
    public void rerunUndoesWhatTheCodeDidAfterTheTask() {
        WorkflowModel workflow = freshWorkflow(false);
        applied(workflow.getNativeState().getBootstrap());
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
        assertNotRun(t2.getContinuation()); // runs again when task_2 completes again
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
        assertNotRun(x.getContinuation());
        assertNotRun(y.getContinuation()); // its task stays; its code runs again
        assertNotRun(join.getContinuation());
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
                new LinkedHashMap<>(
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
        assertNotRun(after.getContinuation());
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
        entry.getMetadata().put("continuation", 2);
        WorkflowModel workflow = freshWorkflow(false);
        TaskModel task =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withWorkflowTask(entry)
                        .withTaskId("t-1")
                        .build()
                        .createTaskModel();
        // which continuation it is stays on the entry; the marker starts out not run
        assertEquals(entry, task.getWorkflowTask());
        assertNotRun(task.getContinuation());
    }

    // ------------------------------------------------------------------ pinning

    private static final String PIN = "pin-host-a1b2c3";
    private static final String PINNED_QUEUE = PIN + ":" + CODE_TYPE;

    private QueueDAO queues;

    /** A completion whose worker keeps the code parked, in its domain and with its stamp. */
    private static Map<String, Object> parked(
            Map<String, Object> out, String domain, String stamp) {
        out.put("pin", Map.of("domain", domain, "stamp", stamp));
        return out;
    }

    /** An execution pinned to {@link #PIN}, with the stamp {@code s-1}. */
    private WorkflowModel pinnedWorkflow(boolean handlers) {
        WorkflowModel workflow = freshWorkflow(handlers);
        applied(workflow.getNativeState().getBootstrap());
        workflow.getNativeState().setPinnedTo(PIN);
        workflow.getNativeState().setStamp("s-1");
        return workflow;
    }

    /** Stores in which the pinned worker polled its queue this long ago. */
    private void useStoresWhereThePinPolled(WorkflowModel workflow, long millisAgo) {
        dao = mock(ExecutionDAOFacade.class);
        when(dao.getWorkflowModel(anyString(), ArgumentMatchers.anyBoolean())).thenReturn(workflow);
        when(dao.getTaskPollDataByDomain(CODE_TYPE, PIN))
                .thenReturn(
                        new PollData(
                                CODE_TYPE,
                                PIN,
                                "worker-a",
                                System.currentTimeMillis() - millisAgo));
        queues = mock(QueueDAO.class);
        service.setQueueDAO(queues);
        service.setExecutionDAOFacade(dao);
    }

    @Test
    public void aCompletionWhoseWorkerParksTheCodePinsTheExecutionToIt() {
        WorkflowModel workflow = freshWorkflow(false);
        useStoresWhereThePinPolled(workflow, 60_000);
        lease(workflow.getNativeState().getBootstrap(), 0);
        Map<String, Object> out =
                parked(
                        output(1, 0, List.of(entry("first", "SIMPLE", "main", "main", 2))),
                        PIN,
                        "s-1");

        NativeContinuationService.Completion completion =
                completeHoldingSlot(workflow, null, result("wf-1|main", out));

        assertEquals(PIN, workflow.getNativeState().getPinnedTo());
        assertEquals("s-1", workflow.getNativeState().getStamp());
        assertEquals(PIN, completion.pinnedTo);
        assertNull(completion.ackPinnedQueue); // it was not pinned when it was offered
        // the worker has just answered: its queue counts as polled, so the next offer goes there
        Mockito.verify(dao).updateTaskLastPoll(CODE_TYPE, PIN, "worker-a");
    }

    @Test
    public void aCompletionThatParksNothingUnpinsAndIsAcknowledgedOnBothQueues() {
        WorkflowModel workflow = pinnedWorkflow(false);
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 0);

        // another worker took it from the shared queue and keeps nothing
        NativeContinuationService.Completion completion =
                completeHoldingSlot(
                        workflow, awaited, result("id-task_1", output(2, 0, List.of())));

        assertNull(workflow.getNativeState().getPinnedTo());
        assertNull(workflow.getNativeState().getStamp()); // parked code elsewhere no longer matches
        assertNull(completion.pinnedTo);
        assertEquals(CODE_TYPE, completion.ackQueue);
        assertEquals(PINNED_QUEUE, completion.ackPinnedQueue);
    }

    @Test
    public void aPinMustNameADomainThatCanBeAQueue() {
        WorkflowModel workflow = freshWorkflow(false);
        lease(workflow.getNativeState().getBootstrap(), 0);

        completeHoldingSlot(
                workflow, null, result("wf-1|main", parked(output(1, 0, List.of()), "a:b", "s-1")));

        assertNull(workflow.getNativeState().getPinnedTo());
        assertNull(workflow.getNativeState().getStamp());
    }

    @Test
    public void aHandlerRunLeavesThePinAndItsStamp() {
        WorkflowModel workflow = pinnedWorkflow(true);
        lease(workflow.getNativeState().getHandlers(), 0);
        Map<String, Object> out = output(1, 0, List.of());
        out.put("variables", Map.of("stage", "paid"));

        completeHoldingSlot(workflow, null, result("wf-1|handlers", out));

        // handlers change variables, which a poll hands out, not what the code has done
        assertEquals(PIN, workflow.getNativeState().getPinnedTo());
        assertEquals("s-1", workflow.getNativeState().getStamp());
    }

    @Test
    public void aDeferredCompletionLeavesTheStampOfTheLastOneThatApplied() {
        WorkflowModel workflow = pinnedWorkflow(false);
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 0);
        TaskResult failed = result("id-task_1", parked(output(2, 0, List.of()), PIN, "s-2"));
        failed.setStatus(TaskResult.Status.FAILED);

        assertTrue(completeHoldingSlot(workflow, awaited, failed).deferred);

        // the worker ran ahead of the execution: its new stamp never matches, so it replays
        assertEquals("s-1", workflow.getNativeState().getStamp());
    }

    @Test
    public void aPinnedExecutionIsOfferedToItsWorkerWithAFallbackAfterThePinTimeout() {
        WorkflowModel workflow = pinnedWorkflow(false);
        useStoresWhereThePinPolled(workflow, 1_000);
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);
        task.getContinuation().setLeased(false);
        task.setEndTime(1);

        NativeContinuationService.Wakeups wakeups = service.repairAndWake(workflow);

        NativeContinuationService.Offer offer = wakeups.offers.get(0);
        assertEquals("id-task_1", offer.messageId());
        assertEquals(PINNED_QUEUE, offer.queue());
        assertEquals(CODE_TYPE, offer.fallbackQueue());
        assertEquals(5, offer.fallbackDelaySeconds());
        service.push(workflow, wakeups.offers);
        Mockito.verify(queues).pushIfNotExists(PINNED_QUEUE, "id-task_1", 0, 0L);
        Mockito.verify(queues).pushIfNotExists(CODE_TYPE, "id-task_1", 0, 5L);

        // The slot holder's message is pushed again on every decide, to the shared queue only:
        // it is there already (the fallback), so nothing is added. A push to the pinned queue
        // could add a second message for an offer its worker is running.
        offer = service.repairAndWake(workflow).offers.get(0);
        assertEquals("id-task_1", offer.messageId());
        assertEquals(CODE_TYPE, offer.queue());
        assertNull(offer.fallbackQueue());
    }

    @Test
    public void anOfferMadeToTheSharedQueueIsNeverPushedToAPinnedQueueLater() {
        WorkflowModel workflow = pinnedWorkflow(false);
        // the pinned worker was not polling when the offer was made: it went to the shared queue
        useStoresWhereThePinPolled(workflow, 11_000);
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);
        task.getContinuation().setLeased(false);
        task.setEndTime(1);
        assertEquals(CODE_TYPE, service.repairAndWake(workflow).offers.get(0).queue());

        // it polls again while another worker runs the offer: the repair must not hand it a copy
        when(dao.getTaskPollDataByDomain(CODE_TYPE, PIN))
                .thenReturn(new PollData(CODE_TYPE, PIN, "worker-a", System.currentTimeMillis()));
        NativeContinuationService.Offer repair = service.repairAndWake(workflow).offers.get(0);

        assertEquals(CODE_TYPE, repair.queue());
        assertNull(repair.fallbackQueue());
    }

    @Test
    public void thePinTimeoutIsConfigured() {
        properties.getNativeWorkflow().setPinTimeout(java.time.Duration.ofSeconds(2));
        WorkflowModel workflow = pinnedWorkflow(false);
        useStoresWhereThePinPolled(workflow, 1_000);
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);
        task.getContinuation().setLeased(false);
        task.setEndTime(1);

        assertEquals(2, service.repairAndWake(workflow).offers.get(0).fallbackDelaySeconds());
    }

    @Test
    public void anExecutionPinnedToAWorkerThatStoppedPollingIsOfferedToAnyWorker() {
        WorkflowModel workflow = pinnedWorkflow(false);
        // not within conductor.app.activeWorkerLastPollTimeout (10 seconds)
        useStoresWhereThePinPolled(workflow, 11_000);
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);
        task.getContinuation().setLeased(false);
        task.setEndTime(1);

        NativeContinuationService.Offer offer = service.repairAndWake(workflow).offers.get(0);

        assertEquals(CODE_TYPE, offer.queue());
        assertNull(offer.fallbackQueue());
    }

    @Test
    public void aLeaseFromThePinnedQueueGivesItsWorkerThePinTimeoutAgainToAnswer() {
        WorkflowModel workflow = pinnedWorkflow(false);
        useStoresWhereThePinPolled(workflow, 1_000);
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);

        assertTrue(service.lease(PINNED_QUEUE, "id-task_1", task, "worker-a") != null);

        // Its own message waits out the continuation timeout, as any lease does. The one on the
        // shared queue becomes visible after the pin timeout: a worker killed with its poll open
        // on the server takes the message and never answers, and any worker steps in as soon as
        // for one that never polled.
        Mockito.verify(queues).setUnackTimeout(PINNED_QUEUE, "id-task_1", 30_000L);
        Mockito.verify(queues).setUnackTimeout(CODE_TYPE, "id-task_1", 5_000L);
        Mockito.verify(queues, Mockito.never()).remove(anyString(), anyString());
    }

    @Test
    public void theSharedMessageNeverWaitsLongerThanTheContinuationTimeout() {
        properties.getNativeWorkflow().setPinTimeout(java.time.Duration.ofMinutes(5));
        WorkflowModel workflow = pinnedWorkflow(false);
        useStoresWhereThePinPolled(workflow, 1_000);
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);

        service.lease(PINNED_QUEUE, "id-task_1", task, "worker-a");

        Mockito.verify(queues).setUnackTimeout(CODE_TYPE, "id-task_1", 30_000L);
    }

    @Test
    public void aLeaseFromTheSharedQueueTakesTheMessageOffThePinnedQueue() {
        WorkflowModel workflow = pinnedWorkflow(false);
        useStoresWhereThePinPolled(workflow, 1_000);
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);

        // the pinned worker did not take it within the pin timeout
        assertTrue(service.lease(CODE_TYPE, "id-task_1", task, "worker-b") != null);

        Mockito.verify(queues).setUnackTimeout(CODE_TYPE, "id-task_1", 30_000L);
        Mockito.verify(queues).remove(PINNED_QUEUE, "id-task_1");
    }

    @Test
    public void aMessageOnAnotherWorkersQueueIsStale() {
        WorkflowModel workflow = pinnedWorkflow(false);
        useStoresWhereThePinPolled(workflow, 1_000);
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 0);
        String other = "pin-host-zzzzzz:" + CODE_TYPE;

        // offered there before the execution was pinned to another worker
        assertNull(service.lease(other, "id-task_1", task, "worker-z"));

        Mockito.verify(queues).remove(other, "id-task_1");
    }

    @Test
    public void aLeaseHandsOutTheStampAndTheVariablesAndStoresNeither() {
        WorkflowModel workflow = pinnedWorkflow(false);
        useStoresWhereThePinPolled(workflow, 1_000);
        workflow.getVariables().put("stage", "new");
        TaskModel task = awaitedTask(workflow, "task_1", "main", 2, 3);

        Task leased = service.lease(PINNED_QUEUE, "id-task_1", task, "worker-a");

        assertEquals("s-1", leased.getContinuation().getStamp());
        assertEquals(Map.of("stage", "new"), leased.getContinuation().getVariables());
        assertTrue(leased.getContinuation().isLeased());
        assertEquals(3, leased.getContinuation().getRetryCount());
        // the task's own marker, which is what is stored, carries neither
        assertNull(task.getContinuation().getStamp());
        assertNull(task.getContinuation().getVariables());
    }

    @Test
    public void anExecutionNoWorkerHoldsHandsOutItsVariablesAndNoStamp() {
        WorkflowModel workflow = freshWorkflow(false);
        useMockedStores(workflow);
        lease(workflow.getNativeState().getBootstrap(), 0);

        Task leased = service.lease(CODE_TYPE, "wf-1|main", null, "worker-a");

        // the variables, even when there are none, say that this server pins executions
        assertEquals(Map.of(), leased.getContinuation().getVariables());
        assertNull(leased.getContinuation().getStamp());
    }

    @Test
    public void aRerunUnpins() {
        WorkflowModel workflow = pinnedWorkflow(false);
        TaskModel t1 = ran(workflow, "task_1", "main", 2, 100, 110);
        appendedBy(t1, "main#1");
        TaskModel t2 = ran(workflow, "task_2", "main", 3, 0, 0);
        appendedBy(t2, "main#2");

        NativeContinuationService.RerunPlan plan = service.planRerun(workflow, t1);
        workflow.getTasks().remove(t2);
        service.adjustRerun(workflow, t1, plan, new ArrayList<>());

        // the parked code already ran what the rerun runs again
        assertNull(workflow.getNativeState().getPinnedTo());
        assertNull(workflow.getNativeState().getStamp());
    }

    @Test
    public void aRetryOfTheCodeUnpins() {
        WorkflowModel workflow = pinnedWorkflow(false);
        ran(workflow, "task_1", "main", 2, 100, 110);

        assertTrue(service.rearmLastMain(workflow) != null);

        assertNull(workflow.getNativeState().getPinnedTo());
        assertNull(workflow.getNativeState().getStamp());
    }

    @Test
    public void aRejectedFragmentUnpins() {
        WorkflowModel workflow = pinnedWorkflow(false);
        TaskModel awaited = awaitedTask(workflow, "task_1", "main", 2, 0);
        Map<String, Object> lost = entry("later", "SIMPLE", "main", "main", 3);
        ((Map<String, Object>) lost.get("metadata")).put("after", "nowhere");

        NativeContinuationService.Completion rejected =
                completeHoldingSlot(
                        workflow,
                        awaited,
                        result("id-task_1", parked(output(2, 0, List.of(lost)), PIN, "s-2")));

        assertTrue(rejected.finished);
        // its worker believes the fragment was appended
        assertNull(workflow.getNativeState().getPinnedTo());
        assertNull(workflow.getNativeState().getStamp());
    }

    @Test
    public void aRestartStartsUnpinned() {
        WorkflowModel workflow = pinnedWorkflow(false);

        service.initialize(workflow);

        assertNull(workflow.getNativeState().getPinnedTo());
        assertNull(workflow.getNativeState().getStamp());
    }
}
