package io.conductor.e2e.processing;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.TestUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class IdempotencyTests {

    @Test
    @DisplayName("Deleting a workflow shouldn't break idempotency")
    @SneakyThrows
    void deletingAWorkflowShouldNotBreakIdempotency() {
        WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
        MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;

        var mapper = new ObjectMapperProvider().getObjectMapper();
        var wfDef = mapper.readValue(TestUtil.getResourceAsString("metadata/broken_idempotency_logic_wf.json"), WorkflowDef.class);

        // Register workflow definition (overwrite if already exists)
        metadataClient.registerWorkflowDef(wfDef);

        // 1. Start a workflow with an idempotency Key
        var idempotencyKey = UUID.randomUUID().toString();
        var startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(wfDef.getName());
        startWorkflowRequest.setVersion(wfDef.getVersion());
        startWorkflowRequest.setIdempotencyKey(idempotencyKey);
        startWorkflowRequest.setIdempotencyStrategy(IdempotencyStrategy.FAIL);

        var workflowId0 = workflowClient.startWorkflow(startWorkflowRequest);
        assertNotNull(workflowId0, "First workflow ID should not be null");

        // Wait for workflow to be fully registered
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertNotNull(workflowClient.getWorkflow(workflowId0, false)));

        // 2. delete the workflow
        workflowClient.deleteWorkflow(workflowId0, false);

        // Wait for deletion to complete
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    var thrown = assertThrows(ConductorClientException.class, () -> workflowClient.getWorkflow(workflowId0, true));
                    assertEquals(404, thrown.getStatusCode(), "Deleted workflow should return 404");
                });

        // 3. Start another workflow with the same idempotency key
        // --- The first one should start successfully (idempotency should be reset after deletion)
        var workflowId1 = workflowClient.startWorkflow(startWorkflowRequest);
        assertNotNull(workflowId1, "Second workflow ID should not be null");
        assertNotEquals(workflowId0, workflowId1, "Second workflow should have a different ID");

        // Wait for workflow to be registered
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertNotNull(workflowClient.getWorkflow(workflowId1, false)));

        // --- The second one should fail (idempotency key now in use by workflowId1)
        var conflictException = assertThrows(ConductorClientException.class, () -> workflowClient.startWorkflow(startWorkflowRequest),
                "Starting a workflow with the same idempotency key should fail");
        assertEquals(409, conflictException.getStatusCode());
        assertTrue(conflictException
                .getMessage()
                .contains(String.format("Workflow with the idempotency key %s already exists", idempotencyKey)),
                "Error message should mention the idempotency key");

        // Clean up the second workflow
        try {
            workflowClient.deleteWorkflow(workflowId1, false);
        } catch (Exception e) {
            // Ignore cleanup errors
            System.err.println("Failed to cleanup workflow: " + e.getMessage());
        }
    }
}
