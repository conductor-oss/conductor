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
import com.netflix.conductor.core.events.EventQueues
import com.netflix.conductor.core.events.queue.Message
import com.netflix.conductor.core.events.queue.ObservableQueue
import com.netflix.conductor.core.exception.ApplicationException
import com.netflix.conductor.core.utils.ParametersUtils
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification
import spock.lang.Subject

class EventSpec extends Specification {

    EventQueues eventQueues
    ParametersUtils parametersUtils
    ObjectMapper objectMapper
    ObservableQueue observableQueue

    String payloadJSON = "payloadJSON"
    WorkflowDef testWorkflowDefinition
    WorkflowModel workflow

    @Subject
    Event event

    def setup() {
        parametersUtils = Mock(ParametersUtils.class)
        eventQueues = Mock(EventQueues.class)
        observableQueue = Mock(ObservableQueue.class)
        objectMapper = Mock(ObjectMapper.class) {
            writeValueAsString(_) >> payloadJSON
        }

        testWorkflowDefinition = new WorkflowDef(name: "testWorkflow", version: 2)
        workflow = new WorkflowModel(workflowDefinition: testWorkflowDefinition, workflowId: 'workflowId', correlationId: 'corrId')

        event = new Event(eventQueues, parametersUtils, objectMapper)
    }

    def "verify that event task is async"() {
        when:
        def async = event.isAsync()

        then:
        async
    }

    def "event cancel calls ack on the queue"() {
        given:
        // status is intentionally left as null
        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', inputData: ['sink': 'conductor'])

        String queueName = "conductor:${workflow.workflowName}:${task.referenceTaskName}"

        when:
        event.cancel(workflow, task, null)

        then:
        task.status == null // task status is NOT updated by the cancel method

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': 'conductor']
        1 * eventQueues.getQueue(queueName) >> observableQueue
        // Event.cancel sends a list with one Message object to ack
        1 * observableQueue.ack({it.size() == 1})
    }

    def "event task with 'conductor' as sink"() {
        given:
        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', inputData: ['sink': 'conductor'])

        String queueName = "conductor:${workflow.workflowName}:${task.referenceTaskName}"
        Message expectedMessage

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.COMPLETED
        verifyOutputData(task, queueName)

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': 'conductor']
        1 * eventQueues.getQueue(queueName) >> observableQueue
        // capture the Message object sent to the publish method. Event.start sends a list with one Message object
        1 * observableQueue.publish({ it.size() == 1 }) >> { it -> expectedMessage = it[0][0] as Message }

        verifyMessage(expectedMessage, task)
    }

    def "event task with 'conductor:<eventname>' as sink"() {
        given:
        String eventName = 'testEvent'
        String sinkValue = "conductor:$eventName".toString()

        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', inputData: ['sink': sinkValue])

        String queueName = "conductor:${workflow.workflowName}:$eventName"
        Message expectedMessage

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.COMPLETED
        verifyOutputData(task, queueName)

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': sinkValue]
        1 * eventQueues.getQueue(queueName) >> observableQueue
        // capture the Message object sent to the publish method. Event.start sends a list with one Message object
        1 * observableQueue.publish({ it.size() == 1 }) >> { it -> expectedMessage = it[0][0] as Message }

        verifyMessage(expectedMessage, task)
    }

    def "event task with 'sqs' as sink"() {
        given:
        String eventName = 'testEvent'
        String sinkValue = "sqs:$eventName".toString()

        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', inputData: ['sink': sinkValue])

        // for non conductor queues, queueName is the same as the value of the 'sink' field in the inputData
        String queueName = sinkValue
        Message expectedMessage

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.COMPLETED
        verifyOutputData(task, queueName)

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': sinkValue]
        1 * eventQueues.getQueue(queueName) >> observableQueue
        // capture the Message object sent to the publish method. Event.start sends a list with one Message object
        1 * observableQueue.publish({ it.size() == 1 }) >> { it -> expectedMessage = it[0][0] as Message }

        verifyMessage(expectedMessage, task)
    }

