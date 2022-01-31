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
package com.netflix.conductor.core.execution.tasks;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.MockQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests the {@link Event#getQueue(WorkflowModel, TaskModel)} method with a real {@link
 * ParametersUtils} object.
 */
@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class EventQueueResolutionTest {

    private WorkflowDef testWorkflowDefinition;
    private EventQueues eventQueues;
    private ParametersUtils parametersUtils;

    @Autowired private ObjectMapper objectMapper;

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

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);

        TaskModel task1 = new TaskModel();
        task1.setReferenceTaskName("t1");
        task1.getOutputData().put("q", "t1_queue");
        workflow.getTasks().add(task1);

        TaskModel task2 = new TaskModel();
        task2.setReferenceTaskName("t2");
        task2.getOutputData().put("q", "task2_queue");
        workflow.getTasks().add(task2);

        TaskModel task = new TaskModel();
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
        assertEquals(
                workflow.getWorkflowName() + ":" + task.getReferenceTaskName(), queue.getName());
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
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(testWorkflowDefinition);

        TaskModel task = new TaskModel();
        task.setReferenceTaskName("task0");
        task.setTaskId("task_id_0");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.getInputData().put("sink", "conductor:some_arbitary_queue");

        ObservableQueue queue = event.getQueue(workflow, task);
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertNotNull(queue);
        assertEquals("testWorkflow:some_arbitary_queue", queue.getName());
        assertEquals("testWorkflow:some_arbitary_queue", queue.getURI());
        assertEquals("conductor", queue.getType());
        assertEquals(
                "conductor:testWorkflow:some_arbitary_queue",
                task.getOutputData().get("event_produced"));

        task.getInputData().put("sink", "conductor");
        queue = event.getQueue(workflow, task);
        assertEquals(
                "not in progress: " + task.getReasonForIncompletion(),
                TaskModel.Status.IN_PROGRESS,
                task.getStatus());
        assertNotNull(queue);
        assertEquals("testWorkflow:task0", queue.getName());

        task.getInputData().put("sink", "sqs:my_sqs_queue_name");
        queue = event.getQueue(workflow, task);
        assertEquals(
                "not in progress: " + task.getReasonForIncompletion(),
                TaskModel.Status.IN_PROGRESS,
                task.getStatus());
        assertNotNull(queue);
        assertEquals("my_sqs_queue_name", queue.getName());
        assertEquals("sqs", queue.getType());
    }
}
