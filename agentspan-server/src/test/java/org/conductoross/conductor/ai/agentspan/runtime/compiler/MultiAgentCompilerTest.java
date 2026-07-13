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
import java.util.Set;

import org.conductoross.conductor.ai.agentspan.runtime.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.*;

class MultiAgentCompilerTest {

    private AgentCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new AgentCompiler();
    }

    private AgentConfig simpleSubAgent(String name, String instructions) {
        return AgentConfig.builder()
                .name(name)
                .model("openai/gpt-4o")
                .instructions(instructions)
                .build();
    }

    /**
     * In the post-compileGate-restructure layout, the plan SUB_WORKFLOW + status check + exec_route
     * SWITCH live inside ``compile_gate``'s defaultCase, not as direct siblings of the has_plan
     * branch. Walk through compile_gate to reach them.
     */
    private List<WorkflowTask> compileSuccessTasks(List<WorkflowTask> hasPlanBranch) {
        WorkflowTask compileGate =
                hasPlanBranch.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .contains("compile_gate"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "Expected compile_gate SWITCH in has_plan branch"));
        return compileGate.getDefaultCase();
    }

    @Test
    void testHandoff() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .instructions("Route to the best agent.")
                        .strategy("handoff")
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "Handle A tasks"),
                                        simpleSubAgent("agent_b", "Handle B tasks")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        assertThat(wf.getName()).isEqualTo("team");

        // Handoff: ctx_resolve + init + DoWhile loop + final LLM
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");

        // Loop should contain router LLM + switch with agent cases
        WorkflowTask loop = wf.getTasks().get(2);
        boolean hasSwitchInLoop =
                loop.getLoopOver().stream().anyMatch(t -> "SWITCH".equals(t.getType()));
        assertThat(hasSwitchInLoop).isTrue();

        // Switch should have 2 cases (one per agent)
        WorkflowTask switchTask =
                loop.getLoopOver().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(switchTask.getDecisionCases()).containsKeys("agent_a", "agent_b");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHandoffDefaultMaxTurnsIsNumAgentsPlusOne() {
        // 3 sub-agents → default maxTurns should be 4 (3 + 1), not 25.
        // This prevents the router from re-routing to an agent that already responded.
        AgentConfig config =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .strategy("handoff")
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "Handle A"),
                                        simpleSubAgent("agent_b", "Handle B"),
                                        simpleSubAgent("agent_c", "Handle C")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask loop = wf.getTasks().get(2); // DO_WHILE
        // Loop condition encodes maxTurns as a literal integer.
        // With 3 agents: expected maxTurns = 4 (agents.size() + 1).
        assertThat(loop.getLoopCondition()).contains("< 4");
        assertThat(loop.getLoopCondition()).doesNotContain("< 25");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHandoffExplicitMaxTurnsIsRespected() {
        // Explicit maxTurns overrides the default.
        AgentConfig config =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .strategy("handoff")
                        .maxTurns(10)
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "Handle A"),
                                        simpleSubAgent("agent_b", "Handle B")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask loop = wf.getTasks().get(2); // DO_WHILE
        assertThat(loop.getLoopCondition()).contains("< 10");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHandoffRouterPromptPreventsReDelegation() {
        // Router prompt must tell the LLM not to re-route to an agent
        // that has already responded (identified by "[agent_name]:" in the conversation).
        AgentConfig config =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .instructions("Route to the best agent.")
                        .strategy("handoff")
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "Handle A tasks"),
                                        simpleSubAgent("agent_b", "Handle B tasks")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        WorkflowTask loop = wf.getTasks().get(2); // DO_WHILE

        // Router LLM is the first task inside the loop (wrapped in a SUB_WORKFLOW)
        WorkflowTask routerSubWf = loop.getLoopOver().get(0);
        WorkflowTask routerLlm =
                routerSubWf.getSubWorkflowParam().getWorkflowDef().getTasks().get(0);
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) routerLlm.getInputParameters().get("messages");
        String systemPrompt = (String) messages.get(0).get("message");

        // Must tell the router that an agent that has already responded should not be called again.
        assertThat(systemPrompt).containsIgnoringCase("at most once");
        // Must reference the conversation marker pattern so the LLM can recognise responded agents.
        assertThat(systemPrompt).contains("[agent_name]:");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testHandoffWithDynamicInstructionsAddsInstructionTasks() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("dynamic_team")
                        .model("openai/gpt-4o")
                        .instructions(
                                Map.of(
                                        "_worker_ref", "build_team_instructions",
                                        "description", "Create routing instructions"))
                        .strategy("handoff")
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "Handle A tasks"),
                                        simpleSubAgent("agent_b", "Handle B tasks")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        assertThat(wf.getTasks()).hasSize(6);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("SIMPLE");
        assertThat(wf.getTasks().get(0).getTaskReferenceName())
                .isEqualTo("dynamic_team_instructions_worker");
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("INLINE");
        assertThat(wf.getTasks().get(1).getTaskReferenceName())
                .isEqualTo("dynamic_team_instructions");

        WorkflowTask loop = wf.getTasks().get(4);
        WorkflowTask routerSubWorkflow = loop.getLoopOver().get(0);
        WorkflowTask routerLlm =
                routerSubWorkflow.getSubWorkflowParam().getWorkflowDef().getTasks().get(0);
        List<Map<String, Object>> routerMessages =
                (List<Map<String, Object>>) routerLlm.getInputParameters().get("messages");
        String routerSystemMsg = (String) routerMessages.get(0).get("message");
        assertThat(routerSystemMsg).contains("${dynamic_team_instructions.output.result}");

        WorkflowTask finalLlm = wf.getTasks().get(5);
        List<Map<String, Object>> finalMessages =
                (List<Map<String, Object>>) finalLlm.getInputParameters().get("messages");
        String finalSystemMsg = (String) finalMessages.get(0).get("message");
        assertThat(finalSystemMsg).contains("${dynamic_team_instructions.output.result}");
    }

    @Test
    void testSequential() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("pipeline")
                        .model("openai/gpt-4o")
                        .strategy("sequential")
                        .agents(
                                List.of(
                                        simpleSubAgent("writer", "Write content"),
                                        simpleSubAgent("reviewer", "Review content")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_init_resolve + ctx_init + SUB_WORKFLOW(writer) + ctx_merge_0 + ctx_set_0 + coerce +
        // SUB_WORKFLOW(reviewer) + ctx_merge_1 + ctx_set_1
        assertThat(wf.getTasks()).hasSize(9);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_init_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE"); // ctx_init
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("SUB_WORKFLOW");
        assertThat(wf.getTasks().get(2).getTaskReferenceName()).contains("writer");
        assertThat(wf.getTasks().get(5).getType()).isEqualTo("INLINE");
        assertThat(wf.getTasks().get(5).getTaskReferenceName()).contains("coerce");
        assertThat(wf.getTasks().get(6).getType()).isEqualTo("SUB_WORKFLOW");
        assertThat(wf.getTasks().get(6).getTaskReferenceName()).contains("reviewer");
    }

    @Test
    void testParallel() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("parallel_team")
                        .model("openai/gpt-4o")
                        .strategy("parallel")
                        .agents(
                                List.of(
                                        simpleSubAgent("analyst", "Analyze data"),
                                        simpleSubAgent("researcher", "Research topic")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + ctx_init + Fork + Join + Aggregate INLINE + ctx_merge + ctx_set
        assertThat(wf.getTasks()).hasSize(7);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE"); // ctx_init
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("FORK_JOIN");
        assertThat(wf.getTasks().get(3).getType()).isEqualTo("JOIN");
        assertThat(wf.getTasks().get(4).getType()).isEqualTo("INLINE");
        assertThat(wf.getTasks().get(4).getTaskReferenceName())
                .isEqualTo("parallel_team_aggregate");

        // Output references the aggregate task — result is always a string
        assertThat(wf.getOutputParameters().get("result").toString()).contains("_aggregate");
        assertThat(wf.getOutputParameters()).containsKey("subResults");
    }

    @Test
    void testRoundRobin() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("discussion")
                        .model("openai/gpt-4o")
                        .strategy("round_robin")
                        .maxTurns(10)
                        .agents(
                                List.of(
                                        simpleSubAgent("alice", "Alice's perspective"),
                                        simpleSubAgent("bob", "Bob's perspective")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + Init + DoWhile
        assertThat(wf.getTasks()).hasSize(3);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");

        // DoWhile should have select + switch
        WorkflowTask loop = wf.getTasks().get(2);
        assertThat(loop.getLoopOver()).hasSize(2);
        assertThat(loop.getLoopOver().get(0).getType()).isEqualTo("INLINE");
        assertThat(loop.getLoopOver().get(1).getType()).isEqualTo("SWITCH");
    }

    @Test
    void testRandom() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("random_team")
                        .model("openai/gpt-4o")
                        .strategy("random")
                        .agents(
                                List.of(
                                        simpleSubAgent("alice", "Alice"),
                                        simpleSubAgent("bob", "Bob")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + Init + DoWhile
        assertThat(wf.getTasks()).hasSize(3);
        WorkflowTask loop = wf.getTasks().get(2);

        // Select script should use Math.random
        WorkflowTask selectTask = loop.getLoopOver().get(0);
        String script = (String) selectTask.getInputParameters().get("expression");
        assertThat(script).contains("Math.random()");
    }

    @Test
    void testSwarm() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("swarm")
                        .model("openai/gpt-4o")
                        .instructions("Triage requests")
                        .strategy("swarm")
                        .handoffs(
                                List.of(
                                        HandoffConfig.builder()
                                                .type("on_text_mention")
                                                .target("agent_b")
                                                .text("transfer to b")
                                                .build()))
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "Handle A"),
                                        simpleSubAgent("agent_b", "Handle B")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + Init + DoWhile + Final LLM
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");
        assertThat(wf.getTasks().get(3).getType()).isEqualTo("LLM_CHAT_COMPLETE");

        // Init should contain last_response, is_transfer, transfer_to
        WorkflowTask init = wf.getTasks().get(1);
        assertThat(init.getInputParameters()).containsKey("last_response");
        assertThat(init.getInputParameters()).containsKey("is_transfer");
        assertThat(init.getInputParameters()).containsKey("transfer_to");

        // Loop: switch + handoff_check + update_active
        WorkflowTask loop = wf.getTasks().get(2);
        assertThat(loop.getLoopOver()).hasSize(3);

        // Switch should have 3 cases: "0" (parent), "1" (agent_a), "2" (agent_b)
        WorkflowTask switchTask =
                loop.getLoopOver().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(switchTask.getDecisionCases()).containsKeys("0", "1", "2");
        assertThat(switchTask.getDecisionCases()).hasSize(3);

        // Each case should have a SUB_WORKFLOW with inline workflow containing DO_WHILE with
        // check_transfer
        for (String caseKey : List.of("0", "1", "2")) {
            List<WorkflowTask> caseTasks = switchTask.getDecisionCases().get(caseKey);
            assertThat(caseTasks).isNotEmpty();
            WorkflowTask subWfTask = caseTasks.get(0);
            assertThat(subWfTask.getType()).isEqualTo("SUB_WORKFLOW");
            // Verify inline workflow definition exists
            assertThat(subWfTask.getSubWorkflowParam()).isNotNull();
            assertThat(subWfTask.getSubWorkflowParam().getWorkflowDef()).isNotNull();
            WorkflowDef inlineWf = subWfTask.getSubWorkflowParam().getWorkflowDef();
            // Inline workflow should have init_state + DO_WHILE
            assertThat(inlineWf.getTasks()).hasSize(2);
            assertThat(inlineWf.getTasks().get(0).getType()).isEqualTo("SET_VARIABLE");
            assertThat(inlineWf.getTasks().get(1).getType()).isEqualTo("DO_WHILE");
            // DO_WHILE should contain check_transfer
            WorkflowTask innerLoop = inlineWf.getTasks().get(1);
            boolean hasCheckTransfer =
                    innerLoop.getLoopOver().stream()
                            .anyMatch(
                                    t ->
                                            t.getName() != null
                                                    && t.getName().contains("check_transfer"));
            assertThat(hasCheckTransfer).isTrue();
            // Output should include is_transfer and transfer_to
            assertThat(inlineWf.getOutputParameters()).containsKey("is_transfer");
            assertThat(inlineWf.getOutputParameters()).containsKey("transfer_to");
        }

        // Handoff check inputs should include is_transfer and transfer_to
        WorkflowTask handoffTask =
                loop.getLoopOver().stream()
                        .filter(t -> "SIMPLE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(handoffTask.getInputParameters()).containsKey("is_transfer");
        assertThat(handoffTask.getInputParameters()).containsKey("transfer_to");

        // Output should reference final LLM
        assertThat(wf.getOutputParameters().get("result").toString())
                .contains("_final.output.result");

        // Loop condition should include handoff check for early termination
        assertThat(loop.getLoopCondition()).contains("handoff");
    }

    @Test
    void testManual() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("manual_team")
                        .model("openai/gpt-4o")
                        .strategy("manual")
                        .agents(
                                List.of(
                                        simpleSubAgent("writer", "Write"),
                                        simpleSubAgent("editor", "Edit")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + Init + DoWhile
        assertThat(wf.getTasks()).hasSize(3);
        WorkflowTask loop = wf.getTasks().get(2);

        // Loop: human + process_selection + switch
        assertThat(loop.getLoopOver()).hasSize(3);
        assertThat(loop.getLoopOver().get(0).getType()).isEqualTo("HUMAN");
    }

    @Test
    void testRouter_Worker() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("routed")
                        .model("openai/gpt-4o")
                        .strategy("router")
                        .router(WorkerRef.builder().taskName("my_router_fn").build())
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "A"),
                                        simpleSubAgent("agent_b", "B")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Iterative: ctx_resolve + init + DoWhile + final LLM
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");
        assertThat(wf.getTasks().get(3).getType()).isEqualTo("LLM_CHAT_COMPLETE");

        // Loop should contain the worker router task
        WorkflowTask loop = wf.getTasks().get(2);
        WorkflowTask routerInLoop = loop.getLoopOver().get(0);
        assertThat(routerInLoop.getType()).isEqualTo("SIMPLE");
        assertThat(routerInLoop.getName()).isEqualTo("my_router_fn");
    }

    @Test
    void testRouter_Agent() {
        AgentConfig routerAgent =
                AgentConfig.builder()
                        .name("router_agent")
                        .model("anthropic/claude-sonnet-4-20250514")
                        .instructions("Route intelligently.")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("routed")
                        .model("openai/gpt-4o")
                        .strategy("router")
                        .router(routerAgent)
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "A"),
                                        simpleSubAgent("agent_b", "B")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Iterative: ctx_resolve + init + DoWhile + final LLM
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");

        // Loop should contain router sub-workflow (using anthropic model) + annotation + switch
        WorkflowTask loop = wf.getTasks().get(2);
        boolean hasSwitchInLoop =
                loop.getLoopOver().stream().anyMatch(t -> "SWITCH".equals(t.getType()));
        assertThat(hasSwitchInLoop).isTrue();

        // Switch should have agent cases + DONE
        WorkflowTask switchTask =
                loop.getLoopOver().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(switchTask.getDecisionCases()).containsKeys("agent_a", "agent_b", "DONE");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRouterAgentWithDynamicInstructionsAddsInstructionTasks() {
        AgentConfig routerAgent =
                AgentConfig.builder()
                        .name("router_agent")
                        .model("anthropic/claude-sonnet-4-20250514")
                        .instructions(
                                Map.of(
                                        "_worker_ref", "build_router_instructions",
                                        "description", "Create router prompt"))
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("routed_dynamic")
                        .model("openai/gpt-4o")
                        .strategy("router")
                        .router(routerAgent)
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "A"),
                                        simpleSubAgent("agent_b", "B")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        assertThat(wf.getTasks()).hasSize(6);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("SIMPLE");
        assertThat(wf.getTasks().get(0).getTaskReferenceName())
                .isEqualTo("routed_dynamic_router_instructions_worker");
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("INLINE");
        assertThat(wf.getTasks().get(1).getTaskReferenceName())
                .isEqualTo("routed_dynamic_router_instructions");

        WorkflowTask loop = wf.getTasks().get(4);
        WorkflowTask routerSubWorkflow = loop.getLoopOver().get(0);
        WorkflowTask routerLlm =
                routerSubWorkflow.getSubWorkflowParam().getWorkflowDef().getTasks().get(0);
        List<Map<String, Object>> routerMessages =
                (List<Map<String, Object>>) routerLlm.getInputParameters().get("messages");
        String routerSystemMsg = (String) routerMessages.get(0).get("message");
        assertThat(routerSystemMsg).contains("${routed_dynamic_router_instructions.output.result}");
    }

    @Test
    void testAllowedTransitions() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("constrained")
                        .model("openai/gpt-4o")
                        .strategy("round_robin")
                        .allowedTransitions(
                                Map.of(
                                        "alice", List.of("bob"),
                                        "bob", List.of("alice")))
                        .agents(
                                List.of(
                                        simpleSubAgent("alice", "Alice"),
                                        simpleSubAgent("bob", "Bob")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Init should set last_agent (at index 1, after ctx_resolve)
        WorkflowTask init = wf.getTasks().get(1);
        assertThat(init.getInputParameters()).containsKey("last_agent");

        // Select script should reference allowed transitions
        WorkflowTask loop = wf.getTasks().get(2);
        WorkflowTask select = loop.getLoopOver().get(0);
        String script = (String) select.getInputParameters().get("expression");
        assertThat(script).contains("allowed");
    }

    @Test
    void testDuplicateAgentNames() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("dupes")
                        .model("openai/gpt-4o")
                        .strategy("handoff")
                        .agents(
                                List.of(
                                        simpleSubAgent("same_name", "A"),
                                        simpleSubAgent("same_name", "B")))
                        .build();

        assertThatThrownBy(() -> compiler.compile(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate agent names");
    }

    @Test
    void testHandoffWithAllowedTransitions() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .instructions("Route to the best agent.")
                        .strategy("handoff")
                        .allowedTransitions(
                                Map.of(
                                        "specialist_a", List.of("specialist_b"),
                                        "specialist_b", List.of("specialist_a", "team"),
                                        "specialist_c", List.of("team")))
                        .agents(
                                List.of(
                                        simpleSubAgent("specialist_a", "Handle A tasks"),
                                        simpleSubAgent("specialist_b", "Handle B tasks"),
                                        simpleSubAgent("specialist_c", "Summarize")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        assertThat(wf.getName()).isEqualTo("team");

        // With allowedTransitions, handoff compiles as ctx_resolve + init + loop + final
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");

        // Loop should contain sub-agent routing logic
        WorkflowTask loop = wf.getTasks().get(2);
        assertThat(loop.getLoopOver()).isNotEmpty();
    }

    @Test
    void testRoundRobinWithTransferControlConstraints() {
        // Simulates ADK's disallow_transfer_to_parent/peers mapped to allowedTransitions
        AgentConfig config =
                AgentConfig.builder()
                        .name("coordinator")
                        .model("openai/gpt-4o")
                        .strategy("round_robin")
                        .allowedTransitions(
                                Map.of(
                                        "data_collector", List.of("analyst"),
                                        "analyst",
                                                List.of(
                                                        "coordinator",
                                                        "data_collector",
                                                        "summarizer"),
                                        "summarizer", List.of("coordinator")))
                        .agents(
                                List.of(
                                        simpleSubAgent("data_collector", "Gather data"),
                                        simpleSubAgent("analyst", "Analyze data"),
                                        simpleSubAgent("summarizer", "Summarize")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + Init + DoWhile
        assertThat(wf.getTasks()).hasSize(3);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");

        // Select script should contain allowed transitions mapping
        WorkflowTask loop = wf.getTasks().get(2);
        WorkflowTask selectTask = loop.getLoopOver().get(0);
        String script = (String) selectTask.getInputParameters().get("expression");
        assertThat(script).contains("allowed");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCapabilitiesMetadata() {
        // Simple agent → ["simple"]
        AgentConfig simple =
                AgentConfig.builder()
                        .name("basic")
                        .model("openai/gpt-4o")
                        .instructions("Hello")
                        .build();
        WorkflowDef simpleWf = compiler.compile(simple);
        List<String> simpleCaps = (List<String>) simpleWf.getMetadata().get("agent_capabilities");
        assertThat(simpleCaps).containsExactly("simple");

        // Agent with tools → ["tool-calling"]
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();
        AgentConfig withTools =
                AgentConfig.builder()
                        .name("tooler")
                        .model("openai/gpt-4o")
                        .instructions("Use tools")
                        .tools(List.of(tool))
                        .build();
        WorkflowDef toolWf = compiler.compile(withTools);
        List<String> toolCaps = (List<String>) toolWf.getMetadata().get("agent_capabilities");
        assertThat(toolCaps).containsExactly("tool-calling");

        // Swarm with tool-using sub-agent → includes both "multi-agent-swarm" and "tool-calling"
        AgentConfig swarmConfig =
                AgentConfig.builder()
                        .name("swarm_team")
                        .model("openai/gpt-4o")
                        .instructions("Triage")
                        .strategy("swarm")
                        .agents(
                                List.of(
                                        AgentConfig.builder()
                                                .name("tool_agent")
                                                .model("openai/gpt-4o")
                                                .instructions("Use tools")
                                                .tools(List.of(tool))
                                                .build(),
                                        simpleSubAgent("helper", "Help")))
                        .build();
        WorkflowDef swarmWf = compiler.compile(swarmConfig);
        List<String> swarmCaps = (List<String>) swarmWf.getMetadata().get("agent_capabilities");
        assertThat(swarmCaps).contains("multi-agent-swarm", "tool-calling");

        // Handoff with simple sub-agents → ["multi-agent-handoff", "simple"]
        AgentConfig handoffConfig =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .instructions("Route")
                        .strategy("handoff")
                        .agents(List.of(simpleSubAgent("a", "A"), simpleSubAgent("b", "B")))
                        .build();
        WorkflowDef handoffWf = compiler.compile(handoffConfig);
        List<String> handoffCaps = (List<String>) handoffWf.getMetadata().get("agent_capabilities");
        assertThat(handoffCaps).contains("multi-agent-handoff", "simple");
    }

    @Test
    void testCollectCapabilities() {
        // Direct unit test of the static helper
        AgentConfig simple = AgentConfig.builder().name("s").model("openai/gpt-4o").build();
        assertThat(AgentCompiler.collectCapabilities(simple)).containsExactly("simple");

        ToolConfig tool =
                ToolConfig.builder()
                        .name("t")
                        .description("T")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        // Hybrid: tools + agents
        AgentConfig hybrid =
                AgentConfig.builder()
                        .name("h")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .agents(List.of(simple))
                        .build();
        Set<String> hybridCaps = AgentCompiler.collectCapabilities(hybrid);
        assertThat(hybridCaps).contains("tool-calling", "multi-agent-hybrid", "simple");

        // round_robin strategy
        AgentConfig rr =
                AgentConfig.builder()
                        .name("rr")
                        .model("openai/gpt-4o")
                        .strategy("round_robin")
                        .agents(List.of(simple))
                        .build();
        assertThat(AgentCompiler.collectCapabilities(rr)).contains("multi-agent-round-robin");
    }

    @Test
    void testSequentialWithToolsHasCoercion() {
        // Sub-agents with tools produce SUB_WORKFLOW tasks whose output.result
        // may be null. A coercion INLINE task must be inserted between stages
        // to convert null → empty string before the next stage's message.
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("pipeline")
                        .model("openai/gpt-4o")
                        .strategy("sequential")
                        .agents(
                                List.of(
                                        // Agent with tools → SUB_WORKFLOW → needs coercion
                                        AgentConfig.builder()
                                                .name("researcher")
                                                .model("openai/gpt-4o")
                                                .instructions("Research")
                                                .tools(List.of(tool))
                                                .build(),
                                        // Simple agent → inline LLM → receives coerced ref
                                        simpleSubAgent("writer", "Write content")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Should have: ctx_init_resolve + ctx_init + SUB_WORKFLOW(researcher) + ctx_merge_0 +
        // ctx_set_0 +
        // INLINE(coerce) + SUB_WORKFLOW(writer) + ctx_merge_1 + ctx_set_1
        assertThat(wf.getTasks()).hasSize(9);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_init_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE"); // ctx_init
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("SUB_WORKFLOW");
        assertThat(wf.getTasks().get(5).getType()).isEqualTo("INLINE");
        assertThat(wf.getTasks().get(5).getTaskReferenceName()).contains("coerce");
        assertThat(wf.getTasks().get(6).getType()).isEqualTo("SUB_WORKFLOW");

        // The sub-workflow's prompt input should reference the coerced output
        String promptInput = (String) wf.getTasks().get(6).getInputParameters().get("prompt");
        assertThat(promptInput).contains("coerce");
        assertThat(promptInput).contains(".output.result");
    }

    @Test
    void testSwarmWithHierarchicalSubAgent() {
        // Swarm with a handoff sub-agent — the handoff structure should be preserved
        AgentConfig config =
                AgentConfig.builder()
                        .name("ceo")
                        .model("openai/gpt-4o")
                        .instructions("Delegate to the right team lead")
                        .strategy("swarm")
                        .agents(
                                List.of(
                                        // engineering_lead has its own sub-agents (handoff
                                        // strategy)
                                        AgentConfig.builder()
                                                .name("engineering_lead")
                                                .model("openai/gpt-4o")
                                                .instructions("Route to backend or frontend dev")
                                                .strategy("handoff")
                                                .agents(
                                                        List.of(
                                                                simpleSubAgent(
                                                                        "backend_dev",
                                                                        "Handle backend tasks"),
                                                                simpleSubAgent(
                                                                        "frontend_dev",
                                                                        "Handle frontend tasks")))
                                                .build(),
                                        simpleSubAgent("marketing_lead", "Handle marketing tasks")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + Init + DoWhile + Final LLM
        assertThat(wf.getTasks()).hasSize(4);

        WorkflowTask loop = wf.getTasks().get(2);
        WorkflowTask switchTask =
                loop.getLoopOver().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();

        // Case "1" is engineering_lead (hierarchical)
        List<WorkflowTask> engCase = switchTask.getDecisionCases().get("1");
        assertThat(engCase).isNotEmpty();
        WorkflowTask engSubWf = engCase.get(0);
        assertThat(engSubWf.getType()).isEqualTo("SUB_WORKFLOW");

        // The inline workflow should use the hierarchical path:
        // inner SUB_WORKFLOW (handoff strategy) + coerce result + transfer LLM + check_transfer
        WorkflowDef engInlineWf = engSubWf.getSubWorkflowParam().getWorkflowDef();
        assertThat(engInlineWf.getTasks()).hasSize(4);
        assertThat(engInlineWf.getTasks().get(0).getType())
                .isEqualTo("SUB_WORKFLOW"); // inner handoff
        assertThat(engInlineWf.getTasks().get(1).getType())
                .isEqualTo("INLINE"); // coerce result to string
        assertThat(engInlineWf.getTasks().get(2).getType())
                .isEqualTo("LLM_CHAT_COMPLETE"); // transfer decision
        assertThat(engInlineWf.getTasks().get(3).getType()).isEqualTo("SIMPLE"); // check_transfer

        // The inner SUB_WORKFLOW should contain the handoff strategy (ctx_resolve + init + loop +
        // final)
        WorkflowDef innerHandoff =
                engInlineWf.getTasks().get(0).getSubWorkflowParam().getWorkflowDef();
        assertThat(innerHandoff.getTasks()).hasSize(4);
        assertThat(innerHandoff.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(innerHandoff.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE"); // init
        assertThat(innerHandoff.getTasks().get(2).getType()).isEqualTo("DO_WHILE"); // handoff loop
        assertThat(innerHandoff.getTasks().get(3).getType())
                .isEqualTo("LLM_CHAT_COMPLETE"); // handoff final

        // Verify the handoff loop has the switch with backend_dev and frontend_dev
        WorkflowTask handoffLoop = innerHandoff.getTasks().get(2);
        WorkflowTask handoffSwitch =
                handoffLoop.getLoopOver().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(handoffSwitch.getDecisionCases()).containsKeys("backend_dev", "frontend_dev");

        // Case "2" is marketing_lead (flat) — should use the original flat path
        List<WorkflowTask> mktCase = switchTask.getDecisionCases().get("2");
        assertThat(mktCase).isNotEmpty();
        WorkflowTask mktSubWf = mktCase.get(0);
        WorkflowDef mktInlineWf = mktSubWf.getSubWorkflowParam().getWorkflowDef();
        // Flat path: init_state + DO_WHILE(llm, tool_router, check_transfer)
        assertThat(mktInlineWf.getTasks()).hasSize(2);
        assertThat(mktInlineWf.getTasks().get(0).getType()).isEqualTo("SET_VARIABLE");
        assertThat(mktInlineWf.getTasks().get(1).getType()).isEqualTo("DO_WHILE");
    }

    @Test
    void testHandoffRouterPromptCoversAllParts() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .instructions("Route to the best agent.")
                        .strategy("handoff")
                        .agents(
                                List.of(
                                        simpleSubAgent("agent_a", "Handle A tasks"),
                                        simpleSubAgent("agent_b", "Handle B tasks")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Extract the router LLM system prompt from the loop
        // The router is a SUB_WORKFLOW wrapping an inner LLM task
        WorkflowTask loop = wf.getTasks().get(2);
        WorkflowTask routerSubWf = loop.getLoopOver().get(0);
        WorkflowTask innerLlm =
                routerSubWf.getSubWorkflowParam().getWorkflowDef().getTasks().get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) innerLlm.getInputParameters().get("messages");
        String systemMsg = (String) messages.get(0).get("message");

        // Should assign a coordinator role and handle multi-part requests
        assertThat(systemMsg).contains("coordinator that delegates");
        assertThat(systemMsg).contains("NOT yet been addressed");
        // Should enforce single-word output (agent name or DONE)
        assertThat(systemMsg).contains("single word");
        // Should NOT contain the old early-termination language
        assertThat(systemMsg)
                .doesNotContain("Once an agent has responded, you should typically respond DONE");
    }

    // ── Timeout tests ──────────────────────────────────────────────────

    @Test
    void testHandoffAppliesTimeout() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("team")
                        .model("openai/gpt-4o")
                        .instructions("Route tasks.")
                        .strategy("handoff")
                        .timeoutSeconds(120)
                        .agents(List.of(simpleSubAgent("a", "A"), simpleSubAgent("b", "B")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        assertThat(wf.getTimeoutSeconds()).isEqualTo(120L);
        assertThat(wf.getTimeoutPolicy()).isEqualTo(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
    }

    @Test
    void testSequentialAppliesTimeout() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("pipeline")
                        .model("openai/gpt-4o")
                        .instructions("Run steps.")
                        .strategy("sequential")
                        .timeoutSeconds(60)
                        .agents(
                                List.of(
                                        simpleSubAgent("step1", "First"),
                                        simpleSubAgent("step2", "Second")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        assertThat(wf.getTimeoutSeconds()).isEqualTo(60L);
        assertThat(wf.getTimeoutPolicy()).isEqualTo(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
    }

    @Test
    void testParallelAppliesTimeout() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("parallel_team")
                        .model("openai/gpt-4o")
                        .instructions("Run in parallel.")
                        .strategy("parallel")
                        .timeoutSeconds(90)
                        .agents(
                                List.of(
                                        simpleSubAgent("p1", "Agent 1"),
                                        simpleSubAgent("p2", "Agent 2")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        assertThat(wf.getTimeoutSeconds()).isEqualTo(90L);
    }

    @Test
    void testRouterAppliesTimeout() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("router_team")
                        .model("openai/gpt-4o")
                        .instructions("Route intelligently.")
                        .strategy("router")
                        .timeoutSeconds(45)
                        .agents(
                                List.of(
                                        simpleSubAgent("r1", "Agent 1"),
                                        simpleSubAgent("r2", "Agent 2")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        assertThat(wf.getTimeoutSeconds()).isEqualTo(45L);
    }

    @Test
    void testSwarmAppliesTimeout() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("swarm_team")
                        .model("openai/gpt-4o")
                        .instructions("Swarm orchestration.")
                        .strategy("swarm")
                        .timeoutSeconds(200)
                        .agents(
                                List.of(
                                        simpleSubAgent("s1", "Agent 1"),
                                        simpleSubAgent("s2", "Agent 2")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        assertThat(wf.getTimeoutSeconds()).isEqualTo(200L);
    }

    @Test
    void testRoundRobinAppliesTimeout() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("rr_team")
                        .model("openai/gpt-4o")
                        .instructions("Take turns.")
                        .strategy("round_robin")
                        .timeoutSeconds(30)
                        .agents(
                                List.of(
                                        simpleSubAgent("rr1", "Agent 1"),
                                        simpleSubAgent("rr2", "Agent 2")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        assertThat(wf.getTimeoutSeconds()).isEqualTo(30L);
    }

    @Test
    void testMultiAgentNoTimeoutSetsNoPolicy() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("no_timeout_team")
                        .model("openai/gpt-4o")
                        .instructions("No timeout set.")
                        .strategy("handoff")
                        .agents(List.of(simpleSubAgent("a", "A"), simpleSubAgent("b", "B")))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        // Default AgentCompiler timeoutSeconds is 0, so no timeout should be set
        assertThat(wf.getTimeoutPolicy()).isNull();
    }

    // ── Gate tests ──────────────────────────────────────────────────

    @Test
    void testSequentialWithTextGate() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("pipeline")
                        .model("openai/gpt-4o")
                        .strategy("sequential")
                        .agents(
                                List.of(
                                        AgentConfig.builder()
                                                .name("fetcher")
                                                .model("openai/gpt-4o")
                                                .instructions("Fetch issues")
                                                .gate(
                                                        Map.of(
                                                                "type",
                                                                "text_contains",
                                                                "text",
                                                                "NO_OPEN_ISSUES",
                                                                "caseSensitive",
                                                                true))
                                                .build(),
                                        simpleSubAgent("coder", "Write code"),
                                        simpleSubAgent("pusher", "Push PR")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_init_resolve + ctx_init + SUB_WORKFLOW(fetcher) + ctx_merge_0 + ctx_set_0 + coerce +
        // INLINE(gate) +
        // SWITCH(gate_switch) + output_selector
        assertThat(wf.getTasks()).hasSize(9);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_init_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE"); // ctx_init
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("SUB_WORKFLOW");
        assertThat(wf.getTasks().get(2).getTaskReferenceName()).contains("fetcher");
        assertThat(wf.getTasks().get(5).getType()).isEqualTo("INLINE"); // coerce
        assertThat(wf.getTasks().get(6).getType()).isEqualTo("INLINE"); // gate
        assertThat(wf.getTasks().get(6).getTaskReferenceName()).isEqualTo("pipeline_gate_0");
        assertThat(wf.getTasks().get(7).getType()).isEqualTo("SWITCH");
        assertThat(wf.getTasks().get(7).getTaskReferenceName()).isEqualTo("pipeline_gate_switch_0");
        assertThat(wf.getTasks().get(8).getType()).isEqualTo("INLINE"); // output_selector
        assertThat(wf.getTasks().get(8).getTaskReferenceName())
                .isEqualTo("pipeline_output_selector");

        // SWITCH should have "continue" case with remaining stages
        WorkflowTask switchTask = wf.getTasks().get(7);
        assertThat(switchTask.getDecisionCases()).containsKey("continue");
        List<WorkflowTask> continueTasks = switchTask.getDecisionCases().get("continue");
        // coder SUB_WORKFLOW + coerce + pusher SUB_WORKFLOW
        assertThat(continueTasks).hasSize(3);
        assertThat(continueTasks.get(0).getType()).isEqualTo("SUB_WORKFLOW");
        assertThat(continueTasks.get(0).getTaskReferenceName()).contains("coder");
        assertThat(continueTasks.get(2).getType()).isEqualTo("SUB_WORKFLOW");
        assertThat(continueTasks.get(2).getTaskReferenceName()).contains("pusher");

        // Default case (stop) should be empty
        assertThat(switchTask.getDefaultCase()).isEmpty();
    }

    @Test
    void testSequentialWithoutGate() {
        // Ensure no gate = no SWITCH task
        AgentConfig config =
                AgentConfig.builder()
                        .name("pipeline")
                        .model("openai/gpt-4o")
                        .strategy("sequential")
                        .agents(
                                List.of(
                                        simpleSubAgent("a", "Step A"),
                                        simpleSubAgent("b", "Step B")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_init_resolve + ctx_init + SUB_WORKFLOW(a) + ctx_merge_0 + ctx_set_0 + coerce +
        // SUB_WORKFLOW(b) +
        // ctx_merge_1 + ctx_set_1 — no SWITCH
        assertThat(wf.getTasks()).hasSize(9);
        boolean hasSwitch = wf.getTasks().stream().anyMatch(t -> "SWITCH".equals(t.getType()));
        assertThat(hasSwitch).isFalse();
    }

    @Test
    void testSequentialWithWorkerGate() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("pipeline")
                        .model("openai/gpt-4o")
                        .strategy("sequential")
                        .agents(
                                List.of(
                                        AgentConfig.builder()
                                                .name("fetcher")
                                                .model("openai/gpt-4o")
                                                .instructions("Fetch")
                                                .gate(Map.of("taskName", "fetcher_gate"))
                                                .build(),
                                        simpleSubAgent("coder", "Code")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_init_resolve + ctx_init + SUB_WORKFLOW + ctx_merge_0 + ctx_set_0 + coerce +
        // SIMPLE(gate) + SWITCH +
        // output_selector
        assertThat(wf.getTasks()).hasSize(9);
        assertThat(wf.getTasks().get(6).getType()).isEqualTo("SIMPLE");
        assertThat(wf.getTasks().get(6).getName()).isEqualTo("fetcher_gate");
        assertThat(wf.getTasks().get(7).getType()).isEqualTo("SWITCH");
        assertThat(wf.getTasks().get(8).getType()).isEqualTo("INLINE"); // output_selector
    }

    // ── Plan-Execute tests ──────────────────────────────────────────

    @Test
    void testPlanExecute_emits_planner_route_switch_gating_on_static_plan() {
        // dg-review F1 / recommendation #13: when workflow.input.static_plan
        // is supplied, the planner LLM_CHAT_COMPLETE's output is discarded
        // by extract_json Case 0. Running it costs tokens + latency. The
        // compiler emits a SWITCH that routes around the planner sub-workflow
        // when static_plan is present.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("pae_with_gate")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);

        // Find the planner_route SWITCH at the top level of the harness.
        WorkflowTask gate =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "INLINE".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_planner_gate"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "missing planner_gate INLINE — static_plan gating not emitted"));
        assertThat((String) gate.getInputParameters().get("staticPlan"))
                .isEqualTo("${workflow.input.static_plan}");

        WorkflowTask route =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_planner_route"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "missing planner_route SWITCH — static_plan gating not emitted"));

        // The skip case must exist and contain a single no-op INLINE
        // (NOT a planner SUB_WORKFLOW or any LLM call).
        assertThat(route.getDecisionCases()).containsKey("skip");
        List<WorkflowTask> skipBranch = route.getDecisionCases().get("skip");
        assertThat(skipBranch).hasSize(1);
        assertThat(skipBranch.get(0).getType()).isEqualTo("INLINE");
        assertThat(skipBranch.get(0).getTaskReferenceName()).endsWith("_planner_skipped");

        // The default case must contain the planner sub-workflow + its
        // three follow-on tasks (merge, ctx_set, coerce). Any of those
        // four being moved out of the SWITCH defeats the gating.
        List<WorkflowTask> live = route.getDefaultCase();
        assertThat(live).hasSize(4);
        // First task is the planner sub-workflow.
        assertThat(live.get(0).getType()).isEqualTo("SUB_WORKFLOW");
        assertThat(live.get(0).getTaskReferenceName()).endsWith("_planner");
        // Remaining three are the INLINE merge / SET_VARIABLE / coerce.
        assertThat(live.get(1).getType()).isEqualTo("INLINE");
        assertThat(live.get(1).getTaskReferenceName()).endsWith("_planner_ctx_merge");
        assertThat(live.get(2).getType()).isEqualTo("SET_VARIABLE");
        assertThat(live.get(2).getTaskReferenceName()).endsWith("_planner_ctx_set");
        assertThat(live.get(3).getType()).isEqualTo("INLINE");
        assertThat(live.get(3).getTaskReferenceName()).endsWith("_planner_coerce");

        // The gate expression returns 'run' for null staticPlan and 'skip'
        // for object/non-empty-string. Pin both behaviours via spec.
        String expr = (String) gate.getInputParameters().get("expression");
        assertThat(expr).contains("if (sp == null) return 'run'").contains("return 'skip'");

        // /dg #3: the gate must mirror extract_json Case 0's accept-criteria
        // — objects need a ``steps`` key, strings need substring ``"steps"``
        // — otherwise an empty dict ``{}`` from ``runtime.run(plan={})``
        // takes the skip branch and then no-plan-found fallback fires.
        assertThat(expr)
                .as("object skip must require a steps key to mirror extract_json Case 0")
                .contains("hasSteps = sp.steps != null")
                .contains("hasSteps ? 'skip' : 'run'");
        assertThat(expr)
                .as("string skip must require a steps-shaped JSON substring")
                .contains("sp.indexOf('\"steps\"') >= 0");
    }

    @Test
    void testPlanExecute_with_text_only_plannerContext_emits_ctx_build_inside_live_branch() {
        // plannerContext = [{text: "..."}, {text: "..."}] must produce an
        // INLINE that joins them into the planner's ## Reference Context
        // block. No HTTP fetch tasks; the INLINE lives in the SWITCH's
        // default branch (so the static-plan path skips it for free).
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("pae_with_text_ctx")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .plannerContext(
                                List.of(
                                        Map.of(
                                                "text",
                                                "Onboarding takes 3 phases: KYC, setup, training."),
                                        Map.of(
                                                "text",
                                                "Reject KYC unless ID + proof-of-address are both present.")))
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);

        WorkflowTask route =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_planner_route"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing planner_route SWITCH"));

        List<WorkflowTask> live = route.getDefaultCase();
        // First task in the live branch must be the context-builder INLINE
        // — emitted BEFORE the planner so its output can be templated into
        // the planner's prompt.
        WorkflowTask ctxBuild = live.get(0);
        assertThat(ctxBuild.getType()).isEqualTo("INLINE");
        assertThat(ctxBuild.getTaskReferenceName()).endsWith("_ctx_build");

        // No HTTP fetches in the live branch for text-only context — only
        // the planner-stage core (planner SUB_WORKFLOW + merge + ctx_set +
        // coerce) PLUS the ctx_build INLINE = 5 tasks total.
        assertThat(live).hasSize(5);
        long httpCount = live.stream().filter(t -> "HTTP".equals(t.getType())).count();
        assertThat(httpCount).isZero();

        // Regression guard: the ctx_build INLINE's expression MUST NOT
        // contain a literal ``${`` — Conductor's ParametersUtils scans
        // every input-parameter value for ``${path}`` and interpolates,
        // and it doesn't parse JS quoting. A literal ``${`` inside our
        // script string would be eaten at task-dispatch time, breaking
        // the JS. Real failure caught while running example 115 against
        // a built server — fix is in plannerContextBuilderScript via
        // ``TPL_OPEN = '$' + '{'`` runtime concat.
        String expr = (String) ctxBuild.getInputParameters().get("expression");
        assertThat(expr)
                .as(
                        "ctx_build expression must not contain literal ${ — Conductor templater would substitute it")
                .doesNotContain("${");

        // Skip branch must still be exactly the no-op INLINE — context-
        // builder is NOT in the skip branch, so static_plan path is free.
        List<WorkflowTask> skip = route.getDecisionCases().get("skip");
        assertThat(skip).hasSize(1);
        assertThat(skip.get(0).getTaskReferenceName()).endsWith("_planner_skipped");
    }

    @Test
    void testPlanExecute_with_url_plannerContext_emits_http_fetch_with_escaped_credentials() {
        // plannerContext entry with a URL + credentialed headers must:
        //   1) emit an HTTP fetch task in the live branch BEFORE ctx_build
        //   2) rewrite ${CRED} → ${workflow.secrets.CRED} in headers (matches
        //      ToolCompiler's pipeline so credential resolution is single-source)
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("pae_with_url_ctx")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .plannerContext(
                                List.of(
                                        Map.of(
                                                "url",
                                                "https://confluence.example.com/onboarding-rules.md",
                                                "headers",
                                                Map.of(
                                                        "Authorization",
                                                        "Bearer ${CONFLUENCE_TOKEN}"))))
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);

        WorkflowTask route =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_planner_route"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing planner_route SWITCH"));

        List<WorkflowTask> live = route.getDefaultCase();
        WorkflowTask fetch = live.get(0);
        // /dg #4: task type is now PLANNER_CONTEXT_FETCH (custom system
        // task with cache + ETag) instead of Conductor's built-in HTTP.
        assertThat(fetch.getType()).isEqualTo("PLANNER_CONTEXT_FETCH");
        assertThat(fetch.getTaskReferenceName()).endsWith("_ctx_fetch_0");

        // Inputs are flattened (no nested http_request wrapper) so the
        // PLANNER_CONTEXT_FETCH system task can read them directly.
        Map<String, Object> inputs = fetch.getInputParameters();
        assertThat(inputs)
                .containsEntry("url", "https://confluence.example.com/onboarding-rules.md");

        // Headers must have the credential placeholder rewritten to a secret reference.
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) inputs.get("headers");
        assertThat(headers)
                .containsEntry("Authorization", "Bearer ${workflow.secrets.CONFLUENCE_TOKEN}");

        // ctx_build INLINE comes after the fetch, referencing its
        // output.response.body via template.
        WorkflowTask ctxBuild = live.get(1);
        assertThat(ctxBuild.getType()).isEqualTo("INLINE");
        assertThat(ctxBuild.getTaskReferenceName()).endsWith("_ctx_build");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> descriptors =
                (List<Map<String, Object>>) ctxBuild.getInputParameters().get("entries");
        assertThat(descriptors).hasSize(1);
        assertThat(descriptors.get(0)).containsEntry("type", "url");
        assertThat((String) descriptors.get(0).get("body"))
                .isEqualTo("${" + fetch.getTaskReferenceName() + ".output.response.body}");
    }

    @Test
    void testPlanExecute_output_select_reads_final_result_variable_not_branch_refs() {
        // /dg #5: the output selector used to pattern-match four mutually-
        // exclusive ``${prefix_X.output.result}`` template strings to find
        // the live one — Conductor leaves unresolved refs as literal
        // ``${...}`` strings, and the script filtered them with a
        // ``String.fromCharCode(36) + '{'`` marker. Refactored: each of
        // the four terminal arms now writes ``workflow.variables.final_result``
        // via SET_VARIABLE, and the selector reads from that single
        // resolved variable. No more dead-branch leftovers to filter.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig fallback = simpleSubAgent("fallback", "Fix");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("out_sel_refactor")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .fallback(fallback)
                        .build();
        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);

        WorkflowTask outputSelect =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "INLINE".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_output_select"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing _output_select INLINE"));

        // Reads from the workflow variable, not from four branch refs.
        assertThat(outputSelect.getInputParameters().get("r"))
                .as("output selector must read workflow.variables.final_result")
                .isEqualTo("${workflow.variables.final_result}");
        assertThat(outputSelect.getInputParameters())
                .as(
                        "output selector must not reference branch refs anymore — "
                                + "previously had planResult / fallbackResult / "
                                + "compileFallbackResult / noPlanResult inputs")
                .doesNotContainKeys(
                        "planResult", "fallbackResult", "compileFallbackResult", "noPlanResult");

        // The expression must not need a fromCharCode dollar-sign trick
        // anymore — the variable resolves cleanly, no template-string
        // pattern-matching required.
        String expr = (String) outputSelect.getInputParameters().get("expression");
        assertThat(expr)
                .as("expression must not pattern-match unresolved templates")
                .doesNotContain("fromCharCode")
                .doesNotContain("indexOf");

        // Verify the four terminal SET_VARIABLEs exist.
        java.util.Set<String> setVarRefs =
                collectTaskRefsRecursive(wf.getTasks()).stream()
                        .filter(r -> r.endsWith("_set"))
                        .collect(java.util.stream.Collectors.toSet());
        assertThat(setVarRefs)
                .as("each terminal arm writes final_result via SET_VARIABLE")
                .contains(
                        "out_sel_refactor_exec_success_set",
                        "out_sel_refactor_fallback_set",
                        "out_sel_refactor_compile_fallback_set",
                        "out_sel_refactor_noplan_fallback_set");
    }

    /**
     * Walk the workflow's task tree (top-level + every SWITCH branch) and collect
     * taskReferenceNames. Used by the output-selector test to find SET_VARIABLEs that live inside
     * SWITCH branches.
     */
    private static List<String> collectTaskRefsRecursive(List<WorkflowTask> tasks) {
        List<String> refs = new java.util.ArrayList<>();
        if (tasks == null) return refs;
        for (WorkflowTask t : tasks) {
            if (t.getTaskReferenceName() != null) refs.add(t.getTaskReferenceName());
            if (t.getDecisionCases() != null) {
                for (List<WorkflowTask> branch : t.getDecisionCases().values()) {
                    refs.addAll(collectTaskRefsRecursive(branch));
                }
            }
            if (t.getDefaultCase() != null) {
                refs.addAll(collectTaskRefsRecursive(t.getDefaultCase()));
            }
            if (t.getForkTasks() != null) {
                for (List<WorkflowTask> branch : t.getForkTasks()) {
                    refs.addAll(collectTaskRefsRecursive(branch));
                }
            }
        }
        return refs;
    }

    @Test
    void testPlanExecute_plannerContext_credential_escape_only_anchored_identifiers() {
        // /dg #2: the credential rewrite used to be a greedy substring match
        // ``replace("${","#{")``. That ate any opening brace pair, including
        // literal ``${...}`` substrings that happened to start with ``${`` but
        // weren't credentials. Anchored regex now only rewrites
        // ``${IDENTIFIER}`` patterns to ``${workflow.secrets.IDENTIFIER}`` —
        // anything that doesn't look like a placeholder passes through verbatim.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("pae_credential_escape")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .plannerContext(
                                List.of(
                                        Map.of(
                                                "url",
                                                "https://docs.example.com/page",
                                                "headers",
                                                Map.of(
                                                        "Authorization",
                                                                "Bearer ${CONFLUENCE_TOKEN}",
                                                        // Mixed: a placeholder + literal ${...}
                                                        // substring that
                                                        // looks like one but isn't (no closing
                                                        // brace).
                                                        "X-Custom",
                                                                "value-with-${UNCLOSED and ${REAL_CRED}",
                                                        // Pure literal — no rewrites at all.
                                                        "X-Literal", "plain-text-value"))))
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        WorkflowTask route =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_planner_route"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing planner_route SWITCH"));
        WorkflowTask fetch =
                route.getDefaultCase().stream()
                        .filter(
                                t ->
                                        t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_ctx_fetch_0"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "missing _ctx_fetch_0 task in live branch"));

        // /dg #4: headers live at the top level of inputParameters now,
        // not nested under ``http_request`` — the PLANNER_CONTEXT_FETCH
        // system task reads them directly.
        @SuppressWarnings("unchecked")
        Map<String, Object> headers =
                (Map<String, Object>) fetch.getInputParameters().get("headers");

        assertThat(headers)
                .as("anchored placeholder rewritten to ${workflow.secrets.IDENTIFIER}")
                .containsEntry("Authorization", "Bearer ${workflow.secrets.CONFLUENCE_TOKEN}");
        assertThat(headers)
                .as("real placeholder rewritten; un-anchored ${...} preserved")
                .containsEntry(
                        "X-Custom", "value-with-${UNCLOSED and ${workflow.secrets.REAL_CRED}");
        assertThat(headers)
                .as("literal value with no placeholder passes through unchanged")
                .containsEntry("X-Literal", "plain-text-value");
    }

    @Test
    void testPlanExecute_plannerContext_rejects_CRLF_in_header_value() {
        // /dg #2: header values must reject CR/LF to close the
        // HTTP-response-splitting / header-injection vector.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("pae_crlf_reject")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .plannerContext(
                                List.of(
                                        Map.of(
                                                "url",
                                                "https://docs.example.com/page",
                                                "headers",
                                                Map.of("X-Bad", "value\r\nInjected: header"))))
                        .build();

        assertThatThrownBy(() -> new MultiAgentCompiler(compiler).compile(harness))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CR/LF")
                .hasMessageContaining("X-Bad");
    }

    @Test
    void testPlanExecute_url_plannerContext_with_required_false_sets_optional_on_fetch() {
        // required=false → fetch task gets .optional=true so a fetch failure
        // doesn't fail the workflow; the INLINE then substitutes the
        // [doc unavailable] marker.
        //
        // /dg #4: with ≥2 URLs the fetches are now wrapped in a FORK_JOIN
        // for parallel execution. The fetch tasks live inside the fork's
        // branches, not at the top of the live branch.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("pae_with_optional_url_ctx")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .plannerContext(
                                List.of(
                                        Map.of("url", "https://docs.example.com/required.md"),
                                        Map.of(
                                                "url",
                                                "https://docs.example.com/optional.md",
                                                "required",
                                                false)))
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);

        WorkflowTask route =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_planner_route"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing planner_route SWITCH"));

        List<WorkflowTask> live = route.getDefaultCase();

        // First task in the live branch is the FORK_JOIN (≥2 URLs).
        WorkflowTask fork = live.get(0);
        assertThat(fork.getType())
                .as("≥2 URLs must be wrapped in FORK_JOIN for parallel fetching")
                .isEqualTo("FORK_JOIN");
        assertThat(fork.getForkTasks()).hasSize(2);

        // JOIN immediately after FORK_JOIN.
        WorkflowTask join = live.get(1);
        assertThat(join.getType()).isEqualTo("JOIN");
        assertThat(join.getJoinOn()).hasSize(2);

        // Each fork branch contains one fetch task.
        WorkflowTask requiredFetch = fork.getForkTasks().get(0).get(0);
        WorkflowTask optionalFetch = fork.getForkTasks().get(1).get(0);
        assertThat(requiredFetch.getTaskReferenceName()).endsWith("_ctx_fetch_0");
        assertThat(optionalFetch.getTaskReferenceName()).endsWith("_ctx_fetch_1");
        assertThat(requiredFetch.isOptional()).isFalse();
        assertThat(optionalFetch.isOptional()).isTrue();
    }

    @Test
    void testPlanExecute_without_plannerContext_emits_no_ctx_build_task() {
        // Counterfactual: without plannerContext, the live branch goes back
        // to its 4-task core (planner + merge + ctx_set + coerce). Proves
        // the ctx_build emission is gated on the field's presence.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("pae_no_ctx")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);

        WorkflowTask route =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName() != null
                                                && t.getTaskReferenceName()
                                                        .endsWith("_planner_route"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("missing planner_route SWITCH"));

        List<WorkflowTask> live = route.getDefaultCase();
        assertThat(live).hasSize(4);
        assertThat(
                        live.stream()
                                .noneMatch(
                                        t ->
                                                t.getTaskReferenceName() != null
                                                        && (t.getTaskReferenceName()
                                                                        .endsWith("_ctx_build")
                                                                || t.getTaskReferenceName()
                                                                        .contains("_ctx_fetch_"))))
                .isTrue();
    }

    @Test
    void testPlanExecuteWithFallback() {
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig fallback = simpleSubAgent("fallback", "Fix errors");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("harness")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .fallback(fallback)
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        assertThat(wf.getName()).isEqualTo("harness");

        boolean hasPlanRouteSwitch =
                wf.getTasks().stream()
                        .anyMatch(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("plan_route"));
        assertThat(hasPlanRouteSwitch).isTrue();

        // has_plan branch 'failed' path must route to a fallback SUB_WORKFLOW (not TERMINATE)
        WorkflowTask routeSwitch2 =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("plan_route"))
                        .findFirst()
                        .orElseThrow();
        List<WorkflowTask> hasPlanBranch2 = routeSwitch2.getDecisionCases().get("has_plan");
        assertThat(hasPlanBranch2).isNotNull();
        WorkflowTask execRouteSwitch2 =
                compileSuccessTasks(hasPlanBranch2).stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("exec_route"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "Expected exec_route SWITCH in compile-success branch"));
        List<WorkflowTask> execFailedBranch2 = execRouteSwitch2.getDecisionCases().get("failed");
        assertThat(execFailedBranch2).isNotEmpty();
        // With a fallback agent, the last task in the failed branch must be a SUB_WORKFLOW
        // (fallback agent)
        // Not a TERMINATE — that would mean the fallback was silently dropped
        boolean hasFallbackSubWorkflow =
                execFailedBranch2.stream().anyMatch(t -> "SUB_WORKFLOW".equals(t.getType()));
        assertThat(hasFallbackSubWorkflow)
                .as(
                        "Expected fallback SUB_WORKFLOW in the failed branch when fallbackConfig is provided")
                .isTrue();
    }

    @Test
    void testPlanExecuteWithoutFallback_singleAgent() {
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("coder")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        assertThat(wf.getName()).isEqualTo("coder");

        boolean hasPlanRouteSwitch =
                wf.getTasks().stream()
                        .anyMatch(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("plan_route"));
        assertThat(hasPlanRouteSwitch).isTrue();

        // Find the plan_route SWITCH
        WorkflowTask routeSwitch =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("plan_route"))
                        .findFirst()
                        .orElseThrow();

        // no-plan branch (defaultCase) must terminate with FAILED — no fallback sub-workflow
        List<WorkflowTask> noPlanBranch = routeSwitch.getDefaultCase();
        assertThat(noPlanBranch).isNotEmpty();
        WorkflowTask noPlanLastTask = noPlanBranch.get(noPlanBranch.size() - 1);
        assertThat(noPlanLastTask.getType()).isEqualTo("TERMINATE");
        assertThat(noPlanLastTask.getInputParameters().get("terminationStatus"))
                .isEqualTo("FAILED");

        // has_plan branch must contain an exec_route SWITCH whose 'failed' case also TERMINATEs.
        // The exec_route now lives inside compile_gate's defaultCase (compile-success path).
        List<WorkflowTask> hasPlanBranch = routeSwitch.getDecisionCases().get("has_plan");
        assertThat(hasPlanBranch).isNotNull();
        WorkflowTask execRouteSwitch =
                compileSuccessTasks(hasPlanBranch).stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("exec_route"))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "Expected exec_route SWITCH in compile-success branch"));
        List<WorkflowTask> execFailedBranch = execRouteSwitch.getDecisionCases().get("failed");
        assertThat(execFailedBranch).isNotEmpty();
        WorkflowTask execFailedLast = execFailedBranch.get(execFailedBranch.size() - 1);
        assertThat(execFailedLast.getType()).isEqualTo("TERMINATE");
        assertThat(execFailedLast.getInputParameters().get("terminationStatus"))
                .isEqualTo("FAILED");
    }

    @Test
    void testPlanExecute_failsCompile_whenGuardrailedToolHasRetryButNoFallback() {
        // RETRY/FIX/HUMAN guardrails collapse to TERMINATE in plan mode;
        // without a fallback agent the whole pipeline silently degrades to
        // fail-loud-on-trip instead of the retry-with-feedback semantics
        // the user asked for. PAC used to log.warn; now it fails compile
        // and forces the user to either configure a fallback or
        // explicitly set on_fail=raise.
        GuardrailConfig retryGuard =
                GuardrailConfig.builder()
                        .name("size_limit")
                        .guardrailType("regex")
                        .position("input")
                        .onFail("retry")
                        .patterns(List.of("too_big"))
                        .mode("block")
                        .build();
        ToolConfig guardedTool =
                ToolConfig.builder()
                        .name("upload")
                        .toolType("worker")
                        .guardrails(List.of(retryGuard))
                        .build();

        AgentConfig planner = simpleSubAgent("planner", "Plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("no_fb_with_retry_guardrail")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .tools(List.of(guardedTool))
                        // intentionally no fallback
                        .build();

        assertThatThrownBy(() -> new MultiAgentCompiler(compiler).compile(harness))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("on_fail=retry")
                .hasMessageContaining("fallback")
                .hasMessageContaining("upload");
    }

    @Test
    void testPlanExecute_compilesOk_whenGuardrailHasRetryButHarnessHasFallback() {
        // Same shape as the previous test, with a fallback added —
        // compile must succeed because retry/fix/human can be served by
        // the fallback's LLM-loop recovery.
        GuardrailConfig retryGuard =
                GuardrailConfig.builder()
                        .name("size_limit")
                        .guardrailType("regex")
                        .position("input")
                        .onFail("retry")
                        .patterns(List.of("too_big"))
                        .mode("block")
                        .build();
        ToolConfig guardedTool =
                ToolConfig.builder()
                        .name("upload")
                        .toolType("worker")
                        .guardrails(List.of(retryGuard))
                        .build();

        AgentConfig planner = simpleSubAgent("planner", "Plan");
        AgentConfig fallback = simpleSubAgent("fb", "Recover");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("ok_with_fallback")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .fallback(fallback)
                        .tools(List.of(guardedTool))
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        assertThat(wf).isNotNull();
        assertThat(wf.getName()).isEqualTo("ok_with_fallback");
    }

    @Test
    void testPlanExecute_compilesOk_whenGuardrailIsOnFailRaiseWithoutFallback() {
        // on_fail=raise acknowledges fail-closed semantics — compile must
        // succeed without a fallback. This is the "I know retry collapses
        // and I'm fine with it" path that the new compile-error guards.
        GuardrailConfig raiseGuard =
                GuardrailConfig.builder()
                        .name("size_limit")
                        .guardrailType("regex")
                        .position("input")
                        .onFail("raise")
                        .patterns(List.of("too_big"))
                        .mode("block")
                        .build();
        ToolConfig guardedTool =
                ToolConfig.builder()
                        .name("upload")
                        .toolType("worker")
                        .guardrails(List.of(raiseGuard))
                        .build();

        AgentConfig planner = simpleSubAgent("planner", "Plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("ok_raise_no_fallback")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .tools(List.of(guardedTool))
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        assertThat(wf).isNotNull();
        assertThat(wf.getName()).isEqualTo("ok_raise_no_fallback");
    }

    @Test
    void testPlanExecute_failsCompile_whenGuardrailedToolCannotSerialize() {
        // A guardrail wrapper that Jackson can't serialise must fail the
        // PAC compile, not silently drop the guardrail and emit a bare
        // SIMPLE. Drop = fail-open on a safety control; we want fail-closed.
        // The smallest way to force convertValue() to throw is a circular
        // reference in the tool's config map: Jackson stack-overflows /
        // throws JsonMappingException trying to walk it.
        java.util.Map<String, Object> cyclic = new java.util.LinkedHashMap<>();
        cyclic.put("self", cyclic);

        GuardrailConfig g =
                GuardrailConfig.builder()
                        .name("must_wrap")
                        .guardrailType("regex")
                        .position("input")
                        .onFail("raise")
                        .patterns(List.of("never"))
                        .mode("block")
                        .build();
        ToolConfig badTool =
                ToolConfig.builder()
                        .name("circular_config_tool")
                        .toolType("worker")
                        .guardrails(List.of(g))
                        .config(cyclic)
                        .build();

        AgentConfig planner = simpleSubAgent("planner", "Plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("fail_closed_harness")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .tools(List.of(badTool))
                        .build();

        assertThatThrownBy(() -> new MultiAgentCompiler(compiler).compile(harness))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("circular_config_tool")
                .hasMessageContaining("guardrail");
    }

    @Test
    void testPlanExecuteRequiresPlannerSlot() {
        // No planner slot — must reject with a clear migration message.
        // The legacy ``agents=[planner, fallback]`` positional shape is no
        // longer accepted at the server (matches the Python SDK's hard cut
        // at construction time).
        AgentConfig harness =
                AgentConfig.builder()
                        .name("bad")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .build();

        assertThatThrownBy(() -> new MultiAgentCompiler(compiler).compile(harness))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires ``planner=")
                .hasMessageContaining("no longer accepted");
    }

    @Test
    void testPlanExecuteRejectsLegacyAgentsList() {
        // Even when ``agents=[planner, fallback]`` is provided — the hard
        // cut means it's rejected. Forces the user to migrate to named slots.
        AgentConfig planner =
                AgentConfig.builder()
                        .name("planner_inner")
                        .model("openai/gpt-4o-mini")
                        .instructions("p")
                        .build();
        AgentConfig fallback =
                AgentConfig.builder()
                        .name("fallback_inner")
                        .model("openai/gpt-4o-mini")
                        .instructions("f")
                        .build();
        AgentConfig harness =
                AgentConfig.builder()
                        .name("bad_legacy")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .agents(List.of(planner, fallback)) // legacy positional
                        .build();

        assertThatThrownBy(() -> new MultiAgentCompiler(compiler).compile(harness))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("named slots");
    }

    @Test
    void testPlanExecutePlanSourceWithUnknownToolIsRejectedAtCompile() {
        // planSource.tool that isn't registered anywhere in the harness must
        // surface a compile-time error — not silently swallow at runtime.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("bad_plan_source")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .planSource(Map.of("tool", "tool_that_does_not_exist", "args", Map.of()))
                        .build();

        assertThatThrownBy(() -> new MultiAgentCompiler(compiler).compile(harness))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan_source.tool")
                .hasMessageContaining("tool_that_does_not_exist");
    }

    @Test
    void testPlanExecutePlanSourceMissingToolFieldIsRejected() {
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("bad_plan_source_2")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .planSource(Map.of("args", Map.of("section", "x"))) // no "tool"
                        .build();

        assertThatThrownBy(() -> new MultiAgentCompiler(compiler).compile(harness))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty 'tool'");
    }

    @Test
    void testPlanExecutePlanSourceWithHarnessLevelToolCompiles() {
        // Counter-test: tool registered on the harness itself compiles cleanly.
        // The harness namespace is what matters because plan_reader is emitted
        // as a SIMPLE task at the parent level.
        ToolConfig contextbookRead =
                ToolConfig.builder()
                        .name("contextbook_read")
                        .description("Read from contextbook")
                        .toolType("worker")
                        .inputSchema(Map.of("type", "object"))
                        .build();
        AgentConfig planner = simpleSubAgent("planner", "Plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("good_plan_source")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .tools(List.of(contextbookRead)) // ← harness-level
                        .planSource(
                                Map.of(
                                        "tool",
                                        "contextbook_read",
                                        "args",
                                        Map.of("section", "coder_plan")))
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        assertThat(wf.getName()).isEqualTo("good_plan_source");

        // Verify the plan_reader SIMPLE task actually got emitted.
        boolean hasPlanReader =
                wf.getTasks().stream()
                        .anyMatch(
                                t ->
                                        "SIMPLE".equals(t.getType())
                                                && "contextbook_read".equals(t.getName())
                                                && t.getTaskReferenceName()
                                                        .contains("plan_reader"));
        assertThat(hasPlanReader)
                .as("Expected a SIMPLE plan_reader task calling contextbook_read")
                .isTrue();
    }

    @Test
    void testPlanExecutePlanSourceWithSubAgentOnlyToolIsRejected() {
        // Tool registered only on a sub-agent (not on the harness) must fail
        // compile. The plan_reader SIMPLE task is emitted in the harness's task
        // namespace; a worker registered only on a sub-agent will not be polled
        // for the parent's task. Surfacing this at deploy beats a silent
        // runtime hang.
        ToolConfig contextbookRead =
                ToolConfig.builder()
                        .name("contextbook_read")
                        .description("Read from contextbook")
                        .toolType("worker")
                        .inputSchema(Map.of("type", "object"))
                        .build();
        AgentConfig planner =
                AgentConfig.builder()
                        .name("planner")
                        .model("openai/gpt-4o-mini")
                        .instructions("Plan")
                        .tools(List.of(contextbookRead)) // ← only on sub-agent
                        .build();
        AgentConfig harness =
                AgentConfig.builder()
                        .name("sub_agent_only_tool")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .planSource(Map.of("tool", "contextbook_read", "args", Map.of()))
                        .build();

        assertThatThrownBy(() -> new MultiAgentCompiler(compiler).compile(harness))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan_source.tool")
                .hasMessageContaining("contextbook_read")
                .hasMessageContaining("not registered");
    }

    @Test
    void testPlanExecuteSurfacesCompileErrors() {
        // Verify the new compile-error gate exists: after compile_plan there
        // should be a compile_status INLINE that emits 'compile_error' on
        // {error: "..."} returns, and a compile_gate SWITCH that TERMINATEs
        // with the actual error message instead of letting parse_wf trip.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig fallback = simpleSubAgent("fallback", "Fix");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("error_surfacing")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .fallback(fallback)
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);

        WorkflowTask routeSwitch =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("plan_route"))
                        .findFirst()
                        .orElseThrow();
        List<WorkflowTask> hasPlanBranch = routeSwitch.getDecisionCases().get("has_plan");
        assertThat(hasPlanBranch).isNotNull();

        boolean hasCompileStatus =
                hasPlanBranch.stream()
                        .anyMatch(
                                t ->
                                        "INLINE".equals(t.getType())
                                                && t.getTaskReferenceName()
                                                        .contains("compile_status"));
        assertThat(hasCompileStatus)
                .as("has_plan branch must include compile_status INLINE to detect compile errors")
                .isTrue();

        WorkflowTask compileGate =
                hasPlanBranch.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName()
                                                        .contains("compile_gate"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Expected compile_gate SWITCH"));
        List<WorkflowTask> errBranch = compileGate.getDecisionCases().get("compile_failed");
        assertThat(errBranch).isNotEmpty();
        // With a fallback agent configured, compile failure routes to the fallback
        // SUB_WORKFLOW. The last task is now a SET_VARIABLE that writes the
        // fallback's result into ``workflow.variables.final_result`` for the
        // output selector (/dg #5). The fallback SUB_WORKFLOW lives just before it.
        WorkflowTask lastErrTask = errBranch.get(errBranch.size() - 1);
        assertThat(lastErrTask.getType())
                .as("compile_failed branch ends with the final_result SET_VARIABLE (/dg #5)")
                .isEqualTo("SET_VARIABLE");
        WorkflowTask penultimate = errBranch.get(errBranch.size() - 2);
        assertThat(penultimate.getType())
                .as(
                        "compile_failed branch's terminal action must still be the fallback SUB_WORKFLOW")
                .isEqualTo("SUB_WORKFLOW");
    }

    @Test
    void testPlanExecuteCompileErrorTerminatesWhenNoFallback() {
        // Counter-test: with no fallback agent, compile failure must TERMINATE
        // with a visible error message rather than silently swallowing.
        AgentConfig planner = simpleSubAgent("planner", "Plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("no_fallback_compile")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        WorkflowTask routeSwitch =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("plan_route"))
                        .findFirst()
                        .orElseThrow();
        List<WorkflowTask> hasPlanBranch = routeSwitch.getDecisionCases().get("has_plan");
        WorkflowTask compileGate =
                hasPlanBranch.stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName()
                                                        .contains("compile_gate"))
                        .findFirst()
                        .orElseThrow();
        List<WorkflowTask> errBranch = compileGate.getDecisionCases().get("compile_failed");
        assertThat(errBranch).hasSize(1);
        assertThat(errBranch.get(0).getType()).isEqualTo("TERMINATE");
        assertThat(errBranch.get(0).getInputParameters().get("terminationReason"))
                .asString()
                .contains("Plan compilation failed");
    }

    @Test
    void testPlanExecuteSubWorkflowForwardsCwdCredentialsMedia() {
        // Sub-workflow input must include cwd / credentials / media so the
        // compiled plan's tools have everything the parent does. Previously
        // these were silently dropped, forcing examples to hardcode WORK_DIR.
        AgentConfig planner = simpleSubAgent("planner", "Write a plan");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("forwarding")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        WorkflowTask routeSwitch =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("plan_route"))
                        .findFirst()
                        .orElseThrow();
        List<WorkflowTask> hasPlanBranch = routeSwitch.getDecisionCases().get("has_plan");
        WorkflowTask exec =
                compileSuccessTasks(hasPlanBranch).stream()
                        .filter(t -> "SUB_WORKFLOW".equals(t.getType()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Expected SUB_WORKFLOW task"));

        Map<String, Object> inputs = exec.getInputParameters();
        assertThat(inputs).containsKey("cwd");
        assertThat(inputs).containsKey("credentials");
        assertThat(inputs).containsKey("media");
        assertThat(inputs.get("cwd")).isEqualTo("${workflow.input.cwd}");
        assertThat(inputs.get("credentials")).isEqualTo("${workflow.input.credentials}");
    }

    @Test
    void testAgentConfigToBuilderPreservesAllFieldsExceptOverridden() {
        // The fallback rebuild in compilePlanExecute uses ``toBuilder().maxTurns(N).build()``
        // instead of an explicit field-copy whitelist (which previously dropped
        // memory/promptInputs/handoffs/etc when fallback_max_turns was set).
        // Verify the toBuilder mechanism preserves every set field except the override.
        AgentConfig original =
                AgentConfig.builder()
                        .name("fallback")
                        .model("openai/gpt-4o")
                        .instructions("Original instructions")
                        .maxTurns(20)
                        .maxTokens(8192)
                        .temperature(0.7)
                        .credentials(List.of("CRED_A", "CRED_B"))
                        .build();

        AgentConfig rebuilt = original.toBuilder().maxTurns(7).build();

        assertThat(rebuilt.getMaxTurns()).as("override should apply").isEqualTo(7);
        assertThat(rebuilt.getName()).isEqualTo("fallback");
        assertThat(rebuilt.getModel()).isEqualTo("openai/gpt-4o");
        assertThat(rebuilt.getInstructions()).isEqualTo("Original instructions");
        assertThat(rebuilt.getMaxTokens()).isEqualTo(8192);
        assertThat(rebuilt.getTemperature()).isEqualTo(0.7);
        assertThat(rebuilt.getCredentials()).containsExactly("CRED_A", "CRED_B");
    }

    @Test
    void testPlanExecuteSubWorkflowIsOptional() {
        // optional:true is REQUIRED on the SUB_WORKFLOW. Without it, a
        // non-COMPLETED dynamic plan (guardrail trip TERMINATE, step
        // failure, etc.) halts the entire parent workflow before
        // ``statusCheck`` / ``statusSwitch`` can read the status and
        // route to the fallback agent. The earlier inversion of this
        // invariant ("must NOT be optional") was based on a misreading
        // of Conductor semantics — non-optional task failures propagate
        // up regardless of any downstream SWITCH, so there's no way to
        // catch them without optional:true.
        AgentConfig planner = simpleSubAgent("planner", "Plan");
        AgentConfig fb = simpleSubAgent("fallback", "Fix");
        AgentConfig harness =
                AgentConfig.builder()
                        .name("optional_plan_exec")
                        .model("openai/gpt-4o-mini")
                        .strategy("plan_execute")
                        .planner(planner)
                        .fallback(fb)
                        .build();

        WorkflowDef wf = new MultiAgentCompiler(compiler).compile(harness);
        WorkflowTask routeSwitch =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SWITCH".equals(t.getType())
                                                && t.getTaskReferenceName().contains("plan_route"))
                        .findFirst()
                        .orElseThrow();
        List<WorkflowTask> hasPlanBranch = routeSwitch.getDecisionCases().get("has_plan");
        WorkflowTask exec =
                compileSuccessTasks(hasPlanBranch).stream()
                        .filter(t -> "SUB_WORKFLOW".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(exec.isOptional())
                .as(
                        "plan SUB_WORKFLOW must be optional so the status SWITCH can route failures to fallback")
                .isTrue();
    }

    @Test
    void testSequentialWithMultipleGates() {
        // Two gates: stage 0 and stage 1 both have gates, stage 2 has none
        AgentConfig config =
                AgentConfig.builder()
                        .name("pipeline")
                        .model("openai/gpt-4o")
                        .strategy("sequential")
                        .agents(
                                List.of(
                                        AgentConfig.builder()
                                                .name("a")
                                                .model("openai/gpt-4o")
                                                .instructions("A")
                                                .gate(
                                                        Map.of(
                                                                "type",
                                                                "text_contains",
                                                                "text",
                                                                "STOP_A",
                                                                "caseSensitive",
                                                                true))
                                                .build(),
                                        AgentConfig.builder()
                                                .name("b")
                                                .model("openai/gpt-4o")
                                                .instructions("B")
                                                .gate(
                                                        Map.of(
                                                                "type",
                                                                "text_contains",
                                                                "text",
                                                                "STOP_B",
                                                                "caseSensitive",
                                                                true))
                                                .build(),
                                        simpleSubAgent("c", "C")))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Top level: ctx_init_resolve + ctx_init + SUB_WORKFLOW(a) + ctx_merge_0 + ctx_set_0 +
        // coerce + gate_0 +
        // SWITCH_0 + output_selector
        assertThat(wf.getTasks()).hasSize(9);
        assertThat(wf.getTasks().get(7).getType()).isEqualTo("SWITCH");
        assertThat(wf.getTasks().get(8).getType()).isEqualTo("INLINE"); // output_selector

        // Inside SWITCH_0's "continue" case: SUB_WORKFLOW(b) + coerce + gate_1 + SWITCH_1
        List<WorkflowTask> continueCase0 = wf.getTasks().get(7).getDecisionCases().get("continue");
        assertThat(continueCase0).hasSize(4);
        assertThat(continueCase0.get(2).getTaskReferenceName()).isEqualTo("pipeline_gate_1");
        assertThat(continueCase0.get(3).getType()).isEqualTo("SWITCH");

        // Inside SWITCH_1's "continue" case: SUB_WORKFLOW(c)
        List<WorkflowTask> continueCase1 = continueCase0.get(3).getDecisionCases().get("continue");
        assertThat(continueCase1).hasSize(1);
        assertThat(continueCase1.get(0).getTaskReferenceName()).contains("c");
    }
}
