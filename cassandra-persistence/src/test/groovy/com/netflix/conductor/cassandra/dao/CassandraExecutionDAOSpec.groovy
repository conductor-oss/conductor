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
package com.netflix.conductor.cassandra.dao

import com.netflix.conductor.common.metadata.events.EventExecution
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.core.exception.ApplicationException
import com.netflix.conductor.core.utils.IDGenerator
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel

import spock.lang.Subject

import static com.netflix.conductor.common.metadata.events.EventExecution.Status.COMPLETED
import static com.netflix.conductor.core.exception.ApplicationException.Code.INVALID_INPUT

class CassandraExecutionDAOSpec extends CassandraSpec {

    @Subject
    CassandraExecutionDAO executionDAO

    def setup() {
        executionDAO = new CassandraExecutionDAO(session, objectMapper, cassandraProperties, statements)
    }

    def "verify if tasks are validated"() {
        given:
        def tasks = []

        // create tasks for a workflow and add to list
        TaskModel task1 = new TaskModel(workflowInstanceId: 'uuid', taskId: 'task1id', referenceTaskName: 'task1')
        TaskModel task2 = new TaskModel(workflowInstanceId: 'uuid', taskId: 'task2id', referenceTaskName: 'task2')
        tasks << task1 << task2

        when:
        executionDAO.validateTasks(tasks)

        then:
        noExceptionThrown()

        and:
        // add a task from a different workflow to the list
        TaskModel task3 = new TaskModel(workflowInstanceId: 'other-uuid', taskId: 'task3id', referenceTaskName: 'task3')
        tasks << task3

        when:
        executionDAO.validateTasks(tasks)

        then:
        def ex = thrown(ApplicationException.class)
        ex.message == "Tasks of multiple workflows cannot be created/updated simultaneously"
    }

    def "workflow CRUD"() {
        given:
        String workflowId = IDGenerator.generate()
        WorkflowDef workflowDef = new WorkflowDef()
        workflowDef.name = "def1"
        workflowDef.setVersion(1)
        WorkflowModel workflow = new WorkflowModel()
        workflow.setWorkflowDefinition(workflowDef)
        workflow.setWorkflowId(workflowId)
        workflow.setInput(new HashMap<>())
        workflow.setStatus(WorkflowModel.Status.RUNNING)
        workflow.setCreateTime(System.currentTimeMillis())

        when:
        // create a new workflow in the datastore
        String id = executionDAO.createWorkflow(workflow)

        then:
        workflowId == id

        when:
        // read the workflow from the datastore
        WorkflowModel found = executionDAO.getWorkflow(workflowId)

        then:
        workflow == found

        and:
        // update the workflow
        workflow.setStatus(WorkflowModel.Status.COMPLETED)
        executionDAO.updateWorkflow(workflow)

        when:
        found = executionDAO.getWorkflow(workflowId)

        then:
        workflow == found

        when:
        // remove the workflow from datastore
        boolean removed = executionDAO.removeWorkflow(workflowId)

        then:
        removed

        when:
        // read workflow again
        workflow = executionDAO.getWorkflow(workflowId, true)

        then:
        workflow == null
    }

    def "create tasks and verify methods that read tasks and workflow"() {
        given: 'we create a workflow'
        String workflowId = IDGenerator.generate()
        WorkflowDef workflowDef = new WorkflowDef(name: 'def1', version: 1)
        WorkflowModel workflow = new WorkflowModel(workflowDefinition: workflowDef, workflowId: workflowId, input: new HashMap(), status: WorkflowModel.Status.RUNNING, createTime: System.currentTimeMillis())
        executionDAO.createWorkflow(workflow)

        and: 'create tasks for this workflow'
        TaskModel task1 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task1', referenceTaskName: 'task1', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())
        TaskModel task2 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task2', referenceTaskName: 'task2', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())
        TaskModel task3 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task3', referenceTaskName: 'task3', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())

        def taskList = [task1, task2, task3]

        when: 'add the tasks to the datastore'
        List<TaskModel> tasks = executionDAO.createTasks(taskList)

        then:
        tasks != null
        taskList == tasks

        when: 'read the tasks from the datastore'
        def retTask1 = executionDAO.getTask(task1.taskId)
        def retTask2 = executionDAO.getTask(task2.taskId)
        def retTask3 = executionDAO.getTask(task3.taskId)

        then:
        task1 == retTask1
        task2 == retTask2
        task3 == retTask3

        when: 'lookup workflowId for the task'
        def foundId1 = executionDAO.lookupWorkflowIdFromTaskId(task1.taskId)
        def foundId2 = executionDAO.lookupWorkflowIdFromTaskId(task2.taskId)
        def foundId3 = executionDAO.lookupWorkflowIdFromTaskId(task3.taskId)

        then:
        foundId1 == workflowId
        foundId2 == workflowId
        foundId3 == workflowId

        when: 'check the metadata'
        def workflowMetadata = executionDAO.getWorkflowMetadata(workflowId)

        then:
        workflowMetadata.totalTasks == 3
        workflowMetadata.totalPartitions == 1

        when: 'check the getTasks api'
        def fetchedTasks = executionDAO.getTasks([task1.taskId, task2.taskId, task3.taskId])

        then:
        fetchedTasks != null && fetchedTasks.size() == 3

        when: 'get the tasks for the workflow'
        fetchedTasks = executionDAO.getTasksForWorkflow(workflowId)

        then:
        fetchedTasks != null && fetchedTasks.size() == 3

        when: 'read workflow with tasks'
        WorkflowModel found = executionDAO.getWorkflow(workflowId, true)

        then:
        found != null
        workflow.workflowId == found.workflowId
        found.tasks != null && found.tasks.size() == 3
        found.getTaskByRefName('task1') == task1
        found.getTaskByRefName('task2') == task2
        found.getTaskByRefName('task3') == task3
    }

