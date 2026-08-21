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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.normalizer.NormalizerRegistry;
import org.conductoross.conductor.ai.agentspan.runtime.util.AgentExecutionTokenUsageAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;
import org.conductoross.conductor.dao.SecretsDAO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentService#openStream} — verifies that SSE streams are rejected for
 * unknown execution IDs (issue #1334).
 */
class AgentServiceOpenStreamTest {

    private WorkflowService workflowService;
    private AgentStreamRegistry streamRegistry;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        workflowService = mock(WorkflowService.class);
        streamRegistry = mock(AgentStreamRegistry.class);
        agentService =
                new AgentService(
                        mock(AgentCompiler.class),
                        mock(NormalizerRegistry.class),
                        mock(ExecutionDAO.class),
                        mock(MetadataDAO.class),
                        workflowService,
                        new AgentExecutionTokenUsageAggregator(workflowService),
                        mock(TaskService.class),
                        mock(WorkflowExecutor.class),
                        streamRegistry,
                        mock(SkillRegistryService.class),
                        mock(MetadataService.class),
                        mock(AzureFoundryAgentClient.class),
                        mock(BedrockAgentClient.class),
                        mock(SecretsDAO.class));
    }

    @Test
    void openStream_throwsNotFoundForUnknownExecutionId() {
        when(workflowService.getExecutionStatus(eq("bad-id"), anyBoolean()))
                .thenThrow(new NotFoundException("bad-id"));

        assertThatThrownBy(() -> agentService.openStream("bad-id", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void openStream_doesNotRegisterEmitterWhenExecutionNotFound() {
        when(workflowService.getExecutionStatus(eq("bad-id"), anyBoolean()))
                .thenThrow(new NotFoundException("bad-id"));

        try {
            agentService.openStream("bad-id", null);
        } catch (NotFoundException ignored) {
        }

        verify(streamRegistry, never()).register(any(), any());
    }

    @Test
    void openStream_registersEmitterWhenExecutionExists() {
        SseEmitter emitter = new SseEmitter();
        when(workflowService.getExecutionStatus(eq("good-id"), anyBoolean())).thenReturn(null);
        when(streamRegistry.register(eq("good-id"), eq(null))).thenReturn(emitter);

        SseEmitter result = agentService.openStream("good-id", null);

        assertThat(result).isSameAs(emitter);
        verify(streamRegistry).register("good-id", null);
    }
}
