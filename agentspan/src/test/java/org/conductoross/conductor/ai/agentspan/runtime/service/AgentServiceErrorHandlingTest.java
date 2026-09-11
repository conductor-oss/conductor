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
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.dao.SecretsDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for error handling in {@link AgentService} — verifies that client errors produce
 * meaningful HTTP-mappable exceptions instead of NPEs (issue #1332).
 */
class AgentServiceErrorHandlingTest {

    private ExecutionDAO executionDAO;
    private WorkflowService workflowService;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        executionDAO = mock(ExecutionDAO.class);
        workflowService = mock(WorkflowService.class);
        agentService =
                new AgentService(
                        mock(AgentCompiler.class),
                        mock(NormalizerRegistry.class),
                        executionDAO,
                        mock(MetadataDAO.class),
                        workflowService,
                        new AgentExecutionTokenUsageAggregator(workflowService),
                        mock(TaskService.class),
                        mock(WorkflowExecutor.class),
                        mock(AgentStreamRegistry.class),
                        mock(SkillRegistryService.class),
                        mock(MetadataService.class),
                        mock(AzureFoundryAgentClient.class),
                        mock(BedrockAgentClient.class),
                        mock(SecretsDAO.class));
    }

    // ── stop / signal with unknown execution ID ──────────────────────

    @Test
    void stopAgent_throwsNotFoundForUnknownExecutionId() {
        when(executionDAO.getWorkflow(eq("bad-id"), anyBoolean())).thenReturn(null);

        assertThatThrownBy(() -> agentService.stopAgent("bad-id"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void signalAgent_throwsNotFoundForUnknownExecutionId() {
        when(executionDAO.getWorkflow(eq("bad-id"), anyBoolean())).thenReturn(null);

        assertThatThrownBy(() -> agentService.signalAgent("bad-id", "hello"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── compile / start with missing agentConfig ─────────────────────

    @Test
    void compile_throwsIllegalArgumentWhenAgentConfigIsNull() {
        assertThatThrownBy(() -> agentService.compile(new AgentStartRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentConfig is required");
    }

    @Test
    void start_throwsIllegalArgumentWhenNoAgentSpecified() {
        AgentStartRequest request = new AgentStartRequest();
        request.setPrompt("hello");
        assertThatThrownBy(() -> agentService.start(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentName or inline agent construction details");
    }

    // ── resume on non-PAUSED execution ───────────────────────────────

    @Test
    void resumeAgent_throwsConflictWhenWorkflowIsNotPaused() {
        doThrow(
                        new IllegalStateException(
                                "The workflow completed-id is not PAUSED so cannot resume."))
                .when(workflowService)
                .resumeWorkflow(eq("completed-id"));

        assertThatThrownBy(() -> agentService.resumeAgent("completed-id"))
                .isInstanceOf(ConflictException.class);
    }
}
