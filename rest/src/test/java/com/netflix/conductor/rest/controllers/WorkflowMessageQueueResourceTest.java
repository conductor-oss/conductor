/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.rest.controllers;

import java.util.Map;
import java.util.function.Consumer;

import org.junit.Test;
import org.springframework.http.ResponseEntity;

import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.WorkflowService;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Messages are pushed inside a decide, in its one acquisition of the workflow's lock, so a
 * completion cannot discard them; the same decide wakes whoever waits for them.
 */
public class WorkflowMessageQueueResourceTest {

    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final WorkflowMessageQueueDAO dao = mock(WorkflowMessageQueueDAO.class);
    private final WorkflowExecutor executor = mock(WorkflowExecutor.class);
    private final WorkflowMessageQueueResource resource =
            new WorkflowMessageQueueResource(workflowService, dao, executor);

    /** The executor's decide, which applies the change to the workflow as stored. */
    @SuppressWarnings("unchecked")
    private void stored(WorkflowModel.Status status) {
        WorkflowModel w = new WorkflowModel();
        w.setWorkflowId("wf");
        w.setStatus(status);
        when(executor.decide(eq("wf"), any(Consumer.class)))
                .thenAnswer(
                        call -> {
                            ((Consumer<WorkflowModel>) call.getArgument(1)).accept(w);
                            return w;
                        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aBusyWorkflowAsksTheClientToRetry() {
        when(executor.decide(eq("wf"), any(Consumer.class)))
                .thenThrow(new TransientException("busy"));
        ResponseEntity<String> response = resource.pushMessage("wf", Map.of("name", "close"));
        assertEquals(503, response.getStatusCode().value());
        verify(dao, never()).push(anyString(), any());
    }

    @Test
    public void pushesInsideTheDecide() {
        stored(WorkflowModel.Status.RUNNING);
        ResponseEntity<String> response = resource.pushMessage("wf", Map.of("name", "close"));
        assertEquals(200, response.getStatusCode().value());
        verify(dao).push(eq("wf"), any());
        verify(executor, never()).decide("wf"); // the push's own decide is the only one
    }

    @Test
    public void refusesAWorkflowThatHasEnded() {
        stored(WorkflowModel.Status.COMPLETED);
        ResponseEntity<String> response = resource.pushMessage("wf", Map.of("name", "close"));
        assertEquals(409, response.getStatusCode().value());
        verify(dao, never()).push(anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void anUnknownWorkflowIsNotFound() {
        when(executor.decide(eq("wf"), any(Consumer.class)))
                .thenThrow(new NotFoundException("No such workflow found by id: %s", "wf"));
        assertEquals(404, resource.pushMessage("wf", Map.of()).getStatusCode().value());
    }
}
