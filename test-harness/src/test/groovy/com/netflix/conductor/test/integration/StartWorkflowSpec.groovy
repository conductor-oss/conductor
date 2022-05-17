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
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.StartWorkflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification
import com.netflix.conductor.test.utils.MockExternalPayloadStorage

import spock.lang.Shared
import spock.lang.Unroll

class StartWorkflowSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Autowired
    StartWorkflow startWorkflowTask

    @Autowired
    MockExternalPayloadStorage mockExternalPayloadStorage

    @Shared
    def WORKFLOW_THAT_STARTS_ANOTHER_WORKFLOW = 'workflow_that_starts_another_workflow'

    static String workflowInputPath = "${UUID.randomUUID()}.json"

    def setup() {
        workflowTestUtil.registerWorkflows('workflow_that_starts_another_workflow.json',
                'simple_workflow_1_integration_test.json')
        mockExternalPayloadStorage.upload(workflowInputPath, StartWorkflowSpec.class.getResourceAsStream("/start_workflow_input.json"), 0)
    }

    @Unroll
    def "start another workflow using #testCase.name"() {
        setup: 'create the correlationId for the starter workflow'
        def correlationId = UUID.randomUUID().toString()

        when: "starter workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_THAT_STARTS_ANOTHER_WORKFLOW, 1,
                correlationId, testCase.workflowInput, testCase.workflowInputPath, null, null)

        then: "verify that the starter workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'START_WORKFLOW'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the START_WORKFLOW task is started"
        List<String> polledTaskIds = queueDAO.pop("START_WORKFLOW", 1, 200)
        String startWorkflowTaskId = polledTaskIds.get(0)
        asyncSystemTaskExecutor.execute(startWorkflowTask, startWorkflowTaskId)

        then: "verify the START_WORKFLOW task and workflow are COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 1
            tasks[0].taskType == 'START_WORKFLOW'
            tasks[0].status == Task.Status.COMPLETED
        }

        when: "the started workflow is retrieved"
        def startWorkflowTask = workflowExecutionService.getTask(startWorkflowTaskId)
        String startedWorkflowId = startWorkflowTask.outputData['workflowId']

        then: "verify that the started workflow is RUNNING"
        with(workflowExecutionService.getExecutionStatus(startedWorkflowId, false)) {
            status == Workflow.WorkflowStatus.RUNNING
            it.correlationId == correlationId
            // when the "starter" workflow is started with input from external payload storage,
            // it sends a large input to the "started" workflow
            // see start_workflow_input.json
            if(testCase.workflowInputPath) {
                externalInputPayloadStoragePath != null
            } else {
                input != null
            }
        }

        where:
        testCase << [workflowName(), workflowDef(), workflowRequestWithExternalPayloadStorage()]
    }

    def "start_workflow does not conform to StartWorkflowRequest"() {
        given: "start_workflow that does not conform to StartWorkflowRequest"
        def startWorkflowParam = ['param1': 'value1', 'param2': 'value2']
        def workflowInput = ['start_workflow': startWorkflowParam]

        when: "starter workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_THAT_STARTS_ANOTHER_WORKFLOW, 1,
                null, workflowInput, null, null, null)

        then: "verify that the starter workflow is in RUNNING state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'START_WORKFLOW'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the START_WORKFLOW task is started"
        List<String> polledTaskIds = queueDAO.pop("START_WORKFLOW", 1, 200)
        String startWorkflowTaskId = polledTaskIds.get(0)
        asyncSystemTaskExecutor.execute(startWorkflowTask, startWorkflowTaskId)

        then: "verify the START_WORKFLOW task and workflow FAILED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 1
            tasks[0].taskType == 'START_WORKFLOW'
            tasks[0].status == Task.Status.FAILED
            tasks[0].reasonForIncompletion != null
        }
    }

    /**
     * Builds a TestCase for a StartWorkflowRequest with a WorkflowDef that contains two tasks.
     */
    static workflowDef() {
        def task1 = ['name': 'integration_task_1', 'taskReferenceName': 't1', 'type': 'SIMPLE',
                     'inputParameters': ['tp1': '${workflow.input.param1}', 'tp2': '${workflow.input.param2}', 'tp3': '${CPEWF_TASK_ID}']]
        def task2 = ['name': 'integration_task_2', 'taskReferenceName': 't2', 'type': 'SIMPLE',
                     'inputParameters': ['tp1': '${workflow.input.param1}', 'tp2': '${t1.output.op}', 'tp3': '${CPEWF_TASK_ID}']]
        def workflowDef = ['name': 'dynamic_wf', 'version': 1, 'tasks': [task1, task2], 'ownerEmail': 'abc@abc.com']

        def startWorkflow = ['name': 'dynamic_wf', 'workflowDef': workflowDef]

        new TestCase(name: 'workflow definition', workflowInput: ['startWorkflow': startWorkflow])
    }

    /**
     * Builds a TestCase for a StartWorkflowRequest with a workflow name.
     */
    static workflowName() {
        def startWorkflow = ['name': 'integration_test_wf', 'input': ['param1': 'value1', 'param2': 'value2']]

        new TestCase(name: 'name and version', workflowInput: ['startWorkflow': startWorkflow])
    }

    /**
     * Builds a TestCase for a StartWorkflowRequest with a workflow name and input in external payload storage.
     */
    static workflowRequestWithExternalPayloadStorage() {
        new TestCase(name: 'name and version with external input', workflowInputPath: workflowInputPath)
    }

    static class TestCase {
        String name
        Map workflowInput
        String workflowInputPath
    }
}
