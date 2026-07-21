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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.util.EmbeddedMode;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure compiler invariants for worker credential declarations. Registration and persistence are
 * verified by {@code AgentSpanDeploymentContractEndToEndTest} using the real metadata service.
 */
class WorkerRuntimeMetadataTest {

    @AfterEach
    void resetEmbedded() {
        new EmbeddedMode().setEmbedded(false);
    }

    @Test
    void enrichScriptNeverPersistsResolvedCredentialsInEitherRuntimeMode() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("github")
                        .description("GitHub lookup")
                        .toolType("worker")
                        .config(Map.of("credentials", List.of("GITHUB_TOKEN")))
                        .build();

        new EmbeddedMode().setEmbedded(true);
        String embedded = enrichScript(tool);
        new EmbeddedMode().setEmbedded(false);
        String standalone = enrichScript(tool);

        assertThat(embedded).doesNotContain("__resolved_credentials__");
        assertThat(standalone).doesNotContain("__resolved_credentials__");
    }

    @Test
    void collectToolCredentialsPreservesDeclaredNames() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("agent")
                        .model("openai/gpt-4o-mini")
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("github")
                                                .description("GitHub lookup")
                                                .toolType("worker")
                                                .config(
                                                        Map.of(
                                                                "credentials",
                                                                List.of(
                                                                        "GITHUB_TOKEN",
                                                                        "GH_APP_ID")))
                                                .build()))
                        .build();

        assertThat(AgentCompiler.collectToolCredentials(config).get("github"))
                .containsExactly("GITHUB_TOKEN", "GH_APP_ID");
    }

    @Test
    void collectAgentCredentialsDeduplicatesInDeclarationOrder() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("agent")
                        .model("openai/gpt-4o-mini")
                        .credentials(List.of("FIRST", "SECOND", "FIRST"))
                        .build();

        assertThat(AgentCompiler.collectAgentCredentials(config))
                .containsExactly("FIRST", "SECOND");
    }

    private static String enrichScript(ToolConfig tool) {
        Object[] result =
                new ToolCompiler().buildEnrichTask("agent", "agent_llm", List.of(tool), "");
        return (String) ((WorkflowTask) result[0]).getInputParameters().get("expression");
    }
}
