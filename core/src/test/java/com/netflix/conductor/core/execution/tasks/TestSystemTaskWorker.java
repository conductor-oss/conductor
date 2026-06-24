/*
 * Copyright 2021 Conductor Authors.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.utils.SemaphoreUtil;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
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
        when(properties.getSystemTaskMaxPollCount()).thenReturn(10);
        when(properties.getSystemTaskWorkerCallbackDuration()).thenReturn(Duration.ofSeconds(30));
        when(properties.getSystemTaskWorkerPollInterval()).thenReturn(Duration.ofSeconds(30));
        when(properties.getSystemTaskQueuePopTimeout()).thenReturn(Duration.ofMillis(100));
        when(properties.getTaskWorkerConfigs()).thenReturn(new HashMap<>());

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

    // -----------------------------------------------------------------------
    // Scenario 1: isolated queue — own dedicated pool, own semaphore
    // -----------------------------------------------------------------------

    @Test
    public void testIsolatedQueueGetsOwnPoolAndSemaphore() {
        when(properties.getIsolatedSystemTaskWorkerThreadCount()).thenReturn(7);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);

        ExecutionConfig isolated = systemTaskWorker.getExecutionConfig("HTTP-iso");
        ExecutionConfig nonIsolated = systemTaskWorker.getExecutionConfig("HTTP");

        // Isolated queue gets its own semaphore sized to isolatedSystemTaskWorkerThreadCount.
        assertEquals(7, isolated.getSemaphoreUtil().availableSlots());
        // Isolated queue gets its own pool — completely separate from the shared pool.
        assertNotSame(isolated.getExecutorService(), nonIsolated.getExecutorService());
    }

    // -----------------------------------------------------------------------
    // Scenario 2: no taskWorkerConfigs entry — shared pool, per-queue semaphore
    //             sized to systemTaskWorkerThreadCount (same default as before)
    // -----------------------------------------------------------------------

    @Test
    public void testDefaultNonIsolatedQueueUsesSharedPoolWithPerQueueSemaphore() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(5);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);

        ExecutionConfig http = systemTaskWorker.getExecutionConfig("HTTP");
        ExecutionConfig subWorkflow = systemTaskWorker.getExecutionConfig("SUB_WORKFLOW");

        // Both queues get permits == systemTaskWorkerThreadCount (same value as the old single
        // shared semaphore — the default is unchanged).
        assertEquals(5, http.getSemaphoreUtil().availableSlots());
        assertEquals(5, subWorkflow.getSemaphoreUtil().availableSlots());
        // Semaphores are independent: exhausting HTTP permits does not affect SUB_WORKFLOW.
        assertNotSame(http.getSemaphoreUtil(), subWorkflow.getSemaphoreUtil());
        http.getSemaphoreUtil().acquireSlots(5);
        assertEquals(0, http.getSemaphoreUtil().availableSlots());
        assertEquals(5, subWorkflow.getSemaphoreUtil().availableSlots());
        // Thread pool is shared.
        assertSame(http.getExecutorService(), subWorkflow.getExecutorService());
    }

    // -----------------------------------------------------------------------
    // Scenario 3: taskWorkerConfigs entry present — dedicated pool + own semaphore
    // -----------------------------------------------------------------------

    @Test
    public void testNonIsolatedQueuesHaveIndependentSemaphoresButShareExecutor() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(3);
        when(properties.getTaskWorkerConfigs()).thenReturn(new HashMap<>());
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);

        ExecutionConfig httpConfig = systemTaskWorker.getExecutionConfig("HTTP");
        ExecutionConfig subWorkflowConfig = systemTaskWorker.getExecutionConfig("SUB_WORKFLOW");

        // Each non-isolated queue must have its own semaphore — a slow/busy queue draining all
        // of its permits must not reduce the available slots of any other queue's semaphore.
        assertNotSame(httpConfig.getSemaphoreUtil(), subWorkflowConfig.getSemaphoreUtil());
        httpConfig.getSemaphoreUtil().acquireSlots(3);
        assertEquals(
                "HTTP semaphore should be exhausted",
                0,
                httpConfig.getSemaphoreUtil().availableSlots());
        assertEquals(
                "SUB_WORKFLOW semaphore must be unaffected",
                3,
                subWorkflowConfig.getSemaphoreUtil().availableSlots());

        // Thread pool is still shared — there is only one pool for all non-isolated queues.
        assertSame(httpConfig.getExecutorService(), subWorkflowConfig.getExecutorService());
    }

    @Test
    public void testPerTaskPermitCountOverride() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(10);
        ConductorProperties.TaskWorkerConfig llmConfig = new ConductorProperties.TaskWorkerConfig();
        llmConfig.setPermitCount(3);
        Map<String, ConductorProperties.TaskWorkerConfig> configs = new HashMap<>();
        configs.put("LLM_TEXT_COMPLETE", llmConfig);
        when(properties.getTaskWorkerConfigs()).thenReturn(configs);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);

        ExecutionConfig llmExecConfig = systemTaskWorker.getExecutionConfig("LLM_TEXT_COMPLETE");
        ExecutionConfig httpExecConfig = systemTaskWorker.getExecutionConfig("HTTP");

        // LLM gets a capped semaphore of 3 even though the shared pool has 10 threads.
        assertEquals(3, llmExecConfig.getSemaphoreUtil().availableSlots());
        // HTTP falls through to the default of 10.
        assertEquals(10, httpExecConfig.getSemaphoreUtil().availableSlots());
        // Both still share the same pool.
        assertSame(llmExecConfig.getExecutorService(), httpExecConfig.getExecutorService());
    }

    @Test
    public void testPerTaskDedicatedPoolOverride() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(10);
        ConductorProperties.TaskWorkerConfig httpConfig =
                new ConductorProperties.TaskWorkerConfig();
        httpConfig.setThreadCount(20);
        Map<String, ConductorProperties.TaskWorkerConfig> configs = new HashMap<>();
        configs.put("HTTP", httpConfig);
        when(properties.getTaskWorkerConfigs()).thenReturn(configs);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);

        ExecutionConfig httpExecConfig = systemTaskWorker.getExecutionConfig("HTTP");
        ExecutionConfig subWorkflowExecConfig = systemTaskWorker.getExecutionConfig("SUB_WORKFLOW");

        // HTTP gets 20 permits from its dedicated pool.
        assertEquals(20, httpExecConfig.getSemaphoreUtil().availableSlots());
        // SUB_WORKFLOW still uses the shared pool with default permits.
        assertEquals(10, subWorkflowExecConfig.getSemaphoreUtil().availableSlots());
        // HTTP has its own pool, distinct from the shared one.
        assertNotSame(
                httpExecConfig.getExecutorService(), subWorkflowExecConfig.getExecutorService());
    }

    @Test
    public void testPerTaskDedicatedPoolWithExplicitPermitCount() {
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(10);
        ConductorProperties.TaskWorkerConfig httpConfig =
                new ConductorProperties.TaskWorkerConfig();
        httpConfig.setThreadCount(20);
        httpConfig.setPermitCount(8); // dedicated pool of 20 threads, but cap in-flight at 8
        Map<String, ConductorProperties.TaskWorkerConfig> configs = new HashMap<>();
        configs.put("HTTP", httpConfig);
        when(properties.getTaskWorkerConfigs()).thenReturn(configs);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);

        ExecutionConfig httpExecConfig = systemTaskWorker.getExecutionConfig("HTTP");

        assertEquals(8, httpExecConfig.getSemaphoreUtil().availableSlots());
        assertNotSame(
                httpExecConfig.getExecutorService(),
                systemTaskWorker.getExecutionConfig("SUB_WORKFLOW").getExecutorService());
    }

    @Test
    public void testPollAndExecuteDispatchesTask() throws Exception {
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
    public void testBatchPollDispatchesAllTasks() throws Exception {
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of("t1", "t2"));

        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(
                        invocation -> {
                            latch.countDown();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), anyString());

        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);
        latch.await();

        verify(asyncSystemTaskExecutor, Mockito.times(2)).execute(any(), anyString());
    }

    @Test
    public void testPermitsAcquiredMatchTasksReceived() throws Exception {
        // Poll batch size is 10 (maxPollCount), but only 3 tasks come back.
        // Exactly 3 permits should be in-flight — not 10.
        when(properties.getSystemTaskMaxPollCount()).thenReturn(10);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);
        systemTaskWorker.start();
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of("t1", "t2", "t3"));

        // Block all 3 tasks in-flight so we can observe permits while they are held.
        CountDownLatch allStarted = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            allStarted.countDown();
                            release.await();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), anyString());

        int permitsBefore =
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots();
        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        // Wait until all 3 tasks are actually running (holding their permits).
        assertTrue(allStarted.await(5, TimeUnit.SECONDS));
        assertEquals(
                "Exactly 3 permits in-flight — not the full batch size of 10",
                permitsBefore - 3,
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots());

        // Unblock tasks and wait for whenComplete to fire.
        release.countDown();
        Thread.sleep(200);
        assertEquals(
                "All 3 permits restored after completion",
                permitsBefore,
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots());
    }

    @Test
    public void testEmptyPollAcquiresNoPermits() {
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(Collections.emptyList());

        int permitsBefore =
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots();
        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        assertEquals(
                "No permits should be acquired when poll returns empty",
                permitsBefore,
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots());
        verify(asyncSystemTaskExecutor, Mockito.never()).execute(any(), anyString());
    }

    @Test
    public void testPollExceptionAcquiresNoPermits() {
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenThrow(RuntimeException.class);

        int permitsBefore =
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots();
        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        assertEquals(
                "Poll exception must not consume any permits",
                permitsBefore,
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots());
        verify(asyncSystemTaskExecutor, Mockito.never()).execute(any(), anyString());
    }

    @Test
    public void testDispatchExceptionReleasesPermitImmediately() {
        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of("t1", "t2"));
        // ack throws for t1, succeeds for t2
        doAnswer(
                        invocation -> {
                            String taskId = invocation.getArgument(0);
                            if ("t1".equals(taskId)) throw new RuntimeException("ack failed");
                            return true;
                        })
                .when(executionService)
                .ackTaskReceived(anyString());

        int permitsBefore =
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots();
        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        // t1's permit released immediately on exception; t2's permit still held (async in-flight).
        assertEquals(
                "t1 permit released immediately, t2 still in-flight",
                permitsBefore - 1,
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots());
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

    // -----------------------------------------------------------------------
    // Isolated queue — behavioral tests
    // -----------------------------------------------------------------------

    @Test
    public void testEachDistinctIsolatedQueueGetsItsOwnPool() {
        // Two different isolation groups must never share a thread pool.
        ExecutionConfig groupA = systemTaskWorker.getExecutionConfig("HTTP-groupA");
        ExecutionConfig groupB = systemTaskWorker.getExecutionConfig("HTTP-groupB");

        assertNotSame(
                "Different isolated queues must have separate pools",
                groupA.getExecutorService(),
                groupB.getExecutorService());
        assertNotSame(
                "Different isolated queues must have separate semaphores",
                groupA.getSemaphoreUtil(),
                groupB.getSemaphoreUtil());
    }

    @Test
    public void testIsolatedQueueSemaphoreConsumedAndReleasedOnCompletion() throws Exception {
        when(properties.getIsolatedSystemTaskWorkerThreadCount()).thenReturn(2);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);
        systemTaskWorker.start();

        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of("i1", "i2"));

        CountDownLatch allStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            allStarted.countDown();
                            release.await();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), anyString());

        SemaphoreUtil isoDSemaphore =
                systemTaskWorker.getExecutionConfig(ISOLATED_TASK).getSemaphoreUtil();
        int permitsBefore = isoDSemaphore.availableSlots();
        systemTaskWorker.pollAndExecute(new IsolatedTask(), ISOLATED_TASK);

        assertTrue(
                "Both isolated tasks must start within 5 s", allStarted.await(5, TimeUnit.SECONDS));
        assertEquals(
                "Isolated semaphore must be fully consumed while tasks are in-flight",
                0,
                isoDSemaphore.availableSlots());

        release.countDown();
        Thread.sleep(200);
        assertEquals(
                "Isolated semaphore must be fully restored after completion",
                permitsBefore,
                isoDSemaphore.availableSlots());
    }

    // -----------------------------------------------------------------------
    // Per-task semaphore — behavioral tests
    // -----------------------------------------------------------------------

    @Test
    public void testPerTaskPermitOverrideEnforcedDuringPolling() throws Exception {
        // system_task configured with 2 permits — after 2 in-flight tasks the next poll is skipped.
        ConductorProperties.TaskWorkerConfig cfg = new ConductorProperties.TaskWorkerConfig();
        cfg.setPermitCount(2);
        Map<String, ConductorProperties.TaskWorkerConfig> configs = new HashMap<>();
        configs.put(TEST_TASK, cfg);
        when(properties.getTaskWorkerConfigs()).thenReturn(configs);
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(10);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);
        systemTaskWorker.start();

        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of("p1", "p2"));

        CountDownLatch allStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            allStarted.countDown();
                            release.await();
                            return null;
                        })
                .when(asyncSystemTaskExecutor)
                .execute(any(), anyString());

        // First poll: 2 tasks dispatched, semaphore exhausted.
        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);
        assertTrue("Both tasks must start within 5 s", allStarted.await(5, TimeUnit.SECONDS));
        assertEquals(
                "Semaphore must be exhausted after 2 in-flight tasks",
                0,
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots());

        // Second poll attempt: batchSize = min(0, maxPollCount) = 0 → must return early, no pop.
        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);
        verify(queueDAO, Mockito.times(1)).pop(anyString(), anyInt(), anyInt());

        // Release tasks → permits restored.
        release.countDown();
        Thread.sleep(200);
        assertEquals(
                "Configured permit count restored after tasks complete",
                2,
                systemTaskWorker.getExecutionConfig(TEST_TASK).getSemaphoreUtil().availableSlots());
    }

    @Test
    public void testCaseInsensitiveTaskWorkerConfigLookup() {
        // Config keyed with uppercase "SYSTEM_TASK" must apply to a queue named "system_task".
        ConductorProperties.TaskWorkerConfig cfg = new ConductorProperties.TaskWorkerConfig();
        cfg.setPermitCount(4);
        Map<String, ConductorProperties.TaskWorkerConfig> configs = new HashMap<>();
        configs.put("SYSTEM_TASK", cfg);
        when(properties.getTaskWorkerConfigs()).thenReturn(configs);
        when(properties.getSystemTaskWorkerThreadCount()).thenReturn(10);
        systemTaskWorker =
                new SystemTaskWorker(
                        queueDAO, asyncSystemTaskExecutor, properties, executionService);

        // TEST_TASK = "system_task" — lower-case; config key is "SYSTEM_TASK" — upper-case.
        ExecutionConfig execConfig = systemTaskWorker.getExecutionConfig(TEST_TASK);
        assertEquals(
                "Case-insensitive lookup must apply the configured permitCount",
                4,
                execConfig.getSemaphoreUtil().availableSlots());
        // Still uses the shared pool (no threadCount override).
        assertSame(
                execConfig.getExecutorService(),
                systemTaskWorker.getExecutionConfig("OTHER_TASK").getExecutorService());
    }

    @Test
    public void testPermitAcquireFailureRequeuesPolledTasks() {
        // Simulate a race where availableSlots() > 0 but acquireSlots() fails (e.g. another thread
        // sneaked in). Tasks must be reset so they are immediately re-deliverable — not stuck
        // invisible for the full 30-second unack timeout.
        SemaphoreUtil mockSemaphore = mock(SemaphoreUtil.class);
        when(mockSemaphore.availableSlots()).thenReturn(5);
        when(mockSemaphore.acquireSlots(anyInt())).thenReturn(false);

        ExecutionConfig spyConfig =
                new ExecutionConfig(
                        systemTaskWorker.getExecutionConfig(TEST_TASK).getExecutorService(),
                        mockSemaphore);
        systemTaskWorker.queueExecutionConfigMap.put(TEST_TASK, spyConfig);

        when(queueDAO.pop(anyString(), anyInt(), anyInt())).thenReturn(List.of("r1", "r2"));

        systemTaskWorker.pollAndExecute(new TestTask(), TEST_TASK);

        // Tasks must be reset, not left invisible.
        verify(queueDAO).resetOffsetTime(TEST_TASK, "r1");
        verify(queueDAO).resetOffsetTime(TEST_TASK, "r2");
        // Nothing should have been dispatched.
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
