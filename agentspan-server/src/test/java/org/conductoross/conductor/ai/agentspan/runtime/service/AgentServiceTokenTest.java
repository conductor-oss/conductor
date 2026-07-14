/*
 * Copyright 2025 Conductor Authors.
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

import java.time.Instant;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.context.RequestContext;
import org.conductoross.conductor.ai.agentspan.runtime.context.RequestContextHolder;
import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.conductoross.conductor.ai.agentspan.runtime.model.StartRequest;
import org.conductoross.conductor.ai.agentspan.runtime.normalizer.NormalizerRegistry;
import org.conductoross.conductor.ai.agentspan.runtime.util.ProviderValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTokenTest {

    @Mock private com.netflix.conductor.core.execution.WorkflowExecutor workflowExecutor;

    @Mock private AgentCompiler agentCompiler;

    @Mock private com.netflix.conductor.dao.ExecutionDAO executionDAO;

    @Mock private com.netflix.conductor.dao.MetadataDAO metadataDAO;

    @Mock private com.netflix.conductor.service.WorkflowService workflowService;

    @Mock private com.netflix.conductor.service.ExecutionService executionService;

    @Mock private AgentStreamRegistry streamRegistry;

    @Mock private NormalizerRegistry normalizerRegistry;

    @Mock private ProviderValidator providerValidator;

    @Mock private SkillRegistryService skillRegistryService;

    @Mock private com.netflix.conductor.core.utils.IDGenerator idGenerator;

    @Mock private com.netflix.conductor.service.MetadataService metadataService;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService =
                new AgentService(
                        agentCompiler,
                        normalizerRegistry,
                        executionDAO,
                        metadataDAO,
                        workflowExecutor,
                        workflowService,
                        streamRegistry,
                        executionService,
                        providerValidator,
                        skillRegistryService,
                        idGenerator,
                        metadataService);

        RequestContextHolder.set(
                RequestContext.builder()
                        .requestId("r1")
                        .userId("user-999")
                        .createdAt(Instant.now())
                        .build());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void start_withoutRequestCredentials_omitsCredentialsInput() {
        com.netflix.conductor.common.metadata.workflow.WorkflowDef def =
                new com.netflix.conductor.common.metadata.workflow.WorkflowDef();
        def.setName("test_agent");
        def.setVersion(1);
        when(agentCompiler.compile(any())).thenReturn(def);
        when(workflowExecutor.startWorkflow(any())).thenReturn("wf-xyz");
        when(providerValidator.validateProvider(any())).thenReturn(java.util.Optional.empty());

        StartRequest req =
                StartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name("test_agent")
                                        .model("openai/gpt-4o")
                                        .build())
                        .prompt("hello")
                        .build();

        agentService.start(req);

        ArgumentCaptor<com.netflix.conductor.core.execution.StartWorkflowInput> captor =
                ArgumentCaptor.forClass(
                        com.netflix.conductor.core.execution.StartWorkflowInput.class);
        verify(workflowExecutor).startWorkflow(captor.capture());

        Map<String, Object> input = captor.getValue().getWorkflowInput();
        assertThat(input).doesNotContainKey("credentials");
    }

    @Test
    void start_rejectsBlankInputWithoutMediaOrContext() {
        StartRequest req =
                StartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name("test_agent")
                                        .model("openai/gpt-4o")
                                        .build())
                        .prompt("   ")
                        .build();

        assertThatThrownBy(() -> agentService.start(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty prompt");

        verifyNoInteractions(agentCompiler, workflowExecutor);
    }

    @Test
    void start_includesContextInWorkflowInput() {
        com.netflix.conductor.common.metadata.workflow.WorkflowDef def =
                new com.netflix.conductor.common.metadata.workflow.WorkflowDef();
        def.setName("test_agent");
        def.setVersion(1);
        when(agentCompiler.compile(any())).thenReturn(def);
        when(workflowExecutor.startWorkflow(any())).thenReturn("wf-xyz");
        when(providerValidator.validateProvider(any())).thenReturn(java.util.Optional.empty());

        StartRequest req =
                StartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name("test_agent")
                                        .model("openai/gpt-4o")
                                        .build())
                        .prompt("hello")
                        .context(Map.of("repo", "acme"))
                        .build();

        agentService.start(req);

        ArgumentCaptor<com.netflix.conductor.core.execution.StartWorkflowInput> captor =
                ArgumentCaptor.forClass(
                        com.netflix.conductor.core.execution.StartWorkflowInput.class);
        verify(workflowExecutor).startWorkflow(captor.capture());

        Map<String, Object> input = captor.getValue().getWorkflowInput();
        assertThat(input.get("context")).isEqualTo(Map.of("repo", "acme"));
    }
}
