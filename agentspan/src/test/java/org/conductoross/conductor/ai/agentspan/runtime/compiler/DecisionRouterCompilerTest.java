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
package org.conductoross.conductor.ai.agentspan.runtime.compiler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

class DecisionRouterCompilerTest {
    private final AgentCompiler compiler = new AgentCompiler();

    private AgentConfig leaf(String name) {
        return AgentConfig.builder()
                .name(name)
                .model("openai/gpt-4o")
                .instructions("Handle " + name + " requests")
                .build();
    }

    private AgentConfig team(String name, AgentConfig... children) {
        Map<String, String> choices = new LinkedHashMap<>();
        for (AgentConfig child : children)
            choices.put(child.getName(), "Choose " + child.getName());
        AgentConfig selector =
                AgentConfig.builder()
                        .name(name + "_selector")
                        .kind(AgentConfig.Kind.DECISION)
                        .model("jev-1.13")
                        .questions(
                                Map.of(
                                        "agent.name",
                                        Map.of(
                                                "type",
                                                "choice",
                                                "instructions",
                                                "Choose an agent",
                                                "choices",
                                                choices)))
                        .build();
        return AgentConfig.builder()
                .name(name)
                .strategy(AgentConfig.Strategy.ROUTER)
                .router(selector)
                .agents(List.of(children))
                .build();
    }

    @Test
    void routesStructuredChoiceWithoutChatOrWorkers() throws Exception {
        var config = team("triage", leaf("billing"), leaf("technical"));
        // Exercise the JSON representation sent by SDKs.
        config.setRouter(new ObjectMapper().convertValue(config.getRouter(), Map.class));
        var workflow = compiler.compile(config);
        assertThat(workflow.getTasks())
                .extracting(t -> t.getType())
                .containsExactly("AI_DECISION", "SWITCH");
        var route = workflow.getTasks().get(0);
        assertThat(route.getInputParameters())
                .containsEntry("state", "${workflow.input.prompt}")
                .containsEntry("model", "jev-1.13");
        var dispatch = workflow.getTasks().get(1);
        assertThat(dispatch.getDecisionCases()).containsOnlyKeys("billing", "technical");
        try (Context context = Context.create("js")) {
            context.eval(
                    "js",
                    "var $ = {question: 'agent.name', answers: {'agent.name': {choice: 'technical'}}}");
            String selected = context.eval("js", dispatch.getExpression()).asString();
            var tasks = dispatch.getDecisionCases().get(selected);
            assertThat(tasks.get(0).getSubWorkflowParam().getName()).isEqualTo("technical");
            assertThat(tasks.get(0).getInputParameters())
                    .containsEntry("prompt", "${workflow.input.prompt}");
            assertThat(tasks.get(1).getInputParameters())
                    .containsEntry("result", "${triage_selected_1.output.result}");
        }
        assertThat(dispatch.getDefaultCase().get(0).getInputParameters())
                .containsEntry("terminationStatus", "FAILED");
        assertThat(workflow.getOutputParameters())
                .containsEntry("result", "${workflow.variables.result}");
    }

    @Test
    void compilesThreeNestedLevels() {
        var department = team("billing", leaf("refunds"), leaf("charges"));
        var workflow = compiler.compile(team("triage", department, leaf("technical")));
        var child =
                workflow.getTasks()
                        .get(1)
                        .getDecisionCases()
                        .get("billing")
                        .get(0)
                        .getSubWorkflowParam()
                        .getWorkflowDef();
        var specialist =
                child.getTasks()
                        .get(1)
                        .getDecisionCases()
                        .get("refunds")
                        .get(0)
                        .getSubWorkflowParam()
                        .getWorkflowDef();
        assertThat(specialist.getTasks())
                .extracting(t -> t.getType())
                .containsExactly("LLM_CHAT_COMPLETE");
        assertThat(child.getOutputParameters())
                .containsEntry("result", "${workflow.variables.result}");
    }

    @Test
    void singleChatRouterPreservesSelectedDecisionResult() {
        var config = team("triage", leaf("billing"), leaf("technical"));
        config.setModel("configured/luna-6");
        config.setRouter(
                AgentConfig.builder()
                        .name("selector")
                        .model("configured/luna-6")
                        .instructions("Choose billing or technical")
                        .build());
        config.setMaxTurns(1);
        config.setSynthesize(false);
        var workflow = compiler.compile(config);
        assertThat(workflow.getOutputParameters())
                .containsEntry("result", "${workflow.variables.selectedResult}");
        var loop =
                workflow.getTasks().stream()
                        .filter(t -> "DO_WHILE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        var dispatch =
                loop.getLoopOver().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        var tasks = dispatch.getDecisionCases().get("technical");
        assertThat(tasks.get(tasks.size() - 1).getInputParameters())
                .containsEntry("selectedResult", "${triage_handoff_1_technical.output.result}");
    }

    @Test
    void rejectsMissingAmbiguousOrUnmatchedRoutingQuestions() {
        var config = team("triage", leaf("billing"), leaf("technical"));
        var selector = (AgentConfig) config.getRouter();
        var question = selector.getQuestions().get("agent.name");
        selector.setQuestions(null);
        assertThatThrownBy(() -> compiler.compile(config)).hasMessageContaining("one fixed choice");
        selector.setQuestions(Map.of("a", question, "b", question));
        assertThatThrownBy(() -> compiler.compile(config)).hasMessageContaining("one fixed choice");
        selector.setQuestions(
                Map.of(
                        "agent",
                        Map.of(
                                "type",
                                "choice",
                                "instructions",
                                "Choose",
                                "choices",
                                Map.of("billing", "Billing", "unknown", "Unknown"))));
        assertThatThrownBy(() -> compiler.compile(config))
                .hasMessageContaining("match child agent names");
        selector.setQuestions(Map.of("agent", Map.of("type", "boolean", "instructions", "Ready?")));
        assertThatThrownBy(() -> compiler.compile(config))
                .hasMessageContaining("match child agent names");
    }
}
