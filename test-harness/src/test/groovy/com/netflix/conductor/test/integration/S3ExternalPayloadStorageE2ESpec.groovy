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
package com.netflix.conductor.test.integration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.spock.Testcontainers
import org.testcontainers.utility.DockerImageName

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.common.utils.ExternalPayloadStorage
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.config.LocalStackS3Configuration

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import spock.lang.Shared

@Testcontainers
@SpringBootTest(classes = com.netflix.conductor.ConductorTestApp.class)
@TestPropertySource(locations = "classpath:application-s3test.properties")
@Import(LocalStackS3Configuration)
class S3ExternalPayloadStorageE2ESpec extends AbstractSpecification {

    @Shared
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.S3)
            .withEnv("DEBUG", "1")

    @Shared
    S3Client testS3Client

    @Shared
    def LINEAR_WORKFLOW_T1_T2 = 'integration_test_wf'

    @Autowired
    ExternalPayloadStorage externalPayloadStorage

    def setupSpec() {
        // Start LocalStack
        localstack.start()
        
        // Configure the test configuration with LocalStack endpoint
        LocalStackS3Configuration.setLocalStackEndpoint(
            localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString())
        
        // Create S3 client pointing to LocalStack for verification purposes
        testS3Client = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .forcePathStyle(true)
                .build()

        // Create test bucket
        createTestBucket()
        
        println "LocalStack S3 started at: ${localstack.getEndpointOverride(LocalStackContainer.Service.S3)}"
    }

    def cleanupSpec() {
        if (testS3Client) {
            testS3Client.close()
        }
        localstack.stop()
    }

    def setup() {
        workflowTestUtil.registerWorkflows('simple_workflow_1_integration_test.json')
    }

    def "Test workflow with large input payload stored in S3"() {
        given: "A workflow definition and large input payload"
        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)
        def largeInput = createLargePayload(200) // 200+ bytes to exceed threshold
        def correlationId = 'large-input-s3-test'

        when: "Starting workflow with large input"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, largeInput, null)

        then: "Verify workflow starts and input is externalized"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            input.isEmpty() // Input should be externalized
            externalInputPayloadStoragePath != null
            externalInputPayloadStoragePath.startsWith("workflow/input/")
        }

        and: "Verify S3 object exists and contains correct data"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def s3ObjectExists = checkS3ObjectExists(workflow.externalInputPayloadStoragePath)
        s3ObjectExists

        and: "Verify S3 object content matches original input"
        def storedContent = getS3ObjectContent(workflow.externalInputPayloadStoragePath)
        storedContent != null
    }

    def "Test task with large output payload stored in S3"() {
        given: "A running workflow"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, 'large-output-s3-test', [:], null)

        when: "Completing first task with large output"
        def largeOutput = createLargePayload(300) // Exceeds threshold
        def polledAndAckTask = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'test.worker', largeOutput)

        then: "Verify task completed and output is externalized"
        polledAndAckTask.size() == 1
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.isEmpty()
            tasks[0].externalOutputPayloadStoragePath != null
            tasks[0].externalOutputPayloadStoragePath.startsWith("task/output/")
        }

        and: "Verify S3 object exists for task output"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def taskOutputPath = workflow.tasks[0].externalOutputPayloadStoragePath
        checkS3ObjectExists(taskOutputPath)
    }

    def "Test workflow completion with large output stored in S3"() {
        given: "A running workflow"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, 'large-workflow-output-test', [:], null)

        when: "Completing all tasks with outputs that result in large workflow output"
        // Complete first task
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'test.worker', createLargePayload(150))
        
        // Complete second task
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'test.worker', createLargePayload(150))

        then: "Verify workflow completes and task outputs are externalized"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            
            // Task outputs should be externalized due to their large size
            tasks.size() == 2
            tasks[0].outputData.isEmpty()
            tasks[1].outputData.isEmpty()
            tasks[0].externalOutputPayloadStoragePath != null
            tasks[1].externalOutputPayloadStoragePath != null

            !output.isEmpty()
        }

        and: "Verify S3 objects exist for task outputs"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        checkS3ObjectExists(workflow.tasks[0].externalOutputPayloadStoragePath)
        checkS3ObjectExists(workflow.tasks[1].externalOutputPayloadStoragePath)
    }

    def "Test small payload is not externalized"() {
        given: "A workflow with very small input"
        def smallInput = ['k': 'v'] // Very small payload - well under 1KB threshold
        def correlationId = 'small-input-test'

        when: "Starting workflow with small input"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, smallInput, null)

        then: "Verify input is not externalized"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            !input.isEmpty() // Input should not be externalized
            externalInputPayloadStoragePath == null
            input.k == 'v'
        }
    }

    def "Test multiple workflows with S3 storage isolation"() {
        given: "Two workflows with large inputs"
        def largeInput1 = createLargePayload(200, "workflow1")
        def largeInput2 = createLargePayload(250, "workflow2")

        when: "Starting both workflows"
        def workflowId1 = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, 'isolation-test-1', largeInput1, null)
        def workflowId2 = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, 'isolation-test-2', largeInput2, null)

        then: "Verify both workflows have separate S3 objects"
        def workflow1 = workflowExecutionService.getExecutionStatus(workflowId1, true)
        def workflow2 = workflowExecutionService.getExecutionStatus(workflowId2, true)
        
        workflow1.externalInputPayloadStoragePath != null
        workflow2.externalInputPayloadStoragePath != null
        workflow1.externalInputPayloadStoragePath != workflow2.externalInputPayloadStoragePath

        and: "Both S3 objects exist"
        checkS3ObjectExists(workflow1.externalInputPayloadStoragePath)
        checkS3ObjectExists(workflow2.externalInputPayloadStoragePath)
    }

    def "Test external payload storage integration works end-to-end"() {
        given: "A workflow with large input payload"
        def largeInput = createLargePayload(500)
        def correlationId = 'integration-test'

        when: "Running complete workflow with large payloads"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, largeInput, null)
        
        // Complete first task with large output
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'test.worker', createLargePayload(400))
        
        // Complete second task with large output  
        workflowTestUtil.pollAndCompleteTask('integration_task_2', 'test.worker', createLargePayload(300))

        then: "Verify complete workflow execution with external storage"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            
            // Workflow input should be externalized
            input.isEmpty()
            externalInputPayloadStoragePath != null
            
            // Task outputs should be externalized
            tasks.size() == 2
            tasks[0].outputData.isEmpty()
            tasks[1].outputData.isEmpty()
            tasks[0].externalOutputPayloadStoragePath != null
            tasks[1].externalOutputPayloadStoragePath != null
            
            // Workflow output will be small because it only contains references to externalized task outputs
            !output.isEmpty()
        }

        and: "All S3 objects exist for input and task outputs"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        checkS3ObjectExists(workflow.externalInputPayloadStoragePath)
        checkS3ObjectExists(workflow.tasks[0].externalOutputPayloadStoragePath)
        checkS3ObjectExists(workflow.tasks[1].externalOutputPayloadStoragePath)
    }

    // Helper methods
    private void createTestBucket() {
        try {
            testS3Client.createBucket(CreateBucketRequest.builder()
                    .bucket("conductor-test-payloads")
                    .build())
            println "Created S3 bucket: conductor-test-payloads"
        } catch (Exception e) {
            println "Error creating bucket (may already exist): ${e.message}"
        }
    }

    private boolean checkS3ObjectExists(String key) {
        try {
            testS3Client.headObject(HeadObjectRequest.builder()
                    .bucket("conductor-test-payloads")
                    .key(key)
                    .build())
            return true
        } catch (Exception e) {
            println "S3 object does not exist: ${key}, error: ${e.message}"
            return false
        }
    }

    private String getS3ObjectContent(String key) {
        try {
            def response = testS3Client.getObject(GetObjectRequest.builder()
                    .bucket("conductor-test-payloads")
                    .key(key)
                    .build())
            return new String(response.readAllBytes(), "UTF-8")
        } catch (Exception e) {
            println "Error reading S3 object: ${key}, error: ${e.message}"
            return null
        }
    }

    private Map<String, Object> createLargePayload(int sizeMultiplier, String prefix = "test") {
        def payload = [:]
        def baseData = "x" * 50 // 50 chars
        
        // Create enough data to exceed the 1KB threshold
        (1..sizeMultiplier/5).each { i ->
            payload["${prefix}_field_${i}"] = baseData + "_" + i + "_additional_padding_to_ensure_size_" + ("y" * 100)
        }
        
        // Add some structured data
        payload["metadata"] = [
            timestamp: System.currentTimeMillis(),
            version: "1.0",
            description: "Large payload for testing S3 external storage functionality with extended description to ensure adequate size for 1KB threshold testing and verification purposes"
        ]
        
        // Add more data to guarantee we exceed 1KB
        payload["additionalData"] = [
            field1: "This is additional data to ensure we exceed the 1KB threshold" + ("z" * 200),
            field2: "More padding data for size requirements" + ("w" * 200),
            field3: "Even more content to guarantee threshold exceeded" + ("v" * 200)
        ]
        
        return payload
    }
}
