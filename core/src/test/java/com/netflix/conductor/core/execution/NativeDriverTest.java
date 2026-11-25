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
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.WorkflowDriver.RetryOutcome;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The steps of a workflow whose code decides what runs. */
public class NativeDriverTest {

    private NativeContinuationService service;
    private NativeRerunPlanner planner;
    private MetadataDAO metadataDAO;
    private ExecutionDAOFacade executionDAOFacade;
    private QueueDAO queueDAO;
    private SystemTaskRegistry systemTaskRegistry;
    private NativeDriver driver;

    @Before
    public void setUp() {
        service = mock(NativeContinuationService.class);
        doCallRealMethod().when(service).initialize(any());
        planner = spy(new NativeRerunPlanner());
        metadataDAO = mock(MetadataDAO.class);
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        queueDAO = mock(QueueDAO.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        var standard =
                new StandardDriver(
                        mock(ParametersUtils.class),
                        mock(ExternalPayloadStorageUtils.class),
                        executionDAOFacade,
                        queueDAO);
        driver =
                new NativeDriver(
                        standard,
                        service,
                        planner,
                        metadataDAO,
                        new MetadataMapperService(metadataDAO),
                        executionDAOFacade,
                        queueDAO,
                        systemTaskRegistry);
    }

    private static WorkflowDef nativeDefinition() {
        var def = new WorkflowDef();
        def.setName("native");
        def.setVersion(1);
        def.getMetadata().put("native", new HashMap<>(Map.of("sdk", "test")));
        def.setTasks(new ArrayList<>());
        return def;
    }

    /** A native execution with no tasks; its first continuation has not run. */
    private static WorkflowModel nativeWorkflow() {
        var workflow = new WorkflowModel();
        workflow.setWorkflowId("wf");
        workflow.setWorkflowDefinition(nativeDefinition());
        var state = new NativeState();
        state.setBootstrap(new TaskContinuation());
        workflow.setNativeState(state);
        return workflow;
    }

    private static TaskModel task(String ref, int seq, TaskModel.Status status) {
        var task = new TaskModel();
        task.setTaskId("id-" + ref);
        task.setReferenceTaskName(ref);
        task.setTaskType(TaskType.TASK_TYPE_SIMPLE);
        task.setSeq(seq);
        task.setStatus(status);
        task.setOutputData(new HashMap<>(Map.of("from", ref)));
        return task;
    }

    /** Marks the first continuation as applied: the code returned in it when done is true. */
    private static void applyFirstContinuation(WorkflowModel workflow, boolean done) {
        var bootstrap = workflow.getNativeState().getBootstrap();
        bootstrap.setEndTime(1);
        bootstrap.setDone(done);
    }

    @Test
    public void anEmptyDefinitionStartsWithNoTasks() {
        assertTrue(driver.startWithNoTasks().isEmpty());
    }

    @Test
    public void aFailedOptionalTaskKeepsHowItFailed() {
        var task = task("a", 1, TaskModel.Status.TIMED_OUT);

        driver.completeWithErrors(task);

        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, task.getStatus());
        assertEquals("TIMED_OUT", task.getOutputData().get(NativeWorkflows.OUTPUT_FAILED_STATUS));
    }

    @Test
    public void theOutputIsNotComputedFromTheTasks() {
        var workflow = nativeWorkflow();
        workflow.getTasks().add(task("a", 1, TaskModel.Status.COMPLETED));
        workflow.setOutput(new HashMap<>(Map.of("fromCode", true)));

        driver.updateOutput(workflow, null);

        assertEquals(Map.of("fromCode", true), workflow.getOutput());
    }

    @Test
    public void notCompleteUntilTheCodeReturned() {
        assertFalse(driver.isComplete(nativeWorkflow(), () -> true));
    }

    @Test
    public void completeWithNoTasksWhenTheCodeReturned() {
        var workflow = nativeWorkflow();
        applyFirstContinuation(workflow, true);

        assertTrue(driver.isComplete(workflow, () -> false));
    }

    @Test
    public void notCompleteWhileTheTasksTheCodeCalledRun() {
        var workflow = nativeWorkflow();
        applyFirstContinuation(workflow, true);
        workflow.getTasks().add(task("a", 1, TaskModel.Status.IN_PROGRESS));

        assertFalse(driver.isComplete(workflow, () -> false));
        assertTrue(driver.isComplete(workflow, () -> true));
    }

    /** The registered definition of the workflow: no tasks, as the SDK registers it. */
    private void registerDefinition() {
        when(metadataDAO.getWorkflowDef("native", 1)).thenReturn(Optional.of(nativeDefinition()));
    }

    /** A native execution whose own definition has two tasks and one variable. */
    private static WorkflowModel nativeWorkflowThatGrew() {
        var workflow = nativeWorkflow();
        for (String ref : List.of("a", "b")) {
            var entry = new WorkflowTask();
            entry.setName(ref);
            entry.setTaskReferenceName(ref);
            entry.setType(TaskType.TASK_TYPE_SIMPLE);
            workflow.getWorkflowDefinition().getTasks().add(entry);
        }
        workflow.getVariables().put("stage", "paid");
        applyFirstContinuation(workflow, false);
        return workflow;
    }

