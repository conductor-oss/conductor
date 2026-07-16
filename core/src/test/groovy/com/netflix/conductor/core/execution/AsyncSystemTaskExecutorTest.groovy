/*
 * Copyright 2022 Conductor Authors.
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
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.core.config.ConductorProperties
import com.netflix.conductor.core.dal.ExecutionDAOFacade
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask
import com.netflix.conductor.core.utils.IDGenerator
import com.netflix.conductor.core.utils.ParametersUtils
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
    ParametersUtils parametersUtils

    @Subject
    AsyncSystemTaskExecutor executor

    WorkflowSystemTask workflowSystemTask
    ConductorProperties properties = new ConductorProperties()

    def setup() {
        executionDAOFacade = Mock(ExecutionDAOFacade.class)
        queueDAO = Mock(QueueDAO.class)
        metadataDAO = Mock(MetadataDAO.class)
        workflowExecutor = Mock(WorkflowExecutor.class)
        parametersUtils = Mock(ParametersUtils.class)

        workflowSystemTask = Mock(WorkflowSystemTask.class) {
            isTaskRetrievalRequired() >> true
        }

        properties.taskExecutionPostponeDuration = Duration.ofSeconds(1)
        properties.systemTaskWorkerCallbackDuration = Duration.ofSeconds(1)

        parametersUtils.substituteSecrets(_) >> { args -> args[0] }

        executor = new AsyncSystemTaskExecutor(executionDAOFacade, queueDAO, metadataDAO, properties, workflowExecutor, parametersUtils)
    }

    // this is not strictly a unit test, but its essential to test AsyncSystemTaskExecutor with SubWorkflow
    def "Execute SubWorkflow task"() {
        given:
        String workflowId = "workflowId"
        IDGenerator idGenerator = new IDGenerator()
        String parentTaskId = idGenerator.generate()
        String subWorkflowId = idGenerator.generateSubWorkflowId(workflowId, parentTaskId, 0)
        SubWorkflow subWorkflowTask = new SubWorkflow(new ObjectMapper(), idGenerator)

        TaskModel task1 = new TaskModel()
        task1.setTaskType(SUB_WORKFLOW.name())
        task1.setReferenceTaskName("waitTask")
        task1.setWorkflowInstanceId(workflowId)
        task1.setScheduledTime(System.currentTimeMillis())
        task1.setTaskId(parentTaskId)
        task1.getInputData().put("asyncComplete", true)
        task1.getInputData().put("subWorkflowName", "junit1")
        task1.getInputData().put("subWorkflowVersion", 1)
        task1.setStatus(TaskModel.Status.SCHEDULED)

        String queueName = QueueUtils.getQueueName(task1)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)
        WorkflowModel subWorkflow = new WorkflowModel(workflowId: subWorkflowId, status: WorkflowModel.Status.RUNNING)

        when:
        executor.execute(subWorkflowTask, parentTaskId)

        then:
        1 * executionDAOFacade.getTaskModel(parentTaskId) >> task1
        1 * executionDAOFacade.getWorkflowModel(workflowId, subWorkflowTask.isTaskRetrievalRequired()) >> workflow
        1 * workflowExecutor.startWorkflowIdempotent(*_) >> subWorkflow

        // SUB_WORKFLOW is asyncComplete so its removed from the queue
        1 * queueDAO.remove(queueName, parentTaskId)

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
        // second load is the late-update guard re-reading the stored task before
        // persisting a terminal result; stored copy is still non-terminal here
        2 * executionDAOFacade.getTaskModel(taskId) >>> [task, new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId)]
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * executionDAOFacade.updateTask(task)
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
        // second load is the late-update guard re-reading the stored task before
        // persisting the terminal result; stored copy is still non-terminal for
        // the winning attempt, so the update persists
        2 * executionDAOFacade.getTaskModel(taskId) >>> [task, new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId)]
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

    // ── Issue #1321: extend queue unack window to cover a blocking system task ──
    //
    // AsyncSystemTaskExecutor runs systemTask.start() synchronously while the
    // task row stays SCHEDULED and the queue message's visibility is never
    // extended. If start() blocks longer than the QueueDAO's unack/redelivery
    // window, the message is redelivered and a second worker executes the same
    // task in parallel (duplicate paid LLM_CHAT_COMPLETE calls). The fix must
    // call queueDAO.setUnackTimeout(queueName, taskId, 1000L * responseTimeoutSeconds)
    // BEFORE start() when the TaskDef has responseTimeoutSeconds > 0.

    def "SCHEDULED system task with responseTimeoutSeconds > 0 extends queue unack window before start"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        long responseTimeoutSeconds = 60L

        // TaskDef carrying the response timeout; surfaced both via the task's own
        // WorkflowTask.taskDefinition (task.getTaskDefinition()) and via metadataDAO
        // so the assertion holds regardless of which source the fix reads from.
        TaskDef taskDef = new TaskDef()
        taskDef.setName("taskDefName")
        taskDef.setResponseTimeoutSeconds(responseTimeoutSeconds)
        WorkflowTask workflowTask = new WorkflowTask()
        workflowTask.setName("taskDefName")
        workflowTask.setTaskDefinition(taskDef)

        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                taskDefName: "taskDefName", workflowPriority: 10)
        task.setWorkflowTask(workflowTask)

        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)
        String queueName = QueueUtils.getQueueName(task)
        workflowSystemTask.getEvaluationOffset(_, _) >> Optional.empty()
        metadataDAO.getTaskDef("taskDefName") >> taskDef

        when:
        executor.execute(workflowSystemTask, taskId)

        then: "task and workflow are loaded and the unack window is extended before start"
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * queueDAO.setUnackTimeout(queueName, taskId, 1000L * responseTimeoutSeconds)

        then: "only after the unack extension is the (blocking) start invoked"
        1 * workflowSystemTask.start(workflow, task, workflowExecutor) >> { task.status = TaskModel.Status.IN_PROGRESS }
        1 * executionDAOFacade.updateTask(task)
    }

    def "SCHEDULED system task without a task def does not extend the queue unack window"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        // No WorkflowTask/TaskDef set and metadataDAO returns null -> no responseTimeoutSeconds.
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                taskDefName: "taskDefName", workflowPriority: 10)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)
        String queueName = QueueUtils.getQueueName(task)
        workflowSystemTask.getEvaluationOffset(_, _) >> Optional.empty()
        metadataDAO.getTaskDef("taskDefName") >> null

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * workflowSystemTask.start(workflow, task, workflowExecutor) >> { task.status = TaskModel.Status.IN_PROGRESS }
        1 * executionDAOFacade.updateTask(task)

        // Behavior unchanged when there is no task def / responseTimeoutSeconds == 0
        0 * queueDAO.setUnackTimeout(*_)
    }

    // ── Issue #1322: a late duplicate attempt must not overwrite a terminal task ──
    //
    // When a duplicate attempt (queue redelivery during a long-running start(),
    // worker pause, GC stall) finishes after another attempt already completed the
    // task, its update would overwrite the outputData/endTime the workflow already
    // consumed. Before persisting a terminal result, the executor re-reads the
    // stored task and drops this attempt's update if the stored record is already
    // terminal.

    def "late completion of a duplicate attempt does not overwrite an already terminal task"() {
        given:
        String workflowId = "workflowId"
        String taskId = "taskId"
        TaskModel task = new TaskModel(taskType: "type1", status: TaskModel.Status.SCHEDULED, taskId: taskId, workflowInstanceId: workflowId,
                taskDefName: "taskDefName", workflowPriority: 10)
        // what the winning attempt already persisted while this attempt was still executing
        TaskModel storedTerminal = new TaskModel(taskType: "type1", status: TaskModel.Status.COMPLETED, taskId: taskId, workflowInstanceId: workflowId,
                taskDefName: "taskDefName", workflowPriority: 10)
        WorkflowModel workflow = new WorkflowModel(workflowId: workflowId, status: WorkflowModel.Status.RUNNING)
        String queueName = QueueUtils.getQueueName(task)

        when:
        executor.execute(workflowSystemTask, taskId)

        then:
        2 * executionDAOFacade.getTaskModel(taskId) >>> [task, storedTerminal]
        1 * executionDAOFacade.getWorkflowModel(workflowId, true) >> workflow
        1 * workflowSystemTask.start(workflow, task, workflowExecutor) >> { task.status = TaskModel.Status.COMPLETED }

        // the late terminal update is dropped: nothing persisted, no re-decide
        0 * executionDAOFacade.updateTask(_)
        0 * workflowExecutor.decide(_)
        // the stale queue message is still cleaned up
        1 * queueDAO.remove(queueName, taskId)
    }

    def "secrets are substituted for execution then input restored"() {
        given:
        String workflowId = "wfid"
        String taskId = "taskid"
        WorkflowSystemTask systemTask = Mock(WorkflowSystemTask.class) {
            getTaskType() >> "HTTP"
            isAsyncComplete(_) >> false
        }
        TaskModel task = new TaskModel()
        task.setTaskId(taskId)
        task.setTaskType("HTTP")
        task.setStatus(TaskModel.Status.SCHEDULED)
        task.setWorkflowInstanceId(workflowId)
        def literal = [pwd: '${workflow.secrets.DB_PASSWORD}'] as Map
        task.setInputData(literal)

        WorkflowModel workflow = new WorkflowModel()
        workflow.setStatus(WorkflowModel.Status.RUNNING)

        def resolved = [pwd: 's3cr3t'] as Map
        Map seenDuringStart = null

        when:
        executor.execute(systemTask, taskId)

        then:
        1 * executionDAOFacade.getTaskModel(taskId) >> task
        1 * executionDAOFacade.getWorkflowModel(workflowId, _) >> workflow
        1 * parametersUtils.substituteSecrets(literal) >> resolved
        1 * systemTask.start(workflow, task, _) >> { args ->
            seenDuringStart = (args[1] as TaskModel).getInputData()
        }
        // during start the task saw resolved input...
        seenDuringStart == resolved
        // ...and afterwards the literal is restored for persistence
        task.getInputData() == literal
    }

}
