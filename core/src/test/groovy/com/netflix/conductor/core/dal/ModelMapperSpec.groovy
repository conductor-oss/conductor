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
package com.netflix.conductor.core.dal

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.common.utils.ExternalPayloadStorage
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel

import spock.lang.Specification
import spock.lang.Subject

class ModelMapperSpec extends Specification {

    ExternalPayloadStorageUtils externalPayloadStorageUtils
    WorkflowModel workflowModel
    TaskModel taskModel1, taskModel2

    @Subject
    ModelMapper modelMapper

    def setup() {
        taskModel1 = new TaskModel(taskId: 'taskId1', status: TaskModel.Status.SCHEDULED, inputData: ['key1': 'value1'], startTime: 10L)
        taskModel2 = new TaskModel(taskId: 'taskId2', status: TaskModel.Status.IN_PROGRESS, externalInputPayloadStoragePath: '/relative/task/path')

        workflowModel = new WorkflowModel(workflowId: 'workflowId', status: WorkflowModel.Status.COMPLETED, input: ['app': 'conductor'], endTime: 100L, tasks: [taskModel1, taskModel2], externalOutputPayloadStoragePath: '/relative/workflow/path', workflowDefinition: new WorkflowDef(name: 'test_workflow'))

        externalPayloadStorageUtils = Mock(ExternalPayloadStorageUtils)
        modelMapper = new ModelMapper(externalPayloadStorageUtils)
    }

    def "get workflow from workflow model"() {
        when:
        Workflow workflow = modelMapper.getWorkflow(workflowModel)

        then:
        2 * externalPayloadStorageUtils.verifyAndUpload(_ as WorkflowModel, _ as ExternalPayloadStorage.PayloadType)
        4 * externalPayloadStorageUtils.verifyAndUpload(_ as TaskModel, _ as ExternalPayloadStorage.PayloadType)
        with(workflow) {
            workflowId == 'workflowId'
            status == Workflow.WorkflowStatus.COMPLETED
            input == ['app': 'conductor']
            externalOutputPayloadStoragePath == '/relative/workflow/path'
            endTime == 100L
            tasks.size() == 2
            tasks[0].taskId == 'taskId1'
            tasks[0].status == Task.Status.SCHEDULED
            tasks[0].inputData == ['key1': 'value1']
            tasks[1].taskId == 'taskId2'
            tasks[1].status == Task.Status.IN_PROGRESS
            tasks[1].externalInputPayloadStoragePath == '/relative/task/path'
        }
    }

    def "get full copy of workflow model"() {
        when:
        WorkflowModel fullWorkflowModel = modelMapper.getFullCopy(workflowModel)

        then:
        1 * externalPayloadStorageUtils.downloadPayload("/relative/workflow/path") >> ['wk': 'wv']
        1 * externalPayloadStorageUtils.downloadPayload("/relative/task/path") >> ['tk': 'tv']
        with(fullWorkflowModel) {
            workflowId == 'workflowId'
            status == WorkflowModel.Status.COMPLETED
            input == ['app': 'conductor']
            !externalOutputPayloadStoragePath
            output == ['wk': 'wv']
            endTime == 100L
            tasks.size() == 2
            tasks[0].taskId == 'taskId1'
            tasks[0].status == TaskModel.Status.SCHEDULED
            tasks[0].inputData == ['key1': 'value1']
            tasks[1].taskId == 'taskId2'
            tasks[1].status == TaskModel.Status.IN_PROGRESS
            !tasks[1].externalInputPayloadStoragePath
            tasks[1].inputData == ['tk': 'tv']
        }
    }

    def "get lean copy of workflow model"() {
        when:
        WorkflowModel leanWorkflowModel = modelMapper.getLeanCopy(workflowModel)

        then:
        2 * externalPayloadStorageUtils.verifyAndUpload(_ as WorkflowModel, _ as ExternalPayloadStorage.PayloadType)
        4 * externalPayloadStorageUtils.verifyAndUpload(_ as TaskModel, _ as ExternalPayloadStorage.PayloadType)
        with(leanWorkflowModel) {
            workflowId == 'workflowId'
            status == WorkflowModel.Status.COMPLETED
            input == ['app': 'conductor']
            externalOutputPayloadStoragePath == '/relative/workflow/path'
            endTime == 100L
            tasks.size() == 2
            tasks[0].taskId == 'taskId1'
            tasks[0].status == TaskModel.Status.SCHEDULED
            tasks[0].inputData == ['key1': 'value1']
            tasks[1].taskId == 'taskId2'
            tasks[1].status == TaskModel.Status.IN_PROGRESS
            tasks[1].externalInputPayloadStoragePath == '/relative/task/path'
        }
    }

    def "get task from task model"() {
        when:
        Task task = modelMapper.getTask(taskModel1)

        then:
        2 * externalPayloadStorageUtils.verifyAndUpload(_ as TaskModel, _ as ExternalPayloadStorage.PayloadType)
        with(task) {
            taskId == 'taskId1'
            status == Task.Status.SCHEDULED
            inputData == ['key1': 'value1']
            startTime == 10L
        }
    }

    def "get full copy of task"() {
        when:
        TaskModel fullTaskModel = modelMapper.getFullCopy(taskModel2)

        then:
        1 * externalPayloadStorageUtils.downloadPayload("/relative/task/path") >> ['k': 'v']
        with(fullTaskModel) {
            taskId == 'taskId2'
            status == TaskModel.Status.IN_PROGRESS
            !externalInputPayloadStoragePath
            inputData == ['k': 'v']
        }
    }

    def "get lean copy of task model"() {
        when:
        TaskModel leanTaskModel = modelMapper.getLeanCopy(taskModel2)

        then:
        2 * externalPayloadStorageUtils.verifyAndUpload(_ as TaskModel, _ as ExternalPayloadStorage.PayloadType)
        with(leanTaskModel) {
            taskId == 'taskId2'
            status == TaskModel.Status.IN_PROGRESS
            externalInputPayloadStoragePath == '/relative/task/path'
        }
    }

    def "map task model status to task status"() {
        expect:
        taskStatus == modelMapper.mapToTaskStatus(taskModelStatus)

        where:
        taskModelStatus                             || taskStatus
        TaskModel.Status.IN_PROGRESS                || Task.Status.IN_PROGRESS
        TaskModel.Status.CANCELED                   || Task.Status.CANCELED
        TaskModel.Status.FAILED                     || Task.Status.FAILED
        TaskModel.Status.FAILED_WITH_TERMINAL_ERROR || Task.Status.FAILED_WITH_TERMINAL_ERROR
        TaskModel.Status.COMPLETED                  || Task.Status.COMPLETED
        TaskModel.Status.COMPLETED_WITH_ERRORS      || Task.Status.COMPLETED_WITH_ERRORS
        TaskModel.Status.SCHEDULED                  || Task.Status.SCHEDULED
        TaskModel.Status.TIMED_OUT                  || Task.Status.TIMED_OUT
        TaskModel.Status.SKIPPED                    || Task.Status.SKIPPED
    }
}
