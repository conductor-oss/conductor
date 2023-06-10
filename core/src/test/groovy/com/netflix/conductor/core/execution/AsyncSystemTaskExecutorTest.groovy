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
package com.netflix.conductor.core.execution

import java.time.Duration

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.core.config.ConductorProperties
import com.netflix.conductor.core.dal.ExecutionDAOFacade
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask
import com.netflix.conductor.core.operation.StartWorkflowOperation
import com.netflix.conductor.core.utils.IDGenerator
import com.netflix.conductor.core.utils.QueueUtils
import com.netflix.conductor.dao.MetadataDAO
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.conductor.common.metadata.tasks.TaskType.SUB_WORKFLOW

class AsyncSystemTaskExecutorTest extends Specification {

    ExecutionDAOFacade executionDAOFacade
    QueueDAO queueDAO
    MetadataDAO metadataDAO
    WorkflowExecutor workflowExecutor
    StartWorkflowOperation startWorkflowOperation

    @Subject
    AsyncSystemTaskExecutor executor

    WorkflowSystemTask workflowSystemTask
    ConductorProperties properties = new ConductorProperties()

    def setup() {
        executionDAOFacade = Mock(ExecutionDAOFacade.class)
        queueDAO = Mock(QueueDAO.class)
        metadataDAO = Mock(MetadataDAO.class)
        workflowExecutor = Mock(WorkflowExecutor.class)
        startWorkflowOperation = Mock(StartWorkflowOperation.class)

        workflowSystemTask = Mock(WorkflowSystemTask.class) {
            isTaskRetrievalRequired() >> true
        }

        properties.taskExecutionPostponeDuration = Duration.ofSeconds(1)
        properties.systemTaskWorkerCallbackDuration = Duration.ofSeconds(1)

        executor = new AsyncSystemTaskExecutor(executionDAOFacade, queueDAO, metadataDAO, properties, workflowExecutor)
    }

    // this is not strictly a unit test, but its essential to test AsyncSystemTaskExecutor with SubWorkflow
    def "Execute SubWorkflow task"() {
        given:
        String workflowId = "workflowId"
        String subWorkflowId = "subWorkflowId"
        SubWorkflow subWorkflowTask = new SubWorkflow(new ObjectMapper(), startWorkflowOperation)

        String task1Id = new IDGenerator().generate()
        TaskModel task1 = new TaskModel()
        task1.setTaskType(SUB_WORKFLOW.name())
        task1.setReferenceTaskName("waitTask")
        task1.setWorkflowInstanceId(workflowId)
        task1.setScheduledTime(System.currentTimeMillis())
        task1.setTaskId(task1Id)
        task1.getInputData().put("asyncComplete", true)
        task1.getInputData().put("subWorkflowName", "junit1")
        task1.getInputData().put("subWorkflowVersion", 1)
        task1.setStatus(TaskModel.Status.SCHEDULED)

        String queueName = QueueUtils.getQueueName(task1)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)
        WorkflowModel subWorkflow = new WorkflowModel(workflowId: subWorkflowId, status: WorkflowModel.Status.RUNNING)

        when:
        executor.execute(subWorkflowTask, task1Id)

        then:
        1 * executionDAOFacade.getTaskModel(task1Id) >> task1
        1 * executionDAOFacade.getWorkflowModel(workflowId, subWorkflowTask.isTaskRetrievalRequired()) >> workflow
        1 * startWorkflowOperation.execute(*_) >> subWorkflowId
        1 * workflowExecutor.getWorkflow(subWorkflowId, false) >> subWorkflow

        // SUB_WORKFLOW is asyncComplete so its removed from the queue
        1 * queueDAO.remove(queueName, task1Id)

