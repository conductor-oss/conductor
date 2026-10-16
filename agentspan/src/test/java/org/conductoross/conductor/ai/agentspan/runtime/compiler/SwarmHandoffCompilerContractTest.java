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

import java.util.*;

import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.conductoross.conductor.common.metadata.agent.HandoffConfig;
import org.conductoross.conductor.common.metadata.agent.ToolConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Structural contract for generated SWARM transfer controls. */
class SwarmHandoffCompilerContractTest {

    @Test
    void generatedTransfersStayLlmVisibleAndNeverBecomeSimpleTasks() {
        WorkflowDef workflow = new AgentCompiler().compile(swarm());
        List<WorkflowTask> allTasks = flatten(workflow.getTasks());

        assertThat(allTasks)
                .noneMatch(
                        task ->
                                "SIMPLE".equals(task.getType())
                                        && task.getName() != null
                                        && (task.getName().contains("_transfer_to_")
                                                || task.getName().contains("_check_transfer")
                                                || task.getName().contains("_handoff_check")));
        assertThat(allTasks)
                .anyMatch(
                        task ->
                                "INLINE".equals(task.getType())
                                        && task.getTaskReferenceName().contains("check_transfer"));
        assertThat(allTasks)
                .anyMatch(
                        task ->
                                "INLINE".equals(task.getType())
                                        && task.getTaskReferenceName().contains("handoff_check"));
        assertThat(allTasks)
                .anyMatch(
                        task ->
                                "SIMPLE".equals(task.getType())
                                        && "evaluate_handoff".equals(task.getName()));

        Map<String, Set<String>> transfersByLlm = new LinkedHashMap<>();
        for (WorkflowTask task : allTasks) {
            if (!"LLM_CHAT_COMPLETE".equals(task.getType())) {
                continue;
            }
            Object rawTools = task.getInputParameters().get("tools");
            if (!(rawTools instanceof List<?> tools)) {
                continue;
            }
            Set<String> transfers = new LinkedHashSet<>();
            for (Object rawTool : tools) {
                if (rawTool instanceof Map<?, ?> tool
                        && String.valueOf(tool.get("name")).contains("_transfer_to_")) {
                    assertThat(tool.get("type")).isEqualTo("INLINE");
                    assertThat(tool.get("inputSchema"))
                            .as("generated transfer message schema")
                            .isInstanceOf(Map.class);
                    transfers.add(String.valueOf(tool.get("name")));
                }
            }
            if (!transfers.isEmpty()) {
                transfersByLlm.put(task.getTaskReferenceName(), transfers);
            }
        }

        assertThat(transfersByLlm.values())
                .contains(
                        Set.of("team_transfer_to_agent_b"), Set.of("agent_a_transfer_to_agent_b"));
        assertThat(transfersByLlm.values())
                .noneMatch(names -> names.contains("team_transfer_to_agent_a"));
    }

    @Test
    void rejectsAnExactUserToolCollisionWithCompilerOwnedTransferName() {
        AgentConfig config = swarm();
        config.setTools(
                List.of(
                        ToolConfig.builder()
                                .name("team_transfer_to_agent_b")
                                .description("not a compiler control")
                                .toolType("worker")
                                .build()));

        assertThatThrownBy(() -> new AgentCompiler().compile(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compiler-owned transfer control");
    }

    @Test
    void declarativeToolResultRoutingMatchesStructuredOutputCaseInsensitively() {
        AgentConfig config = swarm();
        config.setHandoffs(
                List.of(
                        HandoffConfig.builder()
                                .type("on_tool_result")
                                .target("agent_b")
                                .toolName("lookup")
                                .resultContains("APPROVED")
                                .build()));
        WorkflowDef workflow = new AgentCompiler().compile(config);
        WorkflowTask handoff =
                flatten(workflow.getTasks()).stream()
                        .filter(task -> task.getTaskReferenceName().contains("team_handoff_check"))
                        .findFirst()
                        .orElseThrow();
        String resolver = (String) handoff.getInputParameters().get("expression");

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            Value routed =
                    context.eval(
                            "js",
                            "var $={active_agent:'1',is_transfer:false,transfer_to:'',result:'',"
                                    + "tool_results:[{name:'lookup',output:{status:'approved'}}]};"
                                    + resolver);
            assertThat(routed.getMember("handoff").asBoolean()).isTrue();
            assertThat(routed.getMember("active_agent").asString()).isEqualTo("2");

            Value notRouted =
                    context.eval(
                            "js",
                            "var $={active_agent:'1',is_transfer:false,transfer_to:'',result:'',"
                                    + "tool_results:[{name:'lookup',output:{status:'declined'}}]};"
                                    + resolver);
            assertThat(notRouted.getMember("handoff").asBoolean()).isFalse();
            assertThat(notRouted.getMember("active_agent").asString()).isEqualTo("1");
        }
    }

    @Test
    void onlyTheAgentWhoseToolIsWatchedByAHandoffSkipsItsPostToolCallTurn() {
        // "team" itself owns "lookup" and hands off on its result. "agent_a" owns an unrelated
        // tool ("other_tool") that no handoff references. Only team's own loop should have its
        // post-tool-call turn suppressed (needed so the handoff can route immediately on the
        // JOIN output) — agent_a must keep looping normally so it can read its own tool's result
        // and answer with it, instead of its turn ending right after the tool call.
        AgentConfig config =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .strategy(AgentConfig.Strategy.SWARM)
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("lookup")
                                                .description("owned by team")
                                                .toolType("worker")
                                                .build()))
                        .handoffs(
                                List.of(
                                        HandoffConfig.builder()
                                                .type("on_tool_result")
                                                .target("agent_a")
                                                .toolName("lookup")
                                                .resultContains("ok")
                                                .build()))
                        .agents(
                                List.of(
                                        AgentConfig.builder()
                                                .name("agent_a")
                                                .model("openai/gpt-4o")
                                                .tools(
                                                        List.of(
                                                                ToolConfig.builder()
                                                                        .name("other_tool")
                                                                        .description(
                                                                                "owned by agent_a, unrelated to the handoff")
                                                                        .toolType("worker")
                                                                        .build()))
                                                .build()))
                        .build();

