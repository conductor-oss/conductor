/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.test.resiliency;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.rest.controllers.TaskResource;
import com.netflix.conductor.rest.controllers.WorkflowResource;
import com.netflix.conductor.test.base.AbstractResiliencySpecification;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When QueueDAO is unavailable, Ensure All Workflow and Task resource endpoints either: 1. Fails
 * and/or throws an Exception 2. Succeeds 3. Doesn't involve QueueDAO
 */
@TestPropertySource(properties = "conductor.app.workflow.name-validation.enabled=true")
@Disabled(
        "FIXME: interaction-based testing requires spy support; BaseRedisQueueDAO methods are final")
class QueueResiliencyTest extends AbstractResiliencySpecification {

    @Autowired WorkflowResource workflowResource;

    @Autowired TaskResource taskResource;

    private static final String SIMPLE_TWO_TASK_WORKFLOW = "integration_test_wf";

    @BeforeEach
    void setup() {
        workflowTestUtil.taskDefinitions();
        workflowTestUtil.registerWorkflows("simple_workflow_1_integration_test.json");
    }

    // Workflow Resource endpoints

    @Test
    @DisplayName("Verify Start workflow fails when QueueDAO is unavailable")
    void verifyStartWorkflowFailsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String response =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));
        // then: Verify workflow starts when there are no Queue failures
        assertNotNull(response);

        // when: We try same request with Queue failure
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.push(*_) >> { throw new TransientException("Queue push failed
        // from Spy") }
        // Original assertion: thrown(TransientException.class)
        assertThrows(
                TransientException.class,
                () ->
                        workflowResource.startWorkflow(
                                new StartWorkflowRequest()
                                        .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                        .withVersion(1)));
    }

    @Test
    @DisplayName("Verify terminate succeeds when QueueDAO is unavailable")
    void verifyTerminateSucceedsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));
        // then: Verify workflow is started
        Workflow workflow = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());

        // when: We terminate it when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 2 * queueDAO.remove(*_) >> { throw new TransientException("Queue remove failed
        // from Spy") }
        // Original: 0 * queueDAO._
        workflowResource.terminate(workflowInstanceId, "Terminated from a test");

        // then: Verify that terminate is successful without any exceptions
        Workflow terminatedWorkflow = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, terminatedWorkflow.getStatus());
        assertEquals(1, terminatedWorkflow.getTasks().size());
        assertEquals("integration_task_1", terminatedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.CANCELED, terminatedWorkflow.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Verify Restart workflow fails when QueueDAO is unavailable")
    void verifyRestartWorkflowFailsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // and: We terminate it when QueueDAO is unavailable
        workflowResource.terminate(workflowInstanceId, "Terminated from a test");

        // then: Verify that workflow is in terminated state
        Workflow terminatedWorkflow = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, terminatedWorkflow.getStatus());
        assertEquals(1, terminatedWorkflow.getTasks().size());
        assertEquals("integration_task_1", terminatedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.CANCELED, terminatedWorkflow.getTasks().get(0).getStatus());

        // when: We restart workflow when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.push(*_) >> { throw new TransientException(...) }
        // Original: 1 * queueDAO.remove(*_) >> { throw new TransientException(...) }
        // Original: 0 * queueDAO._
        // Original assertion: thrown(TransientException.class)
        final String wfId = workflowInstanceId;
        assertThrows(TransientException.class, () -> workflowResource.restart(wfId, false));

        Workflow afterRestart = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, afterRestart.getStatus());
        assertEquals(0, afterRestart.getTasks().size());
    }

    @Test
    @DisplayName("Verify rerun fails when QueueDAO is unavailable")
    void verifyRerunFailsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // and: terminate it
        workflowResource.terminate(workflowInstanceId, "Terminated from a test");

        // then: Verify that workflow is in terminated state
        Workflow terminatedWorkflow = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, terminatedWorkflow.getStatus());
        assertEquals(1, terminatedWorkflow.getTasks().size());
        assertEquals("integration_task_1", terminatedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.CANCELED, terminatedWorkflow.getTasks().get(0).getStatus());

        // when: Workflow is rerun when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.push(*_) >> { throw new TransientException(...) }
        // Original: 0 * queueDAO._
        // Original assertion: thrown(TransientException.class)
        RerunWorkflowRequest rerunWorkflowRequest = new RerunWorkflowRequest();
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowInstanceId);
        final String wfId = workflowInstanceId;
        assertThrows(
                TransientException.class, () -> workflowResource.rerun(wfId, rerunWorkflowRequest));

        Workflow afterRerun = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, afterRerun.getStatus());
        assertEquals(0, afterRerun.getTasks().size());
    }

    @Test
    @DisplayName("Verify retry fails when QueueDAO is unavailable")
    void verifyRetryFailsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // and: terminate it
        workflowResource.terminate(workflowInstanceId, "Terminated from a test");

        // then: Verify that workflow is in terminated state
        Workflow terminatedWorkflow = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, terminatedWorkflow.getStatus());
        assertEquals(1, terminatedWorkflow.getTasks().size());
        assertEquals("integration_task_1", terminatedWorkflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.CANCELED, terminatedWorkflow.getTasks().get(0).getStatus());

        // when: workflow is restarted when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.push(*_) >> { throw new TransientException(...) }
        // Original: 0 * queueDAO._
        // Original assertion: thrown(TransientException.class)
        final String wfId = workflowInstanceId;
        assertThrows(TransientException.class, () -> workflowResource.retry(wfId, false));

        // then: Verify retry fails
        Workflow afterRetry = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.TERMINATED, afterRetry.getStatus());
        assertEquals(1, afterRetry.getTasks().size());
        assertEquals("integration_task_1", afterRetry.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.CANCELED, afterRetry.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Verify getWorkflow succeeds when QueueDAO is unavailable")
    void verifyGetWorkflowSucceedsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));
        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: We get a workflow when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO._
        Workflow workflow = workflowResource.getExecutionStatus(workflowInstanceId, true);

        // then: Verify workflow is returned
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(Task.Status.SCHEDULED, workflow.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Verify getWorkflows succeeds when QueueDAO is unavailable")
    void verifyGetWorkflowsSucceedsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));
        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: We get workflows when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO._
        // Original: notThrown(Exception)
        List<Workflow> workflows =
                workflowResource.getWorkflows(SIMPLE_TWO_TASK_WORKFLOW, "", true, true);
        // then: Verify queueDAO is not involved and an exception is not thrown
        assertNotNull(workflows);
    }

    @Test
    @DisplayName("Verify remove workflow succeeds when QueueDAO is unavailable")
    void verifyRemoveWorkflowSucceedsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));
        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: We delete the workflow when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.remove(Utils.DECIDER_QUEUE, _)
        workflowResource.delete(workflowInstanceId, false);

        // when: We try to get deleted workflow, verify the status and check if tasks are not
        // removed from queue
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO.remove(QueueUtils.getQueueName(tasks[0]), _)
        // then: thrown(NotFoundException.class)
        final String wfId = workflowInstanceId;
        assertThrows(
                NotFoundException.class, () -> workflowResource.getExecutionStatus(wfId, true));
    }

    @Test
    @DisplayName("Verify decide succeeds when QueueDAO is unavailable")
    void verifyDecideSucceedsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: We decide a workflow
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO._
        workflowResource.decide(workflowInstanceId);
        // then: Verify queueDAO is not involved — no assertion needed if we reach here
    }

    @Test
    @DisplayName("Verify pause succeeds when QueueDAO is unavailable")
    void verifyPauseSucceedsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: The workflow is paused when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.remove(*_) >> { throw new IllegalStateException("Queue remove
        // failed from Spy") }
        // Original: 0 * queueDAO._
        workflowResource.pauseWorkflow(workflowInstanceId);

        // then: Verify workflow is paused without any exceptions
        Workflow paused = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.PAUSED, paused.getStatus());
        assertEquals(1, paused.getTasks().size());
        assertEquals("integration_task_1", paused.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, paused.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Verify resume fails when QueueDAO is unavailable")
    void verifyResumeFailsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: The workflow is paused
        workflowResource.pauseWorkflow(workflowInstanceId);

        // then: Verify workflow is paused
        Workflow paused = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.PAUSED, paused.getStatus());
        assertEquals(1, paused.getTasks().size());
        assertEquals("integration_task_1", paused.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, paused.getTasks().get(0).getStatus());

        // when: Workflow is resumed when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.push(*_) >> { throw new TransientException("Queue push failed
        // from Spy") }
        // Original assertion: thrown(TransientException.class)
        final String wfId = workflowInstanceId;
        assertThrows(TransientException.class, () -> workflowResource.resumeWorkflow(wfId));

        // then: exception is thrown, workflow remains PAUSED
        Workflow stillPaused = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.PAUSED, stillPaused.getStatus());
        assertEquals(1, stillPaused.getTasks().size());
        assertEquals("integration_task_1", stillPaused.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, stillPaused.getTasks().get(0).getStatus());
    }

    @Test
    @DisplayName("Verify reset callbacks fails when QueueDAO is unavailable")
    void verifyResetCallbacksFailsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: Task is updated with callBackAfterSeconds
        Workflow workflow = workflowResource.getExecutionStatus(workflowInstanceId, true);
        Task task = workflow.getTasks().get(0);
        TaskResult taskResult = new TaskResult(task);
        taskResult.setCallbackAfterSeconds(120);
        taskResource.updateTask(taskResult);

        // and: then reset callbacks when QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.resetOffsetTime(*_) >> { throw new TransientException("Queue
        // resetOffsetTime failed from Spy") }
        // Original assertion: thrown(TransientException.class)
        final String wfId = workflowInstanceId;
        assertThrows(TransientException.class, () -> workflowResource.resetWorkflow(wfId));
    }

    @Test
    @DisplayName("Verify search is not impacted by QueueDAO")
    void verifySearchIsNotImpactedByQueueDAO() {
        // when: We perform a search
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO._
        workflowResource.search(0, 1, "", "", "");
        // then: Verify it doesn't involve QueueDAO — no assertion needed if we reach here
    }

    @Test
    @DisplayName("Verify search workflows by tasks is not impacted by QueueDAO")
    void verifySearchWorkflowsByTasksIsNotImpactedByQueueDAO() {
        // when: We perform a search
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO._
        workflowResource.searchWorkflowsByTasks(0, 1, "", "", "");
        // then: Verify it doesn't involve QueueDAO — no assertion needed if we reach here
    }

    @Test
    @DisplayName("Verify get external storage location is not impacted by QueueDAO")
    void verifyGetExternalStorageLocationIsNotImpactedByQueueDAO() {
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO._
        workflowResource.getExternalStorageLocation(
                "",
                ExternalPayloadStorage.Operation.READ.name(),
                ExternalPayloadStorage.PayloadType.WORKFLOW_INPUT.name());
        // then: Verify it doesn't involve QueueDAO — no assertion needed if we reach here
    }

    // Task Resource endpoints

    @Test
    @DisplayName("Verify polls return with no result when QueueDAO is unavailable")
    void verifyPollsReturnWithNoResultWhenQueueDAOIsUnavailable() {
        // when: Some task 'integration_task_1' is polled
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.pop(*_) >> { throw new IllegalStateException("Queue pop failed
        // from Spy") }
        // Original: 0 * queueDAO._
        // Original: notThrown(Exception)
        ResponseEntity<Task> responseEntity = taskResource.poll("integration_task_1", "test", "");

        // then: Verify poll returns no content without throwing
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
        assertNull(responseEntity.getBody());
    }

    @Test
    @DisplayName("Verify updateTask with COMPLETE status succeeds when QueueDAO is unavailable")
    void verifyUpdateTaskWithCompleteStatusSucceedsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: The first task 'integration_task_1' is polled
        ResponseEntity<Task> responseEntity = taskResource.poll("integration_task_1", "test", null);

        // then: Verify task is returned successfully
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(Task.Status.IN_PROGRESS, responseEntity.getBody().getStatus());
        assertEquals("integration_task_1", responseEntity.getBody().getTaskType());

        // when: the above task is updated, while QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.remove(*_) >> { throw new IllegalStateException("Queue remove
        // failed from Spy") }
        TaskResult taskResult = new TaskResult(responseEntity.getBody());
        taskResult.setStatus(TaskResult.Status.COMPLETED);
        String result = taskResource.updateTask(taskResult);

        // then: updateTask returns successfully without any exceptions
        assertEquals(responseEntity.getBody().getTaskId(), result);
    }

    @Test
    @DisplayName("Verify updateTask with IN_PROGRESS state fails when QueueDAO is unavailable")
    void verifyUpdateTaskWithInProgressStateFailsWhenQueueDAOIsUnavailable() {
        // when: Start a simple workflow
        String workflowInstanceId =
                workflowResource.startWorkflow(
                        new StartWorkflowRequest()
                                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                                .withVersion(1));

        // then: Verify workflow is started
        Workflow started = workflowResource.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, started.getStatus());
        assertEquals(1, started.getTasks().size());
        assertEquals("integration_task_1", started.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.SCHEDULED, started.getTasks().get(0).getStatus());

        // when: The first task 'integration_task_1' is polled
        ResponseEntity<Task> responseEntity = taskResource.poll("integration_task_1", "test", null);

        // then: Verify task is returned successfully
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(Task.Status.IN_PROGRESS, responseEntity.getBody().getStatus());
        assertEquals("integration_task_1", responseEntity.getBody().getTaskType());

        // when: the above task is updated, while QueueDAO is unavailable
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.postpone(*_) >> { throw new IllegalStateException("Queue postpone
        // failed from Spy") }
        // Original assertion: thrown(Exception)
        TaskResult taskResult = new TaskResult(responseEntity.getBody());
        taskResult.setStatus(TaskResult.Status.IN_PROGRESS);
        taskResult.setCallbackAfterSeconds(120);
        assertThrows(Exception.class, () -> taskResource.updateTask(taskResult));
    }

    @Test
    @DisplayName("verify getTaskQueueSizes fails when QueueDAO is unavailable")
    void verifyGetTaskQueueSizesFailsWhenQueueDAOIsUnavailable() {
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.getSize(*_) >> { throw new IllegalStateException("Queue getSize
        // failed from Spy") }
        assertThrows(
                Exception.class,
                () -> taskResource.size(Arrays.asList("testTaskType", "testTaskType2")));
    }

    @Test
    @DisplayName("Verify getAllQueueDetails fails when QueueDAO is unavailable")
    void verifyGetAllQueueDetailsFailsWhenQueueDAOIsUnavailable() {
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.queuesDetail() >> { throw new IllegalStateException("Queue
        // queuesDetail failed from Spy") }
        assertThrows(Exception.class, () -> taskResource.all());
    }

    @Test
    @DisplayName("Verify getPollData doesn't involve QueueDAO")
    void verifyGetPollDataDoesNotInvolveQueueDAO() {
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO.queuesDetail()
        taskResource.getPollData("integration_test_1");
        // then: no assertion needed if we reach here without exception
    }

    @Test
    @DisplayName("Verify getAllPollData fails when QueueDAO is unavailable")
    void verifyGetAllPollDataFailsWhenQueueDAOIsUnavailable() {
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 1 * queueDAO.queuesDetail() >> { throw new IllegalStateException("Queue
        // queuesDetail failed from Spy") }
        assertThrows(Exception.class, () -> taskResource.getAllPollData());
    }

    @Test
    @DisplayName("Verify task search is not impacted by QueueDAO")
    void verifyTaskSearchIsNotImpactedByQueueDAO() {
        // when: We perform a search
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO._
        taskResource.search(0, 1, "", "", "");
        // then: Verify it doesn't involve QueueDAO — no assertion needed if we reach here
    }

    @Test
    @DisplayName("Verify task get external storage location is not impacted by QueueDAO")
    void verifyTaskGetExternalStorageLocationIsNotImpactedByQueueDAO() {
        // TODO: interaction-based spy verification removed — BaseRedisQueueDAO methods are final
        // Original: 0 * queueDAO._
        taskResource.getExternalStorageLocation(
                "",
                ExternalPayloadStorage.Operation.READ.name(),
                ExternalPayloadStorage.PayloadType.TASK_INPUT.name());
        // then: Verify it doesn't involve QueueDAO — no assertion needed if we reach here
    }
}
