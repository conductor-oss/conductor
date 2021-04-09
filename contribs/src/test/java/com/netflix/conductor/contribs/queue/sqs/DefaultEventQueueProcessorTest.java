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
package com.netflix.conductor.contribs.queue.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.common.config.ObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.DefaultEventQueueProcessor;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.service.ExecutionService;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ContextConfiguration(classes = {ObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class DefaultEventQueueProcessorTest {

    private static SQSObservableQueue queue;
    private static ExecutionService executionService;
    private DefaultEventQueueProcessor defaultEventQueueProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    private static final List<Message> messages = new LinkedList<>();
    private static final List<Task> updatedTasks = new LinkedList<>();

    @Before
    public void init() {
        Map<Status, ObservableQueue> queues = new HashMap<>();
        queues.put(Status.COMPLETED, queue);
        defaultEventQueueProcessor = new DefaultEventQueueProcessor(queues, executionService, objectMapper);
    }

    @BeforeClass
    public static void setup() {

        queue = mock(SQSObservableQueue.class);
        when(queue.getOrCreateQueue()).thenReturn("junit_queue_url");
        when(queue.isRunning()).thenReturn(true);
        Answer<?> answer = (Answer<List<Message>>) invocation -> {
            List<Message> copy = new LinkedList<>(messages);
            messages.clear();
            return copy;
        };

        when(queue.receiveMessages()).thenAnswer(answer);
        when(queue.getOnSubscribe()).thenCallRealMethod();
        when(queue.observe()).thenCallRealMethod();
        when(queue.getName()).thenReturn(Status.COMPLETED.name());

        Task task0 = new Task();
        task0.setStatus(Status.IN_PROGRESS);
        task0.setTaskId("t0");
        task0.setReferenceTaskName("t0");
        task0.setTaskType(TASK_TYPE_WAIT);
        Workflow workflow0 = new Workflow();
        workflow0.setWorkflowId("v_0");
        workflow0.getTasks().add(task0);

        Task task2 = new Task();
        task2.setStatus(Status.IN_PROGRESS);
        task2.setTaskId("t2");
        task2.setTaskType(TASK_TYPE_WAIT);
        Workflow workflow2 = new Workflow();
        workflow2.setWorkflowId("v_2");
        workflow2.getTasks().add(task2);

        doAnswer((Answer<Void>) invocation -> {
            List<Message> msgs = invocation.getArgument(0, List.class);
            messages.addAll(msgs);
            return null;
        }).when(queue).publish(any());

        executionService = mock(ExecutionService.class);
        assertNotNull(executionService);

        doReturn(workflow0).when(executionService).getExecutionStatus(eq("v_0"), anyBoolean());

        doReturn(workflow2).when(executionService).getExecutionStatus(eq("v_2"), anyBoolean());

        doAnswer((Answer<Void>) invocation -> {
            updatedTasks.add(invocation.getArgument(0, Task.class));
            return null;
        }).when(executionService).updateTask(any(Task.class));
    }

    @Test
    public void test() throws Exception {
        defaultEventQueueProcessor.updateByTaskRefName("v_0", "t0", new HashMap<>(), Status.COMPLETED);
        Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);

        assertTrue(updatedTasks.stream().anyMatch(task -> task.getTaskId().equals("t0")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailure() throws Exception {
        defaultEventQueueProcessor.updateByTaskRefName("v_1", "t1", new HashMap<>(), Status.CANCELED);
        Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testWithTaskId() throws Exception {
        defaultEventQueueProcessor.updateByTaskId("v_2", "t2", new HashMap<>(), Status.COMPLETED);
        Uninterruptibles.sleepUninterruptibly(1_000, TimeUnit.MILLISECONDS);
        assertTrue(updatedTasks.stream().anyMatch(task -> task.getTaskId().equals("t2")));
    }
}
