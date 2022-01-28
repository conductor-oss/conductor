/*
 * Copyright 2021 Netflix, Inc.
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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestSystemTaskWorker {

    private static final String TEST_TASK = "system_task";
    private static final String ISOLATED_TASK = "system_task-isolated";

    private AsyncSystemTaskExecutor asyncSystemTaskExecutor;
    private ExecutionService executionService;
    private QueueDAO queueDAO;
    private ConductorProperties properties;

    private SystemTaskWorker systemTaskWorker;

    @Before
    public void setUp() {
        asyncSystemTaskExecutor = mock(AsyncSystemTaskExecutor.class);
        executionService = mock(ExecutionService.class);
        queueDAO = mock(QueueDAO.class);
        properties = mock(ConductorProperties.class);

        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(10);
        when(properties.getIsolatedSystemTaskWorkerThreadCount()).thenReturn(10);
        when(properties.getSystemTaskWorkerCallbackDuration()).thenReturn(Duration.ofSeconds(30));
        when(properties.getSystemTaskMaxPollCount()).thenReturn(1);
        when(properties.getSystemTaskWorkerPollInterval()).thenReturn(Duration.ofSeconds(30));

        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);
        systemTaskWorker.start();
    }

    @After
    public void tearDown() {
        systemTaskWorker.queueExecutionConfigMap.clear();
        systemTaskWorker.stop();
    }

    @Test
    public void testGetExecutionConfigForSystemTask() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(5);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);
        assertEquals(
                systemTaskWorker.getExecutionConfig("").getSemaphoreUtil().availableSlots(), 5);
    }

    @Test
    public void testGetExecutionConfigForIsolatedSystemTask() {
        when(properties.getIsolatedSystemTaskWorkerThreadCount()).thenReturn(7);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);
        assertEquals(
                systemTaskWorker.getExecutionConfig("test-iso").getSemaphoreUtil().availableSlots(),
                7);
    }

    @Test
    public void testPollAndExecuteSystemTask() throws Exception {
        when(queueDAO.pop(anyString(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList("taskId"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            latch.countDown();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), anyString());

        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        latch.await();

        verify(asyncSystemTaskExecutor).execute(any(), anyString());
    }

    @Test
    public void testBatchPollAndExecuteSystemTask() throws Exception {
        when(properties.getSystemTaskMaxPollCount()).thenReturn(2);
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of("t1", "t1"));

        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(
                        invocation -> {
                            latch.countDown();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), eq("t1"));

        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        latch.await();

        verify(asyncSystemTaskExecutor, Mockito.times(2)).execute(any(), eq("t1"));
    }

    @Test
    public void testPollAndExecuteIsolatedSystemTask() throws Exception {
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of("isolated_taskId"));

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            latch.countDown();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), eq("isolated_taskId"));

        systemTaskWorker.pollAndExecute(new IsolatedTask(), ISOLATED_TASK);

        latch.await();

        verify(asyncSystemTaskExecutor, Mockito.times(1)).execute(any(), eq("isolated_taskId"));
    }

    @Test
    public void testPollException() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(1);
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenThrow(RuntimeException.class);

        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        verify(asyncSystemTaskExecutor, Mockito.never()).execute(any(), anyString());
    }

    @Test
    public void testBatchPollException() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(2);
        when(properties.getSystemTaskMaxPollCount()).thenReturn(2);
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenThrow(RuntimeException.class);

        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        verify(asyncSystemTaskExecutor, Mockito.never()).execute(any(), anyString());
    }

    static class TestTask extends WorkflowSystemTask {
        public TestTask() {
            super(TEST_TASK);
        }
    }

    static class IsolatedTask extends WorkflowSystemTask {
        public IsolatedTask() {
            super(ISOLATED_TASK);
        }
    }
}
