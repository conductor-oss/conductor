/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.counductor.integration.test

import com.netflix.archaius.guice.ArchaiusModule
import com.netflix.archaius.test.TestPropertyOverride
import com.netflix.conductor.bootstrap.BootstrapModule
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.tests.integration.model.TaskWrapper
import com.netflix.conductor.tests.utils.EmbeddedTestElasticSearch
import com.netflix.conductor.tests.utils.IntegrationTestModule
import com.netflix.conductor.tests.utils.JsonUtils
import com.netflix.conductor.tests.utils.TestJettyServer
import com.netflix.governator.guice.test.ModulesForTesting
import spock.lang.Specification

import javax.inject.Inject

@ModulesForTesting([BootstrapModule.class, IntegrationTestModule.class, ArchaiusModule.class])
//These settings can be moved to the Integration Module or to a properties file if they end up repeating in each of the test cases
@TestPropertyOverride(["workflow.elasticsearch.embedded.port=9205",
        "workflow.elasticsearch.url=localhost:9305",
        "workflow.decider.locking.enabled=false",
        "EC2_REGION=us-east-1",
        "EC2_AVAILABILITY_ZONE=us-east-1c",
        "workflow.elasticsearch.index.name=conductor",
        "workflow.namespace.prefix=integration-test",
        "db=memory"
])
class ExclusiveJoinEndToEndSpec extends Specification {

    //This ensures that the embedded elastic search is started
    @Inject
    EmbeddedTestElasticSearch embeddedTestElasticSearch
    //This ensures that the jetty server is started
    @Inject
    TestJettyServer jettyServer

    def taskClient = TestJettyServer.getTaskClient()
    def workflowClient = TestJettyServer.getWorkflowClient()
    def metadataClient = TestJettyServer.getMetaDataClient()

    def CONDUCTOR_WORKFLOW_DEF_NAME = "ExclusiveJoinTestWorkflow"

    def setup() {
        registerWorkflowDefinitions()
    }

    def cleanup() {
        unRegisterWorkflowDefinitions()
    }

    def registerWorkflowDefinitions() {
        TaskWrapper taskWrapper = JsonUtils.fromJson("integration/scenarios/legacy/ExclusiveJoinTaskDef.json", TaskWrapper.class)
        metadataClient.registerTaskDefs(taskWrapper.getTaskDefs())

        WorkflowDef conductorWorkflowDef = JsonUtils.fromJson("integration/scenarios/legacy/ExclusiveJoinWorkflowDef.json",
                WorkflowDef.class)
        metadataClient.registerWorkflowDef(conductorWorkflowDef)
    }

    def unRegisterWorkflowDefinitions() {
        WorkflowDef conductorWorkflowDef = JsonUtils.fromJson("integration/scenarios/legacy/ExclusiveJoinWorkflowDef.json",
                WorkflowDef.class)
        metadataClient.unregisterWorkflowDef(conductorWorkflowDef.getName(), conductorWorkflowDef.getVersion())
    }

    def setTaskResult(String workflowInstanceId, String taskId, TaskResult.Status status,
                      Map<String, Object> output) {
        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(taskId)
        taskResult.setWorkflowInstanceId(workflowInstanceId)
        taskResult.setStatus(status)
        taskResult.setOutputData(output)
        return taskResult
    }


    def "Test that the default decision is run"() {
        given: "The input parameter required to make decision_1 is null to ensure that the default decision is run"
        def inputParameters = ["decision_1": "null"]

        and: "A new workflow is started based on the input parameters"
        def startWorkflowRequest = new StartWorkflowRequest()
                .withName(CONDUCTOR_WORKFLOW_DEF_NAME)
                .withInput(inputParameters)
                .withVersion(1)

        and: "The workflow is started"
        String wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest)

        and: "Get the pending taskId "
        String taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1").getTaskId()

