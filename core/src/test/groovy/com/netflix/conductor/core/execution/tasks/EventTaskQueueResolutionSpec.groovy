/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */

package com.netflix.conductor.core.execution.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.conductor.common.config.TestObjectMapperConfiguration
import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.events.EventQueues
import com.netflix.conductor.core.events.MockQueueProvider
import com.netflix.conductor.core.events.queue.ObservableQueue
import com.netflix.conductor.core.utils.ParametersUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import spock.lang.Subject

@ContextConfiguration(classes = TestObjectMapperConfiguration.class)
class EventTaskQueueResolutionSpec extends Specification {

    @Autowired
    ObjectMapper objectMapper

    ParametersUtils parametersUtils
    EventQueues eventQueues

    @Subject
    Event event

    def setup() {
        parametersUtils = new ParametersUtils(objectMapper)
        eventQueues = new EventQueues(["sqs": new MockQueueProvider("sqs"), "conductor": new MockQueueProvider("conductor")], parametersUtils)

        event = new Event(eventQueues, parametersUtils, objectMapper)
    }

    def "verify sink param is resolved correctly"() {
        given:
        String sink = 'sqs:queue_name'

        WorkflowDef wf = new WorkflowDef(name: 'wf0')
        Workflow workflow = new Workflow(workflowDefinition: wf)

        Task task = new Task(referenceTaskName: 'event', taskType: TaskType.EVENT.name(), inputData: ['sink': sink])
        workflow.tasks.add(task)

        when:
        ObservableQueue queue = event.getQueue(workflow, task)

        then:
        queue.name == 'queue_name'
        queue.type == 'sqs'

        when:
        sink = 'sqs:${t1.output.q}'
        task.inputData.put("sink", sink)
        queue = event.getQueue(workflow, task)

        then:
        queue != null
        queue.name == 't1_queue'
        queue.type == 'sqs'

        when:
        sink = 'sqs:${t2.output.q}'
        task.inputData.put("sink", sink)
        queue = event.getQueue(workflow, task)

        then:
        queue != null
        queue.name == 'task2_queue'
        queue.type == 'sqs'

        when:
        sink = "conductor"
        task.inputData.put("sink", sink)
        queue = event.getQueue(workflow, task)

        then:
        queue != null
        queue.name == "${workflow.workflowName}:${task.referenceTaskName}".toString()
        queue.type == 'conductor'

        when:
        sink = "sqs:static_value"
        task.inputData.put("sink", sink)
        queue = event.getQueue(workflow, task)

        then:
        queue != null
        queue.name == 'static_value'
        queue.type == 'sqs'
    }
}
