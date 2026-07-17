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

import org.conductoross.conductor.common.metadata.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowClassifier;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.assertj.core.api.Assertions.*;

class AgentCompilerTest {

    private AgentCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new AgentCompiler();
    }

    @Test
    void testCompileSimple() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test_agent")
                        .model("openai/gpt-4o")
                        .instructions("You are helpful.")
                        .build();

        WorkflowDef wf = compiler.compile(config);

        assertThat(wf.getName()).isEqualTo("test_agent");
        assertThat(wf.getVersion()).isEqualTo(1);
        assertThat(wf.getMetadata())
                .containsEntry("classifier", WorkflowClassifier.AGENT)
                .containsKey("agentDef");
        assertThat(wf.getTasks()).hasSize(1);

        WorkflowTask llmTask = wf.getTasks().get(0);
        assertThat(llmTask.getType()).isEqualTo("LLM_CHAT_COMPLETE");
        assertThat(llmTask.getTaskReferenceName()).isEqualTo("test_agent_llm");
        assertThat(llmTask.getInputParameters().get("llmProvider")).isEqualTo("openai");
        assertThat(llmTask.getInputParameters().get("model")).isEqualTo("gpt-4o");
    }

    @Test
    void testCompileWithTools() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search the web")
                        .inputSchema(
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of("query", Map.of("type", "string"))))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("tool_agent")
                        .model("openai/gpt-4o")
                        .instructions("You can search the web.")
                        .tools(List.of(tool))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        assertThat(wf.getName()).isEqualTo("tool_agent");
        // Should have INLINE (ctx_resolve) + SET_VARIABLE (init state) + DoWhile loop
        // + INLINE (synth_output, post-loop output synthesizer)
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE");
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        WorkflowTask loop = wf.getTasks().get(2);
        assertThat(loop.getType()).isEqualTo("DO_WHILE");
        assertThat(loop.getTaskReferenceName()).isEqualTo("tool_agent_loop");
        WorkflowTask synth = wf.getTasks().get(3);
        assertThat(synth.getType()).isEqualTo("INLINE");
        assertThat(synth.getTaskReferenceName()).isEqualTo("tool_agent_synth_output");

        // Loop should contain ctx_inject + LLM + tool_router at minimum
        assertThat(loop.getLoopOver().size()).isGreaterThanOrEqualTo(3);
        assertThat(loop.getLoopOver().get(0).getType()).isEqualTo("INLINE"); // ctx_inject
        assertThat(loop.getLoopOver().get(1).getType()).isEqualTo("LLM_CHAT_COMPLETE");
    }

    @Test
    void testCompileSimpleWithGuardrails() {
        GuardrailConfig guardrail =
                GuardrailConfig.builder()
                        .name("no_pii")
                        .guardrailType("regex")
                        .position("output")
                        .onFail("retry")
                        .patterns(List.of("\\d{3}-\\d{2}-\\d{4}"))
                        .mode("block")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("guarded_agent")
                        .model("openai/gpt-4o")
                        .instructions("Be helpful.")
                        .guardrails(List.of(guardrail))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Should wrap in DoWhile + resolve_output
        assertThat(wf.getTasks()).hasSize(2);
        WorkflowTask loop = wf.getTasks().get(0);
        assertThat(loop.getType()).isEqualTo("DO_WHILE");
        WorkflowTask resolve = wf.getTasks().get(1);
        assertThat(resolve.getType()).isEqualTo("INLINE");
        assertThat(resolve.getTaskReferenceName()).isEqualTo("guarded_agent_resolve_output");

        // Loop should have LLM + guardrail + guardrail_route
        assertThat(loop.getLoopOver().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void testCompileWithTermination() {
        TerminationConfig term =
                TerminationConfig.builder()
                        .type("text_mention")
                        .text("DONE")
                        .caseSensitive(false)
                        .build();

        ToolConfig tool =
                ToolConfig.builder()
                        .name("calc")
                        .description("Calculator")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("term_agent")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .termination(term)
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + init_state + loop
        WorkflowTask loop = wf.getTasks().get(2);
        assertThat(loop.getType()).isEqualTo("DO_WHILE");

        // Loop condition should include termination check
        String loopCondition = loop.getLoopCondition();
        assertThat(loopCondition).contains("term_agent_termination.should_continue");

        // Regression: the termination clause must NOT be wrapped in
        // ``(finishReason == 'TOOL_CALLS' || …)``. That OR short-circuited
        // count-based terminations (MaxMessage, TokenUsage) on every tool-call
        // turn, so the loop ran to maxTurns instead of stopping at the
        // configured limit. Text-based terminations already return
        // should_continue=true when the LLM result is empty (tool-call turns),
        // so no special-case is needed in the loop condition.
        assertThat(loopCondition)
                .as(
                        "termination clause must not OR with finishReason==TOOL_CALLS — "
                                + "that breaks count-based terminations (MaxMessage)")
                .doesNotContain("'TOOL_CALLS' || $.term_agent_termination")
                .doesNotContain("TOOL_CALLS\" || $.term_agent_termination");
    }

    @Test
    void testCompileWithStopWhen() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("stop_agent")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .stopWhen(WorkerRef.builder().taskName("stop_agent_stop_when").build())
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + init_state + loop
        WorkflowTask loop = wf.getTasks().get(2);
        String loopCondition = loop.getLoopCondition();
        assertThat(loopCondition).contains("stop_agent_stop_when.should_continue");
    }

    @Test
    void testStopWhenFiresEvenOnToolCallTurns() {
        // stop_when must NOT be bypassed when finishReason == TOOL_CALLS.
        // The loop condition for stop_when must be unconditional:
        //   && $.stop_ref.should_continue == true
        // NOT:
        //   && ($.llmRef['finishReason'] == 'TOOL_CALLS' || $.stop_ref.should_continue == true)
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("stop_test")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .stopWhen(WorkerRef.builder().taskName("stop_test_stop_when").build())
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask loop = wf.getTasks().get(2);
        String cond = loop.getLoopCondition();

        // Must contain the stop_when check
        assertThat(cond).contains("stop_test_stop_when.should_continue == true");

        // Must NOT contain the TOOL_CALLS bypass for stop_when
        // (i.e., no "finishReason == 'TOOL_CALLS' || ...stop_when.should_continue")
        assertThat(cond)
                .as("stop_when must fire on tool-call turns — no TOOL_CALLS bypass")
                .doesNotContain("'TOOL_CALLS' || $.stop_test_stop_when.should_continue");
    }

    @Test
    void testTerminationStillBypassedOnToolCallTurns() {
        // Despite the (now-removed) TOOL_CALLS bypass: text-based terminations
        // (text_mention/stop_message) already return should_continue=true when
        // the LLM result is empty (tool-call turns), so they never trigger on
        // tool-call turns by themselves. Count-based terminations (MaxMessage)
        // MUST fire on tool-call turns to honor the configured cap. The loop
        // condition therefore evaluates termination.should_continue
        // unconditionally — no TOOL_CALLS short-circuit.
        ToolConfig tool =
                ToolConfig.builder()
                        .name("calc")
                        .description("Calculator")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        TerminationConfig term =
                TerminationConfig.builder()
                        .type("text_mention")
                        .text("DONE")
                        .caseSensitive(false)
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("term_test")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .termination(term)
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask loop = wf.getTasks().get(2);
        String cond = loop.getLoopCondition();

        // Termination is checked unconditionally — no TOOL_CALLS short-circuit.
        // Text-based terminations naturally pass through on tool-call turns
        // (empty result → no text match → should_continue=true), so the OR
        // clause that used to live here was both unnecessary for text-based
        // and broken for count-based (MaxMessage) terminations.
        assertThat(cond).contains("$.term_test_termination.should_continue == true");
        assertThat(cond).doesNotContain("'TOOL_CALLS' || $.term_test_termination");
    }

    @Test
    void testStopWhenAndTerminationBothEvaluatedUnconditionally() {
        // Both stop_when and termination are evaluated unconditionally on every
        // turn — the previous behavior, which OR-ed termination with
        // ``finishReason == 'TOOL_CALLS'``, broke count-based terminations.
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("both_agent")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .stopWhen(WorkerRef.builder().taskName("both_agent_stop_when").build())
                        .termination(
                                TerminationConfig.builder()
                                        .type("text_mention")
                                        .text("FINISHED")
                                        .build())
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask loop = wf.getTasks().get(2);
        String cond = loop.getLoopCondition();

        // stop_when: no TOOL_CALLS bypass
        assertThat(cond).contains("both_agent_stop_when.should_continue == true");
        assertThat(cond).doesNotContain("'TOOL_CALLS' || $.both_agent_stop_when.should_continue");

        // termination: also no TOOL_CALLS bypass (regression: this was wrapped
        // in ``finishReason == 'TOOL_CALLS' || …`` which broke MaxMessage).
        assertThat(cond).contains("$.both_agent_termination.should_continue == true");
        assertThat(cond).doesNotContain("'TOOL_CALLS' || $.both_agent_termination");
    }

    @Test
    void testCompileHybrid() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search the web")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig subAgent =
                AgentConfig.builder()
                        .name("summarizer")
                        .model("openai/gpt-4o")
                        .instructions("Summarize the conversation.")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("hybrid_agent")
                        .model("openai/gpt-4o")
                        .instructions("You are a research assistant.")
                        .tools(List.of(tool))
                        .agents(List.of(subAgent))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Should have ctx_resolve + init_state + DoWhile + transfer switch
        assertThat(wf.getTasks().size()).isGreaterThanOrEqualTo(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");
        assertThat(wf.getTasks().get(3).getType()).isEqualTo("SWITCH");
    }

    @Test
    void testExternalAgentCannotBeCompiled() {
        AgentConfig config = AgentConfig.builder().name("external_agent").external(true).build();

        assertThatThrownBy(() -> compiler.compile(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot compile external agent");
    }

    @Test
    void testSubAgentResultRef() {
        // All sub-workflow tasks use .output.result (Conductor SUB_WORKFLOW
        // exposes child workflow's outputParameters directly)

        // Agent with tools -> .output.result
        AgentConfig withTools =
                AgentConfig.builder()
                        .name("agent1")
                        .model("openai/gpt-4o")
                        .tools(List.of(ToolConfig.builder().name("t").toolType("worker").build()))
                        .build();
        assertThat(AgentCompiler.subAgentResultRef(withTools, "ref1"))
                .isEqualTo("${ref1.output.result}");

        // Simple agent -> .output.result
        AgentConfig simple = AgentConfig.builder().name("agent2").model("openai/gpt-4o").build();
        assertThat(AgentCompiler.subAgentResultRef(simple, "ref2"))
                .isEqualTo("${ref2.output.result}");

        // External agent -> .output.result
        AgentConfig external = AgentConfig.builder().name("agent3").external(true).build();
        assertThat(AgentCompiler.subAgentResultRef(external, "ref3"))
                .isEqualTo("${ref3.output.result}");
    }

    @Test
    void testCompileWithPromptTemplate() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("template_agent")
                        .model("openai/gpt-4o")
                        .instructions(
                                Map.of(
                                        "type",
                                        "prompt_template",
                                        "name",
                                        "my_template",
                                        "variables",
                                        Map.of("role", "assistant"),
                                        "version",
                                        1))
                        .build();

        WorkflowDef wf = compiler.compile(config);
        WorkflowTask llm = wf.getTasks().get(0);
        assertThat(llm.getInputParameters().get("instructionsTemplate")).isEqualTo("my_template");
    }

    @Test
    void testCompileWithDynamicInstructions() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("dynamic_agent")
                        .model("openai/gpt-4o")
                        .instructions(
                                Map.of(
                                        "_worker_ref", "get_dynamic_instructions",
                                        "description", "Generate dynamic instructions"))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        assertThat(wf.getTasks()).hasSize(3);

        WorkflowTask workerTask = wf.getTasks().get(0);
        assertThat(workerTask.getType()).isEqualTo("SIMPLE");
        assertThat(workerTask.getName()).isEqualTo("get_dynamic_instructions");
        assertThat(workerTask.getTaskReferenceName())
                .isEqualTo("dynamic_agent_instructions_worker");

        WorkflowTask normalizeTask = wf.getTasks().get(1);
        assertThat(normalizeTask.getType()).isEqualTo("INLINE");
        assertThat(normalizeTask.getTaskReferenceName()).isEqualTo("dynamic_agent_instructions");

        WorkflowTask llmTask = wf.getTasks().get(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("messages");
        String systemMsg =
                messages.stream()
                        .filter(m -> "system".equals(m.get("role")))
                        .map(m -> (String) m.get("message"))
                        .findFirst()
                        .orElse("");
        assertThat(systemMsg).contains("${dynamic_agent_instructions.output.result}");
    }

    @Test
    void testCompileWithOutputType() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("structured_agent")
                        .model("openai/gpt-4o")
                        .instructions("Return structured data.")
                        .outputType(
                                OutputTypeConfig.builder()
                                        .schema(
                                                Map.of(
                                                        "properties",
                                                        Map.of("name", Map.of("type", "string"))))
                                        .className("Person")
                                        .build())
                        .build();

        WorkflowDef wf = compiler.compile(config);
        WorkflowTask llm = wf.getTasks().get(0);
        assertThat(llm.getInputParameters().get("jsonOutput")).isEqualTo(true);
    }

    @Test
    void testCompileWithCallbacks() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("callback_agent")
                        .model("openai/gpt-4o")
                        .instructions("You are helpful.")
                        .tools(List.of(tool))
                        .callbacks(
                                List.of(
                                        CallbackConfig.builder()
                                                .position("before_model")
                                                .taskName("log_before")
                                                .build(),
                                        CallbackConfig.builder()
                                                .position("after_model")
                                                .taskName("inspect_after")
                                                .build(),
                                        CallbackConfig.builder()
                                                .position("before_agent")
                                                .taskName("agent_start")
                                                .build(),
                                        CallbackConfig.builder()
                                                .position("after_agent")
                                                .taskName("agent_end")
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // before_agent + ctx_resolve + init_state + DoWhile + synth_output + after_agent
        assertThat(wf.getTasks()).hasSize(6);

        // First task: before_agent callback (SIMPLE worker)
        WorkflowTask beforeAgent = wf.getTasks().get(0);
        assertThat(beforeAgent.getType()).isEqualTo("SIMPLE");
        assertThat(beforeAgent.getName()).isEqualTo("agent_start");
        assertThat(beforeAgent.getTaskReferenceName()).isEqualTo("callback_agent_before_agent");
        assertThat(beforeAgent.getInputParameters().get("callback_position"))
                .isEqualTo("before_agent");

        // Second task: ctx_resolve INLINE
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("INLINE");

        // Third task: init_state SET_VARIABLE
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("SET_VARIABLE");

        // Fourth task: DoWhile loop
        WorkflowTask loop = wf.getTasks().get(3);
        assertThat(loop.getType()).isEqualTo("DO_WHILE");

        // Inside loop: ctx_inject + before_model + LLM + after_model + guardrails + tool_router +
        // ...
        List<WorkflowTask> loopTasks = loop.getLoopOver();
        // First in loop should be ctx_inject INLINE
        assertThat(loopTasks.get(0).getType()).isEqualTo("INLINE"); // ctx_inject
        // Second in loop should be before_model callback
        assertThat(loopTasks.get(1).getType()).isEqualTo("SIMPLE");
        assertThat(loopTasks.get(1).getName()).isEqualTo("log_before");
        // Third should be LLM
        assertThat(loopTasks.get(2).getType()).isEqualTo("LLM_CHAT_COMPLETE");
        // Fourth should be after_model callback
        assertThat(loopTasks.get(3).getType()).isEqualTo("SIMPLE");
        assertThat(loopTasks.get(3).getName()).isEqualTo("inspect_after");
        // after_model should have llm_result input wired
        assertThat(loopTasks.get(3).getInputParameters().get("llm_result")).isNotNull();

        // Last task: after_agent callback
        WorkflowTask afterAgent = wf.getTasks().get(4);
        assertThat(afterAgent.getType()).isEqualTo("SIMPLE");
        assertThat(afterAgent.getName()).isEqualTo("agent_end");
    }

    @Test
    void testCompileWithRequiredTools() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("submit_filing")
                        .description("Submit a filing")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("filing_agent")
                        .model("openai/gpt-4o")
                        .instructions("You must submit a filing.")
                        .tools(List.of(tool))
                        .requiredTools(List.of("submit_filing"))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Should have: ctx_resolve + init_state + outer DO_WHILE + synth_output
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(3).getType()).isEqualTo("INLINE"); // synth_output

        WorkflowTask outerLoop = wf.getTasks().get(2);
        assertThat(outerLoop.getType()).isEqualTo("DO_WHILE");
        assertThat(outerLoop.getTaskReferenceName()).isEqualTo("filing_agent_required_tools_loop");

        // Outer loop should contain: inner loop + INLINE check
        assertThat(outerLoop.getLoopOver()).hasSize(2);
        assertThat(outerLoop.getLoopOver().get(0).getType()).isEqualTo("DO_WHILE");
        assertThat(outerLoop.getLoopOver().get(0).getTaskReferenceName())
                .isEqualTo("filing_agent_loop");
        assertThat(outerLoop.getLoopOver().get(1).getType()).isEqualTo("INLINE");
        assertThat(outerLoop.getLoopOver().get(1).getTaskReferenceName())
                .isEqualTo("filing_agent_required_tools_check");
    }

    @Test
    void testCompileWithoutRequiredToolsHasNoOuterLoop() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("normal_agent")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + init_state + inner loop + synth_output (no outer loop)
        assertThat(wf.getTasks()).hasSize(4);
        WorkflowTask loop = wf.getTasks().get(2);
        assertThat(loop.getType()).isEqualTo("DO_WHILE");
        assertThat(loop.getTaskReferenceName()).isEqualTo("normal_agent_loop");
        assertThat(wf.getTasks().get(3).getType()).isEqualTo("INLINE");
    }

    @Test
    void testCompileWithAgentTool() {
        // Agent tool: a tool that wraps another agent
        ToolConfig agentTool =
                ToolConfig.builder()
                        .name("researcher")
                        .description("Research agent")
                        .toolType("agent_tool")
                        .inputSchema(
                                Map.of(
                                        "type", "object",
                                        "properties", Map.of("request", Map.of("type", "string")),
                                        "required", List.of("request")))
                        .config(Map.of("workflowName", "researcher_agent_wf"))
                        .build();

        ToolConfig regularTool =
                ToolConfig.builder()
                        .name("calculator")
                        .description("Math calculator")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("manager")
                        .model("openai/gpt-4o")
                        .instructions("You manage a research team.")
                        .tools(List.of(agentTool, regularTool))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + init_state + DoWhile loop + synth_output
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        WorkflowTask loop = wf.getTasks().get(2);
        assertThat(loop.getType()).isEqualTo("DO_WHILE");
        assertThat(wf.getTasks().get(3).getType()).isEqualTo("INLINE"); // synth_output

        // LLM task should have both tools in its tool specs (after ctx_inject at index 0)
        WorkflowTask llmTask = loop.getLoopOver().get(1);
        assertThat(llmTask.getType()).isEqualTo("LLM_CHAT_COMPLETE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("tools");
        assertThat(tools).hasSize(2);
        assertThat(tools.stream().map(t -> t.get("name")).toList())
                .containsExactlyInAnyOrder("researcher", "calculator");
        // agent_tool should have SUB_WORKFLOW type in spec
        Map<String, Object> agentToolSpec =
                tools.stream()
                        .filter(t -> "researcher".equals(t.get("name")))
                        .findFirst()
                        .orElseThrow();
        assertThat(agentToolSpec.get("type")).isEqualTo("SUB_WORKFLOW");
    }

    @Test
    void testCompileFrameworkPassthrough() {
        // Build a passthrough AgentConfig as produced by LangGraphNormalizer
        ToolConfig worker = ToolConfig.builder().name("my_graph").toolType("worker").build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("my_graph")
                        .metadata(Map.of("_framework_passthrough", true))
                        .tools(List.of(worker))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        assertThat(wf.getName()).isEqualTo("my_graph");
        assertThat(wf.getTasks()).hasSize(1);
        WorkflowTask task = wf.getTasks().get(0);
        assertThat(task.getType()).isEqualTo("SIMPLE");
        assertThat(task.getName()).isEqualTo("my_graph");
        assertThat(task.getTaskReferenceName()).isEqualTo("_fw_task");
        // prompt/session_id/media must be wired from workflow input
        assertThat(task.getInputParameters().get("prompt")).isEqualTo("${workflow.input.prompt}");
        assertThat(task.getInputParameters().get("session_id"))
                .isEqualTo("${workflow.input.session_id}");
        // Output must reference the _fw_task
        assertThat(wf.getOutputParameters().get("result")).isEqualTo("${_fw_task.output.result}");
    }

    @Test
    void testPassthroughGuardPreventsCrashOnNullModel() {
        // Passthrough configs have no model — this must NOT throw.
        // Without the passthrough guard, compile() falls through to compileSimple()
        // which calls ModelParser.parse(null) and throws NullPointerException.
        AgentConfig config =
                AgentConfig.builder()
                        .name("my_graph")
                        .metadata(Map.of("_framework_passthrough", true))
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("my_graph")
                                                .toolType("worker")
                                                .build()))
                        .build();

        assertThatNoException().isThrownBy(() -> compiler.compile(config));
    }

    // ── Graph-structure compilation tests ──────────────────────────────

    @Test
    void testCompileGraphStructureSequential() {
        // Sequential graph: __start__ → fetch → process → __end__
        Map<String, Object> graphStructure =
                Map.of(
                        "nodes",
                                List.of(
                                        Map.of("name", "fetch", "_worker_ref", "seq_graph_fetch"),
                                        Map.of(
                                                "name",
                                                "process",
                                                "_worker_ref",
                                                "seq_graph_process")),
                        "edges",
                                List.of(
                                        Map.of("source", "__start__", "target", "fetch"),
                                        Map.of("source", "fetch", "target", "process"),
                                        Map.of("source", "process", "target", "__end__")),
                        "input_key", "query");

        AgentConfig config =
                AgentConfig.builder()
                        .name("seq_graph")
                        .metadata(Map.of("_graph_structure", graphStructure))
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("seq_graph_fetch")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("seq_graph_process")
                                                .toolType("worker")
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        assertThat(wf.getName()).isEqualTo("seq_graph");
        assertThat(wf.getTasks()).hasSize(2);

        WorkflowTask first = wf.getTasks().get(0);
        assertThat(first.getType()).isEqualTo("SIMPLE");
        assertThat(first.getName()).isEqualTo("seq_graph_fetch");

        WorkflowTask second = wf.getTasks().get(1);
        assertThat(second.getType()).isEqualTo("SIMPLE");
        assertThat(second.getName()).isEqualTo("seq_graph_process");

        // Second task's state should reference first task's output
        assertThat(second.getInputParameters().get("state").toString()).contains("seq_graph_fetch");
    }

    @Test
    void testCompileGraphStructureConditionalSwitch() {
        // Graph with conditional routing: __start__ → classify → SWITCH(positive | negative)
        Map<String, Object> graphStructure =
                Map.of(
                        "nodes",
                                List.of(
                                        Map.of("name", "classify", "_worker_ref", "test_classify"),
                                        Map.of("name", "positive", "_worker_ref", "test_positive"),
                                        Map.of("name", "negative", "_worker_ref", "test_negative")),
                        "edges", List.of(Map.of("source", "__start__", "target", "classify")),
                        "conditional_edges",
                                List.of(
                                        Map.of(
                                                "source", "classify",
                                                "_router_ref", "test_classify_router",
                                                "targets",
                                                        Map.of(
                                                                "positive",
                                                                "positive",
                                                                "negative",
                                                                "negative"))));

        AgentConfig config =
                AgentConfig.builder()
                        .name("cond_graph")
                        .model("openai/gpt-4o")
                        .metadata(Map.of("_graph_structure", graphStructure))
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("test_classify")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("test_positive")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("test_negative")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("test_classify_router")
                                                .toolType("worker")
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Should contain at least: classify SIMPLE, router SIMPLE, SWITCH
        List<String> taskTypes = wf.getTasks().stream().map(WorkflowTask::getType).toList();
        assertThat(taskTypes).contains("SIMPLE", "SWITCH");

        // Find the router task
        WorkflowTask routerTask =
                wf.getTasks().stream()
                        .filter(
                                t ->
                                        "SIMPLE".equals(t.getType())
                                                && "test_classify_router".equals(t.getName()))
                        .findFirst()
                        .orElse(null);
        assertThat(routerTask).isNotNull();

        // Find the SWITCH task
        WorkflowTask switchTask =
                wf.getTasks().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(switchTask.getDecisionCases()).containsKey("positive");
        assertThat(switchTask.getDecisionCases()).containsKey("negative");
    }

    @Test
    void testCompileGraphStructureForkJoin() {
        // Fan-out from START: __start__ → pros, __start__ → cons, pros → merge, cons → merge, merge
        // → __end__
        Map<String, Object> graphStructure = new java.util.LinkedHashMap<>();
        graphStructure.put(
                "nodes",
                List.of(
                        Map.of("name", "pros", "_worker_ref", "test_pros"),
                        Map.of("name", "cons", "_worker_ref", "test_cons"),
                        Map.of("name", "merge", "_worker_ref", "test_merge")));
        graphStructure.put(
                "edges",
                List.of(
                        Map.of("source", "__start__", "target", "pros"),
                        Map.of("source", "__start__", "target", "cons"),
                        Map.of("source", "pros", "target", "merge"),
                        Map.of("source", "cons", "target", "merge"),
                        Map.of("source", "merge", "target", "__end__")));
        graphStructure.put("_reducers", Map.of("arguments", "add"));

        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("_graph_structure", graphStructure);
        metadata.put("_reducers", Map.of("arguments", "add"));

        AgentConfig config =
                AgentConfig.builder()
                        .name("fork_graph")
                        .metadata(metadata)
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("test_pros")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("test_cons")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("test_merge")
                                                .toolType("worker")
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        List<String> taskTypes = wf.getTasks().stream().map(WorkflowTask::getType).toList();

        // Should include FORK_JOIN and JOIN tasks
        assertThat(taskTypes).contains("FORK_JOIN");
        assertThat(taskTypes).contains("JOIN");

        // Should include an INLINE merge task
        WorkflowTask inlineMerge =
                wf.getTasks().stream()
                        .filter(t -> "INLINE".equals(t.getType()))
                        .findFirst()
                        .orElse(null);
        assertThat(inlineMerge).isNotNull();
    }

    @Test
    void testCompileGraphStructureLlmNode() {
        // Graph with LLM node: __start__ → prepare → analyze(LLM) → __end__
        Map<String, Object> graphStructure =
                Map.of(
                        "nodes",
                                List.of(
                                        Map.of("name", "prepare", "_worker_ref", "test_prepare"),
                                        Map.of(
                                                "name",
                                                "analyze",
                                                "_worker_ref",
                                                "test_analyze_prep",
                                                "_llm_node",
                                                true,
                                                "_llm_prep_ref",
                                                "test_analyze_prep",
                                                "_llm_finish_ref",
                                                "test_analyze_finish")),
                        "edges",
                                List.of(
                                        Map.of("source", "__start__", "target", "prepare"),
                                        Map.of("source", "prepare", "target", "analyze"),
                                        Map.of("source", "analyze", "target", "__end__")));

        AgentConfig config =
                AgentConfig.builder()
                        .name("llm_graph")
                        .model("openai/gpt-4o")
                        .metadata(Map.of("_graph_structure", graphStructure))
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("test_prepare")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("test_analyze_prep")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("test_analyze_finish")
                                                .toolType("worker")
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        List<String> taskTypes = wf.getTasks().stream().map(WorkflowTask::getType).toList();

        // Should contain a SWITCH task (for _skip_llm)
        assertThat(taskTypes).contains("SWITCH");

        // Find the SWITCH task for the LLM node
        WorkflowTask switchTask =
                wf.getTasks().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();

        // Default case should contain LLM_CHAT_COMPLETE task
        assertThat(switchTask.getDefaultCase()).isNotEmpty();
        boolean hasLlmTask =
                switchTask.getDefaultCase().stream()
                        .anyMatch(t -> "LLM_CHAT_COMPLETE".equals(t.getType()));
        assertThat(hasLlmTask).isTrue();

        // Should have an INLINE coalesce task
        WorkflowTask coalesceTask =
                wf.getTasks().stream()
                        .filter(t -> "INLINE".equals(t.getType()))
                        .findFirst()
                        .orElse(null);
        assertThat(coalesceTask).isNotNull();
    }

    @Test
    void testCompileGraphStructureSubgraphNode() {
        // Build a simple sub-agent config (passthrough)
        AgentConfig subAgent =
                AgentConfig.builder()
                        .name("sub_graph")
                        .metadata(Map.of("_framework_passthrough", true))
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("sub_graph")
                                                .toolType("worker")
                                                .build()))
                        .build();

        // Graph with subgraph node: __start__ → sub → __end__
        Map<String, Object> graphStructure =
                Map.of(
                        "nodes",
                                List.of(
                                        Map.of(
                                                "name",
                                                "sub",
                                                "_worker_ref",
                                                "test_sub_prep",
                                                "_subgraph_node",
                                                true,
                                                "_subgraph_prep_ref",
                                                "test_sub_prep",
                                                "_subgraph_finish_ref",
                                                "test_sub_finish")),
                        "edges",
                                List.of(
                                        Map.of("source", "__start__", "target", "sub"),
                                        Map.of("source", "sub", "target", "__end__")));

        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("_graph_structure", graphStructure);
        metadata.put("_subgraph_configs", Map.of("sub", subAgent));

        AgentConfig config =
                AgentConfig.builder()
                        .name("sg_graph")
                        .metadata(metadata)
                        .tools(
                                List.of(
                                        ToolConfig.builder()
                                                .name("test_sub_prep")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("test_sub_finish")
                                                .toolType("worker")
                                                .build(),
                                        ToolConfig.builder()
                                                .name("sub_graph")
                                                .toolType("worker")
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        List<String> taskTypes = wf.getTasks().stream().map(WorkflowTask::getType).toList();

        // Should contain a SWITCH task (for _skip_subgraph)
        assertThat(taskTypes).contains("SWITCH");

        // Find the SWITCH task
        WorkflowTask switchTask =
                wf.getTasks().stream()
                        .filter(t -> "SWITCH".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();

        // Default case should contain a SUB_WORKFLOW task
        assertThat(switchTask.getDefaultCase()).isNotEmpty();
        boolean hasSubWf =
                switchTask.getDefaultCase().stream()
                        .anyMatch(t -> "SUB_WORKFLOW".equals(t.getType()));
        assertThat(hasSubWf).isTrue();

        // Should have an INLINE coalesce task
        WorkflowTask coalesceTask =
                wf.getTasks().stream()
                        .filter(t -> "INLINE".equals(t.getType()))
                        .findFirst()
                        .orElse(null);
        assertThat(coalesceTask).isNotNull();
    }

    @Test
    void testApplyRetryPolicyMapsAllParams() {
        WorkflowTask task = new WorkflowTask();
        Map<String, Object> policy =
                Map.of(
                        "max_attempts", 5,
                        "initial_interval", 2.5,
                        "backoff_factor", 3);

        AgentCompiler.applyRetryPolicy(task, policy);

        // max_attempts 5 → retryCount = 5 - 1 = 4
        assertThat(task.getRetryCount()).isEqualTo(4);
        // initial_interval and backoff_factor are stored in _retry_meta (TaskDef-level properties)
        @SuppressWarnings("unchecked")
        Map<String, Object> retryMeta =
                (Map<String, Object>) task.getInputParameters().get("_retry_meta");
        assertThat(retryMeta).isNotNull();
        assertThat(retryMeta.get("retryDelaySeconds")).isEqualTo(3);
        assertThat(retryMeta.get("backoffScaleFactor")).isEqualTo(3);
    }

    // ── Hyphenated-name regression (GitHub issue: JS identifier with hyphens) ──

    @Test
    void toRef_replacesHyphensWithUnderscores() {
        assertThat(AgentCompiler.toRef("prepare-information")).isEqualTo("prepare_information");
        assertThat(AgentCompiler.toRef("my-agent-name")).isEqualTo("my_agent_name");
        assertThat(AgentCompiler.toRef("plain_name")).isEqualTo("plain_name");
        assertThat(AgentCompiler.toRef("has spaces")).isEqualTo("has_spaces");
        assertThat(AgentCompiler.toRef("dot.separated")).isEqualTo("dot_separated");
    }

    @Test
    void hyphenatedAgentName_loopConditionUsesValidJsIdentifier() {
        // Regression: $.prepare-information_loop[...] is invalid JS — the hyphen is
        // parsed as subtraction. The compiler must sanitize the name.
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search the web")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("prepare-information")
                        .model("openai/gpt-4o")
                        .instructions("Prepare information.")
                        .tools(List.of(tool))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // The loop task ref must not contain a hyphen
        WorkflowTask loop = wf.getTasks().get(2);
        assertThat(loop.getType()).isEqualTo("DO_WHILE");
        assertThat(loop.getTaskReferenceName()).isEqualTo("prepare_information_loop");
        assertThat(loop.getTaskReferenceName()).doesNotContain("-");

        // The loop condition must reference the sanitized name
        String condition = loop.getLoopCondition();
        assertThat(condition).contains("prepare_information_loop");
        assertThat(condition).doesNotContain("prepare-information");

        // All task reference names inside the loop must be valid JS identifiers
        for (WorkflowTask task : loop.getLoopOver()) {
            assertThat(task.getTaskReferenceName())
                    .as("task ref '%s' must not contain hyphens", task.getTaskReferenceName())
                    .doesNotContain("-");
        }
    }

    @Test
    void hyphenatedAgentName_allTopLevelRefsAreSanitized() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("my-agent")
                        .model("openai/gpt-4o")
                        .instructions("I am helpful.")
                        .build();

        WorkflowDef wf = compiler.compile(config);

        for (WorkflowTask task : wf.getTasks()) {
            assertThat(task.getTaskReferenceName())
                    .as("top-level task ref must not contain hyphens")
                    .doesNotContain("-");
        }
    }

    // ── Prefill tools tests ─────────────────────────────────────────

    @Test
    void testCompileWithSinglePrefillTool() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("contextbook_read")
                        .description("Read contextbook")
                        .inputSchema(
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of("section", Map.of("type", "string"))))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("prefill_agent")
                        .model("openai/gpt-4o")
                        .instructions("You implement code.")
                        .tools(List.of(tool))
                        .prefillTools(
                                List.of(
                                        PrefillToolCallConfig.builder()
                                                .toolName("contextbook_read")
                                                .arguments(Map.of("section", "coder_plan"))
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + init_state + prefill SIMPLE + DoWhile + synth_output
        assertThat(wf.getTasks()).hasSize(5);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE"); // init_state
        WorkflowTask prefillTask = wf.getTasks().get(2);
        assertThat(prefillTask.getType()).isEqualTo("SIMPLE");
        assertThat(prefillTask.getName()).isEqualTo("contextbook_read");
        assertThat(prefillTask.getTaskReferenceName()).isEqualTo("prefill_agent_prefill_0");
        assertThat(prefillTask.getInputParameters().get("section")).isEqualTo("coder_plan");
        assertThat(wf.getTasks().get(3).getType()).isEqualTo("DO_WHILE"); // loop

        // Prefill outputs MUST NOT be injected as ``tool_call``/``tool`` message
        // pairs — that pattern teaches the LLM (via conversation history) that
        // those tools are callable, leading to hallucinated calls and wasted
        // tool budgets (observed across executions 72e8fef3, 1c2f5baf, etc.).
        // Instead they're combined into a single system message after the
        // instructions, before the user prompt.
        WorkflowTask loop = wf.getTasks().get(3);
        WorkflowTask llmTask =
                loop.getLoopOver().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("messages");

        // No tool_call / tool messages from prefill.
        long toolCallCount =
                messages.stream().filter(m -> "tool_call".equals(m.get("role"))).count();
        long toolRespCount = messages.stream().filter(m -> "tool".equals(m.get("role"))).count();
        assertThat(toolCallCount)
                .as("prefill must NOT produce tool_call messages anymore")
                .isZero();
        assertThat(toolRespCount)
                .as("prefill must NOT produce tool response messages anymore")
                .isZero();

        // Locate the prefill-context system message (the SECOND system message —
        // the first is the agent's instructions).
        List<Map<String, Object>> systemMsgs =
                messages.stream().filter(m -> "system".equals(m.get("role"))).toList();
        assertThat(systemMsgs)
                .as(
                        "expect [agent instructions, prefill context] as the two leading system messages")
                .hasSize(2);
        String prefillCtx = (String) systemMsgs.get(1).get("message");
        assertThat(prefillCtx).contains("Pre-loaded context");
        assertThat(prefillCtx).contains("contextbook_read");
        assertThat(prefillCtx).contains("section=coder_plan");
        assertThat(prefillCtx)
                .as("body must template in the prefill task output via ${...} ref")
                .contains("${prefill_agent_prefill_0.output.result}");

        // And it must appear BEFORE the user message.
        int prefillCtxIdx = messages.indexOf(systemMsgs.get(1));
        int userIdx = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof Map<?, ?> m && "user".equals(m.get("role"))) {
                userIdx = i;
                break;
            }
        }
        assertThat(prefillCtxIdx).isLessThan(userIdx);
    }

    @Test
    void testCompileWithMultiplePrefillToolsForkJoin() {
        ToolConfig tool1 =
                ToolConfig.builder()
                        .name("contextbook_read")
                        .description("Read contextbook")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();
        ToolConfig tool2 =
                ToolConfig.builder()
                        .name("git_diff")
                        .description("Git diff")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("multi_prefill")
                        .model("openai/gpt-4o")
                        .instructions("You review code.")
                        .tools(List.of(tool1, tool2))
                        .prefillTools(
                                List.of(
                                        PrefillToolCallConfig.builder()
                                                .toolName("contextbook_read")
                                                .arguments(Map.of("section", "impl_report"))
                                                .build(),
                                        PrefillToolCallConfig.builder()
                                                .toolName("git_diff")
                                                .arguments(Map.of())
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // ctx_resolve + init_state + FORK_JOIN + JOIN + DoWhile + synth_output
        assertThat(wf.getTasks()).hasSize(6);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE"); // ctx_resolve
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE"); // init_state
        WorkflowTask fork = wf.getTasks().get(2);
        assertThat(fork.getType()).isEqualTo("FORK_JOIN");
        assertThat(fork.getForkTasks()).hasSize(2);
        WorkflowTask join = wf.getTasks().get(3);
        assertThat(join.getType()).isEqualTo("JOIN");
        assertThat(wf.getTasks().get(4).getType()).isEqualTo("DO_WHILE");
        assertThat(wf.getTasks().get(5).getType()).isEqualTo("INLINE"); // synth_output

        // Multiple prefills are still combined into ONE system message — the
        // body contains a labeled section per prefill, each with its own
        // ${refName.output.result} placeholder.
        WorkflowTask loop = wf.getTasks().get(4);
        WorkflowTask llmTask =
                loop.getLoopOver().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("messages");

        long toolCallCount =
                messages.stream().filter(m -> "tool_call".equals(m.get("role"))).count();
        long toolResultCount = messages.stream().filter(m -> "tool".equals(m.get("role"))).count();
        assertThat(toolCallCount).isZero();
        assertThat(toolResultCount).isZero();

        List<Map<String, Object>> systemMsgs =
                messages.stream().filter(m -> "system".equals(m.get("role"))).toList();
        assertThat(systemMsgs).hasSize(2);
        String body = (String) systemMsgs.get(1).get("message");
        assertThat(body).contains("contextbook_read");
        assertThat(body).contains("section=impl_report");
        assertThat(body).contains("git_diff");
        assertThat(body).contains("${multi_prefill_prefill_0.output.result}");
        assertThat(body).contains("${multi_prefill_prefill_1.output.result}");
    }

    @Test
    void testPrefillNeverEmitsToolCallMessages() {
        // Locks in the new contract: prefill outputs are combined into a
        // single system message — they MUST NOT appear as ``tool_call`` or
        // ``tool`` messages in the conversation. The old pattern made the
        // LLM hallucinate calls to prefill-only tool names (contextbook_read,
        // list_directory, git_status, git_diff) because it saw them in
        // history as past tool_calls (executions 72e8fef3 / 1c2f5baf).
        ToolConfig tool =
                ToolConfig.builder()
                        .name("my_tool")
                        .description("A tool")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("field_test")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .prefillTools(
                                List.of(
                                        PrefillToolCallConfig.builder()
                                                .toolName("my_tool")
                                                .arguments(Map.of("key", "val"))
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask loop = wf.getTasks().get(3); // after ctx_resolve, init_state, prefill task
        WorkflowTask llmTask =
                loop.getLoopOver().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("messages");

        // No tool_call / tool messages anywhere.
        boolean anyToolCall = messages.stream().anyMatch(m -> "tool_call".equals(m.get("role")));
        boolean anyToolResp = messages.stream().anyMatch(m -> "tool".equals(m.get("role")));
        assertThat(anyToolCall)
                .as(
                        "prefill must NOT inject tool_call messages — they make the "
                                + "LLM hallucinate calls to the prefill tool names")
                .isFalse();
        assertThat(anyToolResp)
                .as("prefill must NOT inject tool response messages either")
                .isFalse();

        // The prefill-context system message carries the placeholder and
        // surfaces the tool name + args for the model's benefit.
        Map<String, Object> prefillCtxMsg =
                messages.stream()
                        .filter(m -> "system".equals(m.get("role")))
                        .reduce((a, b) -> b)
                        .orElseThrow();
        String body = (String) prefillCtxMsg.get("message");
        assertThat(body).contains("my_tool");
        assertThat(body).contains("key=val");
        assertThat(body).contains("${field_test_prefill_0.output.result}");
    }

    @Test
    void testCompileWithNoPrefillToolsUnchanged() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("no_prefill")
                        .model("openai/gpt-4o")
                        .tools(List.of(tool))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // No prefill → ctx_resolve + init_state + DoWhile + synth_output
        assertThat(wf.getTasks()).hasSize(4);
        assertThat(wf.getTasks().get(0).getType()).isEqualTo("INLINE");
        assertThat(wf.getTasks().get(1).getType()).isEqualTo("SET_VARIABLE");
        assertThat(wf.getTasks().get(2).getType()).isEqualTo("DO_WHILE");

        // LLM messages should NOT have tool_call or tool messages
        WorkflowTask loop = wf.getTasks().get(2);
        WorkflowTask llmTask =
                loop.getLoopOver().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("messages");
        assertThat(messages.stream().noneMatch(m -> "tool_call".equals(m.get("role")))).isTrue();
        assertThat(messages.stream().noneMatch(m -> "tool".equals(m.get("role")))).isTrue();
    }

    // ── Dispatch + prefill: option-B refactor ───────────────────────
    //
    // Three guarantees that let SDK examples drop their workarounds:
    //   1. compileSimple now honors prefill_tools (no need for a dummy tool
    //      to route through compileWithTools).
    //   2. An explicit non-handoff strategy (PLAN_EXECUTE etc.) routes to
    //      MultiAgentCompiler regardless of whether ``tools`` is non-empty
    //      (no need to set tools=[] just to dodge compileHybrid).
    //   3. Handoff with both agents+tools still goes to compileHybrid
    //      (regression guard for the original hybrid use-case).

    @Test
    void compileSimpleHonorsPrefillTools() {
        // No tools, no agents — pure simple path. Prefill must still produce
        // a pre-loop SIMPLE task. Its output is woven into a combined system
        // message (not tool_call/tool pairs), same as the with-tools path.
        ToolConfig tool =
                ToolConfig.builder()
                        .name("contextbook_read")
                        .description("Read contextbook")
                        .inputSchema(
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of("section", Map.of("type", "string"))))
                        .toolType("worker")
                        .build();
        // Worker registry — register so PrefillToolCallConfig can resolve.
        // (Not strictly required for compileSimple, but matches real usage.)
        AgentConfig config =
                AgentConfig.builder()
                        .name("planner_no_tools")
                        .model("openai/gpt-4o")
                        .instructions("Produce a JSON plan.")
                        // tools intentionally omitted — this is the simple path.
                        .prefillTools(
                                List.of(
                                        PrefillToolCallConfig.builder()
                                                .toolName("contextbook_read")
                                                .arguments(Map.of("section", "coder_plan"))
                                                .build()))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Pre-loop layout: instructions resolve (INLINE) + prefill SIMPLE +
        // LLM. Order matters — prefill must run before the LLM call.
        List<WorkflowTask> tasks = wf.getTasks();
        WorkflowTask prefillTask =
                tasks.stream()
                        .filter(
                                t ->
                                        "SIMPLE".equals(t.getType())
                                                && "contextbook_read".equals(t.getName()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "Expected a SIMPLE prefill task on the simple-compile path, got: "
                                                        + tasks.stream()
                                                                .map(WorkflowTask::getType)
                                                                .toList()));
        assertThat(prefillTask.getTaskReferenceName()).isEqualTo("planner_no_tools_prefill_0");
        assertThat(prefillTask.getInputParameters().get("section")).isEqualTo("coder_plan");

        // Prefill must be ordered before the LLM task.
        int prefillIdx = tasks.indexOf(prefillTask);
        int llmIdx = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if ("LLM_CHAT_COMPLETE".equals(tasks.get(i).getType())) {
                llmIdx = i;
                break;
            }
        }
        assertThat(llmIdx).as("LLM task must exist on simple path").isGreaterThanOrEqualTo(0);
        assertThat(prefillIdx).as("prefill must precede LLM").isLessThan(llmIdx);

        // LLM messages: NO tool_call / tool messages for the prefill. The
        // prefill output flows in via a combined system message templated
        // with the ${prefillRef.output.result} placeholder.
        WorkflowTask llmTask = tasks.get(llmIdx);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("messages");

        boolean anyToolCall = messages.stream().anyMatch(m -> "tool_call".equals(m.get("role")));
        boolean anyToolResp = messages.stream().anyMatch(m -> "tool".equals(m.get("role")));
        assertThat(anyToolCall).isFalse();
        assertThat(anyToolResp).isFalse();

        Map<String, Object> prefillCtxMsg =
                messages.stream()
                        .filter(m -> "system".equals(m.get("role")))
                        .reduce((a, b) -> b) // last system msg is the prefill context
                        .orElseThrow();
        String body = (String) prefillCtxMsg.get("message");
        assertThat(body).contains("contextbook_read");
        assertThat(body).contains("section=coder_plan");
        assertThat(body).contains("${planner_no_tools_prefill_0.output.result}");
    }

    /**
     * Walk every task in a WorkflowDef including those nested in SWITCH decisionCases /
     * defaultCase, FORK_JOIN forkTasks, and DO_WHILE loopOver. The PLAN_EXECUTE shape buries
     * PLAN_AND_COMPILE several levels deep, so naive top-level scans miss it.
     */
    private static List<WorkflowTask> walkAllTasks(WorkflowDef wf) {
        List<WorkflowTask> out = new java.util.ArrayList<>();
        collectAllTasks(wf.getTasks(), out);
        return out;
    }

    private static void collectAllTasks(List<WorkflowTask> tasks, List<WorkflowTask> out) {
        if (tasks == null) return;
        for (WorkflowTask t : tasks) {
            out.add(t);
            String type = t.getType();
            if ("SWITCH".equals(type)) {
                if (t.getDecisionCases() != null) {
                    t.getDecisionCases().values().forEach(branch -> collectAllTasks(branch, out));
                }
                collectAllTasks(t.getDefaultCase(), out);
            } else if ("DO_WHILE".equals(type)) {
                collectAllTasks(t.getLoopOver(), out);
            } else if ("FORK_JOIN".equals(type)) {
                if (t.getForkTasks() != null) {
                    t.getForkTasks().forEach(branch -> collectAllTasks(branch, out));
                }
            }
        }
    }

    @Test
    void planExecuteWithToolsRoutesToMultiAgentNotHybrid() {
        // The dispatch fix: even with non-empty parent-level ``tools``, an
        // explicit ``strategy=plan_execute`` must engage MultiAgentCompiler
        // (which knows PLAN_EXECUTE shape). Pre-fix, this would have routed
        // to ``compileHybrid`` and produced a single-LLM-with-tools workflow,
        // silently dropping the strategy.
        AgentConfig planner =
                AgentConfig.builder()
                        .name("planner_inner")
                        .model("openai/gpt-4o-mini")
                        .instructions("Produce a JSON plan ending in ```json … ```.")
                        .build();
        AgentConfig fallback =
                AgentConfig.builder()
                        .name("fallback_inner")
                        .model("openai/gpt-4o-mini")
                        .instructions("Recover.")
                        .build();
        ToolConfig accidentalTool =
                ToolConfig.builder()
                        .name("contextbook_read")
                        .description("Read contextbook")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("plan_exec_with_parent_tools")
                        .model("openai/gpt-4o-mini")
                        .strategy(AgentConfig.Strategy.PLAN_EXECUTE)
                        .planner(planner)
                        .fallback(fallback)
                        .tools(List.of(accidentalTool)) // ← would have triggered hybrid
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // PLAN_AND_COMPILE is the unmistakable signature of the PLAN_EXECUTE
        // path — compileHybrid would never emit it. PAC lives nested inside
        // the ``has_plan`` SWITCH branch, so walk the whole tree.
        List<WorkflowTask> all = walkAllTasks(wf);
        boolean hasPlanAndCompile =
                all.stream().anyMatch(t -> "PLAN_AND_COMPILE".equals(t.getType()));
        assertThat(hasPlanAndCompile)
                .as(
                        "PLAN_EXECUTE strategy must route to MultiAgentCompiler "
                                + "(emitting a PLAN_AND_COMPILE task) even when parent ``tools`` is non-empty")
                .isTrue();

        // The parent must NOT have a hybrid LLM loop with tools injected
        // from its own tool list. ``tools`` (lowercase) is the LLM input key.
        boolean hybridShape =
                all.stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .anyMatch(
                                t ->
                                        t.getInputParameters() != null
                                                && t.getInputParameters().containsKey("tools"));
        assertThat(hybridShape)
                .as("PLAN_EXECUTE parent should not produce a hybrid LLM-with-tools loop")
                .isFalse();
    }

    @Test
    void handoffStrategyWithToolsStillUsesHybrid() {
        // Regression: the dispatch refactor must not break the original
        // hybrid use-case (handoff strategy with parent-level tools). The
        // hybrid path is the ONLY thing that handles "agent with sub-agents
        // AND its own tool list" cleanly for handoff semantics.
        AgentConfig sub =
                AgentConfig.builder()
                        .name("sub_inner")
                        .model("openai/gpt-4o-mini")
                        .instructions("Sub.")
                        .build();
        ToolConfig parentTool =
                ToolConfig.builder()
                        .name("search_web")
                        .description("Search.")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();
        AgentConfig config =
                AgentConfig.builder()
                        .name("handoff_with_tools")
                        .model("openai/gpt-4o-mini")
                        // strategy unset → defaults to handoff
                        .agents(List.of(sub))
                        .tools(List.of(parentTool))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        // Handoff+tools should produce the hybrid LLM-with-tools loop —
        // its calling card. ``tools`` (lowercase) is the LLM input key.
        List<WorkflowTask> all = walkAllTasks(wf);
        boolean hybridShape =
                all.stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .anyMatch(
                                t ->
                                        t.getInputParameters() != null
                                                && t.getInputParameters().containsKey("tools"));
        assertThat(hybridShape)
                .as("handoff strategy with parent tools must still route to compileHybrid")
                .isTrue();

        // And it should NOT have spuriously turned into a plan workflow.
        boolean hasPlanAndCompile =
                all.stream().anyMatch(t -> "PLAN_AND_COMPILE".equals(t.getType()));
        assertThat(hasPlanAndCompile)
                .as("handoff strategy must not produce a PLAN_AND_COMPILE task")
                .isFalse();
    }

    // ── Regression: reasoning models lose visible reasoning output unless the
    // compiled LLM task carries ``reasoningSummary``. Conductor's OpenAI
    // Responses adapter only emits chain-of-thought text on reasoning items
    // when ``reasoning.summary`` is set on the request — agentspan
    // historically set ``reasoningEffort`` but not ``reasoningSummary`` so
    // gpt-5.x / o-series spent reasoning tokens and surfaced nothing.
    @Test
    void testReasoningEffortAutoEnablesReasoningSummary() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("reasoning_agent")
                        .model("openai/gpt-5.3-codex")
                        .instructions("Think then answer.")
                        .reasoningEffort("medium")
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask llmTask =
                wf.getTasks().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();

        assertThat(llmTask.getInputParameters().get("reasoningEffort")).isEqualTo("medium");
        assertThat(llmTask.getInputParameters().get("reasoningSummary"))
                .as(
                        "reasoningSummary must default to 'auto' when reasoningEffort is set, "
                                + "otherwise OpenAI returns empty reasoning summary blocks")
                .isEqualTo("auto");
    }

    // ── Thinking config: ChatCompletion's wire key is ``thinkingTokenLimit`` — emitting a
    // ``thinkingConfig`` map is silently dropped by the task-input ObjectMapper (unknown key)
    // and thinking never activates. These tests pin the working wire contract.
    @Test
    void testThinkingConfigEmitsThinkingTokenLimit() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("thinking_agent")
                        .model("anthropic/claude-sonnet-4-6")
                        .instructions("Think deeply.")
                        .thinkingConfig(
                                ThinkingConfig.builder().enabled(true).budgetTokens(4096).build())
                        .build();

        WorkflowTask llmTask = findLlmTask(compiler.compile(config));

        assertThat(llmTask.getInputParameters().get("thinkingTokenLimit")).isEqualTo(4096);
        assertThat(llmTask.getInputParameters())
                .as("the dead thinkingConfig map key must no longer be emitted")
                .doesNotContainKey("thinkingConfig");
    }

    @Test
    void testThinkingConfigEnabledWithoutBudgetGetsDefault() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("thinking_agent")
                        .model("anthropic/claude-sonnet-4-6")
                        .instructions("Think deeply.")
                        .thinkingConfig(ThinkingConfig.builder().enabled(true).build())
                        .build();

        WorkflowTask llmTask = findLlmTask(compiler.compile(config));

        assertThat(llmTask.getInputParameters().get("thinkingTokenLimit"))
                .isEqualTo(AgentCompiler.DEFAULT_THINKING_BUDGET_TOKENS);
    }

    @Test
    void testNoThinkingConfigEmitsNoThinkingTokenLimit() {
        AgentConfig plain =
                AgentConfig.builder()
                        .name("plain_agent")
                        .model("anthropic/claude-sonnet-4-6")
                        .instructions("Answer.")
                        .build();
        assertThat(findLlmTask(compiler.compile(plain)).getInputParameters())
                .doesNotContainKey("thinkingTokenLimit");

        AgentConfig disabled =
                AgentConfig.builder()
                        .name("disabled_agent")
                        .model("anthropic/claude-sonnet-4-6")
                        .instructions("Answer.")
                        .thinkingConfig(ThinkingConfig.builder().enabled(false).build())
                        .build();
        assertThat(findLlmTask(compiler.compile(disabled)).getInputParameters())
                .doesNotContainKey("thinkingTokenLimit");
    }

    // ── Invariant: thinking is used ONLY when thinkingConfig is set. Anthropic forwards
    // reasoningEffort as ``output_config.effort``, which modulates thinking on adaptive models
    // (Opus 4.7+/Fable) — so effort must not reach Anthropic without thinkingConfig.
    @Test
    void testReasoningEffortDroppedForAnthropicWithoutThinking() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("effort_agent")
                        .model("anthropic/claude-opus-4-8")
                        .instructions("Answer.")
                        .reasoningEffort("high")
                        .build();

        WorkflowTask llmTask = findLlmTask(compiler.compile(config));

        assertThat(llmTask.getInputParameters()).doesNotContainKey("reasoningEffort");
        assertThat(llmTask.getInputParameters()).doesNotContainKey("reasoningSummary");
    }

    @Test
    void testReasoningEffortForwardedForAnthropicWithThinking() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("effort_agent")
                        .model("anthropic/claude-opus-4-8")
                        .instructions("Answer.")
                        .reasoningEffort("high")
                        .thinkingConfig(
                                ThinkingConfig.builder().enabled(true).budgetTokens(2048).build())
                        .build();

        WorkflowTask llmTask = findLlmTask(compiler.compile(config));

        assertThat(llmTask.getInputParameters().get("reasoningEffort")).isEqualTo("high");
        assertThat(llmTask.getInputParameters().get("thinkingTokenLimit")).isEqualTo(2048);
    }

    // ── Prefill isolation: tools declared ONLY in prefillTools must not be
    // advertised in the LLM's callable tool set. The LLM may only call tools
    // explicitly listed in ``tools``. Prefill workers still need to be
    // registered (so the prefill task can execute) but they must be invisible
    // to the model.
    @Test
    void testPrefillOnlyToolNotInLLMToolsArray() {
        ToolConfig llmCallable =
                ToolConfig.builder()
                        .name("llm_callable_tool")
                        .description("Tool the LLM may call")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        PrefillToolCallConfig prefillOnly =
                PrefillToolCallConfig.builder()
                        .toolName("prefill_only_tool")
                        .arguments(Map.of("foo", "bar"))
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("prefill_iso_agent")
                        .model("openai/gpt-4o")
                        .instructions("Use only your declared tools.")
                        .tools(List.of(llmCallable))
                        .prefillTools(List.of(prefillOnly))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask llmTask = findLlmTask(wf);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolSpecs =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("tools");

        assertThat(toolSpecs)
                .as("LLM tools array must be present when agent declares tools")
                .isNotNull();

        List<String> advertisedNames = toolSpecs.stream().map(m -> (String) m.get("name")).toList();

        assertThat(advertisedNames)
                .as("LLM-callable tools must include the declared tool")
                .contains("llm_callable_tool");

        assertThat(advertisedNames)
                .as(
                        "LLM-callable tools must NOT include prefill-only tool names "
                                + "— prefill tools are deterministic pre-run setup, not LLM-callable")
                .doesNotContain("prefill_only_tool");
    }

    // Recursive search — the LLM task lives inside a DoWhile when tools=[..]
    private WorkflowTask findLlmTask(WorkflowDef wf) {
        return findLlmTaskIn(wf.getTasks()).orElseThrow();
    }

    private java.util.Optional<WorkflowTask> findLlmTaskIn(List<WorkflowTask> tasks) {
        if (tasks == null) return java.util.Optional.empty();
        for (WorkflowTask t : tasks) {
            if ("LLM_CHAT_COMPLETE".equals(t.getType())) {
                return java.util.Optional.of(t);
            }
            if (t.getLoopOver() != null) {
                java.util.Optional<WorkflowTask> nested = findLlmTaskIn(t.getLoopOver());
                if (nested.isPresent()) return nested;
            }
            if (t.getDecisionCases() != null) {
                for (List<WorkflowTask> branch : t.getDecisionCases().values()) {
                    java.util.Optional<WorkflowTask> nested = findLlmTaskIn(branch);
                    if (nested.isPresent()) return nested;
                }
            }
            if (t.getDefaultCase() != null) {
                java.util.Optional<WorkflowTask> nested = findLlmTaskIn(t.getDefaultCase());
                if (nested.isPresent()) return nested;
            }
        }
        return java.util.Optional.empty();
    }

    @Test
    void testNoReasoningEffort_noReasoningSummary() {
        // When reasoningEffort is NOT set, reasoningSummary must NOT be set
        // either. Non-reasoning models should not receive a reasoning block
        // they don't understand, and we shouldn't accidentally enable
        // reasoning for plain chat models.
        AgentConfig config =
                AgentConfig.builder()
                        .name("plain_agent")
                        .model("openai/gpt-4o")
                        .instructions("Be concise.")
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask llmTask =
                wf.getTasks().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();

        assertThat(llmTask.getInputParameters()).doesNotContainKey("reasoningEffort");
        assertThat(llmTask.getInputParameters()).doesNotContainKey("reasoningSummary");
    }

    /**
     * Regression: the user-message template that joins ctx_inject output with the base prompt must
     * NOT contain a literal "\n\n" separator. The ctx_inject script carries its own trailing
     * separator when non-empty (and empty otherwise) — a literal joiner here produces a leading
     * "\n\n" artifact when there's no context to inject, which shifts the LLM's behavior at
     * temperature 0 and (in the suite12 max_message regression) made the model answer in one shot
     * instead of calling tools.
     */
    @Test
    void testCtxInjectMessageHasNoLiteralSeparator() {
        ToolConfig tool =
                ToolConfig.builder()
                        .name("echo_tool")
                        .description("Echo")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();

        AgentConfig config =
                AgentConfig.builder()
                        .name("ctx_join_agent")
                        .model("openai/gpt-4o-mini")
                        .instructions("Be concise.")
                        .tools(List.of(tool))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        WorkflowTask loop =
                wf.getTasks().stream()
                        .filter(t -> "DO_WHILE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();

        WorkflowTask llmTask =
                loop.getLoopOver().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getType()))
                        .findFirst()
                        .orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) llmTask.getInputParameters().get("messages");
        Map<String, Object> userMsg =
                messages.stream()
                        .filter(m -> "user".equals(m.get("role")))
                        .findFirst()
                        .orElseThrow();

        String message = (String) userMsg.get("message");
        assertThat(message)
                .as("user message must reference both ctx_inject result and workflow prompt")
                .contains("ctx_join_agent_ctx_inject.output.result")
                .contains("workflow.input.prompt");
        assertThat(message)
                .as(
                        "user message MUST NOT contain literal \"\\n\\n\" between ctx and prompt — "
                                + "ctx_inject script owns its own trailing separator")
                .doesNotContain("}\n\n${");

        // The LLM task's name must be the lowercase TaskDef alias. If left as
        // "LLM_CHAT_COMPLETE" (matching the type), Conductor misses the
        // registered TaskDef, falls back to default tool-routing config, and
        // the model stops emitting tool calls — which is exactly the suite12
        // max_message regression.
        assertThat(llmTask.getName())
                .as("LLM_CHAT_COMPLETE task name must be lowercase TaskDef alias")
                .isEqualTo("llm_chat_complete");
    }

    @Test
    void testCompileNeverInjectsUndeclaredTools() {
        // Inverse of the deleted auto-expose merger tests: an agent's compiled
        // workflow references exactly its declared tools. Server capabilities
        // like OCG are opted into from the SDK, never appended at compile time.
        ToolConfig tool =
                ToolConfig.builder()
                        .name("search")
                        .description("Search the web")
                        .inputSchema(Map.of("type", "object"))
                        .toolType("worker")
                        .build();
        AgentConfig config =
                AgentConfig.builder()
                        .name("plain_agent")
                        .model("openai/gpt-4o")
                        .instructions("You are helpful.")
                        .tools(List.of(tool))
                        .build();

        WorkflowDef wf = compiler.compile(config);

        assertThat(config.getTools())
                .as("compile must not mutate the declared tool list")
                .hasSize(1);
        assertThat(wf.toString()).doesNotContain("ocg_agent").doesNotContain("_ocg_agent");
    }
}
