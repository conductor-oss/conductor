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
import org.conductoross.conductor.ai.mcp.server.McpServerException;
import org.conductoross.conductor.ai.mcp.server.McpTool;
import org.conductoross.conductor.common.metadata.agent.AgentStartRequest;
import org.conductoross.conductor.common.metadata.agent.AgentStartResponse;
import org.conductoross.conductor.common.metadata.agent.AgentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.config.ObjectMapperProvider;
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
        assertThat(source.listTools()).extracting(McpTool::name).containsExactly("support_bot");
        assertThat(source.listTools().get(0).kind()).isEqualTo(McpTool.Kind.AGENT);
    }

    @Test
    void schemaHasOneRequiredStringPrompt() {
        when(agentService.listAgents()).thenReturn(List.of(agent("support_bot", null)));
        JsonNode schema = source.listTools().get(0).inputSchema();
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("properties").get("prompt").get("type").asString())
                .isEqualTo("string");
        assertThat(schema.get("required")).hasSize(1);
        assertThat(schema.get("required").get(0).asString()).isEqualTo("prompt");
    }

    @Test
    void description_isTheAuthorsNotTheCompilers() {
        when(agentService.listAgents())
                .thenReturn(
                        List.of(
                                agent("deployed", "Agent workflow for deployed"),
                                agent("no_text", "Agent workflow for no_text"),
                                agent("blank", "Agent workflow for blank"),
                                agent("tagged", "Answers questions")));
        when(agentService.getAgentDef("deployed", 0))
                .thenReturn(Map.of("name", "deployed", "description", "Tracks orders"));
        when(agentService.getAgentDef("no_text", 0)).thenReturn(Map.of("name", "no_text"));
        when(agentService.getAgentDef("blank", 0))
                .thenReturn(Map.of("name", "blank", "description", " "));
        when(agentService.getAgentDef("tagged", 0))
                .thenThrow(new NotFoundException("No agent definition found for: tagged"));

        assertThat(source.listTools())
                .extracting(McpTool::description)
                .containsExactly(
                        "Tracks orders",
                        "Conductor tool 'no_text'",
                        "Conductor tool 'blank'",
                        "Answers questions");
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
    void start_withoutAPrompt_isInvalidParams() {
        for (ObjectNode arguments :
                List.of(
                        mapper.createObjectNode(),
                        mapper.createObjectNode().put("prompt", " "),
                        mapper.createObjectNode().put("prompt", 3))) {
            assertThatThrownBy(() -> source.start("support_bot", arguments))
                    .isInstanceOfSatisfying(
                            McpServerException.class,
                            e -> assertThat(e.getCode()).isEqualTo(-32602));
        }
        verify(agentService, never()).start(any());
    }

    @Test
    void exposes_onlyDeployedAgents_withoutReadingTheirDefinitions() {
        when(agentService.listAgents()).thenReturn(List.of(agent("support_bot", null)));
        assertThat(source.exposes("support_bot")).isTrue();
        assertThat(source.exposes("other")).isFalse();
        verify(agentService, never()).getAgentDef(any(), any());
    }

    @Test
    void findTool_onlyDeployedAgents() {
        when(agentService.listAgents()).thenReturn(List.of(agent("support_bot", null)));
        assertThat(source.findTool("support_bot")).isPresent();
        assertThat(source.findTool("other")).isEmpty();
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