    def "verify tasks are updated"() {
        given: 'we create a workflow'
        String workflowId = IDGenerator.generate()
        WorkflowDef workflowDef = new WorkflowDef(name: 'def1', version: 1)
        WorkflowModel workflow = new WorkflowModel(workflowDefinition: workflowDef, workflowId: workflowId, input: new HashMap(), status: WorkflowModel.Status.RUNNING, createTime: System.currentTimeMillis())
        executionDAO.createWorkflow(workflow)

        and: 'create tasks for this workflow'
        TaskModel task1 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task1', referenceTaskName: 'task1', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())
        TaskModel task2 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task2', referenceTaskName: 'task2', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())
        TaskModel task3 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task3', referenceTaskName: 'task3', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())

        and: 'add the tasks to the datastore'
        executionDAO.createTasks([task1, task2, task3])

        and: 'change the status of those tasks'
        task1.setStatus(TaskModel.Status.IN_PROGRESS)
        task2.setStatus(TaskModel.Status.COMPLETED)
        task3.setStatus(TaskModel.Status.FAILED)

        when: 'update the tasks'
        executionDAO.updateTask(task1)
        executionDAO.updateTask(task2)
        executionDAO.updateTask(task3)

        then:
        executionDAO.getTask(task1.taskId).status == TaskModel.Status.IN_PROGRESS
        executionDAO.getTask(task2.taskId).status == TaskModel.Status.COMPLETED
        executionDAO.getTask(task3.taskId).status == TaskModel.Status.FAILED

        when: 'get pending tasks for the workflow'
        List<TaskModel> pendingTasks = executionDAO.getPendingTasksByWorkflow(task1.getTaskType(), workflowId)

        then:
        pendingTasks != null && pendingTasks.size() == 1
        pendingTasks[0] == task1
    }

    def "verify tasks are removed"() {
        given: 'we create a workflow'
        String workflowId = IDGenerator.generate()
        WorkflowDef workflowDef = new WorkflowDef(name: 'def1', version: 1)
        WorkflowModel workflow = new WorkflowModel(workflowDefinition: workflowDef, workflowId: workflowId, input: new HashMap(), status: WorkflowModel.Status.RUNNING, createTime: System.currentTimeMillis())
        executionDAO.createWorkflow(workflow)

        and: 'create tasks for this workflow'
        TaskModel task1 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task1', referenceTaskName: 'task1', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())
        TaskModel task2 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task2', referenceTaskName: 'task2', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())
        TaskModel task3 = new TaskModel(workflowInstanceId: workflowId, taskType: 'task3', referenceTaskName: 'task3', status: TaskModel.Status.SCHEDULED, taskId: IDGenerator.generate())

        and: 'add the tasks to the datastore'
        executionDAO.createTasks([task1, task2, task3])

        when:
        boolean removed = executionDAO.removeTask(task3.getTaskId())

        then:
        removed
        def workflowMetadata = executionDAO.getWorkflowMetadata(workflowId)
        workflowMetadata.totalTasks == 2
        workflowMetadata.totalPartitions == 1

        when: 'read workflow with tasks again'
        def found = executionDAO.getWorkflow(workflowId)

        then:
        found != null
        found.workflowId == workflowId
        found.tasks.size() == 2
        found.getTaskByRefName('task1') == task1
        found.getTaskByRefName('task2') == task2

        and: 'read workflowId for the deleted task id'
        executionDAO.lookupWorkflowIdFromTaskId(task3.taskId) == null

        and: 'try to read removed task'
        executionDAO.getTask(task3.getTaskId()) == null

        when: 'remove the workflow'
        removed = executionDAO.removeWorkflow(workflowId)

        then: 'check task_lookup table'
        removed
        executionDAO.lookupWorkflowIdFromTaskId(task1.taskId) == null
        executionDAO.lookupWorkflowIdFromTaskId(task2.taskId) == null
    }

