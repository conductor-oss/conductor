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
package com.netflix.conductor.common.metadata.agent;

import java.util.List;

import org.conductoross.conductor.common.metadata.agent.*;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

class AgentConfigTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testSerializeDeserialize() throws Exception {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test_agent")
                        .model("openai/gpt-4o")
                        .instructions("Be helpful.")
                        .maxTurns(10)
                        .temperature(0.5)
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("search")
                                                .description("Search")
                                                .toolType("worker")
                                                .build()))
                        .guardrails(
                                List.of(
                                        GuardrailConfig.builder()
                                                .name("no_pii")
                                                .guardrailType("regex")
                                                .patterns(List.of("\\d{3}-\\d{2}-\\d{4}"))
                                                .mode("block")
                                                .build()))
                        .build();

        String json = mapper.writeValueAsString(config);
        AgentConfig deserialized = mapper.readValue(json, AgentConfig.class);

        assertThat(deserialized.getName()).isEqualTo("test_agent");
        assertThat(deserialized.getModel()).isEqualTo("openai/gpt-4o");
        assertThat(deserialized.getMaxTurns()).isEqualTo(10);
        assertThat(deserialized.getTemperature()).isEqualTo(0.5);
        assertThat(deserialized.getTools()).hasSize(1);
        assertThat(deserialized.getGuardrails()).hasSize(1);
    }

    @Test
    void testNullFieldsOmitted() throws Exception {
        AgentConfig config = AgentConfig.builder().name("minimal").model("openai/gpt-4o").build();

        String json = mapper.writeValueAsString(config);
        assertThat(json).doesNotContain("\"tools\"");
        assertThat(json).doesNotContain("\"guardrails\"");
        assertThat(json).doesNotContain("\"termination\"");
    }

    @Test
    void testNestedAgentSerialization() throws Exception {
        AgentConfig config =
                AgentConfig.builder()
                        .name("parent")
                        .model("openai/gpt-4o")
                        .strategy(AgentConfig.Strategy.HANDOFF)
                        .agents(
                                List.of(
                                        AgentConfig.builder()
                                                .name("child1")
                                                .model("openai/gpt-4o")
                                                .build(),
                                        AgentConfig.builder()
                                                .name("child2")
                                                .model("anthropic/claude-sonnet-4-20250514")
                                                .build()))
                        .build();

        String json = mapper.writeValueAsString(config);
        AgentConfig deserialized = mapper.readValue(json, AgentConfig.class);

        assertThat(deserialized.getAgents()).hasSize(2);
        assertThat(deserialized.getAgents().get(0).getName()).isEqualTo("child1");
        assertThat(deserialized.getAgents().get(1).getName()).isEqualTo("child2");
    }

    @Test
    void testTerminationConfigSerialization() throws Exception {
        TerminationConfig term =
                TerminationConfig.builder()
                        .type("and")
                        .conditions(
                                List.of(
                                        TerminationConfig.builder()
                                                .type("text_mention")
                                                .text("DONE")
                                                .build(),
                                        TerminationConfig.builder()
                                                .type("max_message")
                                                .maxMessages(10)
                                                .build()))
                        .build();

        String json = mapper.writeValueAsString(term);
        TerminationConfig deserialized = mapper.readValue(json, TerminationConfig.class);

        assertThat(deserialized.getType()).isEqualTo("and");
        assertThat(deserialized.getConditions()).hasSize(2);
    }

    @Test
    void testRequiredToolsSerialization() throws Exception {
        AgentConfig config =
                AgentConfig.builder()
                        .name("filing_agent")
                        .model("openai/gpt-4o")
                        .requiredTools(List.of("submit_filing", "approve_offer"))
                        .build();

        String json = mapper.writeValueAsString(config);
        assertThat(json).contains("\"requiredTools\"");
        assertThat(json).contains("submit_filing");
        assertThat(json).contains("approve_offer");

        AgentConfig deserialized = mapper.readValue(json, AgentConfig.class);
        assertThat(deserialized.getRequiredTools())
                .containsExactly("submit_filing", "approve_offer");
    }

    @Test
    void testRequiredToolsNullOmitted() throws Exception {
        AgentConfig config = AgentConfig.builder().name("simple").model("openai/gpt-4o").build();

        String json = mapper.writeValueAsString(config);
        assertThat(json).doesNotContain("\"requiredTools\"");
    }

    @Test
    void testStartRequestTimeoutSeconds() throws Exception {
        AgentStartRequest request =
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder().name("test").model("openai/gpt-4o").build())
                        .prompt("hello")
                        .timeoutSeconds(30)
                        .build();

        String json = mapper.writeValueAsString(request);
        assertThat(json).contains("\"timeoutSeconds\":30");

        AgentStartRequest deserialized = mapper.readValue(json, AgentStartRequest.class);
        assertThat(deserialized.getTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void testStartRequestTimeoutSecondsNullWhenNotSet() throws Exception {
        AgentStartRequest request =
                AgentStartRequest.builder()
                        .agentConfig(
                                AgentConfig.builder().name("test").model("openai/gpt-4o").build())
                        .prompt("hello")
                        .build();

        assertThat(request.getTimeoutSeconds()).isNull();
    }

    @Test
    void testStartRequestRegisteredAgentIdentityRoundTrip() throws Exception {
        AgentStartRequest request =
                AgentStartRequest.builder()
                        .name("deployed_agent")
                        .version(4)
                        .prompt("hello")
                        .build();

        String json = mapper.writeValueAsString(request);
        assertThat(json).contains("\"name\":\"deployed_agent\"");
        assertThat(json).contains("\"version\":4");
        assertThat(json).doesNotContain("agentConfig");

        AgentStartRequest deserialized = mapper.readValue(json, AgentStartRequest.class);
        assertThat(deserialized.getName()).isEqualTo("deployed_agent");
        assertThat(deserialized.getVersion()).isEqualTo(4);
    }

    /**
     * {@link AgentConfig.Strategy} must serialize to the exact lowercase snake_case spelling (e.g.
     * {@code "round_robin"}, not the enum's default {@code name()} of {@code "ROUND_ROBIN"}) —
     * every persisted {@code metadata.agentDef}/{@code normalizedAgentDef} row and REST payload
     * written before this field was an enum used that spelling, and {@code ObjectMapperProvider}
     * configures no enum-naming customization to paper over a mismatch.
     */
    @Test
    void strategySerializesToLowercaseSnakeCaseForWireCompatibility() throws Exception {
        for (AgentConfig.Strategy strategy : AgentConfig.Strategy.values()) {
            AgentConfig config =
                    AgentConfig.builder()
                            .name("a")
                            .model("openai/gpt-4o")
                            .strategy(strategy)
                            .build();
            String json = mapper.writeValueAsString(config);
            assertThat(json)
                    .contains("\"strategy\":\"" + strategy.toValue() + "\"")
                    .doesNotContain(strategy.name()); // never the raw enum name() (uppercase)
        }
    }

    @Test
    void strategyDeserializesFromPreExistingLowercaseSnakeCaseRows() throws Exception {
        // Simulates a WorkflowDef.metadata.agentDef map persisted before strategy was an enum.
        String json = "{\"name\":\"a\",\"model\":\"openai/gpt-4o\",\"strategy\":\"round_robin\"}";
        AgentConfig config = mapper.readValue(json, AgentConfig.class);
        assertThat(config.getStrategy()).isEqualTo(AgentConfig.Strategy.ROUND_ROBIN);
    }
}
