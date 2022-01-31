/*
 * Copyright 2022 Netflix, Inc.
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

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.test.base.AbstractSpecification

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Shared

class KafkaPublishTaskSpec extends AbstractSpecification {

    @Autowired
    ObjectMapper objectMapper

    @Shared
    def isWorkflowRegistered = false

    def kafkaInput = ['requestDetails': ['key1': 'value1', 'key2': 42],
                      'path1'         : 'file://path1',
                      'path2'         : 'file://path2',
                      'outputPath'    : 's3://bucket/outputPath'
    ]

    def expectedTaskInput = "{\"kafka_request\":{\"topic\":\"test_kafka_topic\",\"bootStrapServers\":\"localhost:9092\",\"value\":{\"requestDetails\":{\"key1\":\"value1\",\"key2\":42},\"outputPath\":\"s3://bucket/outputPath\",\"inputPaths\":[\"file://path1\",\"file://path2\"]}}}"

    def setup() {
        if (!isWorkflowRegistered) {
            registerKafkaWorkflow()
            isWorkflowRegistered = true
        }
    }

    def "Test the kafka template usage failure case"() {

        given: "Start a workflow based on the registered workflow"
        def workflowInstanceId = workflowExecutor.startWorkflow("template_kafka_workflow", 1,
                "testTaskDefTemplate", kafkaInput,
                null, null, null)

        and: "Get the workflow based on the Id that is being executed"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def task = workflow.tasks.get(0)
        def taskInput = task.inputData

        when: "Ensure that the task is pollable and fail the task"
        def polledTask = workflowExecutionService.poll('KAFKA_PUBLISH', 'test')
        workflowExecutionService.ackTaskReceived(polledTask.taskId)
        def taskResult = new TaskResult(polledTask)
        taskResult.status = TaskResult.Status.FAILED
        taskResult.reasonForIncompletion = 'NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down'
        taskResult.addOutputData("TERMINAL_ERROR", "Integration endpoint down: FOOBAR")
        taskResult.addOutputData("ErrorMessage", "There was a terminal error")
        workflowExecutionService.updateTask(taskResult)

        and: "Then run a decide to move the workflow forward"
        workflowExecutor.decide(workflowInstanceId)

        and: "Get the updated workflow after the task result has been updated"
        def updatedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        then: "Check that the workflow is created and is not terminal"
        workflowInstanceId
        workflow
        !workflow.getStatus().isTerminal()
        !workflow.getReasonForIncompletion()

        and: "Check if the input of the next task to be polled is as expected for a kafka task"
        taskInput
        taskInput.containsKey('kafka_request')
        taskInput['kafka_request'] instanceof Map
        objectMapper.writeValueAsString(taskInput) == expectedTaskInput

        and: "Polled task is not null and the workflowInstanceId of the task is same as the workflow created initially"
        polledTask
        polledTask.workflowInstanceId == workflowInstanceId

        and: "The updated workflow is in a failed state"
        updatedWorkflow
        updatedWorkflow.status == Workflow.WorkflowStatus.FAILED
    }

    def "Test the kafka template usage success case"() {

        given: "Start a workflow based on the registered kafka workflow"
        def workflowInstanceId = workflowExecutor.startWorkflow("template_kafka_workflow", 1,
                "testTaskDefTemplate", kafkaInput,
                null, null, null)

        and: "Get the workflow based on the Id that is being executed"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def task = workflow.tasks.get(0)
        def taskInput = task.inputData

        when: "Ensure that the task is pollable and complete the task"
        def polledTask = workflowExecutionService.poll('KAFKA_PUBLISH', 'test')
        workflowExecutionService.ackTaskReceived(polledTask.taskId)
        def taskResult = new TaskResult(polledTask)
        taskResult.setStatus(TaskResult.Status.COMPLETED)
        workflowExecutionService.updateTask(taskResult)

        and: "Then run a decide to move the workflow forward"
        workflowExecutor.decide(workflowInstanceId)

        and: "Get the updated workflow after the task result has been updated"
        def updatedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        then: "Check that the workflow is created and is not terminal"
        workflowInstanceId
        workflow
        !workflow.getStatus().isTerminal()
        !workflow.getReasonForIncompletion()

        and: "Check if the input of the next task to be polled is as expected for a kafka task"
        taskInput
        taskInput.containsKey('kafka_request')
        taskInput['kafka_request'] instanceof Map
        objectMapper.writeValueAsString(taskInput) == expectedTaskInput

        and: "Polled task is not null and the workflowInstanceId of the task is same as the workflow created initially"
        polledTask
        polledTask.workflowInstanceId == workflowInstanceId

        and: "The updated workflow is complete"
        updatedWorkflow
        updatedWorkflow.status == Workflow.WorkflowStatus.COMPLETED

    }

    def registerKafkaWorkflow() {
        System.setProperty("STACK_KAFKA", "test_kafka_topic")
        TaskDef templatedTask = new TaskDef()
        templatedTask.name = "templated_kafka_task"
        templatedTask.retryCount = 0
        templatedTask.ownerEmail = "test@harness.com"

        def kafkaRequest = new HashMap<>()
        kafkaRequest["topic"] = '${STACK_KAFKA}'
        kafkaRequest["bootStrapServers"] = "localhost:9092"

        def value = new HashMap<>()
        value["inputPaths"] = ['${workflow.input.path1}', '${workflow.input.path2}']
        value["requestDetails"] = '${workflow.input.requestDetails}'
        value["outputPath"] = '${workflow.input.outputPath}'
        kafkaRequest["value"] = value

        templatedTask.inputTemplate["kafka_request"] = kafkaRequest
        metadataService.registerTaskDef([templatedTask])

        WorkflowDef templateWf = new WorkflowDef()
        templateWf.name = "template_kafka_workflow"
        WorkflowTask wft = new WorkflowTask()
        wft.name = templatedTask.name
        wft.workflowTaskType = TaskType.KAFKA_PUBLISH
        wft.taskReferenceName = "t0"
        templateWf.tasks.add(wft)
        templateWf.schemaVersion = 2
        templateWf.ownerEmail = "test@harness.com"
        metadataService.registerWorkflowDef(templateWf)
    }
}
