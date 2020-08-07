package com.netflix.counductor.integration.test

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.matcher.Matchers
import com.netflix.conductor.common.constraints.FaultInjectionInterceptor
import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.core.execution.WorkflowRepairService
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.server.FaultInjectionWithProbability
import com.netflix.conductor.service.ExecutionService
import com.netflix.conductor.test.util.WorkflowTestUtil
import com.netflix.conductor.tests.utils.TestModule
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

class TaskResiliencySpec extends Specification {

    ExecutionService workflowExecutionService

    WorkflowExecutor workflowExecutor

    WorkflowTestUtil workflowTestUtil

    WorkflowRepairService workflowRepairService

    QueueDAO queueDAO

    @Shared
    def SIMPLE_TWO_TASK_WORKFLOW = 'integration_test_wf'

    def dynamicFailureProbability = new FaultInjectionWithProbability.DynamicFailureProbability()

    def setup() {
        Injector injector =
                Guice.createInjector(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                install(new TestModule());
                                bindInterceptor(Matchers.any(), Matchers.annotatedWith(FaultInjectionInterceptor.class),
                                        new FaultInjectionWithProbability(dynamicFailureProbability));
                            }
                        });
        workflowExecutor = injector.getInstance(WorkflowExecutor.class)
        workflowExecutionService = injector.getInstance(ExecutionService.class)
        queueDAO = injector.getInstance(QueueDAO.class)
        workflowTestUtil = injector.getInstance(WorkflowTestUtil.class)
        workflowRepairService = injector.getInstance(WorkflowRepairService.class)

        workflowTestUtil.taskDefinitions()
        workflowTestUtil.registerWorkflows(
                'simple_workflow_1_integration_test.json'
        )
    }

    def cleanup() {
        workflowTestUtil.clearWorkflows()
    }

    def "Verify that a workflow recovers and completes on schedule task failure from queue push failure"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowExecutor.startWorkflow(SIMPLE_TWO_TASK_WORKFLOW, 1,
                '', [:], null, null, null)

        then: "Retrieve the workflow"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        and: "The decider queue has one task that is ready to be polled"
        queueDAO.getSize(WorkflowExecutor.DECIDER_QUEUE) == 1

        // Simulate queue push failure when creating a new task, after completing first task
        when: "The first task 'integration_task_1' is polled and completed"
        dynamicFailureProbability.setMethodName("push")
        dynamicFailureProbability.setFailureProbability(1)
        def task1Try1 = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.integration.worker')

        then: "Verify that the task was polled and acknowledged"
        verifyPolledAndAcknowledgedTask(task1Try1)

        and: "Ensure that the next task is SCHEDULED"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
        }

        when: "The first task 'integration_task_2' is polled and completed"
        def task1Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "Verify that the task was not polled, and the taskId doesn't exist in the queue"
        task1Try2[0] == null
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 2
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.COMPLETED
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.SCHEDULED
            def taskId = tasks[1].getTaskId()
            queueDAO.containsMessage("integration_task_2", taskId) == false
        }

        when: "Running a repair and decide on the workflow (supposedly from sweeper service)"
        dynamicFailureProbability.setFailureProbability(0)
        workflowRepairService.verifyAndRepairWorkflow(workflowInstanceId, true)
        workflowExecutor.decide(workflowInstanceId)
        task1Try2 = workflowTestUtil.pollAndCompleteTask('integration_task_2', 'task2.integration.worker')

        then: "verify that the next scheduled task can be polled and executed successfully"
        with(workflowExecutionService.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.COMPLETED
            tasks.size() == 2
            tasks[1].taskType == 'integration_task_2'
            tasks[1].status == Task.Status.COMPLETED
        }
    }
}