        task1.status == TaskModel.Status.IN_PROGRESS
        task1.subWorkflowId == subWorkflowId
        task1.startTime != 0
    }

    def "Execute with a non-existing task id"() {
        given:
        String taskId = "taskId"

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> null
        0 * workflowSystemTask.start(*_)
        0 * executionDAOFacade.updateTask(_)
    }

    def "Execute with a task id that fails to load"() {
        given:
        String taskId = "taskId"

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> { throw new RuntimeException("datastore unavailable") }
        0 * workflowSystemTask.start(*_)
        0 * executionDAOFacade.updateTask(_)
    }

    def "Execute with a task id that is in terminal state"() {
        given:
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.COMPLETED, taskId: taskId)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * queueDAO.remove(task.taskType, taskId)
        0 * workflowSystemTask.start(*_)
        0 * executionDAOFacade.updateTask(_)
    }

    def "Execute with a task id that is part of a workflow in terminal state"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.COMPLETED)
        String queueName = QueueUtils.getQueueName(task)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * queueDAO.remove(queueName, taskId)

        task.status == TaskModel.Status.CANCELED
        task.startTime == 0
    }

    def "Execute with a task id that exceeds in-progress limit"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"

        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                workflowPriority: 10)
        String queueName = QueueUtils.getQueueName(task)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.exceedsInProgressLimit(task) >> true
        1 * queueDAO.postpone(queueName, taskId, task.workflowPriority, properties.taskExecutionPostponeDuration.seconds)

        task.status == TaskModel.Status.SCHEDULED
        task.startTime == 0
    }

    def "Execute with a task id that is rate limited"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                rateLimitPerFrequency: 1, taskDefName: "taskDefName", workflowPriority: 10)
        String queueName = QueueUtils.getQueueName(task)
        TaskDef taskDef = new TaskDef()

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * metadataDAO.getTaskDef(task.taskDefName) >> taskDef
        1 * executionDAOFacade.exceedsRateLimitPerFrequency(task, taskDef) >> taskDef
        1 * queueDAO.postpone(queueName, taskId, task.workflowPriority, properties.taskExecutionPostponeDuration.seconds)

        task.status == TaskModel.Status.SCHEDULED
        task.startTime == 0
    }

    def "Execute with a task id that is rate limited but postpone fails"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                rateLimitPerFrequency: 1, taskDefName: "taskDefName", workflowPriority: 10)
        String queueName = QueueUtils.getQueueName(task)
        TaskDef taskDef = new TaskDef()

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * metadataDAO.getTaskDef(task.taskDefName) >> taskDef
        1 * executionDAOFacade.exceedsRateLimitPerFrequency(task, taskDef) >> taskDef
        1 * queueDAO.postpone(queueName, taskId, task.workflowPriority, properties.taskExecutionPostponeDuration.seconds) >> { throw new RuntimeException("queue unavailable") }

        task.status == TaskModel.Status.SCHEDULED
        task.startTime == 0
    }

    def "Execute with a task id that is in SCHEDULED state"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                taskDefName: "taskDefName", workflowPriority: 10)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)
        String queueName = QueueUtils.getQueueName(task)
        workflowSystemTask.getEvaluationOffset(task, 1) >> Optional.empty();


        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * executionDAOFacade.updateTask(task)
        1 * queueDAO.postpone(queueName, taskId, task.workflowPriority, properties.systemTaskWorkerCallbackDuration.seconds)
        1 * workflowSystemTask.start(workflow, task, workflowExecutor) >> { task.status = TaskModel.Status.IN_PROGRESS }

        0 * workflowExecutor.decide(workflowId) // verify that workflow is NOT decided

        task.status == TaskModel.Status.IN_PROGRESS
        task.startTime != 0 // verify that startTime is set
        task.endTime == 0 // verify that endTime is not set
        task.pollCount == 1 // verify that poll count is incremented
        task.callbackAfterSeconds == properties.systemTaskWorkerCallbackDuration.seconds
    }

    def "Execute with a task id that is in SCHEDULED state and WorkflowSystemTask.start sets the task in a terminal state"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                taskDefName: "taskDefName", workflowPriority: 10)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)
        String queueName = QueueUtils.getQueueName(task)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * executionDAOFacade.updateTask(task)

        1 * workflowSystemTask.start(workflow, task, workflowExecutor) >> { task.status = TaskModel.Status.COMPLETED }
        1 * queueDAO.remove(queueName, taskId)
        1 * workflowExecutor.decide(workflowId) // verify that workflow is decided

        task.status == TaskModel.Status.COMPLETED
        task.startTime != 0 // verify that startTime is set
        task.endTime != 0 // verify that endTime is set
        task.pollCount == 1 // verify that poll count is incremented
    }

    def "Execute with a task id that is in SCHEDULED state but WorkflowSystemTask.start fails"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                taskDefName: "taskDefName", workflowPriority: 10)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * executionDAOFacade.updateTask(task)

        // simulating a "start" failure that happens after the Task object is modified
        // the modification will be persisted
        1 * workflowSystemTask.start(workflow, task, workflowExecutor) >> {
            task.status = TaskModel.Status.IN_PROGRESS
            throw new RuntimeException("unknown system task failure")
        }

        0 * workflowExecutor.decide(workflowId) // verify that workflow is NOT decided

        task.status == TaskModel.Status.IN_PROGRESS
        task.startTime != 0 // verify that startTime is set
        task.endTime == 0 // verify that endTime is not set
        task.pollCount == 1 // verify that poll count is incremented
    }

    def "Execute with a task id that is in SCHEDULED state and is set to asyncComplete"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                taskDefName: "taskDefName", workflowPriority: 10)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)
        String queueName = QueueUtils.getQueueName(task)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * executionDAOFacade.updateTask(task) // 1st call for pollCount, 2nd call for status update

        1 * workflowSystemTask.isAsyncComplete(task) >> true
        1 * workflowSystemTask.start(workflow, task, workflowExecutor) >> { task.status = TaskModel.Status.IN_PROGRESS }
        1 * queueDAO.remove(queueName, taskId)

        1 * workflowExecutor.decide(workflowId) // verify that workflow is decided

        task.status == TaskModel.Status.IN_PROGRESS
        task.startTime != 0 // verify that startTime is set
        task.endTime == 0 // verify that endTime is not set
        task.pollCount == 1 // verify that poll count is incremented
    }

    def "Execute with a task id that is in IN_PROGRESS state"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.IN_PROGRESS, taskId: taskId, workflowInstanceId: workflowId,
                rateLimitPerFrequency: 1, taskDefName: "taskDefName", workflowPriority: 10, pollCount: 1)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * executionDAOFacade.updateTask(task) // 1st call for pollCount, 2nd call for status update

        0 * workflowSystemTask.start(workflow, task, workflowExecutor)
        1 * workflowSystemTask.execute(workflow, task, workflowExecutor)

        task.status == TaskModel.Status.IN_PROGRESS
        task.endTime == 0 // verify that endTime is not set
        task.pollCount == 2 // verify that poll count is incremented
    }

    def "Execute with a task id that is in IN_PROGRESS state and is set to asyncComplete"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.IN_PROGRESS, taskId: taskId, workflowInstanceId: workflowId,
                rateLimitPerFrequency: 1, taskDefName: "taskDefName", workflowPriority: 10, pollCount: 1)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * executionDAOFacade.updateTask(task) // only one call since pollCount is not incremented

        1 * workflowSystemTask.isAsyncComplete(task) >> true
        0 * workflowSystemTask.start(workflow, task, workflowExecutor)
        1 * workflowSystemTask.execute(workflow, task, workflowExecutor)

        task.status == TaskModel.Status.IN_PROGRESS
        task.endTime == 0 // verify that endTime is not set
        task.pollCount == 1 // verify that poll count is NOT incremented
    }

}
