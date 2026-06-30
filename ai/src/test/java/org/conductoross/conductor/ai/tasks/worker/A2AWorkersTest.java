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
package org.conductoross.conductor.ai.tasks.worker;

import org.conductoross.conductor.ai.a2a.A2AService;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.AgentCard;
import org.conductoross.conductor.ai.a2a.model.TaskState;
import org.conductoross.conductor.ai.a2a.model.TaskStatus;
import org.conductoross.conductor.ai.model.A2AAgentCardRequest;
import org.conductoross.conductor.ai.model.A2ACancelRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class A2AWorkersTest {

    private A2AService a2aService;
    private A2AWorkers workers;

    @BeforeEach
    void setUp() {
        a2aService = mock(A2AService.class);
        workers = new A2AWorkers(a2aService);
    }

    @Test
    void getAgentCard_returnsCard() {
        AgentCard card = new AgentCard();
        card.setName("Currency Agent");
        when(a2aService.getAgentCard(eq("http://agent"), any())).thenReturn(card);

        A2AAgentCardRequest request = new A2AAgentCardRequest();
        request.setAgentUrl("http://agent");

        AgentCard result = workers.getAgentCard(request);

        assertEquals("Currency Agent", result.getName());
    }

    @Test
    void getAgentCard_missingAgentUrl_isNonRetryable() {
        assertThrows(
                NonRetryableException.class, () -> workers.getAgentCard(new A2AAgentCardRequest()));
    }

    @Test
    void cancelAgentTask_callsService() {
        A2ATask cancelled = new A2ATask();
        TaskStatus status = new TaskStatus();
        status.setState(TaskState.CANCELED);
        cancelled.setStatus(status);
        when(a2aService.cancelTask(eq("http://agent"), eq("t1"), any())).thenReturn(cancelled);

        A2ACancelRequest request = new A2ACancelRequest();
        request.setAgentUrl("http://agent");
        request.setTaskId("t1");

        A2ATask result = workers.cancelAgentTask(request);

        assertEquals(TaskState.CANCELED, result.getStatus().getState());
        verify(a2aService).cancelTask(eq("http://agent"), eq("t1"), any());
    }

    @Test
    void cancelAgentTask_missingTaskId_isNonRetryable() {
        A2ACancelRequest request = new A2ACancelRequest();
        request.setAgentUrl("http://agent");

        assertThrows(NonRetryableException.class, () -> workers.cancelAgentTask(request));
    }
}
