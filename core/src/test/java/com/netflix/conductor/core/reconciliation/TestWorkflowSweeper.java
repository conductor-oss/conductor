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
package com.netflix.conductor.core.reconciliation;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.QueueDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestWorkflowSweeper {

    private ConductorProperties properties;
    private WorkflowExecutor workflowExecutor;
    private QueueDAO queueDAO;
    private ExecutionDAO executionDAO;
    private SweeperProperties sweeperProperties;
    private SystemTaskRegistry systemTaskRegistry;
    private ObjectMapper objectMapper;
    private Executor sweeperExecutor;

    @Before
    public void setUp() {
        properties = mock(ConductorProperties.class);
        workflowExecutor = mock(WorkflowExecutor.class);
        queueDAO = mock(QueueDAO.class);
        executionDAO = mock(ExecutionDAO.class);
        sweeperProperties = mock(SweeperProperties.class);
        systemTaskRegistry = mock(SystemTaskRegistry.class);
        objectMapper = mock(ObjectMapper.class);
        sweeperExecutor = mock(Executor.class);

        when(properties.getSweeperThreadCount()).thenReturn(0); // Don't start threads in tests
        when(properties.getWorkflowOffsetTimeout()).thenReturn(Duration.ofSeconds(30));
        when(properties.getLockLeaseTime()).thenReturn(Duration.ofSeconds(60));
        when(sweeperProperties.getSweepBatchSize()).thenReturn(2);
        when(sweeperProperties.getQueuePopTimeout()).thenReturn(100);
    }

    @Test
    public void testWorkflowSweeperInitialization() {
        // Test that WorkflowSweeper can be instantiated
        WorkflowSweeper workflowSweeper =
                new WorkflowSweeper(
                        sweeperExecutor,
                        queueDAO,
                        workflowExecutor,
                        executionDAO,
                        properties,
                        sweeperProperties,
                        systemTaskRegistry,
                        objectMapper);

        // Basic test to ensure constructor works
        // The actual sweep logic is tested via integration tests
    }
}
