package com.netflix.counductor.integration.test


import com.netflix.archaius.guice.ArchaiusModule
import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.workflow.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.ApplicationException
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.test.util.WorkflowTestUtil
import com.netflix.conductor.tests.utils.TestModule
import com.netflix.governator.guice.test.ModulesForTesting
import org.apache.commons.lang3.StringUtils
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

import static com.netflix.conductor.core.execution.ApplicationException.Code.CONFLICT
import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

@ModulesForTesting([TestModule.class, ArchaiusModule.class])
class SimpleWorkflowSpec extends Specification {

    @Inject
    ExecutionService workflowExecutionService

    @Inject
    MetadataService metadataService

    @Inject
    WorkflowExecutor workflowExecutor

    @Inject
    WorkflowTestUtil workflowTestUtil

    @Inject
    QueueDAO queueDAO

    @Shared
    def LINEAR_WORKFLOW_T1_T2 = 'integration_test_wf'

    @Shared
    def INTEGRATION_TEST_WF_NON_RESTARTABLE = "integration_test_wf_non_restartable"

    @Shared
    def WORKFLOW_WITH_OPTIONAL_TASK = "optional_task_wf"


    def setup() {
        //Register LINEAR_WORKFLOW_T1_T2, TEST_WORKFLOW, RTOWF, WORKFLOW_WITH_OPTIONAL_TASK
        workflowTestUtil.registerWorkflows("simple_workflow_1_integration_test.json",
                "simple_workflow_3_integration_test.json",
                "simple_workflow_with_resp_time_out_integration_test.json",
                "simple_workflow_with_optional_task_integration_test.json")
    }

    def cleanup() {
        workflowTestUtil.clearWorkflows()
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

        and: "Start a workflow based on the registered simple workflow"
        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null, null, null)

