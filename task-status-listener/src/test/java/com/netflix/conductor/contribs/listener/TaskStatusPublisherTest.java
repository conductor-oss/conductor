/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.contribs.listener;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.model.TaskModel;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests for TaskStatusPublisher webhook notification wiring. */
public class TaskStatusPublisherTest {

    private RestClientManager restClientManager;
    private ExecutionDAOFacade executionDAOFacade;

    @Before
    public void setUp() {
        restClientManager = Mockito.mock(RestClientManager.class);
        executionDAOFacade = Mockito.mock(ExecutionDAOFacade.class);
    }

    @Test
    public void testOnTaskScheduled_WithScheduledInSubscribedList() throws Exception {
        TaskStatusPublisher publisher =
                new TaskStatusPublisher(
                        restClientManager,
                        executionDAOFacade,
                        Collections.singletonList("SCHEDULED"));

        TaskModel task = createTask("my_task", TaskModel.Status.SCHEDULED);
        publisher.onTaskScheduled(task);

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, timeout(1000).atLeastOnce())
                .postNotification(
                        eq(RestClientManager.NotificationType.TASK),
                        anyString(),
                        eq(task.getTaskId()),
                        any());
    }

    @Test
    public void testOnTaskScheduled_WithoutScheduledInSubscribedList() throws Exception {
        TaskStatusPublisher publisher =
                new TaskStatusPublisher(
                        restClientManager, executionDAOFacade, Collections.singletonList("COMPLETED"));

        publisher.onTaskScheduled(createTask("my_task", TaskModel.Status.SCHEDULED));

        TimeUnit.MILLISECONDS.sleep(100);

        verify(restClientManager, never()).postNotification(any(), anyString(), anyString(), any());
    }

    @Test
    public void testOnTaskScheduled_WhenPublishFails_consumerContinues() throws Exception {
        doThrow(new IOException("publish failed"))
                .when(restClientManager)
                .postNotification(
                        eq(RestClientManager.NotificationType.TASK),
                        anyString(),
                        anyString(),
                        any());

        TaskStatusPublisher publisher =
                new TaskStatusPublisher(
                        restClientManager,
                        executionDAOFacade,
                        Collections.singletonList("SCHEDULED"));

        TaskModel failingTask = createTask("failing_task", TaskModel.Status.SCHEDULED);
        publisher.onTaskScheduled(failingTask);

        TimeUnit.MILLISECONDS.sleep(100);

        TaskModel nextTask = createTask("next_task", TaskModel.Status.SCHEDULED);
        publisher.onTaskScheduled(nextTask);

        verify(restClientManager, timeout(1000).atLeast(2))
                .postNotification(
                        eq(RestClientManager.NotificationType.TASK),
                        anyString(),
                        anyString(),
                        any());
    }

    @Test
    public void testOnTaskScheduled_WhenQueueFull_doesNotEnqueueDroppedTask() throws Exception {
        TaskStatusPublisher publisher =
                new TaskStatusPublisher(
                        restClientManager,
                        executionDAOFacade,
                        Collections.singletonList("SCHEDULED"));

        TaskModel blocked = createTask("blocked", TaskModel.Status.SCHEDULED);
        BlockingQueue<TaskModel> fullQueue = new LinkedBlockingDeque<>(1);
        fullQueue.offer(blocked);
        setBlockingQueue(publisher, fullQueue);

        TaskModel dropped = createTask("dropped_task", TaskModel.Status.SCHEDULED);
        publisher.onTaskScheduled(dropped);

        verify(restClientManager, never())
                .postNotification(
                        eq(RestClientManager.NotificationType.TASK),
                        anyString(),
                        eq(dropped.getTaskId()),
                        any());
    }

    private static void setBlockingQueue(TaskStatusPublisher publisher, BlockingQueue<TaskModel> queue)
            throws Exception {
        Field queueField = TaskStatusPublisher.class.getDeclaredField("blockingQueue");
        queueField.setAccessible(true);
        queueField.set(publisher, queue);
    }

    private static TaskModel createTask(String taskDefName, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskDefName(taskDefName);
        task.setTaskType("SIMPLE");
        task.setStatus(status);
        task.setReferenceTaskName("ref_task");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("ref_task");
        workflowTask.setTaskReferenceName("ref_task");
        workflowTask.setType("SIMPLE");
        workflowTask.setDescription("test task");
        workflowTask.setTaskDefinition(new TaskDef(taskDefName));
        task.setWorkflowTask(workflowTask);
        return task;
    }
}
