/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.execution.tasks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import org.junit.Before;
import org.junit.Test;

public class TestSystemTaskWorkerCoordinator {

    private static final String TEST_QUEUE = "test";
    private static final String EXECUTION_NAMESPACE_CONSTANT = "@exeNS";
    private static final String ISOLATION_CONSTANT = "-iso";

    private QueueDAO queueDAO;
    private WorkflowExecutor workflowExecutor;
    private ExecutionService executionService;

    @Before
    public void setUp() {
        queueDAO = mock(QueueDAO.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        executionService = mock(ExecutionService.class);
    }

    @Test
    public void isSystemTask() {
        createTaskMapping();
        SystemTaskWorkerCoordinator systemTaskWorkerCoordinator = new SystemTaskWorkerCoordinator(queueDAO,
            workflowExecutor, mock(Configuration.class), executionService);
        assertTrue(systemTaskWorkerCoordinator.isAsyncSystemTask(TEST_QUEUE + ISOLATION_CONSTANT));
    }

    @Test
    public void isSystemTaskNotPresent() {
        createTaskMapping();
        SystemTaskWorkerCoordinator systemTaskWorkerCoordinator = new SystemTaskWorkerCoordinator(queueDAO,
            workflowExecutor, mock(Configuration.class), executionService);
        assertFalse(systemTaskWorkerCoordinator.isAsyncSystemTask(null));
    }

    @Test
    public void testIsFromCoordinatorExecutionNameSpace() {
        System.setProperty("workflow.system.task.worker.executionNameSpace", "exeNS");
        Configuration configuration = new SystemPropertiesConfiguration();
        SystemTaskWorkerCoordinator systemTaskWorkerCoordinator = new SystemTaskWorkerCoordinator(queueDAO,
            workflowExecutor, configuration, executionService);
        assertTrue(systemTaskWorkerCoordinator.isFromCoordinatorExecutionNameSpace(TEST_QUEUE + EXECUTION_NAMESPACE_CONSTANT));
    }

    private void createTaskMapping() {
        WorkflowSystemTask mockWorkflowTask = mock(WorkflowSystemTask.class);
        when(mockWorkflowTask.getName()).thenReturn(TEST_QUEUE);
        when(mockWorkflowTask.isAsync()).thenReturn(true);
        SystemTaskWorkerCoordinator.taskNameWorkflowTaskMapping.put(TEST_QUEUE, mockWorkflowTask);
    }
}
