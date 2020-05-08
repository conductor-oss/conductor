package com.netflix.counductor.integration.test

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.archaius.guice.ArchaiusModule
import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.common.utils.JsonMapperProvider
import com.netflix.conductor.core.WorkflowContext
import com.netflix.conductor.core.execution.ApplicationException
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.service.MetadataService
import com.netflix.conductor.tests.utils.TestModule
import com.netflix.conductor.tests.utils.WorkflowCleanUpUtil
import com.netflix.governator.guice.test.ModulesForTesting
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

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

    @Shared
    ObjectMapper objectMapper = new JsonMapperProvider().get()

    @Shared
    def isWorkflowRegistered = false

    @Shared
    def LINEAR_WORKFLOW_T1_T2 = 'junit_test_wf'

    @Shared
    def TEST_WORKFLOW = 'junit_test_wf3'


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
        Map<String, Object> input = new HashMap<>()
        String inputParam1 = 'p1 value'
        input['param1'] = inputParam1
        input['param2'] = 'p2 value'

        and: "Start a workflow based on the registered simple workflow"
        def workflowInstanceId = workflowExecutor.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1,
                correlationId, input,
                null, null, null)

        and: "Retrieve the workflow that was started"
        def workflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        and: "Poll for a junit_task_1 task and complete the junit_task_1 task"
        def polledJunitTask1 = workflowExecutionService.poll('junit_task_1', 'task1.junit.worker')
        workflowExecutionService.ackTaskReceived(polledJunitTask1.taskId)
        polledJunitTask1.outputData['op'] = 'task1.done'
        polledJunitTask1.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledJunitTask1)

        and: "Get the updated workflow instance"
        def updated_workflow_after_junit_task_1 = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)

        and: "Poll for a junit_task_2 task and complete the junit_task_2 task"
        def polledJunitTask2 = workflowExecutionService.poll('junit_task_2', 'task1.junit.worker')
        workflowExecutionService.ackTaskReceived(polledJunitTask2.taskId)
        def junitTask2InputParamTp2 = polledJunitTask2.inputData['tp2']
        def junitTask2InputParamTp1 = polledJunitTask2.inputData['tp1']
        polledJunitTask2.status = Task.Status.COMPLETED
        workflowExecutionService.updateTask(polledJunitTask2)

        when: "Status of the completed workflow is retrieved"
        def completedWorkflow = workflowExecutionService.getExecutionStatus(workflowInstanceId, true)


        then:"Ensure that the initial workflow that was started is in running state"
        workflow
        workflow.getStatus() == Workflow.WorkflowStatus.RUNNING
        workflow.getTasks().size() == 1

        and:"The first polled task is junit_task_1 and the workflowInstanceId of the task is same as running workflowInstanceId"
        polledJunitTask1
        polledJunitTask1.taskType == 'junit_task_1'
        polledJunitTask1.workflowInstanceId == workflowInstanceId

        and:"Updated workflow is not empty"
        updated_workflow_after_junit_task_1

        and:"The input parameters of the task of junit_task_2 task are not null and are valid"
        junitTask2InputParamTp2
        junitTask2InputParamTp2 == 'task1.done'
        junitTask2InputParamTp1
        junitTask2InputParamTp1 == inputParam1

        and:"The workflow is complete"
        completedWorkflow
        completedWorkflow.status == Workflow.WorkflowStatus.COMPLETED
        completedWorkflow.tasks.size() == 2
        completedWorkflow.output.containsKey('o3')

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
        metadataService.updateWorkflowDef(workflowDef1)

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

        WorkflowContext.set(new WorkflowContext("junit_app"))

        (0..20).collect { "junit_task_$it" }
                .findAll { !getPersistedTaskDefinition(it).isPresent() }
                .collect {new TaskDef(it, it, 1, 120)}
                .forEach {metadataService.registerTaskDef([it])}

        (0..4).collect { "junit_task_0_RT_$it" }
                .findAll { !getPersistedTaskDefinition(it).isPresent() }
                .collect {new TaskDef(it, it, 0, 120)}
                .forEach {metadataService.registerTaskDef([it])}

        metadataService.registerTaskDef([new TaskDef('short_time_out', 'short_time_out', 1, 5)])

        WorkflowDef workflowDef = new WorkflowDef()
        workflowDef.name = LINEAR_WORKFLOW_T1_T2
        workflowDef.description = LINEAR_WORKFLOW_T1_T2;
        workflowDef.version = 1
        workflowDef.inputParameters = ["param1", "param2"]

        Map<String, Object> outputParameters = new HashMap<>()
        outputParameters["o1"] = '${workflow.input.param1}'
        outputParameters["o2"] = '${t2.output.uuid}'
        outputParameters["o3"] = '${t1.output.op}'
        workflowDef.outputParameters = outputParameters
        workflowDef.failureWorkflow = '$workflow.input.failureWfName'
        workflowDef.schemaVersion = 2

        LinkedList<WorkflowTask> wftasks = new LinkedList<>();

        WorkflowTask wft1 = new WorkflowTask()
        wft1.name = 'junit_task_1'
        Map<String, Object> ip1 = new HashMap<>()
        ip1['p1'] = '${workflow.input.param1}'
        ip1['p2'] = '${workflow.input.param2}'
        ip1['someNullKey'] = null
        wft1.inputParameters = ip1
        wft1.taskReferenceName = 't1'

        WorkflowTask wft2 = new WorkflowTask()
        wft2.name = 'junit_task_2'
        Map<String, Object> ip2 = new HashMap<>()
        ip2['tp1'] = '${workflow.input.param1}'
        ip2['tp2'] = '${t1.output.op}'
        wft2.inputParameters = ip2
        wft2.taskReferenceName = 't2'

        wftasks.add(wft1)
        wftasks.add(wft2)
        workflowDef.tasks = wftasks

        WorkflowTask wft3 = new WorkflowTask()
        wft3.name = 'junit_task_3'
        Map<String, Object> ip3 = new HashMap<>()
        ip3['tp1'] = '${workflow.input.param1}'
        ip3['tp2'] = '${t1.output.op}'
        wft3.inputParameters = ip3
        wft3.taskReferenceName = 't3'

        WorkflowDef workflowdef2 = new WorkflowDef()
        workflowdef2.name = TEST_WORKFLOW
        workflowdef2.description = workflowdef2.getName()
        workflowdef2.version = 1
        workflowdef2.inputParameters = ['param1', 'param2']

        LinkedList<WorkflowTask> wftasks2 = new LinkedList<>();
        wftasks2.add(wft1)
        wftasks2.add(wft2)
        wftasks2.add(wft3)
        workflowdef2.schemaVersion = 2
        workflowdef2.tasks = wftasks2

        [workflowDef, workflowdef2].forEach { metadataService.updateWorkflowDef(it) }

    }


    def getPersistedTaskDefinition(String taskDefName) {
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