        WorkflowDef workflow = new AgentCompiler().compile(config);
        List<WorkflowTask> allTasks = flatten(workflow.getTasks());

        // "team_loop" is ambiguous: the top-level swarm loop (routes between agent cases) and
        // agent-0's own inline ReAct loop (agent 0 IS "team" acting as itself) share this exact
        // reference name in different workflow scopes. Disambiguate by picking the per-turn
        // loop, which is the one whose condition inspects finishReason.
        WorkflowTask teamLoop =
                allTasks.stream()
                        .filter(task -> "team_loop".equals(task.getTaskReferenceName()))
                        .filter(
                                task ->
                                        task.getLoopCondition() != null
                                                && task.getLoopCondition().contains("finishReason"))
                        .findFirst()
                        .orElseThrow();
        WorkflowTask agentALoop =
                allTasks.stream()
                        .filter(task -> "agent_a_loop".equals(task.getTaskReferenceName()))
                        .findFirst()
                        .orElseThrow();

        assertThat(teamLoop.getLoopCondition())
                .as("team owns the watched tool, so its post-tool-call turn is suppressed")
                .contains("|| false)")
                .doesNotContain("toolCalls");
        assertThat(agentALoop.getLoopCondition())
                .as(
                        "agent_a's own tool isn't referenced by any handoff, so it must keep"
                                + " looping after a tool call to read and answer with the result")
                .contains("toolCalls");

        // Looping again is only useful if the resumed turn can actually see what happened on the
        // prior turn — otherwise the LLM has no way to know it already called the tool and just
        // repeats it. agent_a's loop must carry a ctx_inject task feeding its LLM's user message,
        // the same wiring the single-agent and hybrid loops already have.
        assertThat(agentALoop.getLoopOver())
                .as("agent_a's loop must inject prior state/tool-results, not just the raw prompt")
                .anyMatch(
                        task ->
                                "INLINE".equals(task.getType())
                                        && task.getTaskReferenceName().endsWith("_ctx_inject"));
        WorkflowTask agentALlm =
                agentALoop.getLoopOver().stream()
                        .filter(task -> "LLM_CHAT_COMPLETE".equals(task.getType()))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agentAMessages =
                (List<Map<String, Object>>) agentALlm.getInputParameters().get("messages");
        assertThat(agentAMessages)
                .as("agent_a's user message must read the ctx_inject prefix, not the bare prompt")
                .anyMatch(
                        msg ->
                                "user".equals(msg.get("role"))
                                        && String.valueOf(msg.get("message"))
                                                .contains("_ctx_inject.output.result"));
    }

    private static AgentConfig swarm() {
        return AgentConfig.builder()
                .name("team")
                .model("openai/gpt-4o")
                .strategy(AgentConfig.Strategy.SWARM)
                .allowedTransitions(
                        Map.of("team", List.of("agent_b"), "agent_a", List.of("agent_b")))
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

    private static List<WorkflowTask> flatten(List<WorkflowTask> tasks) {
        List<WorkflowTask> result = new ArrayList<>();
        for (WorkflowTask task : tasks) {
            result.add(task);
            if (task.getLoopOver() != null) {
                result.addAll(flatten(task.getLoopOver()));
            }
            if (task.getDecisionCases() != null) {
                for (List<WorkflowTask> branch : task.getDecisionCases().values()) {
                    result.addAll(flatten(branch));
                }
            }
            if (task.getDefaultCase() != null) {
                result.addAll(flatten(task.getDefaultCase()));
            }
            if (task.getSubWorkflowParam() != null
                    && task.getSubWorkflowParam().getWorkflowDef() != null) {
                result.addAll(flatten(task.getSubWorkflowParam().getWorkflowDef().getTasks()));
            }
        }
        return result;
    }
}
