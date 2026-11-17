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
package org.conductoross.conductor.ai.agentspan.mcp;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.service.AgentService;
import org.conductoross.conductor.ai.mcp.server.MCPTool;
import org.conductoross.conductor.ai.mcp.server.MCPToolArgumentsException;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.NotFoundException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolSourceTest {

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
    private AgentService agentService;
    private AgentToolSource source;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        source = new AgentToolSource(agentService);
    }

    private static AgentSummary agent(String name, String description) {
        AgentSummary summary = new AgentSummary();
        summary.setName(name);
        summary.setDescription(description);
        return summary;
    }

    @Test
    void agentsNeedNoOptIn() {
        when(agentService.listAgents()).thenReturn(List.of(agent("support_bot", null)));
        assertThat(source.listTools()).extracting(MCPTool::name).containsExactly("support_bot");
        assertThat(source.listTools().get(0).kind()).isEqualTo(MCPTool.Kind.AGENT);
    }

    @Test
    void schemaHasOneRequiredStringPrompt() {
        when(agentService.listAgents()).thenReturn(List.of(agent("support_bot", null)));
        JsonNode schema = source.listTools().get(0).inputSchema();
        assertThat(schema.get("type").asString()).isEqualTo("object");
        JsonNode prompt = schema.get("properties").get("prompt");
        assertThat(prompt.get("type").asString()).isEqualTo("string");
        assertThat(prompt.get("description").asString())
                .isEqualTo(AgentToolSource.PROMPT_DESCRIPTION);
        assertThat(schema.get("required")).hasSize(1);
        assertThat(schema.get("required").get(0).asString()).isEqualTo("prompt");
    }

    /** A compiled agent definition whose stored agent definition has this introduction. */
    private static WorkflowDef compiled(String name, String introduction) {
        Map<String, Object> agentDef = new java.util.HashMap<>();
        agentDef.put("name", name);
        if (introduction != null) {
            agentDef.put("introduction", introduction);
        }
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setDescription("Agent workflow for " + name);
        def.setMetadata(Map.of("agent_sdk", "conductor", "agentDef", agentDef));
        return def;
    }

    @Test
    void description_isTheAgentsIntroduction_whenItHasOne() {
        when(agentService.listAgents())
                .thenReturn(
                        List.of(
                                agent("panel", "Round-Robin discussion: panel"),
                                agent("quiet", "Agent workflow for quiet"),
                                agent("blank", "Agent workflow for blank")));
        when(agentService.getAgentDefinition("panel", 0))
                .thenReturn(compiled("panel", "A design review panel."));
        when(agentService.getAgentDefinition("quiet", 0)).thenReturn(compiled("quiet", null));
        when(agentService.getAgentDefinition("blank", 0)).thenReturn(compiled("blank", " "));

        assertThat(source.listTools())
                .extracting(MCPTool::description)
                .containsExactly(
                        "A design review panel.",
                        "Agent workflow for quiet",
                        "Agent workflow for blank");
    }

    @Test
    void findTool_usesTheIntroductionToo() {
        when(agentService.getAgentDefinition("panel", null))
                .thenReturn(compiled("panel", "A design review panel."));
        assertThat(source.findTool("panel"))
                .get()
                .extracting(MCPTool::description)
                .isEqualTo("A design review panel.");
    }

    @Test
    void description_isTheWorkflowDefinitionsAsInA2A() {
        when(agentService.listAgents())
                .thenReturn(
                        List.of(
                                agent("deployed", "Agent workflow for deployed"),
                                agent("no_text", null),
                                agent("blank", " ")));

        assertThat(source.listTools())
                .extracting(MCPTool::description)
                .containsExactly(
                        "Agent workflow for deployed",
                        "Conductor tool 'no_text'",
                        "Conductor tool 'blank'");
        verify(agentService, never()).getAgentDef(any(), any());
    }

    @Test
    void start_runsTheAgentWithThePrompt() {
        AgentStartResponse response = new AgentStartResponse();
        response.setExecutionId("exec-9");
        when(agentService.start(any(AgentStartRequest.class))).thenReturn(response);
        ObjectNode arguments = mapper.createObjectNode().put("prompt", "Where is my order?");

        assertThat(source.start("support_bot", arguments)).isEqualTo("exec-9");

        ArgumentCaptor<AgentStartRequest> request =
                ArgumentCaptor.forClass(AgentStartRequest.class);
        verify(agentService).start(request.capture());
        assertThat(request.getValue().getName()).isEqualTo("support_bot");
        assertThat(request.getValue().getPrompt()).isEqualTo("Where is my order?");
    }

    @Test
    void start_withoutAPrompt_isAnArgumentsError() {
        for (ObjectNode arguments :
                List.of(
                        mapper.createObjectNode(),
                        mapper.createObjectNode().put("prompt", " "),
                        mapper.createObjectNode().put("prompt", 3))) {
            assertThatThrownBy(() -> source.start("support_bot", arguments))
                    .isInstanceOf(MCPToolArgumentsException.class)
                    .hasMessageContaining("'prompt'");
        }
        verify(agentService, never()).start(any());
    }

    @Test
    void exposes_onlyDeployedAgents_readingOnlyThatDefinition() {
        stubDefinitions();
        assertThat(source.exposes("support_bot")).isTrue();
        assertThat(source.exposes("plain_flow")).isFalse();
        assertThat(source.exposes("other")).isFalse();
        verify(agentService, never()).getAgentDef(any(), any());
        verify(agentService, never()).listAgents();
    }

    @Test
    void findTool_onlyDeployedAgents_readingOnlyThatDefinition() {
        stubDefinitions();
        // The description comes from the definition, as listTools takes it from listAgents.
        assertThat(source.findTool("support_bot"))
                .get()
                .extracting(MCPTool::description)
                .isEqualTo("Agent workflow for support_bot");
        assertThat(source.findTool("plain_flow")).isEmpty();
        assertThat(source.findTool("other")).isEmpty();
        verify(agentService, never()).listAgents();
    }

    /** support_bot is an agent, plain_flow is not, other does not exist. */
    private void stubDefinitions() {
        WorkflowDef agentDef = new WorkflowDef();
        agentDef.setName("support_bot");
        agentDef.setMetadata(Map.of("classifier", "agent"));
        agentDef.setDescription("Agent workflow for support_bot");
        WorkflowDef plainDef = new WorkflowDef();
        plainDef.setName("plain_flow");
        when(agentService.getAgentDefinition("support_bot", null)).thenReturn(agentDef);
        when(agentService.getAgentDefinition("plain_flow", null)).thenReturn(plainDef);
        when(agentService.getAgentDefinition("other", null))
                .thenThrow(new NotFoundException("other"));
    }

    @Test
    void metadataIsTheAuthorsOwn_fromTheStoredAgentDefinition() {
        when(agentService.getAgentDef("slow", null))
                .thenReturn(Map.of("name", "slow", "metadata", Map.of("mcp.syncWaitSeconds", 0)));
        when(agentService.getAgentDef("plain", null)).thenReturn(Map.of("name", "plain"));
        when(agentService.getAgentDef("tagged", null)).thenThrow(new NotFoundException("tagged"));

        assertThat(source.metadata("slow")).containsEntry("mcp.syncWaitSeconds", 0);
        assertThat(source.metadata("plain")).isNull();
        assertThat(source.metadata("tagged")).isNull();
    }
}
