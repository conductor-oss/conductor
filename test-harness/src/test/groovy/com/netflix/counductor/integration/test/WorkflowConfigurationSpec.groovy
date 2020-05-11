package com.netflix.counductor.integration.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.archaius.guice.ArchaiusModule
import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.common.utils.JsonMapperProvider
import com.netflix.conductor.core.WorkflowContext
import com.netflix.conductor.core.execution.ApplicationException
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.tests.utils.JsonUtils
import com.netflix.conductor.tests.utils.TestModule
import com.netflix.conductor.tests.utils.WorkflowCleanUpUtil
import com.netflix.governator.guice.test.ModulesForTesting
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

import static com.netflix.conductor.core.execution.ApplicationException.Code.CONFLICT

@ModulesForTesting([TestModule.class, ArchaiusModule.class])
class WorkflowConfigurationSpec extends Specification {

    @Inject
    ExecutionService workflowExecutionService

    @Inject
    MetadataService metadataService

    @Inject
    WorkflowExecutor workflowExecutor

    @Inject
    WorkflowCleanUpUtil cleanUpUtil

    @Inject
    QueueDAO queueDAO

    @Shared
    ObjectMapper objectMapper = new JsonMapperProvider().get()

    @Shared
    def isWorkflowRegistered = false

    @Shared
    def LINEAR_WORKFLOW_T1_T2 = 'integration_test_wf'

    @Shared
    def TEST_WORKFLOW = 'integration_test_wf3'

    @Shared
    def RETRY_COUNT = 1


    def setup() {
        if (!isWorkflowRegistered) {
            registerWorkflows()
            isWorkflowRegistered = true
        }
    }

    def cleanup() {
        cleanUpUtil.clearWorkflows()
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
        Optional<TaskDef> optionalTaskDefinition = getPersistedTaskDefinition('integration_task_1')
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
        TaskDef integration_task_1 = getPersistedTaskDefinition("integration_task_1").get()
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
        integration_task_1.retryCount == 1
        t1.getRetryCount() == 0
        failedWorkflowOutputParams['o1'] == 'p1 value'
        failedWorkflowOutputParams['validationErrors'] == 'There was a terminal error'

        cleanup:
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
        //workflowAfterTaskRtTimeout.tasks.findAll {it.referenceTaskName == 'task1_rt_t1'}
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

    def registerWorkflows() {
        WorkflowContext.set(new WorkflowContext("integration_app"))

        (0..20).collect { "integration_task_$it" }
                .findAll { !getPersistedTaskDefinition(it).isPresent() }
                .collect { new TaskDef(it, it, 1, 120) }
                .forEach { metadataService.registerTaskDef([it]) }

        (0..4).collect { "integration_task_0_RT_$it" }
                .findAll { !getPersistedTaskDefinition(it).isPresent() }
                .collect { new TaskDef(it, it, 0, 120) }
                .forEach { metadataService.registerTaskDef([it]) }

        metadataService.registerTaskDef([new TaskDef('short_time_out', 'short_time_out', 1, 5)])

        TaskDef task = new TaskDef()
        task.name = "task_rt"
        task.timeoutSeconds = 120
        task.retryCount = RETRY_COUNT
        task.retryDelaySeconds = 0
        task.responseTimeoutSeconds = 10
        metadataService.registerTaskDef([task])


        def workflowDefinition1 = JsonUtils.fromJson("simple_workflow_1_integration_test.json", WorkflowDef.class)
        def workflowDefinition3 = JsonUtils.fromJson("simple_workflow_3_integration_test.json", WorkflowDef.class)
        def workflowDefinition2 = JsonUtils.fromJson("simple_workflow_with_resp_time_out_integration_test.json", WorkflowDef.class)
        [workflowDefinition1, workflowDefinition2, workflowDefinition3].forEach {
            metadataService.updateWorkflowDef(it)
        }
    }


    def Optional<TaskDef> getPersistedTaskDefinition(String taskDefName) {
        try {
            return Optional.ofNullable(metadataService.getTaskDef(taskDefName))
        } catch (ApplicationException applicationException) {
            if (applicationException.code == ApplicationException.Code.NOT_FOUND) {
                return Optional.empty()
            } else {
                throw applicationException
            }
        }
    }
}