        and: "Then update the task to be completed"
        TaskResult taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, ["taskReferenceName": "task1"])
        taskClient.updateTask(taskResult)

        when: "Get the workflow that was updated with the task1 and get the output of the exclusiveJoin"
        Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true)
        String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin")
                .getOutputData()
                .get("taskReferenceName")
                .toString()

        then: "Ensure that the output data of exclusiveJoin taskReference is the task that was completed"
        taskReferenceName == "task1"
        Workflow.WorkflowStatus.COMPLETED == workflow.getStatus()
    }

    def "Test when the one decision is true and the other is decision null"() {
        given:
        def inputParameters = ["decision_1": "true", "decision_2": "null"]

        and: "A new workflow is started based on the input parameters"
        def startWorkflowRequest = new StartWorkflowRequest()
                .withName(CONDUCTOR_WORKFLOW_DEF_NAME)
                .withInput(inputParameters)
                .withVersion(1)

        and: "The workflow is started"
        def wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest)

        and: "Get the pending taskId task1"
        def taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1")
                .getTaskId()

        and: "Then update the task to be completed"
        def taskOutput = ["taskReferenceName": "task1"]
        def taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        and: "Get the pending taskId task2"
        taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task2")
                .getTaskId()

        and: "Then update the task to be completed"
        taskOutput.put("taskReferenceName", "task2")
        taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        when: "Get the workflow that was updated with the task2 and get the output of the exclusiveJoin"
        Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true)
        String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin")
                .getOutputData()
                .get("taskReferenceName")
                .toString()

        then: "Ensure that the output data of exclusiveJoin taskReference is the task that was completed"
        taskReferenceName == "task2"
        Workflow.WorkflowStatus.COMPLETED == workflow.getStatus()
    }

    def "Test when both the decisions, decision_1 and decision_2 are true"() {
        given: "The input parameters for the workflow to ensure that both the decisions are true"
        def inputParameters = ["decision_1": "true", "decision_2": "true"]

        and: "A new workflow is started based on the input parameters"
        def startWorkflowRequest = new StartWorkflowRequest()
                .withName(CONDUCTOR_WORKFLOW_DEF_NAME)
                .withInput(inputParameters)
                .withVersion(1)

        and: "The workflow is started"
        def wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest)

        and: "Get the pending taskId task1"
        def taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1")
                .getTaskId()

        and: "Then update the task to be completed"
        def taskOutput = ["taskReferenceName": "task1"]
        def taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        and: "Get the pending taskId task2"
        taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task2")
                .getTaskId()

        and: "Then update the task2 to be completed"
        taskOutput.put("taskReferenceName", "task2")
        taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        and: "Get the pending taskId task3"
        taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task3")
                .getTaskId()

        and: "Then update the task2 to be completed"
        taskOutput.put("taskReferenceName", "task3")
        taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        when: "Get the workflow that was updated with the task3 and get the output of the exclusiveJoin"
        Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true)
        String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin")
                .getOutputData()
                .get("taskReferenceName")
                .toString()

        then: "Ensure that the output data of exclusiveJoin taskReference is the task that was completed"
        taskReferenceName == "task3"
        Workflow.WorkflowStatus.COMPLETED == workflow.getStatus()
    }

    def "Test when decision_1 is false and  decision_3 is default"() {
        given:
        def inputParameters = ["decision_1": "false", "decision_3": "null"]

        and: "A new workflow is started based on the input parameters"
        def startWorkflowRequest = new StartWorkflowRequest()
                .withName(CONDUCTOR_WORKFLOW_DEF_NAME)
                .withInput(inputParameters)
                .withVersion(1)

        and: "The workflow is started"
        def wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest)

        and: "Get the pending taskId task1"
        def taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1")
                .getTaskId()

        and: "Then update the task to be completed"
        def taskOutput = ["taskReferenceName": "task1"]
        def taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        and: "Get the pending taskId task4"
        taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task4")
                .getTaskId()

        and: "Then update the task4 to be completed"
        taskOutput.put("taskReferenceName", "task4")
        taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        when: "Get the workflow that was updated with the task4 and get the output of the exclusiveJoin"
        Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true)
        String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin")
                .getOutputData()
                .get("taskReferenceName")
                .toString()

        then: "Ensure that the output data of exclusiveJoin taskReference is the task that was completed"
        taskReferenceName == "task4"
        Workflow.WorkflowStatus.COMPLETED == workflow.getStatus()
    }

    def "Test when decision_1 is false and  decision_3 is true"() {
        given:
        def inputParameters = ["decision_1": "false", "decision_3": "true"]

        and: "A new workflow is started based on the input parameters"
        def startWorkflowRequest = new StartWorkflowRequest()
                .withName(CONDUCTOR_WORKFLOW_DEF_NAME)
                .withInput(inputParameters)
                .withVersion(1)

        and: "The workflow is started"
        def wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest)

        and: "Get the pending taskId task1"
        def taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1")
                .getTaskId()

        and: "Then update the task to be completed"
        def taskOutput = ["taskReferenceName": "task1"]
        def taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        and: "Get the pending taskId task4"
        taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task4")
                .getTaskId()

        and: "Then update the task4 to be completed"
        taskOutput.put("taskReferenceName", "task4")
        taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        and: "Get the pending taskId task5"
        taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task5")
                .getTaskId()

        and: "Then update the task4 to be completed"
        taskOutput.put("taskReferenceName", "task5")
        taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput)
        taskClient.updateTask(taskResult)

        when: "Get the workflow that was updated with the task5 and get the output of the exclusiveJoin"
        Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true)
        String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin")
                .getOutputData()
                .get("taskReferenceName")
                .toString()

        then: "Ensure that the output data of exclusiveJoin taskReference is the task that was completed"
        taskReferenceName == "task5"
        Workflow.WorkflowStatus.COMPLETED == workflow.getStatus()

    }


}
