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
package com.netflix.conductor.postgres.dao;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.AsyncSystemTaskExecutor;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.postgres.config.PostgresConfiguration;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A system task whose start() blocks for longer than responseTimeout must get one start() call. A
 * pause between the reserve and the write of updateTime makes the overrun check fail.
 */
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            PostgresConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest
public class PostgresSystemTaskOverrunTest {

    private static final String TASK_TYPE = "OVERRUN_REPRO";
    private static final int RESPONSE_TIMEOUT_SECONDS = 3;
    private static final int START_BLOCKS_MS = 6000;
    private static final int PAUSE_AFTER_RESERVE_MS = 300;

    @Autowired private PostgresQueueDAO queueDAO;

    @Test
    public void overrunningStartIsNotCalledAgainAfterAPauseBeforeTheStart() throws Exception {
        var scheduled = new TaskModel();
        scheduled.setTaskId("overrun-task");
        scheduled.setTaskType(TASK_TYPE);
        scheduled.setTaskDefName(TASK_TYPE);
        scheduled.setReferenceTaskName("overrun");
        scheduled.setWorkflowInstanceId("overrun-workflow");
        scheduled.setStatus(TaskModel.Status.SCHEDULED);
        scheduled.setResponseTimeoutSeconds(RESPONSE_TIMEOUT_SECONDS);
        scheduled.setInputData(new HashMap<>());
        var stored = new AtomicReference<>(scheduled);

        var workflow = new WorkflowModel();
        workflow.setWorkflowId("overrun-workflow");
        workflow.setStatus(WorkflowModel.Status.RUNNING);

        var facade = mock(ExecutionDAOFacade.class);
        when(facade.getTaskModel(anyString())).thenAnswer(i -> stored.get().copy());
        when(facade.getWorkflowModel(anyString(), anyBoolean())).thenReturn(workflow);
        // same rule as ExecutionDAOFacade.updateTask: a non-terminal write sets updateTime
        doAnswer(
                        i -> {
                            TaskModel task = i.getArgument(0);
                            if (!task.getStatus().isTerminal() || task.getUpdateTime() == 0) {
                                task.setUpdateTime(System.currentTimeMillis());
                            }
                            stored.set(task.copy());
                            return null;
                        })
                .when(facade)
                .updateTask(any(TaskModel.class));

        var parametersUtils = mock(ParametersUtils.class);
        when(parametersUtils.substituteSecrets(any()))
                .thenAnswer(
                        i -> {
                            // a pause after the reserve: a GC pause or a slow secrets lookup
                            Thread.sleep(PAUSE_AFTER_RESERVE_MS);
                            return i.getArgument(0);
                        });

        var properties = new ConductorProperties();
        var executor =
                new AsyncSystemTaskExecutor(
                        facade,
                        queueDAO,
                        mock(MetadataDAO.class),
                        properties,
                        mock(WorkflowExecutor.class),
                        parametersUtils);

        var starts = new AtomicInteger();
        var slowTask =
                new WorkflowSystemTask(TASK_TYPE) {
                    @Override
                    public void start(
                            WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                        starts.incrementAndGet();
                        try {
                            Thread.sleep(START_BLOCKS_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        task.setStatus(TaskModel.Status.COMPLETED);
                    }

                    @Override
                    public boolean isAsync() {
                        return true;
                    }
                };

        var queueName = QueueUtils.getQueueName(scheduled);
        queueDAO.remove(queueName, scheduled.getTaskId());
        queueDAO.push(queueName, scheduled.getTaskId(), 0);

        // the poll loop of SystemTaskWorker, with its default interval and pop timeout
        var pollIntervalMs = properties.getSystemTaskWorkerPollInterval().toMillis();
        var popTimeoutMs = (int) properties.getSystemTaskQueuePopTimeout().toMillis();
        var pool = Executors.newFixedThreadPool(4);
        var deadline = System.currentTimeMillis() + START_BLOCKS_MS + 1000;
        while (System.currentTimeMillis() < deadline) {
            for (var taskId : queueDAO.pop(queueName, 1, popTimeoutMs)) {
                pool.submit(() -> executor.execute(slowTask, taskId));
            }
            Thread.sleep(pollIntervalMs);
        }
        pool.shutdown();
        pool.awaitTermination(2 * START_BLOCKS_MS, TimeUnit.MILLISECONDS);
        queueDAO.remove(queueName, scheduled.getTaskId());

        assertEquals("start() calls for one task", 1, starts.get());
    }
}
