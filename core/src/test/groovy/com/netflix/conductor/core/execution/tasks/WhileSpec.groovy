/*
 * Copyright 2023 Conductor Authors.
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
import com.netflix.conductor.core.execution.WorkflowExecutor
import com.netflix.conductor.core.utils.ParametersUtils
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HTTP
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WHILE

class WhileSpec extends Specification {

    @Subject
    While whileTask

    WorkflowExecutor workflowExecutor
    ObjectMapper objectMapper
    ParametersUtils parametersUtils
    TaskModel whileTaskModel

    WorkflowTask task1, task2
    TaskModel taskModel1, taskModel2

    def setup() {
        objectMapper = new ObjectMapper();
        workflowExecutor = Mock(WorkflowExecutor.class)
        parametersUtils = new ParametersUtils(objectMapper)

        task1 = new WorkflowTask(name: 'task1', taskReferenceName: 'task1')
        task2 = new WorkflowTask(name: 'task2', taskReferenceName: 'task2')

        whileTask = new While(parametersUtils)
    }

    def "no iteration"() {
        given:
        WorkflowTask whileWorkflowTask = new WorkflowTask(taskReferenceName: 'whileTask', type: TASK_TYPE_WHILE)
        whileWorkflowTask.loopCondition = "if (\$.whileTask['iteration'] < 0) { true; } else { false; }"
        whileWorkflowTask.loopOver = [task1, task2]
        whileTaskModel = new TaskModel(workflowTask: whileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE, referenceTaskName: whileWorkflowTask.taskReferenceName)

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        workflowModel.tasks = [whileTaskModel]

        when:
        def retVal = whileTask.execute(workflowModel, whileTaskModel, workflowExecutor)

        then: "verify that return value is true, iteration value is updated in WHILE TaskModel"
        retVal

        and: "verify the iteration value"
        whileTaskModel.iteration == 0
        whileTaskModel.outputData['iteration'] == 0

        and: "verify whether the first task is not scheduled"
        0 * workflowExecutor.scheduleNextIteration(whileTaskModel, workflowModel)
    }

    def "first iteration"() {
        given:
        WorkflowTask whileWorkflowTask = new WorkflowTask(taskReferenceName: 'whileTask', type: TASK_TYPE_WHILE)
        whileWorkflowTask.loopCondition = "if (\$.whileTask['iteration'] < 1) { true; } else { false; }"
        whileWorkflowTask.loopOver = [task1, task2]
        whileTaskModel = new TaskModel(workflowTask: whileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE, referenceTaskName: whileWorkflowTask.taskReferenceName)

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        workflowModel.tasks = [whileTaskModel]

        when:
        def retVal = whileTask.execute(workflowModel, whileTaskModel, workflowExecutor)

        then: "verify that return value is true, iteration value is updated in WHILE TaskModel"
        retVal

        and: "verify the iteration value"
        whileTaskModel.iteration == 1
        whileTaskModel.outputData['iteration'] == 1

        and: "verify whether the first task is scheduled"
        1 * workflowExecutor.scheduleNextIteration(whileTaskModel, workflowModel)
    }

    def "an iteration - one task is complete and other is not scheduled"() {
        given: "WorkflowModel consists of one iteration of one task inside WHILE already completed"
        taskModel1 = createTaskModel(task1)

        and: "loop over contains two tasks"
        WorkflowTask whileWorkflowTask = new WorkflowTask(taskReferenceName: 'whileTask', type: TASK_TYPE_WHILE)
        whileWorkflowTask.loopCondition = "if (\$.whileTask['iteration'] < 2) { true; } else { false; }"
        whileWorkflowTask.loopOver = [task1, task2] // two tasks

        whileTaskModel = new TaskModel(workflowTask: whileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE, referenceTaskName: whileWorkflowTask.taskReferenceName)
        whileTaskModel.iteration = 1
        whileTaskModel.outputData['iteration'] = 1
        whileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [whileTaskModel, taskModel1]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData

        when:
        def retVal = whileTask.execute(workflowModel, whileTaskModel, workflowExecutor)

        then: "verify that the return value is false, since the iteration is not complete"
        !retVal

        and: "verify that the next iteration is NOT scheduled"
        0 * workflowExecutor.scheduleNextIteration(whileTaskModel, workflowModel)
    }

    def "next iteration - one iteration of all tasks inside WHILE are complete"() {
        given: "WorkflowModel consists of one iteration of tasks inside WHILE already completed"
        taskModel1 = createTaskModel(task1)
        taskModel2 = createTaskModel(task2)

        WorkflowTask whileWorkflowTask = new WorkflowTask(taskReferenceName: 'whileTask', type: TASK_TYPE_WHILE)
        whileWorkflowTask.loopCondition = "if (\$.whileTask['iteration'] < 2) { true; } else { false; }"
        whileWorkflowTask.loopOver = [task1, task2]

        whileTaskModel = new TaskModel(workflowTask: whileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE, referenceTaskName: whileWorkflowTask.taskReferenceName)
        whileTaskModel.iteration = 1
        whileTaskModel.outputData['iteration'] = 1
        whileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [whileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = taskModel2.outputData

        when:
        def retVal = whileTask.execute(workflowModel, whileTaskModel, workflowExecutor)

        then: "verify that the return value is true, since the iteration is updated"
        retVal

        and: "verify that the WHILE TaskModel is correct"
        whileTaskModel.iteration == 2
        whileTaskModel.outputData['iteration'] == 2
        whileTaskModel.outputData['1'] == iteration1OutputData
        whileTaskModel.status == TaskModel.Status.IN_PROGRESS

        and: "verify whether the first task in the next iteration is scheduled"
        1 * workflowExecutor.scheduleNextIteration(whileTaskModel, workflowModel)
    }

    def "next iteration - a task failed in the previous iteration"() {
        given: "WorkflowModel consists of one iteration of tasks one of which is FAILED"
        taskModel1 = createTaskModel(task1)

        taskModel2 = createTaskModel(task2, TaskModel.Status.FAILED)
        taskModel2.reasonForIncompletion = 'no specific reason, i am tired of success'

        WorkflowTask whileWorkflowTask = new WorkflowTask(taskReferenceName: 'whileTask', type: TASK_TYPE_WHILE)
        whileWorkflowTask.loopCondition = "if (\$.whileTask['iteration'] < 2) { true; } else { false; }"
        whileWorkflowTask.loopOver = [task1, task2]

        whileTaskModel = new TaskModel(workflowTask: whileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE, referenceTaskName: whileWorkflowTask.taskReferenceName)
        whileTaskModel.iteration = 1
        whileTaskModel.outputData['iteration'] = 1
        whileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [whileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = taskModel2.outputData

        when:
        def retVal = whileTask.execute(workflowModel, whileTaskModel, workflowExecutor)

        then: "verify that return value is true, status is updated"
        retVal

        and: "verify the status and reasonForIncompletion fields"
        whileTaskModel.iteration == 1
        whileTaskModel.outputData['iteration'] == 1
        whileTaskModel.outputData['1'] == iteration1OutputData
        whileTaskModel.status == TaskModel.Status.FAILED
        whileTaskModel.reasonForIncompletion && whileTaskModel.reasonForIncompletion.contains(taskModel2.reasonForIncompletion)

        and: "verify that next iteration is NOT scheduled"
        0 * workflowExecutor.scheduleNextIteration(whileTaskModel, workflowModel)
    }

    def "next iteration - a task is in progress in the previous iteration"() {
        given: "WorkflowModel consists of one iteration of tasks inside WHILE already completed"
        taskModel1 = createTaskModel(task1)
        taskModel2 = createTaskModel(task2, TaskModel.Status.IN_PROGRESS)
        taskModel2.outputData = [:] // no output data, task is in progress

        WorkflowTask whileWorkflowTask = new WorkflowTask(taskReferenceName: 'whileTask', type: TASK_TYPE_WHILE)
        whileWorkflowTask.loopCondition = "if (\$.whileTask['iteration'] < 2) { true; } else { false; }"
        whileWorkflowTask.loopOver = [task1, task2]

        whileTaskModel = new TaskModel(workflowTask: whileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE, referenceTaskName: whileWorkflowTask.taskReferenceName)
        whileTaskModel.iteration = 1
        whileTaskModel.outputData['iteration'] = 1
        whileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [whileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = [:]

        when:
        def retVal = whileTask.execute(workflowModel, whileTaskModel, workflowExecutor)

        then: "verify that return value is false, since the WHILE task model is not updated"
        !retVal

        and: "verify that WHILE task model is not modified"
        whileTaskModel.iteration == 1
        whileTaskModel.outputData['iteration'] == 1
        whileTaskModel.outputData['1'] == iteration1OutputData
        whileTaskModel.status == TaskModel.Status.IN_PROGRESS

        and: "verify that next iteration is NOT scheduled"
        0 * workflowExecutor.scheduleNextIteration(whileTaskModel, workflowModel)
    }

    def "final step - all iterations are complete and all tasks in them are successful"() {
        given: "WorkflowModel consists of one iteration of tasks inside WHILE already completed"
        taskModel1 = createTaskModel(task1)
        taskModel2 = createTaskModel(task2)

        WorkflowTask whileWorkflowTask = new WorkflowTask(taskReferenceName: 'whileTask', type: TASK_TYPE_WHILE)
        whileWorkflowTask.loopCondition = "if (\$.whileTask['iteration'] < 1) { true; } else { false; }"
        whileWorkflowTask.loopOver = [task1, task2]

        whileTaskModel = new TaskModel(workflowTask: whileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE, referenceTaskName: whileWorkflowTask.taskReferenceName)
        whileTaskModel.iteration = 1
        whileTaskModel.outputData['iteration'] = 1
        whileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [whileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = taskModel2.outputData

        when:
        def retVal = whileTask.execute(workflowModel, whileTaskModel, workflowExecutor)

        then: "verify that the return value is true, WHILE TaskModel is updated"
        retVal

        and: "verify the status and other fields are set correctly"
        whileTaskModel.iteration == 1
        whileTaskModel.outputData['iteration'] == 1
        whileTaskModel.outputData['1'] == iteration1OutputData
        whileTaskModel.status == TaskModel.Status.COMPLETED

        and: "verify that next iteration is not scheduled"
        0 * workflowExecutor.scheduleNextIteration(whileTaskModel, workflowModel)
    }

    def "next iteration - one iteration of all tasks inside WHILE are complete, but the condition is incorrect"() {
        given: "WorkflowModel consists of one iteration of tasks inside WHILE already completed"
        taskModel1 = createTaskModel(task1)
        taskModel2 = createTaskModel(task2)

        WorkflowTask whileWorkflowTask = new WorkflowTask(taskReferenceName: 'whileTask', type: TASK_TYPE_WHILE)
        // condition will produce a ScriptException
        whileWorkflowTask.loopCondition = "if (dollar_sign_goes_here.whileTask['iteration'] < 2) { true; } else { false; }"
        whileWorkflowTask.loopOver = [task1, task2]

        whileTaskModel = new TaskModel(workflowTask: whileWorkflowTask, taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE, referenceTaskName: whileWorkflowTask.taskReferenceName)
        whileTaskModel.iteration = 1
        whileTaskModel.outputData['iteration'] = 1
        whileTaskModel.status = TaskModel.Status.IN_PROGRESS

        def workflowModel = new WorkflowModel(workflowDefinition: new WorkflowDef(name: 'test_workflow'))
        // setup the WorkflowModel
        workflowModel.tasks = [whileTaskModel, taskModel1, taskModel2]

        // this is the expected format of iteration 1's output data
        def iteration1OutputData = [:]
        iteration1OutputData[task1.taskReferenceName] = taskModel1.outputData
        iteration1OutputData[task2.taskReferenceName] = taskModel2.outputData

        when:
        def retVal = whileTask.execute(workflowModel, whileTaskModel, workflowExecutor)

        then: "verify that the return value is true since WHILE TaskModel is updated"
        retVal

        and: "verify the status of WHILE TaskModel"
        whileTaskModel.iteration == 1
        whileTaskModel.outputData['iteration'] == 1
        whileTaskModel.outputData['1'] == iteration1OutputData
        whileTaskModel.status == TaskModel.Status.FAILED_WITH_TERMINAL_ERROR
        whileTaskModel.reasonForIncompletion != null

        and: "verify that next iteration is not scheduled"
        0 * workflowExecutor.scheduleNextIteration(whileTaskModel, workflowModel)
    }

    def "cancel sets the status as CANCELED"() {
        given:
        whileTaskModel = new TaskModel(taskId: UUID.randomUUID().toString(),
                taskType: TASK_TYPE_WHILE)
        whileTaskModel.iteration = 1
        whileTaskModel.outputData['iteration'] = 1
        whileTaskModel.status = TaskModel.Status.IN_PROGRESS

        when: "cancel is called with null for WorkflowModel and WorkflowExecutor"
        // null is used to note that those arguments are not intended to be used by this method
        whileTask.cancel(null, whileTaskModel, null)

        then:
        whileTaskModel.status == TaskModel.Status.CANCELED
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
