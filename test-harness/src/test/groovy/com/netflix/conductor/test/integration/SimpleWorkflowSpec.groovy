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

import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.exception.ConflictException
import com.netflix.conductor.core.exception.NotFoundException
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class SimpleWorkflowSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Shared
    def LINEAR_WORKFLOW_T1_T2 = 'integration_test_wf'

    @Shared
    def INTEGRATION_TEST_WF_NON_RESTARTABLE = "integration_test_wf_non_restartable"


    def setup() {
        //Register LINEAR_WORKFLOW_T1_T2,  RTOWF, WORKFLOW_WITH_OPTIONAL_TASK
        workflowTestUtil.registerWorkflows('simple_workflow_1_integration_test.json',
                'simple_workflow_with_resp_time_out_integration_test.json')
    }

    def "Test simple workflow completion"() {

        given: "An existing simple workflow definition"
        metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        and: "input required to start the workflow execution"
        String correlationId = 'unit_test_1'
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'

        when: "Start a workflow based on the registered simple workflow"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null)

        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete the 'integration_task_1' "
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "verify that the 'integration_task1' is complete and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "poll and complete 'integration_task_2'"
        def pollAndCompleteTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the 'integration_task_2' has been polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try1, ['tp1': inputParam1, 'tp2': 'task1.done'])

        and: "verify that the workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
            output.containsKey('o3')
        }
    }

    def "Test simple workflow with null inputs"() {

        when: "An existing simple workflow definition"
        def workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        then:
        workflowDef.getTasks().get(0).getInputParameters().containsKey('someNullKey')

        when: "Start a workflow based on the registered simple workflow with one input param null"
        String correlationId = "unit_test_1"
        def input = new HashMap()
        input.put("param1", "p1 value")
        input.put("param2", null)

        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null)

        then: "verify the workflow has started and the input params have propagated"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            input['param2'] == null
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
            !tasks[0].inputData['someNullKey']
        }

        when: "'integration_task_1' is polled and completed with output data"
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker',
                ['someOtherKey': ['a': 1, 'A': null], 'someKey': null])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "verify that the task is completed and the output data has propagated as input data to 'integration_task_2'"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].outputData.containsKey('someKey')
            !tasks[0].outputData['someKey']
            def someOtherKey = tasks[0].outputData['someOtherKey'] as Map
            someOtherKey.containsKey('A')
            !someOtherKey['A']
        }
    }

    def "Test simple workflow terminal error condition"() {
        setup: "Modify the task definition and the workflow output definition"
        def persistedTask1Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1').get()
        def modifiedTask1Definition = new TaskDef(persistedTask1Definition.name, persistedTask1Definition.description,
                persistedTask1Definition.ownerEmail, 1, persistedTask1Definition.timeoutSeconds,
                persistedTask1Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedTask1Definition)
        def workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        def outputParameters = workflowDef.outputParameters
        outputParameters['validationErrors'] = '${t1.output.ErrorMessage}'
        metadataService.updateWorkflowDef(workflowDef)

        when: "A simple workflow which is started"
        String correlationId = "unit_test_1"
        def input = new HashMap()
        input.put("param1", "p1 value")
        input.put("param2", "p2 value")

        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null)

        then: "verify that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
        }

        when: "Rewind the running workflow that was just started"
        workflowExecutor.restart(workflowInstanceId, false)

        then: "Ensure that a exception is thrown when a running workflow is being rewind"
        def exceptionThrown = thrown(ConflictException.class)
        exceptionThrown != null

        when: "'integration_task_1' is polled and failed with terminal error"
        def polledIntegrationTask1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        TaskResult taskResult = new TaskResult(polledIntegrationTask1)
        taskResult.reasonForIncompletion = 'NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down'
        taskResult.status = TaskResult.Status.FAILED_WITH_TERMINAL_ERROR
        taskResult.addOutputData('TERMINAL_ERROR', 'Integration endpoint down: FOOBAR')
        taskResult.addOutputData('ErrorMessage', 'There was a terminal error')

        workflowExecutionService.updateTask(taskResult)
        sweep(workflowInstanceId)

        then: "The first polled task is integration_task_1 and the workflowInstanceId of the task is same as running workflowInstanceId"
        polledIntegrationTask1
        polledIntegrationTask1.taskType == 'integration_task_1'
        polledIntegrationTask1.workflowInstanceId == workflowInstanceId

        and: "verify that the workflow is in a failed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            def t1 = getTaskByRefName('t1')
            reasonForIncompletion == "Task ${t1.taskId} failed with status: FAILED and reason: " +
                    "'NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down'"
            output['o1'] == 'p1 value'
            output['validationErrors'] == 'There was a terminal error'
            t1.retryCount == 0
            failedReferenceTaskNames == ['t1'] as HashSet
            failedTaskNames == ['integration_task_1'] as HashSet
        }

        cleanup:
        metadataService.updateTaskDef(modifiedTask1Definition)
        outputParameters.remove('validationErrors')
        metadataService.updateWorkflowDef(workflowDef)
    }


    def "Test Simple Workflow with response timeout "() {
        given: 'Workflow input and correlationId'
        def correlationId = 'unit_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when: "Start a workflow that has a response time out"
        def workflowInstanceId = startWorkflow('RTOWF', 1, correlationId, workflowInput,
                null)


        then: "Workflow is in running state and the task 'task_rt' is ready to be polled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'task_rt'
            tasks[0].status == Task.Status.SCHEDULED
        }
        queueDAO.getSize('task_rt') == 1

        when: "Poll for a 'task_rt' task and then ack the task"
        def polledTaskRtTry1 = workflowExecutionService.poll('task_rt', 'task1.integration.worker.testTimeout')

        then: "Verify that the 'task_rt' was polled"
        polledTaskRtTry1
        polledTaskRtTry1.taskType == 'task_rt'
        polledTaskRtTry1.workflowInstanceId == workflowInstanceId
        polledTaskRtTry1.status == Task.Status.IN_PROGRESS

        when: "An additional poll is done wto retrieved another 'task_rt'"
        def noTaskAvailable = workflowExecutionService.poll('task_rt', 'task1.integration.worker.testTimeout')

        then: "Ensure that there is no additional 'task_rt' available to poll"
        !noTaskAvailable

        when: "The processing of the polled task takes more time than the response time out"
        Thread.sleep(10000)
        workflowExecutor.decide(workflowInstanceId)

        then: "Expect a new task to be added to the queue in place of the timed out task"
        queueDAO.getSize('task_rt') == 1
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].status == Task.Status.TIMED_OUT
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "The task_rt is polled again and the task is set to be called back after 2 seconds"
        def polledTaskRtTry2 = workflowExecutionService.poll('task_rt', 'task1.integration.worker.testTimeout')
        polledTaskRtTry2.callbackAfterSeconds = 2
        polledTaskRtTry2.status = Task.Status.IN_PROGRESS
        workflowExecutionService.updateTask(new TaskResult(polledTaskRtTry2))

        then: "verify that the polled task is not null"
        polledTaskRtTry2

        and: "verify the state of the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "induce the time for the call back for the task to expire and run the un ack process"
        Thread.sleep(2010)
        queueDAO.processUnacks(polledTaskRtTry2.taskDefName)

        and: "run the decide process on the workflow"
        workflowExecutor.decide(workflowInstanceId)

        and: "poll for the task and then complete the task 'task_rt' "
        def pollAndCompleteTaskTry3 = workflowTestUtil.pollAndCompleteTask('task_rt', 'task1.integration.worker.testTimeout', ['op': 'task1.done'])

        then: 'Verify that the task was polled '
        verifyPolledAndAcknowledgedTask(pollAndCompleteTaskTry3)

        when: "The next task of the workflow is polled and then completed"
        def polledIntegrationTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker.testTimeout')

        then: "Verify that 'integration_task_2' is polled and acked"
        verifyPolledAndAcknowledgedTask(polledIntegrationTask2Try1)

        and: "verify that the workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
        }
    }

    def "Test if the workflow definitions with and without schema version can be registered"() {
        given: "A workflow definition with no schema version"
        def workflowDef1 = new WorkflowDef()
        workflowDef1.name = 'Test_schema_version1'
        workflowDef1.version = 1
        workflowDef1.ownerEmail = "test@harness.com"

        and: "A new workflow task is created"
        def workflowTask = new WorkflowTask()
        workflowTask.name = 'integration_task_1'
        workflowTask.taskReferenceName = 't1'
        workflowDef1.tasks.add(workflowTask)

        and: "The workflow definition with no schema version is saved"
        metadataService.updateWorkflowDef(workflowDef1)

        and: "A workflow definition with a schema version is created"
        def workflowDef2 = new WorkflowDef()
        workflowDef2.name = 'Test_schema_version2'
        workflowDef2.version = 1
        workflowDef2.schemaVersion = 2
        workflowDef2.ownerEmail = "test@harness.com"
        workflowDef2.tasks.add(workflowTask)

        and: "The workflow definition with schema version is persisted"
        metadataService.updateWorkflowDef(workflowDef2)

        when: "The persisted workflow definitions are retrieved by their name"
        def foundWorkflowDef1 = metadataService.getWorkflowDef(workflowDef1.getName(), 1)
        def foundWorkflowDef2 = metadataService.getWorkflowDef(workflowDef2.getName(), 1)

        then: "Ensure that the schema version is by default 2"
        foundWorkflowDef1
        foundWorkflowDef1.schemaVersion == 2
        foundWorkflowDef2
        foundWorkflowDef2.schemaVersion == 2
    }

    def "Test Simple workflow restart without using the latest definition"() {
        setup: "Register a task definition with no retries"
        def persistedTask1Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1').get()
        def modifiedTaskDefinition = new TaskDef(persistedTask1Definition.name, persistedTask1Definition.description,
                persistedTask1Definition.ownerEmail, 0, persistedTask1Definition.timeoutSeconds,
                persistedTask1Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedTaskDefinition)

        when: "Get the workflow definition associated with the simple workflow"
        WorkflowDef workflowDefinition = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        then: "Ensure that there is a workflow definition"
        workflowDefinition
        workflowDefinition.failureWorkflow
        StringUtils.isNotBlank(workflowDefinition.failureWorkflow)

        when: "Start a simple workflow with non null params"
        def correlationId = 'integration_test_1' + UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        String inputParam1 = 'p1 value'
        workflowInput['param1'] = inputParam1
        workflowInput['param2'] = 'p2 value'

        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null)

        then: "A workflow instance has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }

        when: "poll the task that is queued and fail the task"
        def polledIntegrationTask1Try1 = workflowTestUtil.pollAndFailTask('integration_task_1', 'task1.integration.worker', 'failed..')

        then: "The workflow ends up in a failed state"
        verifyPolledAndAcknowledgedTask(polledIntegrationTask1Try1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'integration_task_1'
        }

        when: "Rewind the workflow which is in the failed state without the latest definition"
        workflowExecutor.restart(workflowInstanceId, false)

        then: "verify that the rewound workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }

        when: "Poll for the 'integration_task_1' "
        def polledIntegrationTask1Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "verify that the task is polled and the workflow is in a running state"
        verifyPolledAndAcknowledgedTask(polledIntegrationTask1Try2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
        }

        when:
        def polledIntegrationTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker')

        then:
        verifyPolledAndAcknowledgedTask(polledIntegrationTask2Try1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
        }

        cleanup:
        metadataService.updateTaskDef(persistedTask1Definition)
    }

    def "Test Simple workflow restart with the latest definition"() {

        setup: "Register a task definition with no retries"
        def persistedTask1Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1').get()
        def modifiedTaskDefinition = new TaskDef(persistedTask1Definition.name, persistedTask1Definition.description,
                persistedTask1Definition.ownerEmail, 0, persistedTask1Definition.timeoutSeconds,
                persistedTask1Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedTaskDefinition)

        when: "Get the workflow definition associated with the simple workflow"
        WorkflowDef workflowDefinition = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        then: "Ensure that there is a workflow definition"
        workflowDefinition
        workflowDefinition.failureWorkflow
        StringUtils.isNotBlank(workflowDefinition.failureWorkflow)

        when: "Start a simple workflow with non null params"
        def correlationId = 'integration_test_1' + UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        String inputParam1 = 'p1 value'
        workflowInput['param1'] = inputParam1
        workflowInput['param2'] = 'p2 value'

        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null)

        then: "A workflow instance has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }

        when: "poll the task that is queued and fail the task"
        def polledIntegrationTask1Try1 = workflowTestUtil.pollAndFailTask('integration_task_1', 'task1.integration.worker', 'failed..')

        then: "the workflow ends up in a failed state"
        verifyPolledAndAcknowledgedTask(polledIntegrationTask1Try1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'integration_task_1'
        }

        when: "A new version of the workflow definition is registered"
        WorkflowTask workflowTask = new WorkflowTask()
        workflowTask.name = 'integration_task_20'
        workflowTask.taskReferenceName = 'task_added'
        workflowTask.workflowTaskType = TaskType.SIMPLE

        workflowDefinition.tasks.add(workflowTask)
        workflowDefinition.version = 2
        metadataService.updateWorkflowDef(workflowDefinition)

        and: "rewind/restart the workflow with the latest workflow definition"
        workflowExecutor.restart(workflowInstanceId, true)

        then: "verify that the rewound workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }

        when: "Poll and complete the 'integration_task_1' "
        def polledIntegrationTask1Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "verify that the task is polled and the workflow is in a running state"
        verifyPolledAndAcknowledgedTask(polledIntegrationTask1Try2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
        }

        when: "Poll and complete the 'integration_task_2' "
        def polledIntegrationTask2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker')

        then: "verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledIntegrationTask2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }

        when: "Poll and complete the 'integration_task_20' "
        def polledIntegrationTask20Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_20', 'task1.integration.worker')

        then: "verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledIntegrationTask20Try1)
        def polledIntegrationTask20 = polledIntegrationTask20Try1[0] as Task
        polledIntegrationTask20.workflowInstanceId == workflowInstanceId
        polledIntegrationTask20.referenceTaskName == 'task_added'
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
        }

        cleanup:
        metadataService.updateTaskDef(persistedTask1Definition)
        metadataService.unregisterWorkflowDef(workflowDefinition.getName(), 2)
    }

    def "Test simple workflow with task retries"() {
        setup: "Change the task definition to ensure that it has retries and delay between retries"
        def integrationTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTaskDefinition = new TaskDef(integrationTask2Definition.name, integrationTask2Definition.description,
                integrationTask2Definition.ownerEmail, 3, integrationTask2Definition.timeoutSeconds,
                integrationTask2Definition.responseTimeoutSeconds)
        modifiedTaskDefinition.retryDelaySeconds = 2
        metadataService.updateTaskDef(modifiedTaskDefinition)

        when: "A new simple workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null)

        then: "verify that the workflow has started"
        workflowInstanceId
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        workflow.status == Workflow.WorkflowStatus.RUNNING

        when: "Poll for the first task and complete the task"
        def polledIntegrationTask1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        polledIntegrationTask1.status = Task.Status.COMPLETED
        def polledIntegrationTask1Output = "task1.output -> " + polledIntegrationTask1.inputData['p1'] + "." + polledIntegrationTask1.inputData['p2']
        polledIntegrationTask1.outputData['op'] = polledIntegrationTask1Output
        workflowExecutionService.updateTask(new TaskResult(polledIntegrationTask1))

        then: "verify that the 'integration_task_1' is polled and completed"
        with(polledIntegrationTask1) {
            inputData.containsKey('p1')
            inputData.containsKey('p2')
            inputData['p1'] == 'p1 value'
            inputData['p2'] == 'p2 value'
        }

        //Need to figure out how to use expect and where here
        when: " 'integration_task_2'  is polled and marked as failed for the first time"
        Tuple polledAndFailedTaskTry1 = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.integration.worker', 'failure...0', null, 2)

        then: "verify that the task was polled and the input params of the tasks are as expected"
        verifyPolledAndAcknowledgedTask(polledAndFailedTaskTry1, ['tp2': polledIntegrationTask1Output, 'tp1': 'p1 value'])

        when: " 'integration_task_2'  is polled and marked as failed for the second time"
        Tuple polledAndFailedTaskTry2 = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.integration.worker', 'failure...0', null, 2)

        then: "verify that the task was polled and the input params of the tasks are as expected"
        verifyPolledAndAcknowledgedTask(polledAndFailedTaskTry2, ['tp2': polledIntegrationTask1Output, 'tp1': 'p1 value'])

        when: "'integration_task_2'  is polled and marked as completed for the third time"
        def polledAndCompletedTry3 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the task was polled and the input params of the tasks are as expected"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTry3, ['tp2': polledIntegrationTask1Output, 'tp1': 'p1 value'])
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.FAILED
            tasks[2].taskType == 'integration_task_2'
            tasks[2].status == Task.Status.FAILED
            tasks[3].taskType == 'integration_task_2'
            tasks[3].status == Task.Status.COMPLETED
            tasks[1].taskId == tasks[2].retriedTaskId
            tasks[2].taskId == tasks[3].retriedTaskId
            failedReferenceTaskNames == ['t2'] as HashSet
            failedTaskNames == ['integration_task_2'] as HashSet
        }

        cleanup:
        metadataService.updateTaskDef(integrationTask2Definition)
    }

    def "Test simple workflow with retry at workflow level"() {
        setup: "Change the task definition to ensure that it has retries and no delay between retries"
        def integrationTask1Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1').get()
        def modifiedTaskDefinition = new TaskDef(integrationTask1Definition.name, integrationTask1Definition.description,
                integrationTask1Definition.ownerEmail, 1, integrationTask1Definition.timeoutSeconds,
                integrationTask1Definition.responseTimeoutSeconds)
        modifiedTaskDefinition.retryDelaySeconds = 0
        metadataService.updateTaskDef(modifiedTaskDefinition)

        when: "Start a simple workflow with non null params"
        def correlationId = 'retry_test' + UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        String inputParam1 = 'p1 value'
        workflowInput['param1'] = inputParam1
        workflowInput['param2'] = 'p2 value'

        and: "start a simple workflow with input params"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null)

        then: "verify that the workflow has started and the next task is scheduled"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].getInputData().get("p3") == tasks[0].getTaskId()
        }
        with(metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)) {
            failureWorkflow
            StringUtils.isNotBlank(failureWorkflow)
        }

        when: "The first task 'integration_task_1' is polled and failed"
        Tuple polledAndFailedTask1Try1 = workflowTestUtil.pollAndFailTask('integration_task_1', 'task1.integration.worker', 'failure...0')

        then: "verify that the task was polled and acknowledged and the workflow is still in a running state"
        verifyPolledAndAcknowledgedTask(polledAndFailedTask1Try1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].status == Task.Status.FAILED
            tasks[1].status == Task.Status.SCHEDULED
            tasks[1].getInputData().get("p3") == tasks[1].getTaskId()
        }

        when: "The first task 'integration_task_1' is polled and failed for the second time"
        Tuple polledAndFailedTask1Try2 = workflowTestUtil.pollAndFailTask('integration_task_1', 'task1.integration.worker', 'failure...0')

        then: "verify that the task was polled and acknowledged and the workflow is still in a running state"
        verifyPolledAndAcknowledgedTask(polledAndFailedTask1Try2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].status == Task.Status.FAILED
            tasks[1].status == Task.Status.FAILED
        }

        when: "The workflow is retried"
        workflowExecutor.retry(workflowInstanceId, false)

        then:
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].status == Task.Status.FAILED
            tasks[1].status == Task.Status.FAILED
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].getInputData().get("p3") == tasks[2].getTaskId()
        }

        when: "The 'integration_task_1' task is polled and is completed"
        def polledAndCompletedTry3 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task2.integration.worker')

        then: "verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTry3)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].status == Task.Status.SCHEDULED
        }

        when: "The 'integration_task_2' task is polled and is completed"
        def polledAndCompletedTaskTry1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndCompletedTaskTry1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].status == Task.Status.COMPLETED
            failedReferenceTaskNames == ['t1'] as HashSet
            failedTaskNames == ['integration_task_1'] as HashSet
        }

        cleanup:
        metadataService.updateTaskDef(integrationTask1Definition)
    }

    def "Test Long running simple workflow"() {
        given: "A new simple workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when: "start a new workflow with the input"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null)

        then: "verify that the workflow is in running state and the task queue has an entry for the first task of the workflow"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        when: "the first task 'integration_task_1' is polled and then sent back with a callBack seconds"
        def pollTaskTry1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        pollTaskTry1.outputData['op'] = 'task1.in.progress'
        pollTaskTry1.callbackAfterSeconds = 5
        pollTaskTry1.status = Task.Status.IN_PROGRESS
        workflowExecutionService.updateTask(new TaskResult(pollTaskTry1))

        then: "verify that the task is polled and acknowledged"
        pollTaskTry1

        and: "the input data of the data is as expected"
        pollTaskTry1.inputData.containsKey('p1')
        pollTaskTry1.inputData['p1'] == 'p1 value'
        pollTaskTry1.inputData.containsKey('p2')
        pollTaskTry1.inputData['p1'] == 'p1 value'

        and: "the task queue reflects the presence of 'integration_task_1' "
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        when: "the 'integration_task_1' task is polled again"
        def pollTaskTry2 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')

        then: "verify that there was no task polled"
        !pollTaskTry2

        when: "the 'integration_task_1' is polled again after a delay of 5 seconds and completed"
        Thread.sleep(5000)
        def task1Try3Tuple = workflowTestUtil.pollAndCompleteTask('integration_task_1',
                'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask(task1Try3Tuple, [:])

        and: "verify that the workflow is updated with the latest task"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
            tasks[0].outputData['op'] == 'task1.done'
        }

        when: "the 'integration_task_1' is polled and completed"
        def task2Try1Tuple = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the task was polled and completed with the expected inputData for the task that was polled"
        verifyPolledAndAcknowledgedTask(task2Try1Tuple, ['tp2': 'task1.done', 'tp1': 'p1 value'])

        and: "The workflow is in a completed state and reflects the tasks that are completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
        }
    }


    def "Test simple workflow when the task's call back after seconds are reset"() {

        given: "A new simple workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when: "start a new workflow with the input"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null)

        then: "verify that the workflow is in running state and the task queue has an entry for the first task of the workflow"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
        }
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        when: "the first task 'integration_task_1' is polled and then sent back with a callBack seconds"
        def pollTaskTry1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        pollTaskTry1.outputData['op'] = 'task1.in.progress'
        pollTaskTry1.callbackAfterSeconds = 3600
        pollTaskTry1.status = Task.Status.IN_PROGRESS
        workflowExecutionService.updateTask(new TaskResult(pollTaskTry1))

        then: "verify that the task is polled and acknowledged"
        pollTaskTry1

        and: "the input data of the data is as expected"
        pollTaskTry1.inputData.containsKey('p1')
        pollTaskTry1.inputData['p1'] == 'p1 value'
        pollTaskTry1.inputData.containsKey('p2')
        pollTaskTry1.inputData['p1'] == 'p1 value'

        and: "the task queue reflects the presence of 'integration_task_1' "
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        when: "the 'integration_task_1' task is polled again"
        def pollTaskTry2 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')

        then: "verify that there was no task polled"
        !pollTaskTry2

        when: "the 'integration_task_1' task is polled again"
        def pollTaskTry3 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')

        then: "verify that there was no task polled"
        !pollTaskTry3

        when: "The callbackSeconds of the tasks in progress for the workflow are reset"
        workflowExecutor.resetCallbacksForWorkflow(workflowInstanceId)

        and: "the 'integration_task_1' is polled again after all the in progress tasks are reset"
        def task1Try4Tuple = workflowTestUtil.pollAndCompleteTask('integration_task_1',
                'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask(task1Try4Tuple)

        and: "verify that the workflow is updated with the latest task"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
            tasks[0].outputData['op'] == 'task1.done'
        }

        when: "the 'integration_task_1' is polled and completed"
        def task2Try1Tuple = workflowTestUtil.pollAndCompleteTask('integration_task_2',
                'task2.integration.worker')

        then: "verify that the task was polled and completed with the expected inputData for the task that was polled"
        verifyPolledAndAcknowledgedTask(task2Try1Tuple, ['tp2': 'task1.done', 'tp1': 'p1 value'])

        and: "The workflow is in a completed state and reflects the tasks that are completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
        }
    }

    def "Test non restartable simple workflow"() {
        setup: "Change the task definition to ensure that it has no retries and register a non restartable workflow"
        def integrationTask1Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1').get()
        def modifiedTaskDefinition = new TaskDef(integrationTask1Definition.name, integrationTask1Definition.description,
                integrationTask1Definition.ownerEmail, 0, integrationTask1Definition.timeoutSeconds,
                integrationTask1Definition.responseTimeoutSeconds)
        metadataService.updateTaskDef(modifiedTaskDefinition)

        def simpleWorkflowDefinition = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)
        simpleWorkflowDefinition.name = INTEGRATION_TEST_WF_NON_RESTARTABLE
        simpleWorkflowDefinition.restartable = false
        metadataService.updateWorkflowDef(simpleWorkflowDefinition)

        when: "A non restartable workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        def workflowInstanceId = startWorkflow(INTEGRATION_TEST_WF_NON_RESTARTABLE, 1,
                correlationId, workflowInput,
                null)

        and: "the 'integration_task_1' is polled and failed"
        Tuple polledAndFailedTaskTry1 = workflowTestUtil.pollAndFailTask('integration_task_1',
                'task1.integration.worker', 'failure...0')

        then: "verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndFailedTaskTry1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'integration_task_1'
        }

        when: "The failed workflow is rewound"
        workflowExecutor.restart(workflowInstanceId, false)

        and: "The first task 'integration_task_1' is polled and completed"
        def task1Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker',
                ['op': 'task1.done'])

        then: "Verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask(task1Try2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
        }

        when: "The second task 'integration_task_2' is polled and completed"
        def task2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "Verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(task2Try1, ['tp2': 'task1.done', 'tp1': 'p1 value'])

        and: "The workflow is in a completed state and reflects the tasks that are completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
            output['o3'] == 'task1.done'
        }

        when: "The successfully completed non restartable workflow is rewound"
        workflowExecutor.restart(workflowInstanceId, false)

        then: "Ensure that an exception is thrown"
        thrown(NotFoundException.class)

        cleanup: "clean up the changes made to the task and workflow definition during start up"
        metadataService.updateTaskDef(integrationTask1Definition)
        simpleWorkflowDefinition.name = LINEAR_WORKFLOW_T1_T2
        simpleWorkflowDefinition.restartable = true
        metadataService.updateWorkflowDef(simpleWorkflowDefinition)
    }

    def "Test simple workflow when update task's result with call back after seconds"() {

        given: "A new simple workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when: "start a new workflow with the input"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null)

        then: "verify that the workflow is in running state and the task queue has an entry for the first task of the workflow"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
        }
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        when: "the first task 'integration_task_1' is polled and then sent back with no callBack seconds"
        def pollTaskTry1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        pollTaskTry1.outputData['op'] = 'task1.in.progress'
        pollTaskTry1.status = Task.Status.IN_PROGRESS
        workflowExecutionService.updateTask(new TaskResult(pollTaskTry1))

        then: "verify that the task is polled and acknowledged"
        pollTaskTry1

        and: "the input data of the data is as expected"
        pollTaskTry1.inputData.containsKey('p1')
        pollTaskTry1.inputData['p1'] == 'p1 value'
        pollTaskTry1.inputData.containsKey('p2')
        pollTaskTry1.inputData['p1'] == 'p1 value'

        and: "the task gets put back into the queue of 'integration_task_1' immediately for future poll"
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        and: "The task in in SCHEDULED status with workerId reset"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].callbackAfterSeconds == 0
        }

        when: "the 'integration_task_1' task is polled again"
        def pollTaskTry2 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        pollTaskTry2.outputData['op'] = 'task1.in.progress'
        pollTaskTry2.status = Task.Status.IN_PROGRESS
        pollTaskTry2.callbackAfterSeconds = 3600
        workflowExecutionService.updateTask(new TaskResult(pollTaskTry2))

        then: "verify that the task is polled and acknowledged"
        pollTaskTry2

        and: "the task gets put back into the queue of 'integration_task_1' with callbackAfterSeconds delay for future poll"
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        and: "The task in in SCHEDULED status with workerId reset"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].callbackAfterSeconds == pollTaskTry2.callbackAfterSeconds
        }

        when: "the 'integration_task_1' task is polled again"
        def pollTaskTry3 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')

        then: "verify that there was no task polled"
        !pollTaskTry3
    }
}