        and: "Retrieve the workflow that was started"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        and: "Poll for a integration_task_1 task and complete the integration_task_1 task"
        def polledIntegrationTask1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        workflowExecutionService.ackTaskReceived(polledIntegrationTask1.taskId)
        polledIntegrationTask1.outputData['op'] = 'task1.done'
        polledIntegrationTask1.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledIntegrationTask1)

        and: "Get the updated workflow instance"
        def updated_workflow_after_integration_task_1 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        and: "Poll for a integration_task_2 task and complete the integration_task_2 task"
        def polledIntegrationTask2 = workflowExecutionService.poll('integration_task_2', 'task1.integration.worker')
        workflowExecutionService.ackTaskReceived(polledIntegrationTask2.taskId)
        def integrationTask2InputParamTp2 = polledIntegrationTask2.inputData['tp2']
        def integrationTask2InputParamTp1 = polledIntegrationTask2.inputData['tp1']
        polledIntegrationTask2.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledIntegrationTask2)

        when: "Status of the completed workflow is retrieved"
        def completedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        then: "Ensure that the initial workflow that was started is in running state"
        workflow
        workflow.getStatus() == Workflow.WorkflowStatus.RUNNING
        workflow.getTasks().size() == 1

        and: "The first polled task is integration_task_1 and the workflowInstanceId of the task is same as running workflowInstanceId"
        polledIntegrationTask1
        polledIntegrationTask1.taskType == 'integration_task_1'
        polledIntegrationTask1.workflowInstanceId == workflowInstanceId

        and: "Updated workflow is not empty"
        updated_workflow_after_integration_task_1

        and: "The input parameters of the task of integration_task_2 task are not null and are valid"
        integrationTask2InputParamTp2
        integrationTask2InputParamTp2 == 'task1.done'
        integrationTask2InputParamTp1
        integrationTask2InputParamTp1 == inputParam1

        and: "The workflow is complete"
        completedWorkflow
        completedWorkflow.status == Workflow.WorkflowStatus.COMPLETED
        completedWorkflow.tasks.size() == 2
        completedWorkflow.output.containsKey('o3')
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

        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null, null, null)

        then: "Ensure that the workflow has started without errors"
        workflowInstanceId

        when: "The running workflow is retrieved"
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        then: "Ensure that the workflow is running state and has one task that is scheduled"
        workflow
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.getTasks().size() == 1

        and: "Verify that the null input params are propagated"
        workflow.input['param2'] == null
        !workflow.tasks.get(0).inputData['someNullKey']

        when: "Poll for a integration_task_1 task"
        Task polledIntegrationTask1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')

        then: "verify that a integration_task_1 task is polled"
        polledIntegrationTask1
        polledIntegrationTask1.taskType == 'integration_task_1'
        polledIntegrationTask1.workflowInstanceId == workflowInstanceId

        when: "The polled integration_task_1 is completed"
        workflowExecutionService.ackTaskReceived(polledIntegrationTask1)
        polledIntegrationTask1.status = Task.Status.COMPLETED

        def someOtherKey = new HashMap<>()
        someOtherKey['a'] = 1
        someOtherKey['A'] = null
        polledIntegrationTask1.outputData['someOtherKey'] = someOtherKey
        polledIntegrationTask1.outputData['someKey'] = null
        workflowExecutionService.updateTask(polledIntegrationTask1)

        and: "The status of the workflow is retrieved"
        def updatedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        then: "Verify that the workflow has been updated successfully and the output data of the next scheduled task is as expected"
        updatedWorkflow
        Task scheduledTask = updatedWorkflow.getTasks().get(0)
        scheduledTask.outputData.containsKey('someKey')
        !scheduledTask.outputData['someKey']
        def someOtherOutput = (Map<String, Object>) scheduledTask.outputData['someOtherKey']
        someOtherOutput.containsKey('A')
        !someOtherOutput['A']
    }

    def "Test simple workflow terminal error condition"() {
        setup:
        Optional<TaskDef> optionalTaskDefinition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1')
        def integration_task_1_definition = optionalTaskDefinition.get()
        integration_task_1_definition.retryCount = 1
        metadataService.updateTaskDef(integration_task_1_definition)
        def workflowDef = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        def outputParameters = workflowDef.outputParameters
        outputParameters['validationErrors'] = '${t1.output.ErrorMessage}'
        metadataService.updateWorkflowDef(workflowDef)


        and: "Start a workflow based on the registered simple workflow"
        String correlationId = "unit_test_1"
        def input = new HashMap()
        input.put("param1", "p1 value")
        input.put("param2", "p2 value")

        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null, null, null)

        when: "Rewind the running workflow"
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        workflowExecutor.rewind(workflowInstanceId, false)

        then: "Ensure that a exception is thrown when a running workflow is being rewind"
        def exceptionThrown = thrown(ApplicationException)
        exceptionThrown.code == CONFLICT

        when:
        def polledIntegrationTask1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        TaskResult taskResult = new TaskResult(polledIntegrationTask1)
        taskResult.reasonForIncompletion = 'NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down'
        taskResult.status = TaskResult.Status.FAILED_WITH_TERMINAL_ERROR
        taskResult.addOutputData('TERMINAL_ERROR', 'Integration endpoint down: FOOBAR')
        taskResult.addOutputData('ErrorMessage', 'There was a terminal error')

        workflowExecutionService.updateTask(taskResult)
        workflowExecutor.decide(workflowInstanceId)

        def failedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        def failedWorkflowOutputParams = failedWorkflow.output
        TaskDef integration_task_1 = workflowTestUtil.getPersistedTaskDefinition("integration_task_1").get()
        Task t1 = failedWorkflow.getTaskByRefName("t1")

        then:
        optionalTaskDefinition
        optionalTaskDefinition.isPresent()
        workflowDef

        workflow
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.tasks.size() == 1

        and: "The first polled task is integration_task_1 and the workflowInstanceId of the task is same as running workflowInstanceId"
        polledIntegrationTask1
        polledIntegrationTask1.taskType == 'integration_task_1'
        polledIntegrationTask1.workflowInstanceId == workflowInstanceId


        failedWorkflow
        failedWorkflow.status == Workflow.WorkflowStatus.FAILED
        failedWorkflow.reasonForIncompletion == 'NON TRANSIENT ERROR OCCURRED: An integration point required to complete the task is down'
        failedWorkflowOutputParams['o1'] == 'p1 value'
        failedWorkflowOutputParams['validationErrors'] == 'There was a terminal error'
        integration_task_1.retryCount == 1
        t1.getRetryCount() == 0

        cleanup:
        outputParameters.remove('validationErrors')
        metadataService.updateWorkflowDef(workflowDef)
    }

    def "Test simple workflow with task timeout"() {

    }

    def "Test Simple Workflow with response timeout "() {
        given: 'Workflow input and correlationId'
        def correlationId = 'unit_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when: "Start a workflow that has a response time out"
        def workflowId = workflowExecutor.startWorkflow('RTOWF', 1, correlationId, workflowInput,
                null, null, null)
        def workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "Workflow is in running state and the task 'task_rt' is ready to be polled"
        workflowId
        workflow
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.tasks.size() == 1
        queueDAO.getSize('task_rt') == 1

        when: "Poll for a 'task_rt' task and then ack the task"
        def polledTaskRtTry1 = workflowExecutionService.poll('task_rt', 'task1.integration.worker.testTimeout')
        def received = workflowExecutionService.ackTaskReceived(polledTaskRtTry1.taskId)

        then:
        polledTaskRtTry1
        polledTaskRtTry1.taskType == 'task_rt'
        polledTaskRtTry1.workflowInstanceId == workflowId
        received
        polledTaskRtTry1.status == Task.Status.IN_PROGRESS

        when: "An additional poll is done wto retrieved another 'task_rt'"
        def noTaskAvailable = workflowExecutionService.poll('task_rt', 'task1.integration.worker.testTimeout')

        then: "Ensure that there is no additional 'task_rt' available to poll"
        !noTaskAvailable

        when: "The processing of the polled task takes more time than the response time out"
        Thread.sleep(10000)
        workflowExecutor.decide(workflowId)
        def workflowAfterTaskRtTimeout = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "Expect a new task to be added to the queue in place of the timed oput task"
        queueDAO.getSize('task_rt') == 1
        workflowAfterTaskRtTimeout
        workflowAfterTaskRtTimeout.status == Workflow.WorkflowStatus.RUNNING
        workflowAfterTaskRtTimeout.tasks.size() == 2
        workflowAfterTaskRtTimeout.tasks[0].status == Task.Status.TIMED_OUT
        workflowAfterTaskRtTimeout.tasks[1].status == Task.Status.SCHEDULED

        when: "The task_rt is polled again and the task is set to be called back after 2 seconds"
        def polledTaskRtTry2 = workflowExecutionService.poll('task_rt', 'task1.integration.worker.testTimeout')
        polledTaskRtTry2.callbackAfterSeconds = 2
        polledTaskRtTry2.status = Task.Status.IN_PROGRESS
        workflowExecutionService.updateTask(polledTaskRtTry2)

        then: "verify that the polled task is not null"
        polledTaskRtTry2

        when: "Retrieve the current state of the workflow after the call back seconds is added to the task"
        def workflowAfterCallbackTaskRt = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "verify that the workflow is in running state and the tasks associated with it are as expected"
        workflowAfterCallbackTaskRt
        workflowAfterCallbackTaskRt.status == Workflow.WorkflowStatus.RUNNING
        workflowAfterCallbackTaskRt.tasks.size() == 2
        workflowAfterCallbackTaskRt.tasks[1].status == Task.Status.SCHEDULED

        when: "induce the time for the call back for the task to expire and run the unack process"
        Thread.sleep(2010)
        queueDAO.processUnacks(polledTaskRtTry2.taskDefName)

        and: "run the decide process on the workflow"
        workflowExecutor.decide(workflowId)

        and: "poll for the task and then complete the task 'task_rt' "
        def polledTaskRtTry3 = workflowExecutionService.poll('task_rt', 'task1.integration.worker.testTimeout')
        polledTaskRtTry3.outputData['op'] = 'task1.done'
        polledTaskRtTry3.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledTaskRtTry3)

        then: 'Verify that the task was polled '
        polledTaskRtTry3

        when: "The next task of the workflow is polled and then completed"
        def polledIntegrationTask2 = workflowExecutionService.poll('integration_task_2', 'task1.integration.worker.testTimeout')
        def ackReceivedIntegrationTask2 = workflowExecutionService.ackTaskReceived(polledIntegrationTask2.taskId)
        polledIntegrationTask2.status = Task.Status.COMPLETED
        polledIntegrationTask2.reasonForIncompletion = 'Unit test failure'
        workflowExecutionService.updateTask(polledIntegrationTask2)

        then: "Verify that 'integration_task_2' is polled and acked"
        polledIntegrationTask2
        ackReceivedIntegrationTask2

        when: "The workflow is retrieved after the completion of the integration_task_2"
        def completedWorkflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: "verify that the workflow is complete"
        completedWorkflow
        completedWorkflow.status == Workflow.WorkflowStatus.COMPLETED
    }


    def "Test if the workflow definitions with and without schema version can be registered"() {
        given: "A workflow definition with no schema version"
        def workflowDef1 = new WorkflowDef()
        workflowDef1.name = 'Test_schema_version1'
        workflowDef1.version = 1

        and: "The workflow definition with no schema version is saved"
        metadataService.updateWorkflowDef(workflowDef1)

        and: "A workflow definition with a schema version is created"
        def workflowDef2 = new WorkflowDef()
        workflowDef2.name = 'Test_schema_version2'
        workflowDef2.version = 1
        workflowDef2.schemaVersion = 2

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
        setup:"Register a task definition with no retries"
        def taskName = 'integration_task_1'
        def taskDefinition = workflowTestUtil.getPersistedTaskDefinition(taskName).get()
        taskDefinition.retryCount = 0
        metadataService.updateTaskDef(taskDefinition)

        when:"Get the workflow definition associated with the simple workflow"
        WorkflowDef workflowDefinition = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        then:"Ensure that there is a workflow definition"
        workflowDefinition
        workflowDefinition.failureWorkflow
        StringUtils.isNotBlank(workflowDefinition.failureWorkflow)

        when:"Start a simple workflow with non null params"
        def correlationId = 'integration_test_1'+ UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        String inputParam1 = 'p1 value'
        workflowInput['param1'] = inputParam1
        workflowInput['param2'] = 'p2 value'

        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null, null, null)

        then:"A workflow instance has started"
        workflowInstanceId

        when:"poll the task that is queued and fail the task"
        def polledIntegrationTask1Try1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        polledIntegrationTask1Try1.status = Task.Status.FAILED
        workflowExecutionService.updateTask(polledIntegrationTask1Try1)

        then:"The workflow ends up in a failed state"
        def failedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        failedWorkflow
        failedWorkflow.status == Workflow.WorkflowStatus.FAILED
        failedWorkflow.tasks[0].status == Task.Status.FAILED
        failedWorkflow.tasks[0].taskType == 'integration_task_1'


        when:"Rewind the workflow which is in the failed state without the latest definition"
        workflowExecutor.rewind(workflowInstanceId, false)
        def rewindWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        then:"verify that the rewound workflow is in a running state"
        rewindWorkflow
        rewindWorkflow.status == Workflow.WorkflowStatus.RUNNING

        when:"Poll for the 'integration_task_1' "
        def polledIntegrationTask1Try2 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        polledIntegrationTask1Try2.status =Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledIntegrationTask1Try2)

        then:"verify that the task is polled and the workflow is in a running state"
        polledIntegrationTask1Try2
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
        }

        when:
        def polledIntegrationTask2 = workflowExecutionService.poll('integration_task_2', 'task1.integration.worker')
        polledIntegrationTask2.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledIntegrationTask2)

        then:
        polledIntegrationTask2
        polledIntegrationTask2.workflowInstanceId == workflowInstanceId
        def workflowStatusAfterTask2 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        workflowStatusAfterTask2.status == Workflow.WorkflowStatus.COMPLETED
    }

    def "Test Simple workflow restart with the latest definition"() {

        setup:"Register a task definition with no retries"
        def taskName = 'integration_task_1'
        def taskDefinition = workflowTestUtil.getPersistedTaskDefinition(taskName).get()
        taskDefinition.retryCount = 0
        metadataService.updateTaskDef(taskDefinition)

        when:"Get the workflow definition associated with the simple workflow"
        WorkflowDef workflowDefinition = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)

        then:"Ensure that there is a workflow definition"
        workflowDefinition
        workflowDefinition.failureWorkflow
        StringUtils.isNotBlank(workflowDefinition.failureWorkflow)

        when:"Start a simple workflow with non null params"
        def correlationId = 'integration_test_1'+ UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        String inputParam1 = 'p1 value'
        workflowInput['param1'] = inputParam1
        workflowInput['param2'] = 'p2 value'

        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null, null, null)

        then:"A workflow instance has started"
        workflowInstanceId

        when:"poll the task that is queued and fail the task"
        def polledIntegrationTask1Try1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        polledIntegrationTask1Try1.status = Task.Status.FAILED
        workflowExecutionService.updateTask(polledIntegrationTask1Try1)

        then:"the workflow ends up in a failed state"
        def failedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        failedWorkflow
        failedWorkflow.status == Workflow.WorkflowStatus.FAILED
        failedWorkflow.tasks[0].status == Task.Status.FAILED
        failedWorkflow.tasks[0].taskType == 'integration_task_1'

        when:"A new version of the workflow definition is registered"
        WorkflowTask workflowTask = new WorkflowTask()
        workflowTask.name = 'integration_task_20'
        workflowTask.taskReferenceName = 'task_added'
        workflowTask.workflowTaskType = TaskType.SIMPLE

        workflowDefinition.tasks.add(workflowTask)
        workflowDefinition.version = 2
        metadataService.updateWorkflowDef(workflowDefinition)

        and:"rewind/restart the workflow with the latest workflow definition"
        workflowExecutor.rewind(workflowInstanceId, true)
        def rewindWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        then:"verify that the rewound workflow is in a running state"
        rewindWorkflow
        rewindWorkflow.status == Workflow.WorkflowStatus.RUNNING

        when:"Poll for the 'integration_task_1' "
        def polledIntegrationTask1Try2 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        polledIntegrationTask1Try2.status =Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledIntegrationTask1Try2)

        then:"verify that the task is polled and the workflow is in a running state"
        polledIntegrationTask1Try2
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
        }

        when:""
        def polledIntegrationTask2 = workflowExecutionService.poll('integration_task_2', 'task1.integration.worker')
        polledIntegrationTask2.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledIntegrationTask2)

        then:
        polledIntegrationTask2
        polledIntegrationTask2.workflowInstanceId == workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }

        when:
        def polledIntegrationTask20 = workflowExecutionService.poll('integration_task_20', 'task1.integration.worker')
        polledIntegrationTask20.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledIntegrationTask20)

        then:
        polledIntegrationTask20
        polledIntegrationTask20.workflowInstanceId == workflowInstanceId
        polledIntegrationTask20.referenceTaskName == 'task_added'
        def completedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        completedWorkflow.status == Workflow.WorkflowStatus.COMPLETED
        completedWorkflow.tasks.size() == 3

        cleanup:
        metadataService.unregisterWorkflowDef(workflowDefinition.getName(), 2)
    }

    def "Test simple workflow with task retries"() {
        setup:"Change the task definition to ensure that it has retries and delay between retries"
        def integrationTask2Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_2').get()
        def modifiedTaskDefinition = new TaskDef(integrationTask2Definition.name, integrationTask2Definition.description,
                3, integrationTask2Definition.timeoutSeconds)
        modifiedTaskDefinition.retryDelaySeconds = 2
        metadataService.updateTaskDef(modifiedTaskDefinition)

        when:"A new simple workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'
        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null, null, null)

        then:"verify that the workflow has started"
        workflowInstanceId
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)
        workflow.status == Workflow.WorkflowStatus.RUNNING

        when:"Poll for the first task and complete the task"
        def polledIntegrationTask1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        def ackPolledIntegrationTask1 = workflowExecutionService.ackTaskReceived(polledIntegrationTask1.taskId)
        polledIntegrationTask1.status = Task.Status.COMPLETED
        def polledIntegrationTask1Output = "task1.output -> " + polledIntegrationTask1.inputData['p1'] +"."+ polledIntegrationTask1.inputData['p2']
        polledIntegrationTask1.outputData['op'] = polledIntegrationTask1Output
        workflowExecutionService.updateTask(polledIntegrationTask1)

        then:"verify that the 'integration_task_1' is polled and completed"
        ackPolledIntegrationTask1
        with(polledIntegrationTask1) {
            inputData.containsKey('p1')
            inputData.containsKey('p2')
            inputData['p1'] == 'p1 value'
            inputData['p2'] == 'p2 value'
        }

        //Need to figure out how to use expect and where here
        when:" 'integration_task_2'  is polled and marked as failed for the first time"
        Tuple polledAndFailedTaskTry1 = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.integration.worker', 'failure...0', 2)

        then:"verify that the task was polled and the input params of the tasks are as expected"
        verifyPolledAndAcknowledgedTask(['tp2': polledIntegrationTask1Output, 'tp1': 'p1 value'], polledAndFailedTaskTry1)

        when:" 'integration_task_2'  is polled and marked as failed for the second time"
        Tuple polledAndFailedTaskTry2 = workflowTestUtil.pollAndFailTask('integration_task_2', 'task2.integration.worker', 'failure...0', 2)

        then:"verify that the task was polled and the input params of the tasks are as expected"
        verifyPolledAndAcknowledgedTask(['tp2': polledIntegrationTask1Output, 'tp1': 'p1 value'], polledAndFailedTaskTry2)

        when:"'integration_task_2'  is polled and marked as completed for the third time"
        def polledAndCompletedTry3 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker', null, 0)

        then:"verify that the task was polled and the input params of the tasks are as expected"
        verifyPolledAndAcknowledgedTask(['tp2': polledIntegrationTask1Output, 'tp1': 'p1 value'], polledAndCompletedTry3)
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
        }

        cleanup:
        metadataService.updateTaskDef(integrationTask2Definition)
    }

    def "Test simple workflow with retry at workflow level"() {
        setup:"Change the task definition to ensure that it has retries and no delay between retries"
        def integrationTask1Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1').get()
        def modifiedTaskDefinition = new TaskDef(integrationTask1Definition.name, integrationTask1Definition.description,
                1, integrationTask1Definition.timeoutSeconds)
        modifiedTaskDefinition.retryDelaySeconds = 0
        metadataService.updateTaskDef(modifiedTaskDefinition)

        when:"Start a simple workflow with non null params"
        def correlationId = 'retry_test'+ UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        String inputParam1 = 'p1 value'
        workflowInput['param1'] = inputParam1
        workflowInput['param2'] = 'p2 value'

        and:"start a simple workflow with input params"
        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null, null, null)

        then:"verify that the workflow has started and the next task is scheduled"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
        }
        with(metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)) {
            failureWorkflow
            StringUtils.isNotBlank(failureWorkflow)
        }

        when:"The first task 'integration_task_1' is polled and failed"
        Tuple polledAndFailedTask1Try1 = workflowTestUtil.pollAndFailTask('integration_task_1', 'task1.integration.worker', 'failure...0', 0)

        then:"verify that the task was polled and acknowledged and the workflow is still in a running state"
        verifyPolledAndAcknowledgedTask([:], polledAndFailedTask1Try1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].status == Task.Status.FAILED
            tasks[1].status == Task.Status.SCHEDULED
        }

        when:"The first task 'integration_task_1' is polled and failed for the second time"
        Tuple polledAndFailedTask1Try2 = workflowTestUtil.pollAndFailTask('integration_task_1', 'task1.integration.worker', 'failure...0', 0)

        then:"verify that the task was polled and acknowledged and the workflow is still in a running state"
        verifyPolledAndAcknowledgedTask([:], polledAndFailedTask1Try2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.FAILED
            tasks.size() == 2
            tasks[0].status == Task.Status.FAILED
            tasks[1].status == Task.Status.FAILED
        }

        when:"The workflow is retried"
        workflowExecutor.retry(workflowInstanceId)

        then:
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[0].status == Task.Status.FAILED
            tasks[1].status == Task.Status.FAILED
            tasks[2].status == Task.Status.SCHEDULED
        }

        when:"The 'integration_task_1' task is polled and is completed"
        def polledAndCompletedTry3 = workflowTestUtil.pollAndCompleteTask('integration_task_1',
                'task2.integration.worker', null, 0)

        then:"verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask([:], polledAndCompletedTry3)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 4
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].status == Task.Status.SCHEDULED
        }

        when:"The 'integration_task_2' task is polled and is completed"
        def polledAndCompletedTaskTry1 = workflowTestUtil.pollAndCompleteTask('integration_task_2',
                'task2.integration.worker', null, 0)

        then:"verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask([:], polledAndCompletedTaskTry1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 4
            tasks[2].status == Task.Status.COMPLETED
            tasks[3].status == Task.Status.COMPLETED
        }

        cleanup:
        metadataService.updateTaskDef(integrationTask1Definition)
    }

    def "Test Long running simple workflow"() {
        given:"A new simple workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when:"start a new workflow with the input"
        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null, null, null)

        then: "verify that the workflow is in running state and the task queue has an entry for the first task of the workflow"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        when: "the first task 'integration_task_1' is polled and then sent back with a callBack seconds"
        def pollTaskTry1 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')
        def ackReceivedTaskTry1 = workflowExecutionService.ackTaskReceived(pollTaskTry1)
        pollTaskTry1.outputData['op'] = 'task1.in.progress'
        pollTaskTry1.callbackAfterSeconds = 5
        pollTaskTry1.status = Task.Status.IN_PROGRESS
        workflowExecutionService.updateTask(pollTaskTry1)

        then:"verify that the task is polled and acknowledged"
        pollTaskTry1
        ackReceivedTaskTry1

        and:"the input data of the data is as expected"
        pollTaskTry1.inputData.containsKey('p1')
        pollTaskTry1.inputData['p1'] == 'p1 value'
        pollTaskTry1.inputData.containsKey('p2')
        pollTaskTry1.inputData['p1'] == 'p1 value'

        and:"the task queue reflects the presence of 'integration_task_1' "
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        when:"the 'integration_task_1' task is polled again"
        def pollTaskTry2 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')

        then:"verify that there was no task polled"
        !pollTaskTry2

        when:"the 'integration_task_1' is polled again after a delay of 5 seconds and completed"
        Thread.sleep(5000)
        def task1Try3Tuple = workflowTestUtil.pollAndCompleteTask('integration_task_1',
                'task1.integration.worker', ['op': 'task1.done'], 0)

        then:"verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask([:], task1Try3Tuple)

        and:"verify that the workflow is updated with the latest task"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
            tasks[0].outputData['op'] == 'task1.done'
        }

        when:"the 'integration_task_1' is polled and completed"
        def task2Try1Tuple = workflowTestUtil.pollAndCompleteTask('integration_task_2',
                'task2.integration.worker', [:], 0)

        then:"verify that the task was polled and completed with the expected inputData for the task that was polled"
        verifyPolledAndAcknowledgedTask(['tp2' : 'task1.done', 'tp1': 'p1 value'], task2Try1Tuple)

        and:"The workflow is in a completed state and reflects the tasks that are completed"
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

        given:"A new simple workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when:"start a new workflow with the input"
        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, workflowInput,
                null, null, null)

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
        def ackReceivedTaskTry1 = workflowExecutionService.ackTaskReceived(pollTaskTry1)
        pollTaskTry1.outputData['op'] = 'task1.in.progress'
        pollTaskTry1.callbackAfterSeconds = 3600
        pollTaskTry1.status = Task.Status.IN_PROGRESS
        workflowExecutionService.updateTask(pollTaskTry1)

        then:"verify that the task is polled and acknowledged"
        pollTaskTry1
        ackReceivedTaskTry1

        and:"the input data of the data is as expected"
        pollTaskTry1.inputData.containsKey('p1')
        pollTaskTry1.inputData['p1'] == 'p1 value'
        pollTaskTry1.inputData.containsKey('p2')
        pollTaskTry1.inputData['p1'] == 'p1 value'

        and:"the task queue reflects the presence of 'integration_task_1' "
        workflowExecutionService.getTaskQueueSizes(['integration_task_1']).get('integration_task_1') == 1

        when:"the 'integration_task_1' task is polled again"
        def pollTaskTry2 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')

        then:"verify that there was no task polled"
        !pollTaskTry2

        when:"the 'integration_task_1' task is polled again"
        def pollTaskTry3 = workflowExecutionService.poll('integration_task_1', 'task1.integration.worker')

        then:"verify that there was no task polled"
        !pollTaskTry3

        when:"The callbackSeconds of the tasks in progress for the workflow are reset"
        workflowExecutor.resetCallbacksForInProgressTasks(workflowInstanceId)

        and:"the 'integration_task_1' is polled again after all the in progress tasks are reset"
        def task1Try4Tuple = workflowTestUtil.pollAndCompleteTask('integration_task_1',
                'task1.integration.worker', ['op': 'task1.done'], 0)

        then:"verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask([:], task1Try4Tuple)

        and:"verify that the workflow is updated with the latest task"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
            tasks[0].outputData['op'] == 'task1.done'
        }

        when:"the 'integration_task_1' is polled and completed"
        def task2Try1Tuple = workflowTestUtil.pollAndCompleteTask('integration_task_2',
                'task2.integration.worker', [:], 0)

        then:"verify that the task was polled and completed with the expected inputData for the task that was polled"
        verifyPolledAndAcknowledgedTask(['tp2' : 'task1.done', 'tp1': 'p1 value'], task2Try1Tuple)

        and:"The workflow is in a completed state and reflects the tasks that are completed"
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
        setup:"Change the task definition to ensure that it has no retries and register a non restartable workflow"
        def integrationTask1Definition = workflowTestUtil.getPersistedTaskDefinition('integration_task_1').get()
        def modifiedTaskDefinition = new TaskDef(integrationTask1Definition.name, integrationTask1Definition.description,
                0, integrationTask1Definition.timeoutSeconds)
        metadataService.updateTaskDef(modifiedTaskDefinition)

        def simpleWorkflowDefinition = metadataService.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1)
        simpleWorkflowDefinition.name = INTEGRATION_TEST_WF_NON_RESTARTABLE
        simpleWorkflowDefinition.restartable = false
        metadataService.updateWorkflowDef(simpleWorkflowDefinition)

        when:"A non restartable workflow is started"
        def correlationId = 'integration_test_1'
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        def workflowInstanceId = workflowExecutor.startWorkflow(INTEGRATION_TEST_WF_NON_RESTARTABLE, 1,
                correlationId, workflowInput,
                null, null, null)

        and:"the 'integration_task_1' is polled and failed"
        Tuple polledAndFailedTaskTry1 = workflowTestUtil.pollAndFailTask('integration_task_1',
                'task1.integration.worker', 'failure...0', 0)

        then:"verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask([:], polledAndFailedTaskTry1)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)){
            status == Workflow.WorkflowStatus.FAILED
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'integration_task_1'
        }

        when:"The failed workflow is rewound"
        workflowExecutor.rewind(workflowInstanceId, false)

        and:"The first task 'integration_task_1' is polled and completed"
        def task1Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker',
                ['op': 'task1.done'], 0)

        then:"Verify that the task is polled and acknowledged"
        verifyPolledAndAcknowledgedTask([:], task1Try2)
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)){
            status == Workflow.WorkflowStatus.RUNNING
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
        }

        when:"The second task 'integration_task_2' is polled and completed"
        def task2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker',
                [:], 0)

        then:"Verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(['tp2': 'task1.done', 'tp1': 'p1 value'], task2Try1)

        and:"The workflow is in a completed state and reflects the tasks that are completed"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[0].status == Task.Status.COMPLETED
            tasks[0].taskType == 'integration_task_1'
            output['o3'] == 'task1.done'
        }

        when:"The successfully completed non restartable workflow is rewound"
        workflowExecutor.rewind(workflowInstanceId, false)

        then:"Ensure that an exception is thrown"
        def exceptionThrown = thrown(ApplicationException)
        exceptionThrown

        cleanup:"clean up the changes made to the task and worflow definition during start up"
        metadataService.updateTaskDef(integrationTask1Definition)
        simpleWorkflowDefinition.name = LINEAR_WORKFLOW_T1_T2
        simpleWorkflowDefinition.restartable = true
        metadataService.updateWorkflowDef(simpleWorkflowDefinition)
    }

    def "Test simple workflow which has an optional task"() {

        given:"A input parameters for a workflow with an optional task"
        def correlationId = 'integration_test'+UUID.randomUUID().toString()
        def workflowInput = new HashMap()
        workflowInput['param1'] = 'p1 value'
        workflowInput['param2'] = 'p2 value'

        when:"An optional task workflow is started"
        def workflowInstanceId = workflowExecutor.startWorkflow(WORKFLOW_WITH_OPTIONAL_TASK, 1,
                correlationId, workflowInput,
                null, null, null)

        then:"verify that the workflow has started and the optional task is in a scheduled state"
        workflowInstanceId
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].taskType == 'task_optional'
        }

        when:"The first optional task is polled and failed"
        Tuple polledAndFailedTaskTry1 = workflowTestUtil.pollAndFailTask('task_optional',
                'task1.integration.worker', 'NETWORK ERROR', 0)

        then:"Verify that the task_optional was polled and acknowledged"
        verifyPolledAndAcknowledgedTask([:], polledAndFailedTaskTry1)

        when:"A decide is executed on the workflow"
        workflowExecutor.decide(workflowInstanceId)

        then:"verify that the workflow is still running and the first optional task has failed and the retry has kicked in"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].status == Task.Status.FAILED
            tasks[0].taskType == 'task_optional'
            tasks[1].status == Task.Status.SCHEDULED
            tasks[1].taskType == 'task_optional'
        }

        when:"Poll the optional task again and do not complete it and run decide"
        workflowExecutionService.poll('task_optional', 'task1.integration.worker')
        Thread.sleep(5000)
        workflowExecutor.decide(workflowInstanceId)

        then:"Ensure that the workflow is updated"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 3
            tasks[1].status == Task.Status.COMPLETED_WITH_ERRORS
            tasks[1].taskType == 'task_optional'
            tasks[2].status == Task.Status.SCHEDULED
            tasks[2].taskType == 'integration_task_2'
        }

        when:"The second task 'integration_task_2' is polled and completed"
        def task2Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker',
                [:], 0)

        then:"Verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask([:], task2Try1)

        and:"Ensure that the workflow is in completed state"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 3
            tasks[2].status == Task.Status.COMPLETED
            tasks[2].taskType == 'integration_task_2'
        }
    }
}
