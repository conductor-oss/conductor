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

import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("UnstableApiUsage")
public class TestSystemTaskExecutor {

    private static final String TEST_TASK = "system_task";
    private static final String ISOLATED_TASK = "system_task-isolated";

    private WorkflowExecutor workflowExecutor;
    private ExecutionService executionService;
    private QueueDAO queueDAO;
    private ScheduledExecutorService scheduledExecutorService;
    private ConductorProperties properties;

    private SystemTaskExecutor systemTaskExecutor;

    @Before
    public void setUp() {
        workflowExecutor = mock(WorkflowExecutor.class);
        executionService = mock(ExecutionService.class);
        queueDAO = mock(QueueDAO.class);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        createTaskMapping();
        properties = mock(ConductorProperties.class);
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(10);
        when(properties.getSystemTaskWorkerCallbackDuration()).thenReturn(Duration.ofSeconds(30));
        when(properties.getSystemTaskMaxPollCount()).thenReturn(1);
        when(properties.getIsolatedSystemTaskWorkerThreadCount()).thenReturn(1);
    }

    @After
    public void tearDown() {
        shutdownExecutorService(scheduledExecutorService);
        shutdownExecutorService(systemTaskExecutor.defaultExecutionConfig.getExecutorService());
        systemTaskExecutor.queueExecutionConfigMap.values()
            .forEach(e -> shutdownExecutorService(e.getExecutorService()));
    }

    @Test
    public void testGetExecutionConfigForSystemTask() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(5);
        systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);
        assertEquals(systemTaskExecutor.getExecutionConfig("").getSemaphoreUtil().availableSlots(), 5);
    }

    @Test
    public void testGetExecutionConfigForIsolatedSystemTask() {
        when(properties.getIsolatedSystemTaskWorkerThreadCount()).thenReturn(7);
        systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);
        assertEquals(systemTaskExecutor.getExecutionConfig("test-iso").getSemaphoreUtil().availableSlots(), 7);
    }

    @Test
    public void testPollAndExecuteSystemTask() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(1);
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(Collections.singletonList("taskId"));
        systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                latch.countDown();
                return null;
            }
        ).when(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());

        scheduledExecutorService.scheduleAtFixedRate(
            () -> systemTaskExecutor.pollAndExecute(TEST_TASK), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());
    }

    @Test
    public void testBatchPollAndExecuteSystemTask() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(2);
        when(properties.getSystemTaskMaxPollCount()).thenReturn(2);

        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(Collections.nCopies(2, "taskId"));
        systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);

        CountDownLatch latch = new CountDownLatch(10);
        doAnswer(invocation -> {
                latch.countDown();
                return null;
            }
        ).when(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());

        scheduledExecutorService.scheduleAtFixedRate(
            () -> systemTaskExecutor.pollAndExecute(TEST_TASK), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(workflowExecutor, Mockito.times(10)).executeSystemTask(any(), anyString(), anyLong());
    }

    @Test
    public void testPollAndExecuteIsolatedSystemTask() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(1);
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(Collections.singletonList("isolated_taskId"));
        systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                latch.countDown();
                return null;
            }
        ).when(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());

        scheduledExecutorService.scheduleAtFixedRate(
            () -> systemTaskExecutor.pollAndExecute(ISOLATED_TASK), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());
    }

    @Test
    public void testPollException() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(1);
        when(queueDAO.pop(anyString(), anyInt(), anyInt()))
            .thenThrow(RuntimeException.class)
            .thenReturn(Collections.singletonList("taskId"));
        systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
                latch.countDown();
                return null;
            }
        ).when(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());

        scheduledExecutorService.scheduleAtFixedRate(
            () -> systemTaskExecutor.pollAndExecute(TEST_TASK), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());
    }

    @Test
    public void testBatchPollException() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(2);
        when(properties.getSystemTaskMaxPollCount()).thenReturn(2);
        when(queueDAO.pop(anyString(), anyInt(), anyInt()))
            .thenThrow(RuntimeException.class)
            .thenReturn(Collections.nCopies(2, "taskId"));
        systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);

        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(invocation -> {
                latch.countDown();
                return null;
            }
        ).when(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());

        scheduledExecutorService.scheduleAtFixedRate(
            () -> systemTaskExecutor.pollAndExecute(TEST_TASK), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(latch);
        verify(workflowExecutor, Mockito.times(2)).executeSystemTask(any(), anyString(), anyLong());
    }

    @Test
    public void testMultipleQueuesExecution() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(1);
        when(properties.getIsolatedSystemTaskWorkerThreadCount()).thenReturn(1);
        String sysTask = "taskId";
        String isolatedTask = "isolatedTaskId";
        when(queueDAO.pop(TEST_TASK, 1, 200)).thenReturn(Collections.singletonList(sysTask));
        when(queueDAO.pop(ISOLATED_TASK, 1, 200)).thenReturn(Collections.singletonList(isolatedTask));
        systemTaskExecutor = new SystemTaskExecutor(queueDAO, workflowExecutor, properties, executionService);

        CountDownLatch sysTaskLatch = new CountDownLatch(1);
        CountDownLatch isolatedTaskLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
                Object[] args = invocation.getArguments();
                String taskId = args[1].toString();
                if (taskId.equals(sysTask)) {
                    sysTaskLatch.countDown();
                }
                if (taskId.equals(isolatedTask)) {
                    isolatedTaskLatch.countDown();
                }
                return null;
            }
        ).when(workflowExecutor).executeSystemTask(any(), anyString(), anyLong());

        scheduledExecutorService
            .scheduleAtFixedRate(() -> systemTaskExecutor.pollAndExecute(TEST_TASK), 0, 1, TimeUnit.SECONDS);

        ScheduledExecutorService isoTaskService = Executors.newSingleThreadScheduledExecutor();
        isoTaskService
            .scheduleAtFixedRate(() -> systemTaskExecutor.pollAndExecute(ISOLATED_TASK), 0, 1, TimeUnit.SECONDS);

        Uninterruptibles.awaitUninterruptibly(sysTaskLatch);
        Uninterruptibles.awaitUninterruptibly(isolatedTaskLatch);

        shutdownExecutorService(isoTaskService);
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void createTaskMapping() {
        WorkflowSystemTask mockWorkflowTask = mock(WorkflowSystemTask.class);
        when(mockWorkflowTask.getTaskType()).thenReturn(TEST_TASK);
        when(mockWorkflowTask.isAsync()).thenReturn(true);
        SystemTaskWorkerCoordinator.taskNameWorkflowTaskMapping.put(TEST_TASK, mockWorkflowTask);
    }
}
