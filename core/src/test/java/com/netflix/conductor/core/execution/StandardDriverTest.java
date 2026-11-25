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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.WorkflowDriver.RetryOutcome;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** The steps of a workflow that runs the tasks of its definition. */
public class StandardDriverTest {

    private ExecutionDAOFacade executionDAOFacade;
    private QueueDAO queueDAO;
    private StandardDriver driver;

    @Before
    public void setUp() {
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        queueDAO = mock(QueueDAO.class);
        driver =
                new StandardDriver(
                        mock(ParametersUtils.class),
                        mock(ExternalPayloadStorageUtils.class),
                        executionDAOFacade,
                        queueDAO);
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

    private static WorkflowModel workflowWith(TaskModel... tasks) {
        var workflow = new WorkflowModel();
        workflow.setWorkflowId("wf");
        workflow.setWorkflowDefinition(new WorkflowDef());
        for (TaskModel task : tasks) {
            workflow.getTasks().add(task);
        }
        return workflow;
    }

    @Test
    public void anEmptyDefinitionEndsTheExecutionAsCompleted() {
        var ended = assertThrows(TerminateWorkflowException.class, driver::startWithNoTasks);

        assertEquals(WorkflowModel.Status.COMPLETED, ended.getWorkflowStatus());
        assertEquals("No tasks found to be executed", ended.getMessage());
    }

    @Test
    public void aFailedOptionalTaskGetsTheStatusOnly() {
        var task = task("a", 1, TaskModel.Status.TIMED_OUT);

        driver.completeWithErrors(task);

        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, task.getStatus());
        assertFalse(task.getOutputData().containsKey(NativeWorkflows.OUTPUT_FAILED_STATUS));
    }

    @Test
    public void theOutputIsTheOutputOfTheLastTask() {
        var workflow =
                workflowWith(
                        task("a", 1, TaskModel.Status.COMPLETED),
                        task("b", 2, TaskModel.Status.COMPLETED));

        driver.updateOutput(workflow, null);

        assertEquals(Map.of("from", "b"), workflow.getOutput());
    }

    @Test
    public void theOutputIsTheOutputOfTheGivenTask() {
        var first = task("a", 1, TaskModel.Status.COMPLETED);
        var workflow = workflowWith(first, task("b", 2, TaskModel.Status.COMPLETED));

        driver.updateOutput(workflow, first);

        assertEquals(Map.of("from", "a"), workflow.getOutput());
    }

    @Test
    public void completionIsTheTaskCheck() {
        assertTrue(driver.isComplete(workflowWith(), () -> true));
        assertFalse(driver.isComplete(workflowWith(), () -> false));
    }

    @Test
    public void aNewExecutionNeedsNoSetUp() {
        var workflow = workflowWith();

        driver.initialize(workflow);

        assertNull(workflow.getNativeState());
    }

    @Test
    public void aRestartClearsTheNativeStateAndKeepsTheDefinition() {
        var workflow = workflowWith();
        workflow.setNativeState(new NativeState());
        var definition = new WorkflowDef();

        assertSame(definition, driver.resetForRestart(workflow, definition, true));
        assertNull(workflow.getNativeState());
    }

    @Test
    public void aStandardWorkflowHasNoCodeToContinue() {
        driver.continueCode(workflowWith(task("a", 1, TaskModel.Status.COMPLETED)));

        verifyNoInteractions(executionDAOFacade, queueDAO);
    }

    @Test
    public void theDecideIsNotPostponedWhenTheOffsetIsTheTimeout() {
        driver.postponeDecide(workflowWith(), Duration.ofSeconds(30), Duration.ofSeconds(30));

        verifyNoInteractions(queueDAO);
    }

    @Test
    public void theDecideIsPostponedByTheOffset() {
        driver.postponeDecide(workflowWith(), Duration.ofSeconds(90), Duration.ofSeconds(30));

        verify(queueDAO).setUnackTimeout(DECIDER_QUEUE, "wf", 90_000);
    }

    @Test
    public void aRetryLeavesTheFailedTasksToTheExecutor() {
        var workflow = workflowWith(task("a", 1, TaskModel.Status.FAILED));

        assertEquals(RetryOutcome.retryFailedTasks(), driver.retry(workflow));
        verifyNoInteractions(executionDAOFacade, queueDAO);
    }

    @Test
    public void aRerunCanStartFromAnyTask() {
        var fork = task("fork", 1, TaskModel.Status.COMPLETED);
        fork.setTaskType(TaskType.TASK_TYPE_FORK);

        driver.checkCanRerunFrom(fork);
    }

    @Test
    public void aRerunResetsASubWorkflowTaskInPlace() {
        assertTrue(driver.rerunsSubWorkflowInPlace());
    }

    @Test
    public void aRerunRemovesTheTasksAfterTheTask() {
        var first = task("a", 1, TaskModel.Status.FAILED);
        var workflow =
                workflowWith(
                        first,
                        task("b", 2, TaskModel.Status.COMPLETED),
                        task("c", 3, TaskModel.Status.COMPLETED));

        var reopened = driver.undoAfter(workflow, first);

        assertEquals(List.of(first), workflow.getTasks());
        verify(executionDAOFacade).removeTask("id-b");
        verify(executionDAOFacade).removeTask("id-c");
        verifyNoMoreInteractions(executionDAOFacade);
        assertTrue(reopened.isEmpty());
    }

    @Test
    public void aSubWorkflowTaskOfARerunWaitsForItsChild() {
        var task = task("child", 1, TaskModel.Status.FAILED);
        task.setTaskType(TaskType.TASK_TYPE_SUB_WORKFLOW);

        driver.rerunSubWorkflowTask(workflowWith(task), task, Map.of("ignored", 1), null);

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertTrue(task.getStartTime() > 0);
        assertFalse(task.getInputData().containsKey("ignored"));
    }

    @Test
    public void aRerunRunsEachUnsuccessfulTaskAgain() {
        var failed = task("a", 1, TaskModel.Status.FAILED);
        failed.setExecuted(true);

        assertTrue(driver.runsAgainOnRerun(failed));
        assertFalse(driver.runsAgainOnRerun(task("b", 2, TaskModel.Status.COMPLETED)));
        assertFalse(driver.runsAgainOnRerun(task("c", 3, TaskModel.Status.IN_PROGRESS)));
    }

    @Test
    public void aRunningExecutionWithNoRunningTaskIsStalled() {
        assertTrue(driver.isStalled(workflowWith(task("a", 1, TaskModel.Status.COMPLETED))));
        assertFalse(driver.isStalled(workflowWith(task("a", 1, TaskModel.Status.IN_PROGRESS))));
    }
}