    def "CRUD on task def limit"() {
        given:
        String taskDefName = "test_task_def"
        String taskId = IDGenerator.generate()

        TaskDef taskDef = new TaskDef(concurrentExecLimit: 1)
        WorkflowTask workflowTask = new WorkflowTask(taskDefinition: taskDef)
        workflowTask.setTaskDefinition(taskDef)

        TaskModel task = new TaskModel()
        task.taskDefName = taskDefName
        task.taskId = taskId
        task.workflowInstanceId = IDGenerator.generate()
        task.setWorkflowTask(workflowTask)
        task.setTaskType("test_task")
        task.setWorkflowType("test_workflow")
        task.setStatus(TaskModel.Status.SCHEDULED)

        TaskModel newTask = new TaskModel()
        newTask.setTaskDefName(taskDefName)
        newTask.setTaskId(IDGenerator.generate())
        newTask.setWorkflowInstanceId(IDGenerator.generate())
        newTask.setWorkflowTask(workflowTask)
        newTask.setTaskType("test_task")
        newTask.setWorkflowType("test_workflow")
        newTask.setStatus(TaskModel.Status.SCHEDULED)

        when: // no tasks are IN_PROGRESS
        executionDAO.addTaskToLimit(task)

        then:
        !executionDAO.exceedsLimit(task)

        when: // set a task to IN_PROGRESS
        task.setStatus(TaskModel.Status.IN_PROGRESS)
        executionDAO.addTaskToLimit(task)

        then: // same task is checked
        !executionDAO.exceedsLimit(task)

        and: // check if new task can be added
        executionDAO.exceedsLimit(newTask)

        when: // set IN_PROGRESS task to COMPLETED
        task.setStatus(TaskModel.Status.COMPLETED)
        executionDAO.removeTaskFromLimit(task)

        then: // check new task again
        !executionDAO.exceedsLimit(newTask)

        when: // set new task to IN_PROGRESS
        newTask.setStatus(TaskModel.Status.IN_PROGRESS)
        executionDAO.addTaskToLimit(newTask)

        then: // check new task again
        !executionDAO.exceedsLimit(newTask)
    }

    def "verify if invalid identifiers throw correct exceptions"() {
        when: 'verify that a non-conforming uuid throws an exception'
        executionDAO.getTask('invalid_id')

        then:
        def ex = thrown(ApplicationException.class)
        ex && ex.code == INVALID_INPUT

        when: 'verify that a non-conforming uuid throws an exception'
        executionDAO.getWorkflow('invalid_id', true)

        then:
        ex = thrown(ApplicationException.class)
        ex && ex.code == INVALID_INPUT

        and: 'verify that a non-existing generated id returns null'
        executionDAO.getTask(IDGenerator.generate()) == null
        executionDAO.getWorkflow(IDGenerator.generate(), true) == null
    }

    def "CRUD on event execution"() throws Exception {
        given:
        String event = "test-event"
        String executionId1 = "id_1"
        String messageId1 = "message1"
        String eventHandler1 = "test_eh_1"
        EventExecution eventExecution1 = getEventExecution(executionId1, messageId1, eventHandler1, event)

        when: // create event execution explicitly
        executionDAO.addEventExecution(eventExecution1)
        List<EventExecution> eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1)

        then: // fetch executions
        eventExecutionList != null && eventExecutionList.size() == 1
        eventExecutionList[0] == eventExecution1

        when: // add a different execution for same message
        String executionId2 = "id_2"
        EventExecution eventExecution2 = getEventExecution(executionId2, messageId1, eventHandler1, event)
        executionDAO.addEventExecution(eventExecution2)
        eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1)

        then: // fetch executions
        eventExecutionList != null && eventExecutionList.size() == 2
        eventExecutionList[0] == eventExecution1
        eventExecutionList[1] == eventExecution2

        when: // update the second execution
        eventExecution2.setStatus(COMPLETED)
        executionDAO.updateEventExecution(eventExecution2)
        eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1)

        then: // fetch executions
        eventExecutionList != null && eventExecutionList.size() == 2
        eventExecutionList[1].status == COMPLETED

        when: // sleep for 5 seconds (TTL)
        Thread.sleep(5000L)
        eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1)

        then:
        eventExecutionList != null && eventExecutionList.size() == 1

        when: // delete event execution
        executionDAO.removeEventExecution(eventExecution1)
        eventExecutionList = executionDAO.getEventExecutions(eventHandler1, event, messageId1)

        then:
        eventExecutionList != null && eventExecutionList.empty
    }

    private static EventExecution getEventExecution(String id, String msgId, String name, String event) {
        EventExecution eventExecution = new EventExecution(id, msgId);
        eventExecution.setName(name);
        eventExecution.setEvent(event);
        eventExecution.setStatus(EventExecution.Status.IN_PROGRESS);
        return eventExecution;
    }
}
