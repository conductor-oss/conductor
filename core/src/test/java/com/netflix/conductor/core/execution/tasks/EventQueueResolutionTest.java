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
package com.netflix.conductor.core.execution.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.MockQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.utils.ParametersUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests the {@link Event#getQueue(Workflow, Task)} method with a real {@link ParametersUtils} object.
 */
@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class EventQueueResolutionTest {

    private WorkflowDef testWorkflowDefinition;
    private EventQueues eventQueues;
    private ParametersUtils parametersUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        Map<String, EventQueueProvider> providers = new HashMap<>();
        providers.put("sqs", new MockQueueProvider("sqs"));
        providers.put("conductor", new MockQueueProvider("conductor"));

        parametersUtils = new ParametersUtils(objectMapper);
        eventQueues = new EventQueues(providers, parametersUtils);

        testWorkflowDefinition = new WorkflowDef();
        testWorkflowDefinition.setName("testWorkflow");
        testWorkflowDefinition.setVersion(2);
    }

    @Test
    public void testSinkParam() {
        String sink = "sqs:queue_name";

        WorkflowDef def = new WorkflowDef();
        def.setName("wf0");

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);

        Task task1 = new Task();
        task1.setReferenceTaskName("t1");
        task1.getOutputData().put("q", "t1_queue");
        workflow.getTasks().add(task1);

        Task task2 = new Task();
        task2.setReferenceTaskName("t2");
        task2.getOutputData().put("q", "task2_queue");
        workflow.getTasks().add(task2);

        Task task = new Task();
        task.setReferenceTaskName("event");
        task.getInputData().put("sink", sink);
        task.setTaskType(TaskType.EVENT.name());
        workflow.getTasks().add(task);

        Event event = new Event(eventQueues, parametersUtils, objectMapper);
        ObservableQueue queue = event.getQueue(workflow, task);
        assertNotNull(task.getReasonForIncompletion(), queue);
        assertEquals("queue_name", queue.getName());
        assertEquals("sqs", queue.getType());

        sink = "sqs:${t1.output.q}";
        task.getInputData().put("sink", sink);
        queue = event.getQueue(workflow, task);
        assertNotNull(queue);
        assertEquals("t1_queue", queue.getName());
        assertEquals("sqs", queue.getType());

        sink = "sqs:${t2.output.q}";
        task.getInputData().put("sink", sink);
        queue = event.getQueue(workflow, task);
        assertNotNull(queue);
        assertEquals("task2_queue", queue.getName());
        assertEquals("sqs", queue.getType());

        sink = "conductor";
        task.getInputData().put("sink", sink);
        queue = event.getQueue(workflow, task);
        assertNotNull(queue);
        assertEquals(workflow.getWorkflowName() + ":" + task.getReferenceTaskName(), queue.getName());
        assertEquals("conductor", queue.getType());

        sink = "sqs:static_value";
        task.getInputData().put("sink", sink);
        queue = event.getQueue(workflow, task);
        assertNotNull(queue);
        assertEquals("static_value", queue.getName());
        assertEquals("sqs", queue.getType());
        assertEquals(sink, task.getOutputData().get("event_produced"));
    }

    @Test
    public void testDynamicSinks() {
        Event event = new Event(eventQueues, parametersUtils, objectMapper);
        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(testWorkflowDefinition);

        Task task = new Task();
        task.setReferenceTaskName("task0");
        task.setTaskId("task_id_0");
        task.setStatus(Status.IN_PROGRESS);
        task.getInputData().put("sink", "conductor:some_arbitary_queue");

        ObservableQueue queue = event.getQueue(workflow, task);
        assertEquals(Task.Status.IN_PROGRESS, task.getStatus());
        assertNotNull(queue);
        assertEquals("testWorkflow:some_arbitary_queue", queue.getName());
        assertEquals("testWorkflow:some_arbitary_queue", queue.getURI());
        assertEquals("conductor", queue.getType());
        assertEquals("conductor:testWorkflow:some_arbitary_queue", task.getOutputData().get("event_produced"));

        task.getInputData().put("sink", "conductor");
        queue = event.getQueue(workflow, task);
        assertEquals("not in progress: " + task.getReasonForIncompletion(), Task.Status.IN_PROGRESS, task.getStatus());
        assertNotNull(queue);
        assertEquals("testWorkflow:task0", queue.getName());

        task.getInputData().put("sink", "sqs:my_sqs_queue_name");
        queue = event.getQueue(workflow, task);
        assertEquals("not in progress: " + task.getReasonForIncompletion(), Task.Status.IN_PROGRESS, task.getStatus());
        assertNotNull(queue);
        assertEquals("my_sqs_queue_name", queue.getName());
        assertEquals("sqs", queue.getType());
    }
}
