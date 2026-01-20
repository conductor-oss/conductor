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
package com.netflix.conductor.sqs.eventqueue;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.events.queue.DefaultEventQueueProcessor;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.TaskModel.Status;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class DefaultEventQueueProcessorTest {

    private static SQSObservableQueue queue;
    private static WorkflowExecutor workflowExecutor;
    private DefaultEventQueueProcessor defaultEventQueueProcessor;

    @Autowired private ObjectMapper objectMapper;

    private static final List<Message> messages = new LinkedList<>();
    private static final List<TaskResult> updatedTasks = new LinkedList<>();

    @BeforeClass
    public static void setupMocks() {
        queue = mock(SQSObservableQueue.class);

        when(queue.getOrCreateQueue()).thenReturn("junit_queue_url");
        when(queue.isRunning()).thenReturn(true);
        when(queue.receiveMessages())
                .thenAnswer(
                        (Answer<List<Message>>)
                                invocation -> {
                                    List<Message> copy = new ArrayList<>(messages);
                                    messages.clear();
                                    return copy;
                                });
        when(queue.getOnSubscribe()).thenCallRealMethod();
        when(queue.observe()).thenCallRealMethod();
        when(queue.getName()).thenReturn(Status.COMPLETED.name());

        doAnswer(
                        invocation -> {
                            List<Message> msgs = invocation.getArgument(0);
                            messages.addAll(msgs);
                            return null;
                        })
                .when(queue)
                .publish(any());

        workflowExecutor = mock(WorkflowExecutor.class);
        assertNotNull(workflowExecutor);

        TaskModel task0 = createTask("t0", TASK_TYPE_WAIT, Status.IN_PROGRESS);
        WorkflowModel workflow0 = createWorkflow("v_0", task0);
        doReturn(workflow0).when(workflowExecutor).getWorkflow(eq("v_0"), anyBoolean());

        TaskModel task2 = createTask("t2", TASK_TYPE_WAIT, Status.IN_PROGRESS);
        WorkflowModel workflow2 = createWorkflow("v_2", task2);
        doReturn(workflow2).when(workflowExecutor).getWorkflow(eq("v_2"), anyBoolean());

        doAnswer(
                        invocation -> {
                            TaskResult result = invocation.getArgument(0);
                            updatedTasks.add(result);
                            return null;
                        })
                .when(workflowExecutor)
                .updateTask(any(TaskResult.class));
    }

    @Before
    public void initProcessor() {
        messages.clear();
        updatedTasks.clear();
        Map<Status, ObservableQueue> queues = new HashMap<>();
        queues.put(Status.COMPLETED, queue);
        defaultEventQueueProcessor =
                new DefaultEventQueueProcessor(queues, workflowExecutor, objectMapper);
    }

    @Test
    public void shouldUpdateTaskByReferenceName() throws Exception {
        defaultEventQueueProcessor.updateByTaskRefName(
                "v_0", "t0", new HashMap<>(), Status.COMPLETED);
        Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);
        assertTrue(updatedTasks.stream().anyMatch(task -> "t0".equals(task.getTaskId())));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionForUnknownWorkflow() throws Exception {
        defaultEventQueueProcessor.updateByTaskRefName(
                "v_1", "t1", new HashMap<>(), Status.CANCELED);
        Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void shouldUpdateTaskByTaskId() throws Exception {
        defaultEventQueueProcessor.updateByTaskId("v_2", "t2", new HashMap<>(), Status.COMPLETED);
        Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);
        assertTrue(updatedTasks.stream().anyMatch(task -> "t2".equals(task.getTaskId())));
    }

    private static TaskModel createTask(String taskId, String type, Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId(taskId);
        task.setTaskType(type);
        task.setStatus(status);
        task.setReferenceTaskName(taskId);
        return task;
    }

    private static WorkflowModel createWorkflow(String workflowId, TaskModel task) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.getTasks().add(task);
        return workflow;
    }
}