    def "event task with 'conductor' as sink and async complete"() {
        given:
        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', inputData: ['sink': 'conductor', 'asyncComplete': true])

        String queueName = "conductor:${workflow.workflowName}:${task.referenceTaskName}"
        Message expectedMessage

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.IN_PROGRESS
        verifyOutputData(task, queueName)

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': 'conductor']
        1 * eventQueues.getQueue(queueName) >> observableQueue
        // capture the Message object sent to the publish method. Event.start sends a list with one Message object
        1 * observableQueue.publish({ it.size() == 1 }) >> { args -> expectedMessage = args[0][0] as Message }

        verifyMessage(expectedMessage, task)
    }

    def "event task with incorrect 'conductor' sink value"() {
        given:
        String sinkValue = 'conductorinvalidsink'

        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', inputData: ['sink': sinkValue])

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.FAILED
        task.reasonForIncompletion != null

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': sinkValue]
    }

    def "event task with sink value that does not resolve to a queue"() {
        given:
        String sinkValue = 'rabbitmq:abc_123'

        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', inputData: ['sink': sinkValue])

        // for non conductor queues, queueName is the same as the value of the 'sink' field in the inputData
        String queueName = sinkValue

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.FAILED
        task.reasonForIncompletion != null

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': sinkValue]
        1 * eventQueues.getQueue(queueName) >> {throw new IllegalArgumentException() }
    }

    def "publishing to a queue throws a retryable ApplicationException"() {
        given:
        String sinkValue = 'conductor'

        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', status: TaskModel.Status.SCHEDULED, inputData: ['sink': sinkValue])

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.SCHEDULED

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': sinkValue]
        1 * eventQueues.getQueue(_) >> observableQueue
        // capture the Message object sent to the publish method. Event.start sends a list with one Message object
        1 * observableQueue.publish(_) >> { throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "transient error") }
    }

    def "publishing to a queue throws a non-retryable ApplicationException"() {
        given:
        String sinkValue = 'conductor'

        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', status: TaskModel.Status.SCHEDULED, inputData: ['sink': sinkValue])

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.FAILED
        task.reasonForIncompletion != null

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': sinkValue]
        1 * eventQueues.getQueue(_) >> observableQueue
        // capture the Message object sent to the publish method. Event.start sends a list with one Message object
        1 * observableQueue.publish(_) >> { throw new ApplicationException(ApplicationException.Code.INTERNAL_ERROR, "fatal error") }
    }

    def "event task fails to convert the payload to json"() {
        given:
        String sinkValue = 'conductor'

        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', status: TaskModel.Status.SCHEDULED, inputData: ['sink': sinkValue])

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.FAILED
        task.reasonForIncompletion != null

        1 * objectMapper.writeValueAsString(_ as Map) >> { throw new JsonParseException(null, "invalid json") }
    }

    def "event task fails with an unexpected exception"() {
        given:
        String sinkValue = 'conductor'

        TaskModel task = new TaskModel(referenceTaskName: 'task0', taskId: 'task_id_0', status: TaskModel.Status.SCHEDULED, inputData: ['sink': sinkValue])

        when:
        event.start(workflow, task, null)

        then:
        task.status == TaskModel.Status.FAILED
        task.reasonForIncompletion != null

        1 * parametersUtils.getTaskInputV2(_, workflow, task.taskId, _) >> ['sink': sinkValue]
        1 * eventQueues.getQueue(_) >> { throw new NullPointerException("some object is null") }
    }

    private void verifyOutputData(TaskModel task, String queueName) {
        assert task.outputData != null
        assert task.outputData['event_produced'] == queueName
        assert task.outputData['workflowInstanceId'] == workflow.workflowId
        assert task.outputData['workflowVersion'] == workflow.workflowVersion
        assert task.outputData['workflowType'] == workflow.workflowName
        assert task.outputData['correlationId'] == workflow.correlationId
    }

    private void verifyMessage(Message expectedMessage, TaskModel task) {
        assert expectedMessage != null
        assert expectedMessage.id == task.taskId
        assert expectedMessage.receipt == task.taskId
        assert expectedMessage.payload == payloadJSON
    }
}
