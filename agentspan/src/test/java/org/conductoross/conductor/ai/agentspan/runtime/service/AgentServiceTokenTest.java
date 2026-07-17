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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.context.RequestContext;
import org.conductoross.conductor.ai.agentspan.runtime.context.RequestContextHolder;
import org.conductoross.conductor.ai.agentspan.runtime.normalizer.NormalizerRegistry;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTokenTest {

    @Mock private AgentCompiler agentCompiler;

    @Mock private com.netflix.conductor.dao.ExecutionDAO executionDAO;

    @Mock private com.netflix.conductor.dao.MetadataDAO metadataDAO;

    @Mock private com.netflix.conductor.service.WorkflowService workflowService;

    @Mock private com.netflix.conductor.service.TaskService taskService;

    @Mock private AgentStreamRegistry streamRegistry;

    @Mock private NormalizerRegistry normalizerRegistry;

    @Mock private com.netflix.conductor.core.execution.WorkflowExecutor workflowExecutor;

    @Mock private SkillRegistryService skillRegistryService;

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
                        mock(com.netflix.conductor.dao.IndexDAO.class),
                        workflowService,
                        taskService,
                        workflowExecutor,
                        streamRegistry,
                        skillRegistryService,
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
    void start_neverAddsCredentialsToWorkflowInput() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test_agent");
        def.setVersion(1);
        when(agentCompiler.compile(any())).thenReturn(def);
        when(workflowExecutor.startAgentExecution(any(), any(), any(), any()))
                .thenReturn(
                        AgentStartResponse.builder()
                                .executionId("wf-xyz")
                                .agentName("test_agent")
                                .build());

        AgentConfig config =
                AgentConfig.builder().name("test_agent").model("openai/gpt-4o").build();
        AgentStartRequest req =
                AgentStartRequest.builder().agentConfig(config).prompt("hello").build();

        agentService.start(req);

        // AgentService forwards the same request/config it was given to the executor — the
        // downstream workflow input is built entirely inside WorkflowExecutor.startAgentExecution
        // (see WorkflowExecutorOps), which only ever puts prompt/media/context/session_id/cwd into
        // the child workflow's input, never credentials.
        verify(workflowExecutor).startAgentExecution(same(req), same(config), same(def), isNull());
        assertThat(config.getCredentials()).isNull();
    }

    @Test
    void start_rejectsBlankInputWithoutMediaOrContext() {
        AgentStartRequest req =
                AgentStartRequest.builder()
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

        verifyNoInteractions(agentCompiler, workflowService);
    }

    @Test
    void start_includesContextInWorkflowInput() {
        WorkflowDef def = new WorkflowDef();
        def.setName("test_agent");
        def.setVersion(1);
        when(agentCompiler.compile(any())).thenReturn(def);
        when(workflowExecutor.startAgentExecution(any(), any(), any(), any()))
                .thenReturn(
                        AgentStartResponse.builder()
                                .executionId("wf-xyz")
                                .agentName("test_agent")
                                .build());

        AgentStartRequest req =
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder()
                                        .name("test_agent")
                                        .model("openai/gpt-4o")
                                        .build())
                        .prompt("hello")
                        .context(Map.of("repo", "acme"))
                        .build();

        agentService.start(req);

        // The context lands in the child workflow's input downstream, inside
        // WorkflowExecutor.startAgentExecution — confirm AgentService forwards the request
        // (and its context) through unchanged.
        ArgumentCaptor<AgentStartRequest> captor = ArgumentCaptor.forClass(AgentStartRequest.class);
        verify(workflowExecutor).startAgentExecution(captor.capture(), any(), any(), any());
        assertThat(captor.getValue().getContext()).isEqualTo(Map.of("repo", "acme"));
    }

    @Test
    void start_registeredAgent_usesRequestedVersionWithoutCompilingOrRegistering() {
        WorkflowDef registered = registeredAgent("deployed_agent", 3);
        registered.setTimeoutSeconds(600);
        WorkflowTask worker = new WorkflowTask();
        worker.setName("lookup_customer");
        worker.setTaskReferenceName("lookup_customer_ref");
        worker.setType("SIMPLE");
        registered.setTasks(List.of(worker));

        when(metadataDAO.getWorkflowDef("deployed_agent", 3)).thenReturn(Optional.of(registered));
        when(workflowExecutor.startAgentExecution(any(), any(), any(), any()))
                .thenReturn(
                        AgentStartResponse.builder()
                                .executionId("wf-123")
                                .agentName("deployed_agent")
                                .build());

        AgentStartRequest req =
                AgentStartRequest.builder()
                        .name("deployed_agent")
                        .version(3)
                        .prompt("hello")
                        .context(Map.of("customer", "c1"))
                        .runId("run-7")
                        .timeoutSeconds(90)
                        .build();
        agentService.start(req);

        // taskToDomain/context->workflow-input wiring now happens inside
        // WorkflowExecutor.startAgentExecution; confirm AgentService resolved the right
        // definition, applied the per-call timeout to a COPY (not the cached def), and forwarded
        // the original request unchanged.
        ArgumentCaptor<WorkflowDef> defCaptor = ArgumentCaptor.forClass(WorkflowDef.class);
        verify(workflowExecutor).startAgentExecution(same(req), any(), defCaptor.capture(), any());
        WorkflowDef executionDef = defCaptor.getValue();
        assertThat(executionDef.getName()).isEqualTo("deployed_agent");
        assertThat(executionDef.getVersion()).isEqualTo(3);
        assertThat(executionDef.getTimeoutSeconds()).isEqualTo(90);
        assertThat(registered.getTimeoutSeconds()).isEqualTo(600);
        verifyNoInteractions(agentCompiler);
        verify(metadataDAO, never()).updateWorkflowDef(any());
        verifyNoInteractions(metadataService);
    }

    @Test
    void start_registeredAgent_usesLatestVersionWhenVersionIsOmitted() {
        WorkflowDef registered = registeredAgent("deployed_agent", 5);
        when(metadataDAO.getLatestWorkflowDef("deployed_agent"))
                .thenReturn(Optional.of(registered));
        when(workflowExecutor.startAgentExecution(any(), any(), any(), any()))
                .thenReturn(
                        AgentStartResponse.builder()
                                .executionId("wf-456")
                                .agentName("deployed_agent")
                                .build());

        agentService.start(
                AgentStartRequest.builder().name("deployed_agent").prompt("hello").build());

        ArgumentCaptor<WorkflowDef> defCaptor = ArgumentCaptor.forClass(WorkflowDef.class);
        verify(workflowExecutor).startAgentExecution(any(), any(), defCaptor.capture(), any());
        assertThat(defCaptor.getValue().getVersion()).isEqualTo(5);
        verify(metadataDAO, never()).getWorkflowDef(anyString(), anyInt());
    }

    @Test
    void start_rejectsAmbiguousOrInvalidRegisteredIdentity() {
        AgentStartRequest ambiguous =
                AgentStartRequest.builder()
                        .name("deployed_agent")
                        .version(1)
                        .agentConfig(AgentConfig.builder().name("inline").build())
                        .prompt("hello")
                        .build();
        assertThatThrownBy(() -> agentService.start(ambiguous))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not both");

        AgentStartRequest versionOnly =
                AgentStartRequest.builder().version(1).prompt("hello").build();
        assertThatThrownBy(() -> agentService.start(versionOnly))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires agentName");

        when(metadataDAO.getWorkflowDef("missing", 9)).thenReturn(Optional.empty());
        AgentStartRequest missing =
                AgentStartRequest.builder().name("missing").version(9).prompt("hello").build();
        assertThatThrownBy(() -> agentService.start(missing))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("missing v9");
    }

    private static WorkflowDef registeredAgent(String name, int version) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(version);
        def.setTasks(List.of());
        def.setMetadata(
                Map.of(
                        "agent_sdk",
                        "conductor",
                        "agentDef",
                        Map.of("name", name, "model", "openai/gpt-4o")));
        return def;
    }
}