    @Test
    public void aNewExecutionGetsItsFirstContinuation() {
        var workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(nativeDefinition());

        driver.initialize(workflow);

        assertNotNull(workflow.getNativeState().getBootstrap());
        assertFalse(workflow.getNativeState().getBootstrap().isApplied());
    }

    @Test
    public void aRestartRunsTheRegisteredDefinitionWithNoVariables() {
        registerDefinition();
        var workflow = nativeWorkflowThatGrew();

        var definition = driver.resetForRestart(workflow, workflow.getWorkflowDefinition(), false);

        assertTrue(definition.getTasks().isEmpty());
        assertSame(definition, workflow.getWorkflowDefinition());
        assertTrue(workflow.getVariables().isEmpty());
        assertFalse(workflow.getNativeState().getBootstrap().isApplied());
    }

    @Test
    public void aRestartWithTheLatestDefinitionRunsThatDefinition() {
        var workflow = nativeWorkflowThatGrew();
        var latest = nativeDefinition();
        latest.setVersion(2);

        var definition = driver.resetForRestart(workflow, latest, true);

        assertEquals(2, definition.getVersion());
        assertEquals(2, workflow.getWorkflowDefinition().getVersion());
        verify(metadataDAO, never()).getWorkflowDef("native", 1);
    }

    @Test
    public void aRerunFromTheStartRunsTheCodeAgain() {
        registerDefinition();
        var workflow = nativeWorkflowThatGrew();
        workflow.setOutput(new HashMap<>(Map.of("old", 1)));

        driver.resetForRerun(workflow);

        assertTrue(workflow.getWorkflowDefinition().getTasks().isEmpty());
        assertTrue(workflow.getOutput().isEmpty());
    }

    @Test
    public void theCodeIsOfferedAfterADecide() {
        var workflow = nativeWorkflow();
        var wakeups = new NativeContinuationService.Wakeups();
        wakeups.workflowTouched = true;
        wakeups.offers.add(new NativeContinuationService.Offer("wf|main", "native.v1.code", 0));
        when(service.repairAndWake(workflow)).thenReturn(wakeups);

        driver.continueCode(workflow);

        var inOrder = inOrder(executionDAOFacade, service);
        inOrder.verify(executionDAOFacade).updateWorkflow(workflow);
        inOrder.verify(service).push(workflow, wakeups.offers);
    }

    @Test
    public void aPromptRevisitOnlyShortensTheDecideOffset() {
        var workflow = nativeWorkflow(); // its first continuation has not run

        driver.postponeDecide(workflow, Duration.ofSeconds(90), Duration.ofSeconds(30));

        verify(queueDAO).setUnackTimeoutIfShorter(DECIDER_QUEUE, "wf", 30_000);
        verify(queueDAO, never()).setUnackTimeout(anyString(), anyString(), anyLong());
    }

    @Test
    public void withNoPromptRevisitTheDecideIsPostponedByTheOffset() {
        var workflow = nativeWorkflow();
        applyFirstContinuation(workflow, true);

        driver.postponeDecide(workflow, Duration.ofSeconds(90), Duration.ofSeconds(30));

        verify(queueDAO).setUnackTimeout(DECIDER_QUEUE, "wf", 90_000);
    }

    /** A native execution that failed; its first continuation was applied. */
    private static WorkflowModel failedNativeWorkflow() {
        var workflow = nativeWorkflow();
        applyFirstContinuation(workflow, true);
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setReasonForIncompletion("failed");
        return workflow;
    }

    @Test
    public void aRetryRerunsFromTheTaskThatFailedTheRun() {
        var workflow = failedNativeWorkflow();
        workflow.getTasks().add(task("charge", 1, TaskModel.Status.FAILED));
        workflow.setFailedTaskId("id-charge");

        assertEquals(RetryOutcome.rerunFrom("id-charge"), driver.retry(workflow));
        assertEquals(WorkflowModel.Status.FAILED, workflow.getStatus());
    }

    @Test
    public void aRetryRunsTheLastContinuationAgainWhenTheCodeFailed() {
        var workflow = failedNativeWorkflow();

        assertEquals(RetryOutcome.done(), driver.retry(workflow));

        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertNull(workflow.getReasonForIncompletion());
        assertFalse(workflow.getNativeState().getBootstrap().isApplied());
        verify(executionDAOFacade).updateWorkflow(workflow);
        verify(queueDAO).push(DECIDER_QUEUE, "wf", workflow.getPriority(), 0);
    }

    @Test
    public void aRetryRemovesTheMarkOfAThrow() {
        var workflow = failedNativeWorkflow();
        var mark = new WorkflowTask();
        mark.setName("thrown");
        mark.setTaskReferenceName("thrown");
        mark.setType(TaskType.TERMINATE.name());
        mark.getMetadata().put(NativeWorkflows.APPENDED_BY, "main#1");
        workflow.getWorkflowDefinition().getTasks().add(mark);
        var thrown = task("thrown", 1, TaskModel.Status.FAILED);
        thrown.setTaskType(TaskType.TERMINATE.name());
        workflow.getTasks().add(thrown);
        workflow.setFailedTaskId("id-thrown");

        assertEquals(RetryOutcome.done(), driver.retry(workflow));

        assertTrue(workflow.getTasks().isEmpty());
        assertTrue(workflow.getWorkflowDefinition().getTasks().isEmpty());
        verify(executionDAOFacade).removeTask("id-thrown");
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
    }

