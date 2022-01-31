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
package com.netflix.conductor.test.resiliency

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.exception.ApplicationException
import com.netflix.conductor.rest.controllers.TaskResource
import com.netflix.conductor.rest.controllers.WorkflowResource
import com.netflix.conductor.test.base.AbstractResiliencySpecification

/**
 * When QueueDAO is unavailable,
 * Ensure All Worklow and Task resource endpoints either:
 * 1. Fails and/or throws an Exception
 * 2. Succeeds
 * 3. Doesn't involve QueueDAO
 */
class QueueResiliencySpec extends AbstractResiliencySpecification {

    @Autowired
    WorkflowResource workflowResource

    @Autowired
    TaskResource taskResource

    def SIMPLE_TWO_TASK_WORKFLOW = 'integration_test_wf'

    def setup() {
        workflowTestUtil.taskDefinitions()
        workflowTestUtil.registerWorkflows(
                'simple_workflow_1_integration_test.json'
        )
    }

    /// Workflow Resource endpoints

    def "Verify Start workflow fails when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def response = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))
        then: "Verify workflow starts when there are no Queue failures"
        response

        when: "We try same request Queue failure"
        response = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        then: "Verify that workflow start fails with BACKEND_ERROR"
        1 * queueDAO.push(*_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Queue push failed from Spy") }
        thrown(ApplicationException)
    }

    def "Verify terminate succeeds when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))
        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "We terminate it when QueueDAO is unavailable"
        workflowResource.terminate(workflowInstanceId, "Terminated from a test")

