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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.normalizer.NormalizerRegistry;
import org.conductoross.conductor.ai.agentspan.runtime.util.AgentExecutionTokenUsageAggregator;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.conductoross.conductor.dao.SecretsDAO;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.TaskService;
import com.netflix.conductor.service.WorkflowService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link AgentService#listAgents()} carries each agent's introduction from its definition. */
class AgentServiceListAgentsTest {

    private final MetadataDAO metadataDAO = mock(MetadataDAO.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final AgentService agentService =
            new AgentService(
                    mock(AgentCompiler.class),
                    mock(NormalizerRegistry.class),
                    mock(ExecutionDAO.class),
                    metadataDAO,
                    workflowService,
                    new AgentExecutionTokenUsageAggregator(workflowService),
                    mock(TaskService.class),
                    mock(WorkflowExecutor.class),
                    mock(AgentStreamRegistry.class),
                    mock(SkillRegistryService.class),
                    mock(MetadataService.class),
                    mock(AzureFoundryAgentClient.class),
                    mock(BedrockAgentClient.class),
                    mock(BedrockAgentCoreAgentClient.class),
                    mock(SecretsDAO.class));

    private static WorkflowDef agent(String name, int version, String introduction) {
        Map<String, Object> agentDef = new HashMap<>();
        agentDef.put("name", name);
        if (introduction != null) {
            agentDef.put("introduction", introduction);
        }
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(version);
        def.setMetadata(Map.of("agentDef", agentDef));
        return def;
    }

    @Test
    void eachAgent_carriesTheIntroductionOfItsLatestVersion() {
        WorkflowDef plain = new WorkflowDef();
        plain.setName("plain_flow");
        when(metadataDAO.getAllWorkflowDefs())
                .thenReturn(
                        List.of(
                                agent("greeter", 1, "Says hello."),
                                agent("greeter", 2, "Greets people."),
                                agent("quiet", 1, null),
                                agent("blank", 1, " "),
                                plain));

        Map<String, String> introductions = new HashMap<>();
        for (AgentSummary summary : agentService.listAgents()) {
            introductions.put(summary.getName(), summary.getIntroduction());
        }

        assertThat(introductions)
                .containsOnlyKeys("greeter", "quiet", "blank")
                .containsEntry("greeter", "Greets people.")
                .containsEntry("quiet", null)
                .containsEntry("blank", null);
    }

    @Test
    void onlyTheRestList_computesTheChecksum() {
        when(metadataDAO.getAllWorkflowDefs()).thenReturn(List.of(agent("greeter", 1, "Hi.")));

        assertThat(agentService.listAgents())
                .singleElement()
                .satisfies(
                        summary ->
                                assertThat(summary.getChecksum()).hasSize(64).matches("[0-9a-f]+"));
        assertThat(agentService.listAgentsWithoutChecksum())
                .singleElement()
                .satisfies(
                        summary -> {
                            assertThat(summary.getChecksum()).isNull();
                            assertThat(summary.getIntroduction()).isEqualTo("Hi.");
                        });
    }
}