    @Test
    public void aRetryWithNothingToRunAgainRetriesTheFailedTasks() {
        var workflow = failedNativeWorkflow();
        workflow.getNativeState().setBootstrap(null);

        assertEquals(RetryOutcome.retryFailedTasks(), driver.retry(workflow));
        assertEquals(WorkflowModel.Status.FAILED, workflow.getStatus());
    }

    @Test
    public void aRerunCannotStartFromTheStructureOfTheCode() {
        for (String type :
                List.of(
                        TaskType.TASK_TYPE_SWITCH,
                        TaskType.TASK_TYPE_FORK,
                        TaskType.TASK_TYPE_JOIN)) {
            var structure = task("s", 1, TaskModel.Status.COMPLETED);
            structure.setTaskType(type);

            var refused =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> driver.checkCanRerunFrom(structure));

            assertTrue(refused.getMessage().startsWith("Task id-s is a " + type));
        }
    }

    @Test
    public void aRerunCanStartFromATaskTheCodeCalled() {
        driver.checkCanRerunFrom(task("a", 1, TaskModel.Status.FAILED));
    }

    @Test
    public void aRerunDoesNotResetASubWorkflowTaskInPlace() {
        assertFalse(driver.rerunsSubWorkflowInPlace());
    }

    @Test
    public void aRerunRemovesWhatTheCodeDidAfterTheTask() {
        var workflow = nativeWorkflow();
        var a1 = task("a1", 1, TaskModel.Status.FAILED);
        var b1 = task("b1", 2, TaskModel.Status.COMPLETED);
        var a2 = task("a2", 3, TaskModel.Status.COMPLETED);
        var join = task("join", 4, TaskModel.Status.COMPLETED);
        workflow.getTasks().addAll(List.of(a1, b1, a2, join));
        var plan = new NativeRerunPlanner.RerunPlan(Set.of("id-a2"), Set.of(), Set.of());
        doReturn(plan).when(planner).planRerun(workflow, a1);
        doAnswer(
                        call -> {
                            call.<List<TaskModel>>getArgument(3).add(b1);
                            return List.of(join);
                        })
                .when(planner)
                .adjustRerun(eq(workflow), eq(a1), eq(plan), anyList());

        var reopened = driver.undoAfter(workflow, a1);

        assertEquals(List.of(a1, b1, join), workflow.getTasks());
        assertEquals(List.of(join), reopened);
        var inOrder = inOrder(planner, executionDAOFacade);
        inOrder.verify(planner).planRerun(workflow, a1);
        inOrder.verify(executionDAOFacade).removeTask("id-a2");
        inOrder.verify(planner).adjustRerun(eq(workflow), eq(a1), eq(plan), anyList());
        inOrder.verify(executionDAOFacade).updateTasks(List.of(b1));
        inOrder.verify(executionDAOFacade).updateWorkflow(workflow);
    }

    @Test
    public void aSubWorkflowTaskOfARerunStartsANewChild() {
        var workflow = nativeWorkflow();
        var task = task("child", 1, TaskModel.Status.FAILED);
        task.setTaskType(TaskType.TASK_TYPE_SUB_WORKFLOW);
        task.setSubWorkflowId("old-child");
        task.setReasonForIncompletion("failed");
        var subWorkflow = mock(WorkflowSystemTask.class);
        when(systemTaskRegistry.get(TaskType.TASK_TYPE_SUB_WORKFLOW)).thenReturn(subWorkflow);
        var executor = mock(WorkflowExecutor.class);

        driver.rerunSubWorkflowTask(workflow, task, new HashMap<>(Map.of("order", 7)), executor);

        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertEquals(Map.of("order", 7), task.getInputData());
        assertNull(task.getSubWorkflowId());
        assertNull(task.getReasonForIncompletion());
        verify(subWorkflow).start(workflow, task, executor);
    }

    @Test
    public void aRerunDoesNotRunAFailureTheCodeConsumed() {
        var consumed = task("a", 1, TaskModel.Status.FAILED);
        consumed.setExecuted(true);
        var superseded = task("b", 2, TaskModel.Status.FAILED);
        superseded.setRetried(true);

        assertFalse(driver.runsAgainOnRerun(consumed));
        assertFalse(driver.runsAgainOnRerun(superseded));
        assertTrue(driver.runsAgainOnRerun(task("c", 3, TaskModel.Status.CANCELED)));
    }

    @Test
    public void aNativeExecutionWithNoRunningTaskIsNotStalled() {
        var workflow = nativeWorkflow();
        workflow.getTasks().add(task("a", 1, TaskModel.Status.COMPLETED));

        assertFalse(driver.isStalled(workflow));
    }
}