        then: "Verify that terminate is successful without any exceptions"
        2 * queueDAO.remove(*_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Queue remove failed from Spy") }
        0 * queueDAO._
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.CANCELED
        }
    }

    def "Verify Restart workflow fails when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        and: "We terminate it when QueueDAO is unavailable"
        workflowResource.terminate(workflowInstanceId, "Terminated from a test")

        then: "Verify that workflow is in terminated state"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.CANCELED
        }

        when: "We restart workflow when QueueDAO is unavailable"
        workflowResource.restart(workflowInstanceId, false)

        then: ""
        1 * queueDAO.push(*_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Queue push failed from Spy") }
        1 * queueDAO.remove(*_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Queue remove failed from Spy") }
        0 * queueDAO._
        thrown(ApplicationException)
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 0
        }
    }

    def "Verify rerun fails when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        and: "terminate it"
        workflowResource.terminate(workflowInstanceId, "Terminated from a test")

        then: "Verify that workflow is in terminated state"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.CANCELED
        }

        when: "Workflow is rerun when QueueDAO is unavailable"
        def rerunWorkflowRequest = new RerunWorkflowRequest()
        rerunWorkflowRequest.setReRunFromWorkflowId(workflowInstanceId)
        workflowResource.rerun(workflowInstanceId, rerunWorkflowRequest)

        then: ""
        1 * queueDAO.push(*_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Queue push failed from Spy") }
        0 * queueDAO._
        thrown(ApplicationException)
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 0
        }
    }

    def "Verify retry fails when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        and: "terminate it"
        workflowResource.terminate(workflowInstanceId, "Terminated from a test")

        then: "Verify that workflow is in terminated state"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.CANCELED
        }

        when: "workflow is restarted when QueueDAO is unavailable"
        workflowResource.retry(workflowInstanceId, false)

        then: "Verify retry fails"
        1 * queueDAO.push(*_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Queue push failed from Spy") }
        0 * queueDAO._
        thrown(ApplicationException)
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.TERMINATED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.CANCELED
        }
    }

    def "Verify getWorkflow succeeds when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))
        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "We get a workflow when QueueDAO is unavailable"
        def workflow = workflowResource.getExecutionStatus(workflowInstanceId, true)

        then: "Verify workflow is returned"
        0 * queueDAO._
        workflow.getStatus() == Workflow.WorkflowStatus.RUNNING
        workflow.getTasks().size() == 1
        workflow.getTasks()[0].status == Task.Status.SCHEDULED
    }

    def "Verify getWorkflows succeeds when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))
        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "We get a workflow when QueueDAO is unavailable"
        def workflows = workflowResource.getWorkflows(SIMPLE_TWO_TASK_WORKFLOW, "", true, true)

        then: "Verify queueDAO is not involved and an exception is not thrown"
        0 * queueDAO._
        notThrown(Exception)
    }

    def "Verify remove workflow succeeds when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))
        then: "Verify workflow is started"

        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "We get a workflow when QueueDAO is unavailable"
        workflowResource.delete(workflowInstanceId, false)

        then: "Verify queueDAO is called to remove from _deciderQueue"
        1 * queueDAO._

        when: "We try to get deleted workflow"
        workflowResource.getExecutionStatus(workflowInstanceId, true)

        then:
        thrown(ApplicationException)
    }

    def "Verify decide succeeds when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "We decide a workflow"
        workflowResource.decide(workflowInstanceId)

        then: "Verify queueDAO is not involved"
        0 * queueDAO._
    }

    def "Verify pause succeeds when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The workflow is paused when QueueDAO is unavailable"
        workflowResource.pauseWorkflow(workflowInstanceId)

        then: "Verify workflow is paused without any exceptions"
        1 * queueDAO.remove(*_) >> { throw new IllegalStateException("Queue remove failed from Spy") }
        0 * queueDAO._
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.PAUSED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }
    }

    def "Verify resume fails when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The workflow is paused"
        workflowResource.pauseWorkflow(workflowInstanceId)

        then: "Verify workflow is paused"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.PAUSED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Workflow is resumed when QueueDAO is unavailable"
        workflowResource.resumeWorkflow(workflowInstanceId)

        then: "exception is thrown"
        1 * queueDAO.push(*_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Queue push failed from Spy") }
        thrown(ApplicationException)
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.PAUSED
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }
    }

    def "Verify reset callbacks fails when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "Task is updated with callBackAfterSeconds"
        def workflow = workflowResource.getExecutionStatus(workflowInstanceId, true)
        def task = workflow.getTasks().get(0)
        def taskResult = new TaskResult(task)
        taskResult.setCallbackAfterSeconds(120)
        taskResource.updateTask(taskResult)

        and: "and then reset callbacks when QueueDAO is unavailable"
        workflowResource.resetWorkflow(workflowInstanceId)

        then: "Verify an exception is thrown"
        1 * queueDAO.resetOffsetTime(*_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "Queue resetOffsetTime failed from Spy") }
        thrown(ApplicationException)
    }

    def "Verify search is not impacted by QueueDAO"() {
        when: "We perform a search"
        workflowResource.search(0, 1, "", "", "")

        then: "Verify it doesn't involve QueueDAO"
        0 * queueDAO._
    }

    def "Verify search workflows by tasks is not impacted by QueueDAO"() {
        when: "We perform a search"
        workflowResource.searchWorkflowsByTasks(0, 1, "", "", "")

        then: "Verify it doesn't involve QueueDAO"
        0 * queueDAO._
    }

    def "Verify get external storage location is not impacted by QueueDAO"() {
        when:
        workflowResource.getExternalStorageLocation("", "", "")

        then: "Verify it doesn't involve QueueDAO"
        0 * queueDAO._
    }


    /// Task Resource endpoints

    def "Verify polls return with no result when QueueDAO is unavailable"() {
        when: "Some task 'integration_task_1' is polled"
        def responseEntity = taskResource.poll("integration_task_1", "test", "")

        then:
        1 * queueDAO.pop(*_) >> { throw new IllegalStateException("Queue pop failed from Spy") }
        0 * queueDAO._
        notThrown(Exception)
        responseEntity && responseEntity.statusCode == HttpStatus.NO_CONTENT && !responseEntity.body
    }

    def "Verify updateTask with COMPLETE status succeeds when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The first task 'integration_task_1' is polled"
        def responseEntity = taskResource.poll("integration_task_1", "test", null)

        then: "Verify task is returned successfully"
        responseEntity && responseEntity.statusCode == HttpStatus.OK && responseEntity.body
        responseEntity.body.status == Task.Status.IN_PROGRESS
        responseEntity.body.taskType == 'integration_task_1'

        when: "the above task is updated, while QueueDAO is unavailable"
        def taskResult = new TaskResult(responseEntity.body)
        taskResult.setStatus(TaskResult.Status.COMPLETED)
        def result = taskResource.updateTask(taskResult)

        then: "updateTask returns successfully without any exceptions"
        1 * queueDAO.remove(*_) >> { throw new IllegalStateException("Queue remove failed from Spy") }
        result == responseEntity.body.taskId
        notThrown(Exception)
    }

    def "Verify updateTask with IN_PROGRESS state fails when QueueDAO is unavailable"() {
        when: "Start a simple workflow"
        def workflowInstanceId = workflowResource.startWorkflow(new StartWorkflowRequest()
                .withName(SIMPLE_TWO_TASK_WORKFLOW)
                .withVersion(1))

        then: "Verify workflow is started"
        with(workflowResource.getExecutionStatus(workflowInstanceId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "The first task 'integration_task_1' is polled"
        def responseEntity = taskResource.poll("integration_task_1", "test", null)

        then: "Verify task is returned successfully"
        responseEntity && responseEntity.statusCode == HttpStatus.OK
        responseEntity.body.status == Task.Status.IN_PROGRESS
        responseEntity.body.taskType == 'integration_task_1'

        when: "the above task is updated, while QueueDAO is unavailable"
        def taskResult = new TaskResult(responseEntity.body)
        taskResult.setStatus(TaskResult.Status.IN_PROGRESS)
        taskResult.setCallbackAfterSeconds(120)
        def result = taskResource.updateTask(taskResult)

        then: "updateTask fails with an exception"
        2 * queueDAO.postpone(*_) >> { throw new IllegalStateException("Queue postpone failed from Spy") }
        thrown(Exception)
    }

    def "verify getTaskQueueSizes fails when QueueDAO is unavailable"() {
        when:
        taskResource.size(Arrays.asList("testTaskType", "testTaskType2"))

        then:
        1 * queueDAO.getSize(*_) >> { throw new IllegalStateException("Queue getSize failed from Spy") }
        thrown(Exception)
    }

    def "Verify log doesn't involve QueueDAO"() {
        when:
        taskResource.log("testTaskId", "test log")

        then:
        0 * queueDAO._
    }

    def "Verify getTaskLogs doesn't involve QueueDAO"() {
        when:
        taskResource.getTaskLogs("testTaskId")

        then:
        0 * queueDAO._
    }

    def "Verify getTask doesn't involve QueueDAO"() {
        when:
        taskResource.getTask("testTaskId")

        then:
        0 * queueDAO._
    }

    def "Verify getAllQueueDetails fails when QueueDAO is unavailable"() {
        when:
        taskResource.all()

        then:
        1 * queueDAO.queuesDetail() >> { throw new IllegalStateException("Queue queuesDetail failed from Spy") }
        thrown(Exception)
    }

    def "Verify getPollData doesn't involve QueueDAO"() {
        when:
        taskResource.getPollData("integration_test_1")

        then:
        0 * queueDAO.queuesDetail()
    }

    def "Verify getAllPollData fails when QueueDAO is unavailable"() {
        when:
        taskResource.getAllPollData()

        then:
        1 * queueDAO.queuesDetail() >> { throw new IllegalStateException("Queue queuesDetail failed from Spy") }
        thrown(Exception)
    }

    def "Verify task search is not impacted by QueueDAO"() {
        when: "We perform a search"
        taskResource.search(0, 1, "", "", "")

        then: "Verify it doesn't involve QueueDAO"
        0 * queueDAO._
    }

    def "Verify task get external storage location is not impacted by QueueDAO"() {
        when:
        taskResource.getExternalStorageLocation("", "", "")

        then: "Verify it doesn't involve QueueDAO"
        0 * queueDAO._
    }
}
