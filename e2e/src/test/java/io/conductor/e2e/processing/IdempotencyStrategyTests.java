package io.conductor.e2e.processing;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import io.conductor.e2e.util.ApiUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;

public class IdempotencyStrategyTests {

    private static final String WF_NAME = "idempotency_strategy_test_workflow";
    private static final String TASK_NAME = "idempotency_test_task";
    private MetadataClient metadataClient;
    private WorkflowClient workflowClient;
    private TaskClient orkesTaskClient;

    @BeforeEach()
    void beforeEach() {
        metadataClient = ApiUtil.METADATA_CLIENT;
        workflowClient = ApiUtil.WORKFLOW_CLIENT;
        orkesTaskClient = ApiUtil.TASK_CLIENT;
        registerWorkflowDef();
    }

    @Test
    @DisplayName("FAIL strategy should fail if a workflow with same idempotency key exists")
    void failStrategy() {
        var idempotencyKey = UUID.randomUUID().toString();
        var startRequest = new StartWorkflowRequest();
        startRequest.setName(WF_NAME);
        startRequest.setVersion(1);
        startRequest.setIdempotencyStrategy(IdempotencyStrategy.FAIL);
        startRequest.setIdempotencyKey(idempotencyKey);
        var workflowId = workflowClient.startWorkflow(startRequest);
        assertNotNull(workflowId);
        // Workflow is running
        var conflictException = assertThrows(ConductorClientException.class, () -> workflowClient.startWorkflow(startRequest));
        assertTrue(conflictException.getMessage().contains("Workflow with the idempotency key " + idempotencyKey + " already exists"));

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var task = orkesTaskClient.pollTask("idempotency_test_task", "unit_test", null);
                    assertNotNull(task);
                    assertEquals(workflowId, task.getWorkflowInstanceId());
                    var result = new TaskResult(task);
                    result.setStatus(TaskResult.Status.COMPLETED);
                    orkesTaskClient.updateTask(result);
                });

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var wf = workflowClient.getWorkflow(workflowId, false);
                    assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                });

        // Workflow is completed
        conflictException = assertThrows(ConductorClientException.class, () -> workflowClient.startWorkflow(startRequest));
        assertTrue(conflictException.getMessage().contains("Workflow with the idempotency key " + idempotencyKey + " already exists"));
    }

    @Test
    @DisplayName("RETURN_EXISTING returns the existing workflow with same idempotency key exists")
    void returnExisting() {
        var idempotencyKey = UUID.randomUUID().toString();
        var startRequest = new StartWorkflowRequest();
        startRequest.setName(WF_NAME);
        startRequest.setVersion(1);
        startRequest.setIdempotencyStrategy(IdempotencyStrategy.RETURN_EXISTING);
        startRequest.setIdempotencyKey(idempotencyKey);
        var workflowId = workflowClient.startWorkflow(startRequest);
        assertNotNull(workflowId);
        // Workflow is running
        var workflowId2 = workflowClient.startWorkflow(startRequest);
        assertEquals(workflowId, workflowId2);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var task = orkesTaskClient.pollTask("idempotency_test_task", "unit_test", null);
                    assertNotNull(task);
                    assertEquals(workflowId, task.getWorkflowInstanceId());
                    var result = new TaskResult(task);
                    result.setStatus(TaskResult.Status.COMPLETED);
                    orkesTaskClient.updateTask(result);
                });

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var wf = workflowClient.getWorkflow(workflowId, false);
                    assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                });

        // Workflow is completed
        var workflowId3 = workflowClient.startWorkflow(startRequest);
        assertEquals(workflowId, workflowId3);
    }

    @Test
    @DisplayName("FAIL_ON_RUNNING strategy should fail if a workflow with same idempotency key is running. Ignores terminal state")
    void failOnRunningStrategy() {
        var idempotencyKey = UUID.randomUUID().toString();
        var startRequest = new StartWorkflowRequest();
        startRequest.setName(WF_NAME);
        startRequest.setVersion(1);
        startRequest.setIdempotencyStrategy(IdempotencyStrategy.FAIL_ON_RUNNING);
        startRequest.setIdempotencyKey(idempotencyKey);
        var workflowId = workflowClient.startWorkflow(startRequest);
        assertNotNull(workflowId);
        var workflow = workflowClient.getWorkflow(workflowId, false);
        assertNotNull(workflow);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        // Workflow is running
        var conflictException = assertThrows(ConductorClientException.class, () -> workflowClient.startWorkflow(startRequest));
        assertTrue(conflictException.getMessage().contains("Workflow with the idempotency key " + idempotencyKey + " already exists"));

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var task = orkesTaskClient.pollTask("idempotency_test_task", "unit_test", null);
                    assertNotNull(task);
                    assertEquals(workflowId, task.getWorkflowInstanceId());
                    var result = new TaskResult(task);
                    result.setStatus(TaskResult.Status.COMPLETED);
                    orkesTaskClient.updateTask(result);
                });

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var wf = workflowClient.getWorkflow(workflowId, false);
                    assertEquals(Workflow.WorkflowStatus.COMPLETED, wf.getStatus());
                });

        // Workflow is completed
        var workflowId2 =  workflowClient.startWorkflow(startRequest);
        assertNotEquals(workflowId, workflowId2);
    }

    public void registerWorkflowDef() {
        var simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(TASK_NAME);
        simpleTask.setName(TASK_NAME);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);

        var workflowDef = new WorkflowDef();
        workflowDef.setName(WF_NAME);
        workflowDef.setVersion(1);
        workflowDef.setOwnerEmail("test@orkes.io");
        workflowDef.setDescription("Workflow to test Idempotency Key check");
        workflowDef.setTasks(List.of(simpleTask));
        metadataClient.updateWorkflowDefs(List.of(workflowDef));
    }
}
