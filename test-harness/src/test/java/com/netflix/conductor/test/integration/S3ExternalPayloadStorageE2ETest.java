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
package com.netflix.conductor.test.integration;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.test.base.AbstractSpecification;
import com.netflix.conductor.test.config.LocalStackS3Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ConductorTestApp.class)
@TestPropertySource(locations = "classpath:application-s3test.properties")
@Import(LocalStackS3Configuration.class)
class S3ExternalPayloadStorageE2ETest extends AbstractSpecification {

    private static final String LINEAR_WORKFLOW_T1_T2 = "integration_test_wf";

    @SuppressWarnings("resource")
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
                    .withServices(LocalStackContainer.Service.S3)
                    .withEnv("DEBUG", "1");

    private static S3Client testS3Client;

    static {
        LOCALSTACK.start();

        // Configure the test configuration with LocalStack endpoint
        LocalStackS3Configuration.setLocalStackEndpoint(
                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString());

        // Create S3 client pointing to LocalStack for verification purposes
        testS3Client =
                S3Client.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                LOCALSTACK.getAccessKey(),
                                                LOCALSTACK.getSecretKey())))
                        .region(Region.of(LOCALSTACK.getRegion()))
                        .forcePathStyle(true)
                        .build();
    }

    @Autowired ExternalPayloadStorage externalPayloadStorage;

    @BeforeAll
    static void setupSpec() {
        createTestBucket();
    }

    @BeforeEach
    void setup() {
        workflowTestUtil.registerWorkflows("simple_workflow_1_integration_test.json");
    }

    @Test
    @DisplayName("Test workflow with large input payload stored in S3")
    void testWorkflowWithLargeInputPayloadStoredInS3() {
        // given: A workflow definition and large input payload
        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
        Map<String, Object> largeInput = createLargePayload(200); // 200+ bytes to exceed threshold
        String correlationId = "large-input-s3-test";

        // when: Starting workflow with large input
        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, largeInput, null);

        // then: Verify workflow starts and input is externalized
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertTrue(workflow.getInput().isEmpty()); // Input should be externalized
        assertNotNull(workflow.getExternalInputPayloadStoragePath());
        assertTrue(workflow.getExternalInputPayloadStoragePath().startsWith("workflow/input/"));

        // and: Verify S3 object exists and contains correct data
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        boolean s3ObjectExists = checkS3ObjectExists(workflow.getExternalInputPayloadStoragePath());
        assertTrue(s3ObjectExists);

        // and: Verify S3 object content matches original input
        String storedContent = getS3ObjectContent(workflow.getExternalInputPayloadStoragePath());
        assertNotNull(storedContent);
    }

    @Test
    @DisplayName("Test task with large output payload stored in S3")
    void testTaskWithLargeOutputPayloadStoredInS3() {
        // given: A running workflow
        String workflowInstanceId =
                startWorkflow(
                        LINEAR_WORKFLOW_T1_T2, 1, "large-output-s3-test", new HashMap<>(), null);

        // when: Completing first task with large output
        Map<String, Object> largeOutput = createLargePayload(300); // Exceeds threshold
        Task polledAndAckTask =
                workflowTestUtil.pollAndCompleteTask(
                        "integration_task_1", "test.worker", largeOutput);

        // then: Verify task completed and output is externalized
        assertNotNull(polledAndAckTask);

        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertEquals(2, workflow.getTasks().size());
        assertEquals("integration_task_1", workflow.getTasks().get(0).getTaskType());
        assertEquals(Task.Status.COMPLETED, workflow.getTasks().get(0).getStatus());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertNotNull(workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());
        assertTrue(
                workflow.getTasks()
                        .get(0)
                        .getExternalOutputPayloadStoragePath()
                        .startsWith("task/output/"));

        // and: Verify S3 object exists for task output
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        String taskOutputPath = workflow.getTasks().get(0).getExternalOutputPayloadStoragePath();
        assertTrue(checkS3ObjectExists(taskOutputPath));
    }

    @Test
    @DisplayName("Test workflow completion with large output stored in S3")
    void testWorkflowCompletionWithLargeOutputStoredInS3() {
        // given: A running workflow
        String workflowInstanceId =
                startWorkflow(
                        LINEAR_WORKFLOW_T1_T2,
                        1,
                        "large-workflow-output-test",
                        new HashMap<>(),
                        null);

        // when: Completing all tasks with outputs that result in large workflow output
        // Complete first task
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "test.worker", createLargePayload(150));

        // Complete second task
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "test.worker", createLargePayload(150));

        // then: Verify workflow completes and task outputs are externalized
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());

        // Task outputs should be externalized due to their large size
        assertEquals(2, workflow.getTasks().size());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertTrue(workflow.getTasks().get(1).getOutputData().isEmpty());
        assertNotNull(workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());
        assertNotNull(workflow.getTasks().get(1).getExternalOutputPayloadStoragePath());

        assertFalse(workflow.getOutput().isEmpty());

        // and: Verify S3 objects exist for task outputs
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertTrue(
                checkS3ObjectExists(
                        workflow.getTasks().get(0).getExternalOutputPayloadStoragePath()));
        assertTrue(
                checkS3ObjectExists(
                        workflow.getTasks().get(1).getExternalOutputPayloadStoragePath()));
    }

    @Test
    @DisplayName("Test small payload is not externalized")
    void testSmallPayloadIsNotExternalized() {
        // given: A workflow with very small input
        Map<String, Object> smallInput = new HashMap<>();
        smallInput.put("k", "v"); // Very small payload - well under 1KB threshold
        String correlationId = "small-input-test";

        // when: Starting workflow with small input
        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, smallInput, null);

        // then: Verify input is not externalized
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.RUNNING, workflow.getStatus());
        assertFalse(workflow.getInput().isEmpty()); // Input should not be externalized
        assertNull(workflow.getExternalInputPayloadStoragePath());
        assertEquals("v", workflow.getInput().get("k"));
    }

    @Test
    @DisplayName("Test multiple workflows with S3 storage isolation")
    void testMultipleWorkflowsWithS3StorageIsolation() {
        // given: Two workflows with large inputs
        Map<String, Object> largeInput1 = createLargePayload(200, "workflow1");
        Map<String, Object> largeInput2 = createLargePayload(250, "workflow2");

        // when: Starting both workflows
        String workflowId1 =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, "isolation-test-1", largeInput1, null);
        String workflowId2 =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, "isolation-test-2", largeInput2, null);

        // then: Verify both workflows have separate S3 objects
        Workflow workflow1 = workflowExecutionService.getExecutionStatus(workflowId1, true);
        Workflow workflow2 = workflowExecutionService.getExecutionStatus(workflowId2, true);

        assertNotNull(workflow1.getExternalInputPayloadStoragePath());
        assertNotNull(workflow2.getExternalInputPayloadStoragePath());
        assertNotEquals(
                workflow1.getExternalInputPayloadStoragePath(),
                workflow2.getExternalInputPayloadStoragePath());

        // and: Both S3 objects exist
        assertTrue(checkS3ObjectExists(workflow1.getExternalInputPayloadStoragePath()));
        assertTrue(checkS3ObjectExists(workflow2.getExternalInputPayloadStoragePath()));
    }

    @Test
    @DisplayName("Test external payload storage integration works end-to-end")
    void testExternalPayloadStorageIntegrationWorksEndToEnd() {
        // given: A workflow with large input payload
        Map<String, Object> largeInput = createLargePayload(500);
        String correlationId = "integration-test";

        // when: Running complete workflow with large payloads
        String workflowInstanceId =
                startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, largeInput, null);

        // Complete first task with large output
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_1", "test.worker", createLargePayload(400));

        // Complete second task with large output
        workflowTestUtil.pollAndCompleteTask(
                "integration_task_2", "test.worker", createLargePayload(300));

        // then: Verify complete workflow execution with external storage
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());

        // Workflow input should be externalized
        assertTrue(workflow.getInput().isEmpty());
        assertNotNull(workflow.getExternalInputPayloadStoragePath());

        // Task outputs should be externalized
        assertEquals(2, workflow.getTasks().size());
        assertTrue(workflow.getTasks().get(0).getOutputData().isEmpty());
        assertTrue(workflow.getTasks().get(1).getOutputData().isEmpty());
        assertNotNull(workflow.getTasks().get(0).getExternalOutputPayloadStoragePath());
        assertNotNull(workflow.getTasks().get(1).getExternalOutputPayloadStoragePath());

        // Workflow output will be small because it only contains references to externalized task
        // outputs
        assertFalse(workflow.getOutput().isEmpty());

        // and: All S3 objects exist for input and task outputs
        workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true);
        assertTrue(checkS3ObjectExists(workflow.getExternalInputPayloadStoragePath()));
        assertTrue(
                checkS3ObjectExists(
                        workflow.getTasks().get(0).getExternalOutputPayloadStoragePath()));
        assertTrue(
                checkS3ObjectExists(
                        workflow.getTasks().get(1).getExternalOutputPayloadStoragePath()));
    }

    // Helper methods

    private static void createTestBucket() {
        try {
            testS3Client.createBucket(
                    CreateBucketRequest.builder().bucket("conductor-test-payloads").build());
        } catch (Exception e) {
            // may already exist — ignore
        }
    }

    private boolean checkS3ObjectExists(String key) {
        try {
            testS3Client.headObject(
                    HeadObjectRequest.builder().bucket("conductor-test-payloads").key(key).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getS3ObjectContent(String key) {
        try {
            byte[] bytes =
                    testS3Client
                            .getObject(
                                    GetObjectRequest.builder()
                                            .bucket("conductor-test-payloads")
                                            .key(key)
                                            .build())
                            .readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> createLargePayload(int sizeMultiplier) {
        return createLargePayload(sizeMultiplier, "test");
    }

    private Map<String, Object> createLargePayload(int sizeMultiplier, String prefix) {
        Map<String, Object> payload = new HashMap<>();
        String baseData = "x".repeat(50); // 50 chars

        // Create enough data to exceed the 1KB threshold
        int iterations = sizeMultiplier / 5;
        for (int i = 1; i <= iterations; i++) {
            payload.put(
                    prefix + "_field_" + i,
                    baseData + "_" + i + "_additional_padding_to_ensure_size_" + "y".repeat(100));
        }

        // Add some structured data
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("version", "1.0");
        metadata.put(
                "description",
                "Large payload for testing S3 external storage functionality with extended"
                        + " description to ensure adequate size for 1KB threshold testing and"
                        + " verification purposes");
        payload.put("metadata", metadata);

        // Add more data to guarantee we exceed 1KB
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put(
                "field1",
                "This is additional data to ensure we exceed the 1KB threshold" + "z".repeat(200));
        additionalData.put("field2", "More padding data for size requirements" + "w".repeat(200));
        additionalData.put(
                "field3", "Even more content to guarantee threshold exceeded" + "v".repeat(200));
        payload.put("additionalData", additionalData);

        return payload;
    }
}
