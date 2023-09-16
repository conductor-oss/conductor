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

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.StartWorkflowInput
import com.netflix.conductor.core.utils.Utils
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import spock.lang.Shared

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class WorkflowAndTaskConfigurationSpec extends AbstractSpecification {

    @Autowired
    QueueDAO queueDAO

    @Shared
    def LINEAR_WORKFLOW_T1_T2 = 'integration_test_wf'

    @Shared
    def TEMPLATED_LINEAR_WORKFLOW = 'integration_test_template_wf'

    @Shared
    def WORKFLOW_WITH_OPTIONAL_TASK = 'optional_task_wf'

    @Shared
    def TEST_WORKFLOW = 'integration_test_wf3'

    @Shared
    def WAIT_TIME_OUT_WORKFLOW = 'test_wait_timeout'

    def setup() {
        //Register LINEAR_WORKFLOW_T1_T2, TEST_WORKFLOW, RTOWF, WORKFLOW_WITH_OPTIONAL_TASK
        workflowTestUtil.registerWorkflows(
                'simple_workflow_1_integration_test.json',
                'simple_workflow_1_input_template_integration_test.json',
                'simple_workflow_3_integration_test.json',
                'simple_workflow_with_optional_task_integration_test.json',
                'simple_wait_task_workflow_integration_test.json')
    }

    def "Test simple workflow which has an optional task"() {

        given: "A input parameters for a workflow with an optional task"
        def correlationId = 'integration_test' + UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when: "An optional task workflow is started"
        def workflowInstanceId = startWorkflow(WORKFLOW_WITH_OPTIONAL_TASK, 1,
                correlationId, workflowInput,
                null)

        then: "verify that the workflow has started and the optional task is in a scheduled state"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].taskType == 'task_optional'
        }

        when: "The first optional task is polled and failed"
        Tuple polledAndFailedTaskTry1 = workflowTestUtil.pollAndFailTask('task_optional',
                'task1.integration.worker', 'NETWORK ERROR')

        then: "Verify that the task_optional was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(polledAndFailedTaskTry1)

        when: "A decide is executed on the workflow"
        workflowExecutor.decide(workflowInstanceId)

        then: "verify that the workflow is still running and the first optional task has failed and the retry has kicked in"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'task_optional'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[1].taskType == 'task_optional'
        }

        when: "Poll the optional task again and do not complete it and run decide"
        workflowExecutionService.poll('task_optional', 'task1.integration.worker')
        Thread.sleep(5000)
        workflowExecutor.decide(workflowInstanceId)

        then: "Ensure that the workflow is updated"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].status == Task.Status.COMPLETED_WITH_ERRORS
            tasks[1].taskType == 'task_optional'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
        }

        when: "The second task 'integration_task_2' is polled and completed"
        def task2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "Verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(task2Try1)

        and: "Ensure that the workflow is in completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
        }
    }

    def "test workflow with input template parsing"() {
        given: "Input parameters for a workflow with input template"
        def correlationId = 'integration_test' + UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        // leave other params blank on purpose to test input templates
        workflowInput['param3'] = 'external string'

        when: "Is executed and completes"
        def workflowInstanceId = startWorkflow(TEMPLATED_LINEAR_WORKFLOW, 1,
                correlationId, workflowInput,
                null)
        workflowExecutor.decide(workflowInstanceId)
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "Verify that input template is processed"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            output == [
                    output: "task1.done",
                    param3: 'external string',
                    param2: ['list', 'of', 'strings'],
                    param1: [nested_object: [nested_key: "nested_value"]]
            ]
        }
    }

    def "Test simple workflow with task time out configuration"() {

        setup: "Register a task definition with retry policy on time out"
        def persistedTask1Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1').get()
        def modifiedTaskDefinition = new TaskDef(persistedTask1Definition.name, persistedTask1Definition.description,
                persistedTask1Definition.ownerEmail, 1, 1, 1)
        modifiedTaskDefinition.retryDelaySeconds = 0
        modifiedTaskDefinition.timeoutPolicy = TaskDef.TimeoutPolicy.RETRY
        metadataService.updateTaskDef(modifiedTaskDefinition)

        when: "A simple workflow is started that has a task with time out and retry configured"
        String correlationId = 'unit_test_1' + UUID.randomUUID()
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'
        input['failureWfName'] = 'FanInOutTest'

        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null)

        then: "Ensure that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        and: "The decider queue has one task that is ready to be polled"
        queueDAO.getSize(Utils.DECIDER_QUEUE) == 1

        when: "The the first task 'integration_task_1' is polled and acknowledged"
        def task1Try1 = workflowExecutionService.poll('integration_task_1', 'task1.worker')

        then: "Ensure that a task was polled"
        task1Try1
        task1Try1.workflowInstanceId == workflowInstanceId

        and: "Ensure that the decider size queue is 1 to to enable the evaluation"
        queueDAO.getSize(Utils.DECIDER_QUEUE) == 1

        when: "There is a delay of 3 seconds introduced and the workflow is sweeped to run the evaluation"
        Thread.sleep(3000)
        sweep(workflowInstanceId)

        then: "Ensure that the first task has been TIMED OUT and the next task is SCHEDULED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.TIMED_OUT
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "Poll for the task again and acknowledge"
        def task1Try2 = workflowExecutionService.poll('integration_task_1', 'task1.worker')

        then: "Ensure that a task was polled"
        task1Try2
        task1Try2.workflowInstanceId == workflowInstanceId

        when: "There is a delay of 3 seconds introduced and the workflow is swept to run the evaluation"
        Thread.sleep(3000)
        sweep(workflowInstanceId)

        then: "Ensure that the first task has been TIMED OUT and the next task is SCHEDULED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TIMED_OUT
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.TIMED_OUT
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.TIMED_OUT
        }

        cleanup: "Ensure that the changes of the 'integration_task_1' are reverted"
        metadataService.updateTaskDef(persistedTask1Definition)
    }

    def "Test workflow timeout configurations"() {
        setup: "Get the workflow definition and change the workflow configuration"
        def testWorkflowDefinition = metadataService.getWorkflowDef(TEST_WORKFLOW, 1)
        testWorkflowDefinition.timeoutPolicy = WorkflowDef.TimeoutPolicy.TIME_OUT_WF
        testWorkflowDefinition.timeoutSeconds = 5
        metadataService.updateWorkflowDef(testWorkflowDefinition)

        when: "A simple workflow is started that has a workflow timeout configured"
        String correlationId = 'unit_test_3' + UUID.randomUUID()
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'
        input['failureWfName'] = 'FanInOutTest'

        def workflowInstanceId = startWorkflow(TEST_WORKFLOW, 1,
                correlationId, input,
                null)

        then: "Ensure that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The the first task 'integration_task_1' is polled and acknowledged"
        def task1Try1 = workflowExecutionService.poll('integration_task_1', 'task1.worker')

        then: "Ensure that a task was polled"
        task1Try1
        task1Try1.workflowInstanceId == workflowInstanceId

        when: "There is a delay of 6 seconds introduced and the workflow is swept to run the evaluation"
        Thread.sleep(6000)
        sweep(workflowInstanceId)

        then: "Ensure that the workflow has timed out"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TIMED_OUT
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.CANCELED
        }

        cleanup: "Ensure that the workflow configuration changes are reverted"
        testWorkflowDefinition.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        testWorkflowDefinition.timeoutSeconds = 0
        metadataService.updateWorkflowDef(testWorkflowDefinition)
    }

    def "Test retrying a timed out workflow due to workflow timeout"() {
        setup: "Get the workflow definition and change the workflow configuration"
        def testWorkflowDefinition = metadataService.getWorkflowDef(TEST_WORKFLOW, 1)
        testWorkflowDefinition.timeoutPolicy = WorkflowDef.TimeoutPolicy.TIME_OUT_WF
        testWorkflowDefinition.timeoutSeconds = 5
        metadataService.updateWorkflowDef(testWorkflowDefinition)

        when: "A simple workflow is started that has a workflow timeout configured"
        String correlationId = 'retry_timeout_wf'
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'

        def workflowInstanceId = startWorkflow(TEST_WORKFLOW, 1,
                correlationId, input, null)

        then: "Ensure that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The the first task 'integration_task_1' is polled and acknowledged"
        def task1Try1 = workflowExecutionService.poll('integration_task_1', 'task1.worker')

        then: "Ensure that a task was polled"
        task1Try1
        task1Try1.workflowInstanceId == workflowInstanceId

        when: "There is a delay of 6 seconds introduced and the workflow is swept to run the evaluation"
        Thread.sleep(6000)
        sweep(workflowInstanceId)

        then: "Ensure that the workflow has timed out"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TIMED_OUT
            lastRetriedTime == 0
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.CANCELED
        }

        when: "Retrying the workflow"
        workflowExecutor.retry(workflowInstanceId, false)

        then: "Ensure that the workflow is RUNNING and task is retried"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            lastRetriedTime != 0
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.CANCELED
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.SCHEDULED
        }

        cleanup: "Ensure that the workflow configuration changes are reverted"
        testWorkflowDefinition.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        testWorkflowDefinition.timeoutSeconds = 0
        metadataService.updateWorkflowDef(testWorkflowDefinition)
    }

    def "Test retrying a timed out workflow due to workflow timeout without unsuccessful tasks"() {
        setup: "Get the workflow definition and change the workflow configuration"
        def testWorkflowDefinition = metadataService.getWorkflowDef(TEST_WORKFLOW, 1)
        testWorkflowDefinition.timeoutPolicy = WorkflowDef.TimeoutPolicy.TIME_OUT_WF
        testWorkflowDefinition.timeoutSeconds = 5
        metadataService.updateWorkflowDef(testWorkflowDefinition)

        when: "A simple workflow is started that has a workflow timeout configured"
        String correlationId = 'retry_timeout_wf'
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'

        def workflowInstanceId = startWorkflow(TEST_WORKFLOW, 1,
                correlationId, input, null)

        then: "Ensure that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The the first task 'integration_task_1' is polled and acknowledged"
        def task1 = workflowExecutionService.poll('integration_task_1', 'task1.worker')

        then: "Ensure that a task was polled"
        task1
        task1.workflowInstanceId == workflowInstanceId

        when: "There is a delay of 6 seconds introduced and the task is completed"
        Thread.sleep(6000)
        task1.status = Task.Status.COMPLETED
        workflowExecutor.updateTask(new TaskResult(task1))

        then: "verify that the workflow is TIMED_OUT and the task is COMPLETED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TIMED_OUT
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
        }

        when: "Retrying the workflow"
        workflowExecutor.retry(workflowInstanceId, false)
        sweep(workflowInstanceId)

        then: "Ensure that the workflow is RUNNING and next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            lastRetriedTime != 0
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        cleanup: "Ensure that the workflow configuration changes are reverted"
        testWorkflowDefinition.timeoutPolicy = WorkflowDef.TimeoutPolicy.ALERT_ONLY
        testWorkflowDefinition.timeoutSeconds = 0
        metadataService.updateWorkflowDef(testWorkflowDefinition)
    }

    def "Test re-running the simple workflow multiple times after completion"() {

        given: "input required to start the workflow execution"
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

        when: "The completed workflow is re run after integration_task_1"
        def reRunWorkflowRequest1 = new RerunWorkflowRequest()
        reRunWorkflowRequest1.reRunFromWorkflowId = workflowInstanceId
        def reRunTaskId = workflowExecutionService.getExecutionStatus(workflowInstanceId, true).tasks[1].taskId
        reRunWorkflowRequest1.reRunFromTaskId = reRunTaskId
        def reRun1WorkflowInstanceId = workflowExecutor.rerun(reRunWorkflowRequest1)

        then: "Verify that the workflow is in running state and has started the re run after task 1"
        with(workflowExecutionService.getExecutionStatus(reRun1WorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "poll and complete 'integration_task_2'"
        def pollAndCompleteReRunTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the 'integration_task_2' has been polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteReRunTask2Try1, ['tp1': inputParam1, 'tp2': 'task1.done'])

        and: "verify that the re run workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(reRun1WorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
            output.containsKey('o3')
        }

        when: "The completed workflow is re run"
        def reRunWorkflowRequest2 = new RerunWorkflowRequest()
        reRunWorkflowRequest2.reRunFromWorkflowId = workflowInstanceId
        def reRun2WorkflowInstanceId = workflowExecutor.rerun(reRunWorkflowRequest2)


        then: "verify that the workflow is in a running state"
        with(workflowExecutionService.getExecutionStatus(reRun2WorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete the 'integration_task_1' "
        def pollAndCompleteReRun2Task1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteReRun2Task1Try1)

        and: "verify that the 'integration_task1' is complete and the next task is scheduled"
        with(workflowExecutionService.getExecutionStatus(reRun2WorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "poll and complete 'integration_task_2'"
        def pollAndCompleteReRun2Task2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the 'integration_task_2' has been polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteReRun2Task2Try1, ['tp1': inputParam1, 'tp2': 'task1.done'])

        and: "verify that the workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(reRun2WorkflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
            output.containsKey('o3')
        }
    }

    def "Test task skipping in simple workflows"() {

        when: "A simple workflow is started"
        String correlationId = 'unit_test_3' + UUID.randomUUID()
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'

        def workflowInstanceId = startWorkflow(TEST_WORKFLOW, 1,
                correlationId, input,
                null)

        then: "Ensure that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The second task in the workflow is skipped"
        workflowExecutor.skipTaskFromWorkflow(workflowInstanceId, 't2', null)

        then: "Ensure that the second task in the workflow is skipped and the first one is still in scheduled state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_2'
            tasks[0].status == Task.Status.SKIPPED
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete the 'integration_task_1' "
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "Ensure that the third task is scheduled and the first one is in complete state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'integration_task_1'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_3'
            tasks[2].status == Task.Status.SCHEDULED
        }

        when: "Poll and complete the 'integration_task_3' "
        def pollAndCompleteTask3Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_3', 'task3.integration.worker')

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask3Try1)

        and: "verify that the workflow is in a complete state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[2].taskType == 'integration_task_3'
            tasks[2].status == Task.Status.COMPLETED
        }
    }

    def "Test pause and resume simple workflow"() {

        given: "input required to start the workflow execution"
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

        when: "The running workflow is paused"
        workflowExecutor.pauseWorkflow(workflowInstanceId)

        and: "Poll and complete the 'integration_task_1' "
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "verify that the workflow is in PAUSED state and the next task is not scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.PAUSED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
        }

        when: "The next task in the workflow is polled for"
        def task2Try1 = workflowExecutionService.poll('integration_task_2', 'task2.integration.worker')

        then: "verify that there was no task polled"
        !task2Try1

        when: "A decide is run explicitly"
        workflowExecutor.decide(workflowInstanceId)

        and: "The next task is polled again"
        def task2Try2 = workflowExecutionService.poll('integration_task_2', 'task2.integration.worker')

        then: "verify that there was no task polled"
        !task2Try2

        when: "The workflow is resumed"
        workflowExecutor.resumeWorkflow(workflowInstanceId)

        then: "verify that the workflow was resumed and the next task is in a scheduled state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "poll and complete 'integration_task_2'"
        def pollAndCompleteTask2Try3 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the 'integration_task_2' has been polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try3, ['tp1': inputParam1, 'tp2': 'task1.done'])

        and: "verify that the re run workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
            output.containsKey('o3')
        }
    }

    def "Test wait time out task based simple workflow"() {
        when: "Start a workflow based on a task that has a registered wait time out"
        def workflowInstanceId = startWorkflow(WAIT_TIME_OUT_WORKFLOW, 1,
                '', [:], null)

        then: "verify that the workflow is running and the first task scheduled"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'WAIT'
            tasks[0].status == Task.Status.IN_PROGRESS
        }

        when: "A delay is introduced"
        Thread.sleep(3000)

        and: "A decide is executed on the workflow"
        workflowExecutor.decide(workflowInstanceId)

        then: "verify that the workflow is in running state and a replacement task has been scheduled due to time out"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'WAIT'
            tasks[0].status == Task.Status.TIMED_OUT
            tasks[1].taskType == 'WAIT'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "The wait task is completed"
        def waitTask = workflowExecutionService.getExecutionStatus(workflowInstanceId, true).tasks[1]
        waitTask.status = Task.Status.COMPLETED
        workflowExecutor.updateTask(new TaskResult(waitTask))

        and: "verify that the workflow is in running state and the next task is scheduled and 'waitTimeout' task is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].taskType == 'WAIT'
            tasks[1].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.SCHEDULED
        }

        and: "Poll and complete the 'integration_task_1' "
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "The workflow is in a completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[2].taskType == 'integration_task_1'
            tasks[2].status == Task.Status.COMPLETED
        }
    }

    def "Test simple workflow with callbackAfterSeconds for tasks"() {

        given: "input required to start the workflow execution"
        String correlationId = 'unit_test_1'
        def input = new HashMap()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'

        when: "Start a workflow based on the registered simple workflow"
        def workflowInstanceId = startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null)

        then: "Ensure that the workflow has started"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The first task is polled and then a callbackAfterSeconds is added to the task"
        def task1Try1 = workflowExecutionService.poll('integration_task_1', 'task1.worker')
        task1Try1.status = Task.Status.IN_PROGRESS
        task1Try1.callbackAfterSeconds = 2L
        workflowExecutionService.updateTask(new TaskResult(task1Try1))

        then: "verify that the workflow is in running state and the task is in SCHEDULED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "the 'integration_task_1' is polled again"
        def task1Try2 = workflowExecutionService.poll('integration_task_1', 'task1.worker')

        then: "Ensure that there was no task polled due to the callBackAfterSeconds"
        !task1Try2

        then: "verify that the workflow is in running state and the task is in progress"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "There is a delay introduced to go over the callbackAfterSeconds interval"
        Thread.sleep(2050)

        and: "the 'integration_task_1' is polled and completed"
        def pollAndCompleteTask1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker', ['op': 'task1.done'])

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask1Try1)

        and: "verify that the workflow has moved forward and 'integration_task_1 is completed'"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "The second task is polled and then a callbackAfterSeconds is added to the task"
        def task2Try1 = workflowExecutionService.poll('integration_task_2', 'task2.worker')
        task2Try1.status = Task.Status.IN_PROGRESS
        task2Try1.callbackAfterSeconds = 5L
        workflowExecutionService.updateTask(new TaskResult(task2Try1))

        then: "Verify that the workflow is in running state and the task is in scheduled state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "poll for 'integration_task_2'"
        def task2Try2 = workflowExecutionService.poll('integration_task_2', 'task2.worker')

        then: "Ensure that there was no task polled due to the callBackAfterSeconds, even though the task is in scheduled state"
        !task2Try2

        when: "A delay is introduced to get over the callBackAfterSeconds interval"
        Thread.sleep(5100)

        and: "the 'integration_task_2' is polled and completed"
        def pollAndCompleteTask2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task1.integration.worker')

        then: "verify that the 'integration_task_1' was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(pollAndCompleteTask2Try1)

        and: "verify that the workflow has moved forward and 'integration_task_1 is completed'"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }
    }

    def "Test workflow with no tasks"() {
        setup: "Create a workflow definition with no tasks"
        WorkflowDef emptyWorkflowDef = new WorkflowDef()
        emptyWorkflowDef.setName("empty_workflow")
        emptyWorkflowDef.setSchemaVersion(2)

        when: "a workflow is started with this definition"
        def input = new HashMap()
        def correlationId = 'empty_workflow'
        def workflowInstanceId = startWorkflowOperation.execute(new StartWorkflowInput(workflowDefinition: emptyWorkflowDef, workflowInput: input, correlationId: correlationId))

        then: "the workflow is completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 0
        }
    }

    def "Test task def template"() {
        setup: "Register a task definition with input template"
        TaskDef templatedTask = new TaskDef()
        templatedTask.setName('templated_task')
        def httpRequest = new HashMap<>()
        httpRequest['method'] = 'GET'
        httpRequest['vipStack'] = '${STACK2}'
        httpRequest['uri'] = '/get/something'
        def body = new HashMap<>()
        body['inputPaths'] = Arrays.asList('${workflow.input.path1}', '${workflow.input.path2}')
        body['requestDetails'] = '${workflow.input.requestDetails}'
        body['outputPath'] = '${workflow.input.outputPath}'
        httpRequest['body'] = body
        templatedTask.inputTemplate['http_request'] = httpRequest
        templatedTask.ownerEmail = "test@harness.com"
        metadataService.registerTaskDef(Arrays.asList(templatedTask))

        and: "set a system property for STACK2"
        System.setProperty('STACK2', 'test_stack')

        and: "a workflow definition using this task is created"
        WorkflowTask workflowTask = new WorkflowTask()
        workflowTask.setName(templatedTask.getName())
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE)
        workflowTask.setTaskReferenceName("t0")

        WorkflowDef templateWorkflowDef = new WorkflowDef()
        templateWorkflowDef.setName("template_workflow")
        templateWorkflowDef.getTasks().add(workflowTask)
        templateWorkflowDef.setSchemaVersion(2)
        templateWorkflowDef.setOwnerEmail("test@harness.com")
        metadataService.registerWorkflowDef(templateWorkflowDef)

        and: "the input to the workflow is curated"
        def requestDetails = new HashMap<>()
        requestDetails['key1'] = 'value1'
        requestDetails['key2'] = 42

        Map<String, Object> input = new HashMap<>()
        input['path1'] = 'file://path1'
        input['path2'] = 'file://path2'
        input['outputPath'] = 's3://bucket/outputPath'
        input['requestDetails'] = requestDetails

        when: "the workflow is started"
        def correlationId = 'workflow_taskdef_template'
        def workflowInstanceId = startWorkflowOperation.execute(new StartWorkflowInput(workflowDefinition: templateWorkflowDef, workflowInput: input, correlationId: correlationId))

        then: "the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].inputData.get('http_request') instanceof Map
            tasks[0].inputData.get('http_request')['method'] == 'GET'
            tasks[0].inputData.get('http_request')['vipStack'] == 'test_stack'
            tasks[0].inputData.get('http_request')['body'] instanceof Map
            tasks[0].inputData.get('http_request')['body']['requestDetails'] instanceof Map
            tasks[0].inputData.get('http_request')['body']['requestDetails']['key1'] == 'value1'
            tasks[0].inputData.get('http_request')['body']['requestDetails']['key2'] == 42
            tasks[0].inputData.get('http_request')['body']['outputPath'] == 's3://bucket/outputPath'
            tasks[0].inputData.get('http_request')['body']['inputPaths'] instanceof List
            tasks[0].inputData.get('http_request')['body']['inputPaths'][0] == 'file://path1'
            tasks[0].inputData.get('http_request')['body']['inputPaths'][1] == 'file://path2'
            tasks[0].inputData.get('http_request')['uri'] == '/get/something'
        }
    }

    def "Test task def created if not exist"() {
        setup: "Register a workflow definition with task def not registered"
        def taskDefName = "task_not_registered"
        WorkflowTask workflowTask = new WorkflowTask()
        workflowTask.setName(taskDefName)
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE)
        workflowTask.setTaskReferenceName("t0")

        WorkflowDef testWorkflowDef = new WorkflowDef()
        testWorkflowDef.setName("test_workflow")
        testWorkflowDef.getTasks().add(workflowTask)
        testWorkflowDef.setSchemaVersion(2)
        testWorkflowDef.setOwnerEmail("test@harness.com")
        metadataService.registerWorkflowDef(testWorkflowDef)

        when: "the workflow is started"
        def correlationId = 'workflow_taskdef_not_registered'
        def workflowInstanceId = startWorkflowOperation.execute(new StartWorkflowInput(workflowDefinition: testWorkflowDef, workflowInput: [:], correlationId: correlationId))

        then: "the workflow is in running state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskDefName == taskDefName
        }
    }
}
