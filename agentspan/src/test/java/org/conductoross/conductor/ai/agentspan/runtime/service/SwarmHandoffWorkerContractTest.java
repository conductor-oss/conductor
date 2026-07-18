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

import java.lang.reflect.Method;
import java.util.*;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.normalizer.NormalizerRegistry;
import org.conductoross.conductor.common.metadata.agent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.service.MetadataService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Verifies that SWARM's compiler-owned controls never leak into worker deployment contracts. */
class SwarmHandoffWorkerContractTest {

    @Test
    void reportsAndRegistersOnlyDeclaredWorkersNotCompilerOwnedTransferControls() throws Exception {
        AgentConfig config = swarmConfig();
        AgentService service = service(mock(MetadataService.class));

        CompileResponse response =
                service.compile(AgentStartRequest.builder().agentConfig(config).build());

        assertThat(response.getRequiredWorkers())
                .contains("lookup_customer", "search_transfer_to_agent_b_notes", "evaluate_handoff")
                .doesNotContain(
                        "team_handoff_check",
                        "agent_a_check_transfer",
                        "team_transfer_to_agent_a",
                        "team_transfer_to_agent_b");

        MetadataService metadataService = mock(MetadataService.class);
        AgentService registrationService = service(metadataService);
        Method method =
                AgentService.class.getDeclaredMethod("registerTaskDefinitions", AgentConfig.class);
        method.setAccessible(true);
        method.invoke(registrationService, config);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskDef>> captor = ArgumentCaptor.forClass(List.class);
        verify(metadataService, atLeastOnce()).registerTaskDef(captor.capture());
        Set<String> registered = new HashSet<>();
        for (List<TaskDef> definitions : captor.getAllValues()) {
            for (TaskDef definition : definitions) {
                registered.add(definition.getName());
            }
        }

        assertThat(registered)
                .contains(
                        "lookup_customer", "search_transfer_to_agent_b_notes", "evaluate_handoff");
        assertThat(registered)
                .doesNotContain(
                        "team_handoff_check",
                        "agent_a_check_transfer",
                        "team_transfer_to_agent_a",
                        "team_transfer_to_agent_b");
    }

    private static AgentConfig swarmConfig() {
        return AgentConfig.builder()
                .name("team")
                .model("openai/gpt-4o")
                .strategy(AgentConfig.Strategy.SWARM)
                .tools(
                        List.of(
                                ToolConfig.builder()
                                        .name("lookup_customer")
                                        .description("lookup")
                                        .toolType("worker")
                                        .build(),
                                ToolConfig.builder()
                                        .name("search_transfer_to_agent_b_notes")
                                        .description("ordinary similarly named tool")
                                        .toolType("worker")
                                        .build()))
                .handoffs(
                        List.of(
                                HandoffConfig.builder()
                                        .type("on_condition")
                                        .target("agent_b")
                                        .taskName("evaluate_handoff")
                                        .build()))
                .agents(
                        List.of(
                                AgentConfig.builder()
                                        .name("agent_a")
                                        .model("openai/gpt-4o")
                                        .build(),
                                AgentConfig.builder()
                                        .name("agent_b")
                                        .model("openai/gpt-4o")
                                        .build()))
                .build();
    }

    private static AgentService service(MetadataService metadataService) {
        return new AgentService(
                new AgentCompiler(),
                mock(NormalizerRegistry.class),
                mock(com.netflix.conductor.dao.ExecutionDAO.class),
                mock(MetadataDAO.class),
                mock(com.netflix.conductor.dao.IndexDAO.class),
                mock(com.netflix.conductor.service.WorkflowService.class),
                mock(com.netflix.conductor.service.TaskService.class),
                mock(com.netflix.conductor.core.execution.WorkflowExecutor.class),
                mock(AgentStreamRegistry.class),
                mock(SkillRegistryService.class),
                metadataService);
    }
}
