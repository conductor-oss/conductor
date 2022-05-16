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
package com.netflix.conductor.core.execution.tasks

import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.utils.TaskUtils
import com.netflix.conductor.core.exception.TerminateWorkflowException
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.core.utils.ParametersUtils
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_DO_WHILE
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HTTP

class DoWhileSpec extends Specification {

    @Subject
    DoWhile doWhile

    ParametersUtils parametersUtils
    WorkflowExecutor workflowExecutor
    TaskModel doWhileTaskModel

    WorkflowTask task1, task2
    TaskModel taskModel1, taskModel2

    def setup() {
        workflowExecutor = Mock(WorkflowExecutor.class)
        parametersUtils = new ParametersUtils(new ObjectMapper())

        task1 = new WorkflowTask(name: 'task1', taskReferenceName: 'task1')
        task2 = new WorkflowTask(name: 'task2', taskReferenceName: 'task2')

        doWhile = new DoWhile(parametersUtils)
    }

    def "first iteration"() {
        given:
        WorkflowTask doWhileWorkflowTask = new WorkflowTask(taskReferenceName: 'doWhileTask', type: TASK_TYPE_DO_WHILE)
        doWhileWorkflowTask.loopCondition = "if (\$.doWhileTask['iteration'] < 1) { true; } else { false; }"
        doWhileWorkflowTask.loopOver = [task1, task2]
        doWhileTaskModel = new TaskModel(workflowTask: doWhileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_DO_WHILE, referenceTaskName: doWhileWorkflowTask.taskReferenceName)

        def workflowModel = new WorkflowModel()
        workflowModel.tasks = [doWhileTaskModel]

        when:
        def retVal = doWhile.execute(workflowModel, doWhileTaskModel, workflowExecutor)

        then: "verify that return value is true, iteration value is updated in DO_WHILE TaskModel"
        retVal

        and: "verify the iteration value"
        doWhileTaskModel.iteration == 1
        doWhileTaskModel.outputData['iteration'] == 1

        and: "verify whether the first task is scheduled"
        1 * workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflowModel)
    }

    def "next iteration - one iteration of all tasks inside DO_WHILE are complete"() {
        given: "WorkflowModel consists of one iteration of tasks inside DO_WHILE already completed"
        taskModel1 = createTaskModel(task1)
        taskModel2 = createTaskModel(task2)

        WorkflowTask doWhileWorkflowTask = new WorkflowTask(taskReferenceName: 'doWhileTask', type: TASK_TYPE_DO_WHILE)
        doWhileWorkflowTask.loopCondition = "if (\$.doWhileTask['iteration'] < 2) { true; } else { false; }"
        doWhileWorkflowTask.loopOver = [task1, task2]

        doWhileTaskModel = new TaskModel(workflowTask: doWhileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_DO_WHILE, referenceTaskName: doWhileWorkflowTask.taskReferenceName)
        doWhileTaskModel.iteration = 1
        doWhileTaskModel.outputData['iteration'] = 1
        doWhileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [doWhileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = taskModel2.outputData

        when:
        def retVal = doWhile.execute(workflowModel, doWhileTaskModel, workflowExecutor)

        then: "verify that the return value is true, since the iteration is updated"
        retVal

        and: "verify that the DO_WHILE TaskModel is correct"
        doWhileTaskModel.iteration == 2
        doWhileTaskModel.outputData['iteration'] == 2
        doWhileTaskModel.outputData['1'] == iteration1OutputData
        doWhileTaskModel.status == TaskModel.Status.IN_PROGRESS

        and: "verify whether the first task in the next iteration is scheduled"
        1 * workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflowModel)

        and: "verify that WorkflowExecutor.getTaskDefinition throws TerminateWorkflowException, execute method is not impacted"
        1 * workflowExecutor.getTaskDefinition(doWhileTaskModel) >> { throw new TerminateWorkflowException("") }
    }

    def "next iteration - a task failed in the previous iteration"() {
        given: "WorkflowModel consists of one iteration of tasks one of which is FAILED"
        taskModel1 = createTaskModel(task1)

        taskModel2 = createTaskModel(task2, TaskModel.Status.FAILED)
        taskModel2.reasonForIncompletion = 'no specific reason, i am tired of success'

        WorkflowTask doWhileWorkflowTask = new WorkflowTask(taskReferenceName: 'doWhileTask', type: TASK_TYPE_DO_WHILE)
        doWhileWorkflowTask.loopCondition = "if (\$.doWhileTask['iteration'] < 2) { true; } else { false; }"
        doWhileWorkflowTask.loopOver = [task1, task2]

        doWhileTaskModel = new TaskModel(workflowTask: doWhileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_DO_WHILE, referenceTaskName: doWhileWorkflowTask.taskReferenceName)
        doWhileTaskModel.iteration = 1
        doWhileTaskModel.outputData['iteration'] = 1
        doWhileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [doWhileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = taskModel2.outputData

        when:
        def retVal = doWhile.execute(workflowModel, doWhileTaskModel, workflowExecutor)

        then: "verify that return value is true, status is updated"
        retVal

        and: "verify the status and reasonForIncompletion fields"
        doWhileTaskModel.iteration == 1
        doWhileTaskModel.outputData['iteration'] == 1
        doWhileTaskModel.outputData['1'] == iteration1OutputData
        doWhileTaskModel.status == TaskModel.Status.FAILED
        doWhileTaskModel.reasonForIncompletion && doWhileTaskModel.reasonForIncompletion.contains(taskModel2.reasonForIncompletion)

        and: "verify that next iteration is NOT scheduled"
        0 * workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflowModel)
    }

    def "next iteration - a task is in progress in the previous iteration"() {
        given: "WorkflowModel consists of one iteration of tasks inside DO_WHILE already completed"
        taskModel1 = createTaskModel(task1)
        taskModel2 = createTaskModel(task2, TaskModel.Status.IN_PROGRESS)
        taskModel2.outputData = [:] // no output data, task is in progress

        WorkflowTask doWhileWorkflowTask = new WorkflowTask(taskReferenceName: 'doWhileTask', type: TASK_TYPE_DO_WHILE)
        doWhileWorkflowTask.loopCondition = "if (\$.doWhileTask['iteration'] < 2) { true; } else { false; }"
        doWhileWorkflowTask.loopOver = [task1, task2]

        doWhileTaskModel = new TaskModel(workflowTask: doWhileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_DO_WHILE, referenceTaskName: doWhileWorkflowTask.taskReferenceName)
        doWhileTaskModel.iteration = 1
        doWhileTaskModel.outputData['iteration'] = 1
        doWhileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [doWhileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = [:]

        when:
        def retVal = doWhile.execute(workflowModel, doWhileTaskModel, workflowExecutor)

        then: "verify that return value is false, since the DO_WHILE task model is not updated"
        !retVal

        and: "verify that DO_WHILE task model is not modified"
        doWhileTaskModel.iteration == 1
        doWhileTaskModel.outputData['iteration'] == 1
        doWhileTaskModel.outputData['1'] == iteration1OutputData
        doWhileTaskModel.status == TaskModel.Status.IN_PROGRESS

        and: "verify that next iteration is NOT scheduled"
        0 * workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflowModel)
    }

    def "final step - all iterations are complete and all tasks in them are successful"() {
        given: "WorkflowModel consists of one iteration of tasks inside DO_WHILE already completed"
        taskModel1 = createTaskModel(task1)
        taskModel2 = createTaskModel(task2)

        WorkflowTask doWhileWorkflowTask = new WorkflowTask(taskReferenceName: 'doWhileTask', type: TASK_TYPE_DO_WHILE)
        doWhileWorkflowTask.loopCondition = "if (\$.doWhileTask['iteration'] < 1) { true; } else { false; }"
        doWhileWorkflowTask.loopOver = [task1, task2]

        doWhileTaskModel = new TaskModel(workflowTask: doWhileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_DO_WHILE, referenceTaskName: doWhileWorkflowTask.taskReferenceName)
        doWhileTaskModel.iteration = 1
        doWhileTaskModel.outputData['iteration'] = 1
        doWhileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [doWhileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = taskModel2.outputData

        when:
        def retVal = doWhile.execute(workflowModel, doWhileTaskModel, workflowExecutor)

        then: "verify that the return value is true, DO_WHILE TaskModel is updated"
        retVal

        and: "verify the status and other fields are set correctly"
        doWhileTaskModel.iteration == 1
        doWhileTaskModel.outputData['iteration'] == 1
        doWhileTaskModel.outputData['1'] == iteration1OutputData
        doWhileTaskModel.status == TaskModel.Status.COMPLETED

        and: "verify that next iteration is not scheduled"
        0 * workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflowModel)
    }

    def "next iteration - one iteration of all tasks inside DO_WHILE are complete, but the condition is incorrect"() {
        given: "WorkflowModel consists of one iteration of tasks inside DO_WHILE already completed"
        taskModel1 = createTaskModel(task1)
        taskModel2 = createTaskModel(task2)

        WorkflowTask doWhileWorkflowTask = new WorkflowTask(taskReferenceName: 'doWhileTask', type: TASK_TYPE_DO_WHILE)
        // condition will produce a ScriptException
        doWhileWorkflowTask.loopCondition = "if (dollar_sign_goes_here.doWhileTask['iteration'] < 2) { true; } else { false; }"
        doWhileWorkflowTask.loopOver = [task1, task2]

        doWhileTaskModel = new TaskModel(workflowTask: doWhileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_DO_WHILE, referenceTaskName: doWhileWorkflowTask.taskReferenceName)
        doWhileTaskModel.iteration = 1
        doWhileTaskModel.outputData['iteration'] = 1
        doWhileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [doWhileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = taskModel2.outputData

        when:
        def retVal = doWhile.execute(workflowModel, doWhileTaskModel, workflowExecutor)

        then: "verify that the return value is true since DO_WHILE TaskModel is updated"
        retVal

        and: "verify the status of DO_WHILE TaskModel"
        doWhileTaskModel.iteration == 1
        doWhileTaskModel.outputData['iteration'] == 1
        doWhileTaskModel.outputData['1'] == iteration1OutputData
        doWhileTaskModel.status == TaskModel.Status.FAILED_WITH_TERMINAL_ERROR
        doWhileTaskModel.reasonForIncompletion != null

        and: "verify that next iteration is not scheduled"
        0 * workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflowModel)
    }

    def "cancel sets the status as CANCELED"() {
        given:
        doWhileTaskModel = new TaskModel(taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_DO_WHILE)
        doWhileTaskModel.iteration = 1
        doWhileTaskModel.outputData['iteration'] = 1
        doWhileTaskModel.status = TaskModel.Status.IN_PROGRESS

        when: "cancel is called with null for WorkflowModel and WorkflowExecutor"
        // null is used to note that those arguments are not intended to be used by this method
        doWhile.cancel(null, doWhileTaskModel, null)

        then:
        doWhileTaskModel.status == TaskModel.Status.CANCELED
    }

    private static createTaskModel(WorkflowTask workflowTask, TaskModel.Status status = TaskModel.Status.COMPLETED, int iteration = 1) {
        TaskModel taskModel1 = new TaskModel(workflowTask: workflowTask, taskType: TASK_TYPE_HTTP)

        taskModel1.status = status
        taskModel1.outputData = ['k1': 'v1']
        taskModel1.iteration = iteration
        taskModel1.referenceTaskName = TaskUtils.appendIteration(workflowTask.taskReferenceName, iteration)

        return taskModel1
    }
}
