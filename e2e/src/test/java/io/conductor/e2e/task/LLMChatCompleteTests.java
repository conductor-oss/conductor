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
package io.conductor.e2e.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import io.conductor.e2e.util.ApiUtil;
import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-side end-to-end coverage for {@code LLM_CHAT_COMPLETE} against live providers.
 *
 * <p>Each {@code @Test} method is independently gated on the provider's API key being present on
 * the host shell. The {@code docker-compose-postgres.yaml} / {@code
 * docker-compose-postgres-e2e.yaml} files forward those variables through to the conductor-server
 * container, where Spring binds them onto {@code conductor.ai.<provider>.api-key}. If a key isn't
 * set, the dependent tests self-skip via {@link EnabledIfEnvironmentVariable}.
 *
 * <h2>Model selection</h2>
 *
 * Models are pinned to the current generally-available lineup published by each provider:
 *
 * <ul>
 *   <li><b>Anthropic</b> (from <a
 *       href="https://platform.claude.com/docs/en/about-claude/models/overview">claude.com/docs/models/overview</a>):
 *       <ul>
 *         <li>{@link #ANTHROPIC_HAIKU_MODEL} — non-thinking baseline.
 *         <li>{@link #ANTHROPIC_SONNET_THINKING_MODEL} — extended-thinking-capable (legacy {@code
 *             thinking.type=enabled} + {@code budget_tokens} still accepted).
 *         <li>{@link #ANTHROPIC_OPUS_THINKING_MODEL} — adaptive-thinking only; rejects the legacy
 *             shape, the adapter must rewrite {@code thinkingTokenLimit} into {@code
 *             thinking.type=adaptive} + {@code output_config.effort}.
 *       </ul>
 *   <li><b>OpenAI</b> (from <a
 *       href="https://developers.openai.com/api/docs/models/all">developers.openai.com/api/docs/models/all</a>):
 *       <ul>
 *         <li>{@link #OPENAI_CHAT_MODEL} — general-purpose, non-reasoning chat.
 *         <li>{@link #OPENAI_REASONING_MODEL} — reasoning model that honors {@code reasoningEffort}
 *             / {@code reasoningSummary} via the Responses API.
 *       </ul>
 * </ul>
 *
 * <h2>Matrix</h2>
 *
 * <table>
 *   <tr><th>Scenario</th><th>Anthropic non-thinking</th><th>Anthropic Sonnet thinking</th>
 *       <th>Anthropic Opus thinking (adaptive)</th><th>OpenAI non-thinking</th>
 *       <th>OpenAI reasoning</th></tr>
 *   <tr><td>Single chat</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td></tr>
 *   <tr><td>Multi-turn history (`messages`)</td><td>✓</td><td></td><td></td><td>✓</td><td></td></tr>
 *   <tr><td>Function tool calling</td><td>✓</td><td></td><td></td><td>✓</td><td></td></tr>
 *   <tr><td>JSON output</td><td></td><td></td><td></td><td>✓</td><td></td></tr>
 *   <tr><td>Response chaining (previousResponseId)</td><td></td><td></td><td></td>
 *       <td>✓</td><td></td></tr>
 *   <tr><td>LLM in DO_WHILE loop</td><td></td><td></td><td>✓ (regression)</td><td></td><td></td></tr>
 *   <tr><td>Response chaining inside DO_WHILE</td><td></td><td></td><td></td>
 *       <td>✓</td><td></td></tr>
 *   <tr><td>Agentic DO_WHILE (workingState hand-off)</td><td></td><td></td>
 *       <td>✓</td><td></td><td></td></tr>
 * </table>
 *
 * <p>The headline test {@link #anthropic_opus_thinking_inDoWhileLoop_completesAcrossIterations} is
 * the regression lock for the production HTTP 400: pre-fix, Opus 4.7 + {@code thinkingTokenLimit}
 * sent {@code thinking.type=enabled} which Opus 4.7 rejects ({@code "thinking.type.enabled" is not
 * supported for this model. Use "thinking.type.adaptive" and "output_config.effort" ...}); the
 * loop's first iteration failed and the workflow landed in FAILED. Post-fix the adapter rewrites
 * the budget into {@code thinking.type=adaptive} + {@code output_config.effort}, so the loop
 * completes both iterations.
 */
@Slf4j
public class LLMChatCompleteTests {

    // ----------------------------------------------------------------
    // Pinned model IDs (update when migrating to a newer generation)
    // ----------------------------------------------------------------

    /** Fastest non-thinking Claude. Source: claude.com/docs/models/overview "latest". */
    private static final String ANTHROPIC_HAIKU_MODEL = "claude-haiku-4-5";

    /**
     * Sonnet 4.6. Supports both extended thinking (legacy ``thinking.type=enabled`` +
     * ``budget_tokens``, deprecated but still accepted) and adaptive thinking. We use it to cover
     * the legacy-shape path against a current model so this test catches regressions in the older
     * code path even after Opus 4.7 ages out.
     */
    private static final String ANTHROPIC_SONNET_THINKING_MODEL = "claude-sonnet-4-6";

    /**
     * Opus 4.7. Adaptive thinking only — the legacy ``thinking.type=enabled`` shape returns HTTP
     * 400. This is the model that motivated the adapter rewrite under test.
     */
    private static final String ANTHROPIC_OPUS_THINKING_MODEL = "claude-opus-4-7";

    /**
     * General-purpose non-reasoning OpenAI chat model. Source: developers.openai.com
     * "general-purpose chat models" list. Pinned to the latest 4.x mini for cost + parity with the
     * in-repo {@code AIModelIntegrationTest} suite.
     */
    private static final String OPENAI_CHAT_MODEL = "gpt-4.1-mini";

    /**
     * OpenAI reasoning model (Responses API path that nests ``reasoning.effort``). gpt-5-mini is
     * the smallest gpt-5 reasoning variant and matches what the AI module's live integration tests
     * use today.
     */
    private static final String OPENAI_REASONING_MODEL = "gpt-5-mini";

    private final WorkflowClient workflowClient = ApiUtil.WORKFLOW_CLIENT;
    private final MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;

    // ====================================================================
    // Anthropic — non-thinking model (Claude Haiku 4.5)
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    public void anthropic_haiku_singleChat_completes() {
        // Sanity check for the simplest LLM_CHAT_COMPLETE input on a non-thinking model.
        // Haiku 4.5 does not engage thinking; the request omits both thinkingTokenLimit
        // and reasoningEffort, exercising the adapter's bare-minimum chat path.
        String wfName = "ai_e2e_anthropic_haiku_single";
        registerSingleTaskWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "anthropic",
                                "model", ANTHROPIC_HAIKU_MODEL,
                                "instructions", "You are a helpful math assistant. Be concise.",
                                "userInput", "What is 2 + 2? Reply with just the number."),
                        60);
        assertChatCompletedWithAnswer(wf, "4");
    }

    // ====================================================================
    // Anthropic — Sonnet 4.6 + thinking (legacy ``thinking.type=enabled`` shape)
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    public void anthropic_sonnet_thinking_singleChat_completes() {
        // Sonnet 4.6 still accepts the legacy ``thinking.type=enabled`` + ``budget_tokens``
        // shape (the adaptive path is recommended but the deprecated path remains
        // functional). This test guards against the adapter accidentally over-routing every
        // thinking-capable model through adaptive — which would suppress the legacy code
        // path entirely.
        String wfName = "ai_e2e_anthropic_sonnet_thinking_single";
        registerSingleTaskWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "anthropic",
                                "model", ANTHROPIC_SONNET_THINKING_MODEL,
                                "thinkingTokenLimit", 4000,
                                "instructions", "You are a helpful math assistant. Be concise.",
                                "userInput", "What is 5 + 6? Reply with just the number."),
                        90);
        assertChatCompletedWithAnswer(wf, "11");
    }

    // ====================================================================
    // Anthropic — Opus 4.7 + thinking (adaptive-only) — regression for the HTTP 400
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    public void anthropic_opus_thinking_singleChat_completes() {
        // Single-chat companion to the loop regression below. Locks in that Opus 4.7 +
        // thinkingTokenLimit round-trips through the Conductor task pipeline (mapper →
        // worker → adapter → live API → LLMResponse) when the adaptive-thinking rewrite
        // is in place.
        String wfName = "ai_e2e_anthropic_opus_thinking_single";
        registerSingleTaskWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "anthropic",
                                "model", ANTHROPIC_OPUS_THINKING_MODEL,
                                "thinkingTokenLimit", 4000,
                                "instructions", "You are a helpful math assistant. Be concise.",
                                "userInput", "What is 3 + 4? Reply with just the number."),
                        90);
        assertChatCompletedWithAnswer(wf, "7");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    public void anthropic_opus_thinking_inDoWhileLoop_completesAcrossIterations() {
        // The headline regression. Two iterations × LLM_CHAT_COMPLETE × Opus 4.7 ×
        // thinkingTokenLimit. Pre-fix iteration 1 hits HTTP 400 and the workflow lands in
        // FAILED; post-fix both iterations COMPLETE and the workflow reaches COMPLETED.
        String wfName = "ai_e2e_anthropic_opus_thinking_loop";
        registerLoopWorkflow(wfName, 2);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "anthropic",
                                "model", ANTHROPIC_OPUS_THINKING_MODEL,
                                "thinkingTokenLimit", 4000,
                                "instructions", "You are a helpful math assistant. Be concise.",
                                "userInput", "What is 2 + 2? Reply with just the number."),
                        120);

        List<Task> chatTasks = chatTasks(wf);
        assertEquals(
                2,
                chatTasks.size(),
                "expected one LLM_CHAT_COMPLETE per loop iteration (×2); saw: " + chatTasks);
        for (Task t : chatTasks) {
            assertEquals(
                    Task.Status.COMPLETED,
                    t.getStatus(),
                    "iteration "
                            + t.getIteration()
                            + " must complete; reason: "
                            + t.getReasonForIncompletion());
            assertTrue(
                    String.valueOf(t.getOutputData().get("result")).contains("4"),
                    "iteration "
                            + t.getIteration()
                            + " must reach the model's answer '4'; got: "
                            + t.getOutputData().get("result"));
        }
    }

    // ====================================================================
    // Anthropic — multi-turn history via ``messages`` array
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    public void anthropic_haiku_multiTurnHistory_recallsPriorTurn() {
        // Verifies the mapper passes an explicit ``messages`` array (system + user +
        // assistant + user) through to the provider untouched, so the model can answer
        // questions about earlier turns. A failure here typically means either the
        // mapper dropped the history, or the provider adapter mangled the role mapping.
        String wfName = "ai_e2e_anthropic_haiku_history";
        registerHistoryWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of("llmProvider", "anthropic", "model", ANTHROPIC_HAIKU_MODEL),
                        60);

        Object result = wf.getTasks().getFirst().getOutputData().get("result");
        assertNotNull(result);
        assertTrue(
                result.toString().toLowerCase().contains("conductor"),
                "model must recall the name 'Conductor' from the prior assistant turn; got: "
                        + result);
    }

    // ====================================================================
    // Anthropic — function tool calling (model returns tool_use, not result)
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    public void anthropic_haiku_functionTool_returnsToolCall() {
        // The mapper must surface ``tools`` from the input parameters and the adapter
        // must convert each ToolSpec into a custom Anthropic tool. The model's chosen
        // tool_use response then has to round-trip back through the worker into
        // ``toolCalls`` on the task output.
        String wfName = "ai_e2e_anthropic_haiku_tools";
        registerSingleTaskWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "anthropic",
                                "model", ANTHROPIC_HAIKU_MODEL,
                                "instructions", "Use tools to answer the user's question.",
                                "userInput", "What is the weather in Tokyo?",
                                "tools", List.of(weatherTool())),
                        60);

        Task chat = wf.getTasks().getFirst();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls =
                (List<Map<String, Object>>) chat.getOutputData().get("toolCalls");
        assertNotNull(toolCalls, "expected toolCalls on the task output");
        assertFalse(toolCalls.isEmpty(), "expected at least one tool call");
        Map<String, Object> first = toolCalls.getFirst();
        assertEquals("get_weather", first.get("name"));
        // Conductor's ToolCall record exposes parsed args under ``inputParameters`` — the
        // adapter has already JSON-decoded whatever the provider returned, so this is a
        // Map<String,Object> on the task output. Fall back to alternate keys if the
        // serialization shape ever shifts under us.
        Object args =
                first.getOrDefault(
                        "inputParameters", first.getOrDefault("arguments", first.get("input")));
        assertNotNull(args, "expected parsed tool arguments on task output; got: " + first);
        String argsText = args.toString();
        assertTrue(
                argsText.toLowerCase().contains("tokyo"),
                "expected 'tokyo' in tool arguments; got: " + argsText);
    }

    // ====================================================================
    // OpenAI — general-purpose non-reasoning chat (gpt-4.1-mini)
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void openai_chat_singleChat_completes() {
        String wfName = "ai_e2e_openai_chat_single";
        registerSingleTaskWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "openai",
                                "model", OPENAI_CHAT_MODEL,
                                "instructions", "You are a helpful math assistant. Be concise.",
                                "userInput", "What is 2 + 2? Reply with just the number."),
                        60);
        assertChatCompletedWithAnswer(wf, "4");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void openai_chat_jsonOutput_returnsJson() {
        // Locks in the ``jsonOutput=true`` path on a non-thinking OpenAI model.
        // The prompt must mention JSON for the model to honor response_format.
        String wfName = "ai_e2e_openai_chat_json";
        registerSingleTaskWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "openai",
                                "model", OPENAI_CHAT_MODEL,
                                "jsonOutput", true,
                                "instructions",
                                        "Return a JSON object with keys 'name' and 'value'. JSON only.",
                                "userInput",
                                        "Reply with name='test' and value=42 as a JSON object."),
                        60);

        Object result = wf.getTasks().getFirst().getOutputData().get("result");
        assertNotNull(result);
        String text = result.toString();
        assertTrue(text.contains("42"), "expected value 42 in JSON; got: " + text);
        assertTrue(text.contains("name"), "expected key 'name' in JSON; got: " + text);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void openai_chat_multiTurnHistory_recallsPriorTurn() {
        String wfName = "ai_e2e_openai_chat_history";
        registerHistoryWorkflow(wfName);

        Workflow wf =
                runAndWait(wfName, Map.of("llmProvider", "openai", "model", OPENAI_CHAT_MODEL), 60);

        Object result = wf.getTasks().getFirst().getOutputData().get("result");
        assertNotNull(result);
        assertTrue(
                result.toString().toLowerCase().contains("conductor"),
                "model must recall the name 'Conductor' from the prior assistant turn; got: "
                        + result);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void openai_chat_functionTool_returnsToolCall() {
        String wfName = "ai_e2e_openai_chat_tools";
        registerSingleTaskWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "openai",
                                "model", OPENAI_CHAT_MODEL,
                                "instructions", "Use tools to answer the user's question.",
                                "userInput", "What is the weather in Tokyo?",
                                "tools", List.of(weatherTool())),
                        60);

        Task chat = wf.getTasks().getFirst();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls =
                (List<Map<String, Object>>) chat.getOutputData().get("toolCalls");
        assertNotNull(toolCalls, "expected toolCalls on the task output");
        assertFalse(toolCalls.isEmpty(), "expected at least one tool call");
        Map<String, Object> first = toolCalls.getFirst();
        assertEquals("get_weather", first.get("name"));
        Object args =
                first.getOrDefault(
                        "inputParameters", first.getOrDefault("arguments", first.get("input")));
        assertNotNull(args, "expected parsed tool arguments on task output; got: " + first);
        String argsText = args.toString();
        assertTrue(
                argsText.toLowerCase().contains("tokyo"),
                "expected 'tokyo' in tool arguments; got: " + argsText);
    }

    // ====================================================================
    // OpenAI — reasoning model (gpt-5-mini, Responses API)
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void openai_reasoning_singleChat_completesAndSurfacesReasoningTokens() {
        // OpenAI reasoning models go through the Responses API path that nests effort
        // under ``reasoning.effort``. The adapter must forward both reasoningEffort
        // and reasoningSummary; the worker must surface ``reasoningTokens`` (and, when
        // the model emits a summary, ``reasoning``) onto the task output.
        String wfName = "ai_e2e_openai_reasoning_single";
        registerSingleTaskWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "openai",
                                "model", OPENAI_REASONING_MODEL,
                                "reasoningEffort", "medium",
                                "reasoningSummary", "auto",
                                "instructions", "Solve carefully and show your reasoning.",
                                "userInput",
                                        "If a train leaves at 3pm and travels 60mph for 2.5 hours,"
                                                + " how far has it gone? Reply with just the number"
                                                + " of miles."),
                        120);

        Task chat = wf.getTasks().getFirst();
        Object result = chat.getOutputData().get("result");
        assertNotNull(result);
        assertTrue(
                result.toString().contains("150"),
                "expected the reasoning model to reach 150 miles; got: " + result);
        // ``reasoningTokens`` is the part the AI module is responsible for plumbing;
        // its presence proves the reasoning request shape reached OpenAI intact (a
        // value of 0 is the model's prerogative, but the key must exist).
        Object reasoningTokens = chat.getOutputData().get("reasoningTokens");
        assertNotNull(
                reasoningTokens,
                "expected reasoningTokens on a "
                        + OPENAI_REASONING_MODEL
                        + " response — request shape must be carrying the reasoning block");
    }

    // ====================================================================
    // OpenAI — response chaining via previousResponseId (Responses API)
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void openai_chat_previousResponseId_chainsAcrossTasks() {
        // Two LLM_CHAT_COMPLETE tasks in one workflow. Task 1 establishes context ("My
        // name is Conductor"); task 2 references task 1's responseId and asks a follow-up.
        // No ``messages`` array is sent on task 2 — the only way the model can answer
        // correctly is if the OpenAI Responses-API server-side store recalls turn 1 from
        // its ``previousResponseId``. This proves the full chain:
        //
        //   task 1 output.responseId  →  ${task1.output.responseId} parameter binding
        //   →  ChatCompletion.previousResponseId  →  OpenAIResponsesChatOptions
        //   →  Responses API ``previous_response_id``  →  server-side context recall.
        //
        // Mapper-side unit coverage lives in AIModelTaskMapperPreviousResponseIdTest;
        // this asserts the same chain works end-to-end against the live API.
        String wfName = "ai_e2e_openai_chat_prev_response_id_chain";
        registerChainedWorkflow(wfName);

        Workflow wf =
                runAndWait(wfName, Map.of("llmProvider", "openai", "model", OPENAI_CHAT_MODEL), 90);

        // The workflow holds two LLM_CHAT_COMPLETE tasks. Task 1 must surface a non-blank
        // responseId — without it, task 2 has nothing to chain to and the test below would
        // be vacuously green.
        List<Task> chatTasks = chatTasks(wf);
        assertEquals(
                2,
                chatTasks.size(),
                "expected two LLM_CHAT_COMPLETE tasks (turn 1 + turn 2); saw: " + chatTasks);

        Task turn1 = chatTasks.get(0);
        Task turn2 = chatTasks.get(1);

        Object turn1ResponseId = turn1.getOutputData().get("responseId");
        assertNotNull(
                turn1ResponseId,
                "turn 1 must emit a ``responseId`` for chaining; if absent, the OpenAI"
                        + " Responses API path didn't populate it");
        assertFalse(
                turn1ResponseId.toString().isBlank(), "turn 1 ``responseId`` must not be blank");

        // The actual proof: task 2 recalls the name from turn 1's context. Turn 2's only
        // input is the bare question ``What is my name?``; no history is sent locally.
        Object turn2Result = turn2.getOutputData().get("result");
        assertNotNull(turn2Result);
        assertTrue(
                turn2Result.toString().toLowerCase().contains("conductor"),
                "turn 2 must recall the name 'Conductor' from the server-side context"
                        + " keyed by previousResponseId; got: "
                        + turn2Result);

        // Sanity: turn 2 should also have its own responseId so a longer chain is
        // possible (turn 3 → turn 2's id, etc.). Tested at the unit layer too, but
        // surfacing here as well is cheap.
        Object turn2ResponseId = turn2.getOutputData().get("responseId");
        assertNotNull(turn2ResponseId, "turn 2 must also emit a ``responseId``");
    }

    // ====================================================================
    // OpenAI — previousResponseId chained across DO_WHILE iterations
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    public void openai_chat_previousResponseId_chain_inDoWhileLoop_recallsAcrossIterations() {
        // Variant of openai_chat_previousResponseId_chainsAcrossTasks that lives *inside*
        // a DO_WHILE. The loop body has two tasks per iteration:
        //
        //   1. chat (LLM_CHAT_COMPLETE)  — previousResponseId=${workflow.variables.lastResponseId}
        //   2. update_state (SET_VARIABLE) — lastResponseId=${chat.output.responseId}
        //
        // Iteration N's chat resumes from iteration N-1's server-side context via the
        // workflow-variable thread (the variable is reset between iterations by the
        // sibling set_variable task). Auto loop-history injection is irrelevant here —
        // no ``messages`` array is sent on any iteration; the model only "remembers"
        // because the Responses-API server-side context store is being keyed by the
        // chained responseId.
        //
        // Iteration script:
        //   iter 1: "Remember the number 42." (no chain — bootstraps a new server context)
        //   iter 2: "Remember the color blue."  (chains from iter 1)
        //   iter 3: "What number and color did I tell you?" (chains from iter 2)
        //
        // The final iteration's result must mention both ``42`` and ``blue`` — proof
        // that the chained context survived two hops through the workflow-variable
        // thread + the OpenAI server-side store.
        String wfName = "ai_e2e_openai_chat_prev_response_id_loop";
        registerChainedLoopWorkflow(wfName);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider", "openai",
                                "model", OPENAI_CHAT_MODEL,
                                "prompts", CHAINED_LOOP_PROMPTS),
                        180);

        List<Task> chatTasks = chatTasks(wf);
        assertEquals(
                3,
                chatTasks.size(),
                "expected three LLM_CHAT_COMPLETE tasks (one per iteration); saw: "
                        + chatTasks.size());

        // Every iteration must surface its own responseId. If any are blank the
        // chain breaks downstream.
        for (Task t : chatTasks) {
            Object rid = t.getOutputData().get("responseId");
            assertNotNull(
                    rid, "iteration " + t.getIteration() + " must emit a ``responseId``; got null");
            assertFalse(
                    rid.toString().isBlank(),
                    "iteration " + t.getIteration() + " ``responseId`` must not be blank");
        }

        // Iterations 2+ must have received the prior iteration's responseId on their
        // own previousResponseId input. Iteration 1 is the bootstrap and is allowed to
        // be unchained (empty string is fine).
        for (int i = 1; i < chatTasks.size(); i++) {
            Task prior = chatTasks.get(i - 1);
            Task current = chatTasks.get(i);
            String priorResponseId = String.valueOf(prior.getOutputData().get("responseId"));
            Object currentPrev = current.getInputData().get("previousResponseId");
            assertEquals(
                    priorResponseId,
                    String.valueOf(currentPrev),
                    "iteration "
                            + current.getIteration()
                            + " previousResponseId must equal iteration "
                            + prior.getIteration()
                            + "'s responseId; saw "
                            + currentPrev);
        }

        // The headline assertion: iteration 3's answer must include both prior facts.
        Task finalTurn = chatTasks.get(chatTasks.size() - 1);
        String finalText = String.valueOf(finalTurn.getOutputData().get("result")).toLowerCase();
        assertTrue(
                finalText.contains("42"),
                "iteration 3 must recall the number 42 from iter 1's context; got: " + finalText);
        assertTrue(
                finalText.contains("blue"),
                "iteration 3 must recall the color blue from iter 2's context; got: " + finalText);
    }

    // ====================================================================
    // Anthropic — Opus 4.7 + thinking in an agentic DO_WHILE
    // ====================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    public void anthropic_opus_thinking_agentLoop_threadsWorkingStateAcrossIterations() {
        // "Agent" pattern on top of Opus 4.7 + thinking. Anthropic providers declare
        // supportsAssistantPrefill=false, so Conductor's auto loop-history injection
        // is suppressed — each iteration's chat would otherwise see *only* its own
        // messages and lose any context from prior iterations.
        //
        // To work around that, the loop body manually threads agent state through a
        // workflow variable:
        //
        //   1. chat (LLM_CHAT_COMPLETE, Opus 4.7 + thinking, instructions reference
        //      ${workflow.variables.workingState})
        //   2. update_state (SET_VARIABLE) — workingState=${chat.output.result}
        //
        // The agent is given a multi-step calculation and asked to do ONE step per
        // iteration, returning the running total. With workingState carrying the
        // prior iteration's result forward, iteration 2 can pick up where iteration 1
        // left off — proving the agent loop pattern works end-to-end with thinking
        // enabled on an adaptive-only model.
        //
        // Problem: compute (3 + 4) × 2.
        //   iter 1: 3 + 4 = 7   → workingState="7"
        //   iter 2: 7 × 2 = 14  → workingState="14"
        String wfName = "ai_e2e_anthropic_opus_thinking_agent_loop";
        registerAgentLoopWorkflow(wfName, 2);

        Workflow wf =
                runAndWait(
                        wfName,
                        Map.of(
                                "llmProvider",
                                "anthropic",
                                "model",
                                ANTHROPIC_OPUS_THINKING_MODEL,
                                "thinkingTokenLimit",
                                4000),
                        180);

        List<Task> chatTasks = chatTasks(wf);
        assertEquals(
                2,
                chatTasks.size(),
                "expected two LLM_CHAT_COMPLETE tasks (one per iteration); saw: "
                        + chatTasks.size());
        for (Task t : chatTasks) {
            assertEquals(
                    Task.Status.COMPLETED,
                    t.getStatus(),
                    "iteration "
                            + t.getIteration()
                            + " must complete; reason: "
                            + t.getReasonForIncompletion());
        }

        // Iteration 2 must have received iteration 1's result on its workingState
        // input — this is the thread that makes the agent "remember" without
        // relying on assistant-prefill auto-injection.
        Task iter1 = chatTasks.get(0);
        Task iter2 = chatTasks.get(1);
        String iter1Result = String.valueOf(iter1.getOutputData().get("result"));
        Object iter2WorkingState = iter2.getInputData().get("workingState");
        assertEquals(
                iter1Result,
                String.valueOf(iter2WorkingState),
                "iteration 2's ``workingState`` must equal iteration 1's chat result —"
                        + " that's the agent state hand-off the test is asserting on");

        // The headline: iteration 2's reply must reach the multi-step answer 14.
        // Models do drift on phrasing; we don't pin "= 14" exactly, only that the
        // number appears.
        String iter2Result = String.valueOf(iter2.getOutputData().get("result"));
        assertTrue(
                iter2Result.contains("14"),
                "iteration 2 must reach the multi-step answer 14 by acting on iter 1's"
                        + " workingState; got: "
                        + iter2Result);
    }

    // ====================================================================
    // Workflow-def helpers
    // ====================================================================

    private void registerSingleTaskWorkflow(String name) {
        ensureLLMChatCompleteTaskDef();
        WorkflowTask chat = passThroughChatTask("chat");
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(chat));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    private void registerLoopWorkflow(String name, int iterations) {
        ensureLLMChatCompleteTaskDef();
        WorkflowTask chat = passThroughChatTask("chat");

        WorkflowTask loop = new WorkflowTask();
        loop.setName("loop");
        loop.setTaskReferenceName("loop");
        loop.setType(TaskType.DO_WHILE.name());
        loop.setLoopCondition(
                "if ( $.loop['iteration'] < " + iterations + " ) { true; } else { false; }");
        loop.setLoopOver(List.of(chat));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(loop));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    /**
     * Workflow with a chat task whose {@code messages} input is wired to a fixed multi-turn
     * conversation: system + user + assistant ("My name is Conductor") + user ("What's my name?").
     * The model must recall the name from the assistant turn, which is the wire-level proof that
     * the history array survived the mapper round-trip.
     */
    private void registerHistoryWorkflow(String name) {
        ensureLLMChatCompleteTaskDef();

        WorkflowTask chat = new WorkflowTask();
        chat.setName("LLM_CHAT_COMPLETE");
        chat.setTaskReferenceName("chat");
        chat.setType("LLM_CHAT_COMPLETE");

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(
                Map.of("role", "system", "message", "You are a helpful and concise assistant."));
        messages.add(Map.of("role", "user", "message", "My name is Conductor. Remember that."));
        messages.add(
                Map.of(
                        "role",
                        "assistant",
                        "message",
                        "Got it — I'll remember your name is Conductor."));
        messages.add(Map.of("role", "user", "message", "What is my name?"));

        Map<String, Object> inputParameters = new HashMap<>();
        inputParameters.put("llmProvider", "${workflow.input.llmProvider}");
        inputParameters.put("model", "${workflow.input.model}");
        inputParameters.put("messages", messages);
        chat.setInputParameters(inputParameters);

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(chat));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    /**
     * Per-iteration prompts used by the chained-DO_WHILE test. Iteration 1 + 2 plant facts, and
     * iteration 3 asks the model to recall them. The list length (3) determines the loop count
     * because the workflow uses Conductor's list-iteration mode ({@code WorkflowTask.setItems}).
     */
    private static final List<String> CHAINED_LOOP_PROMPTS =
            List.of(
                    "Please remember the number 42. Just acknowledge.",
                    "Please remember the color blue. Just acknowledge.",
                    "What number and color did I tell you? Reply with both, no extra prose.");

    /**
     * DO_WHILE that threads {@code previousResponseId} across iterations via a workflow variable.
     * Loop body per iteration:
     *
     * <ol>
     *   <li>{@code chat} — reads {@code previousResponseId=${workflow.variables.lastResponseId}}
     *       and {@code userInput=${loop.output.loopItem}} (the current prompt for this turn).
     *   <li>{@code update_state} ({@code SET_VARIABLE}) — overwrites {@code lastResponseId} with
     *       {@code ${chat.output.responseId}} so the next iteration's chat picks it up.
     * </ol>
     *
     * <p>The DO_WHILE uses Conductor's list-iteration mode ({@code setItems}). The loop runs once
     * per element in {@code ${workflow.input.prompts}}, and {@code DoWhile.injectLoopVariables}
     * binds the current element to {@code loop.output.loopItem} on every iteration's scheduling
     * pass — which is the clean way to vary the per-iteration prompt without arithmetic inside
     * {@code ${...}} expressions.
     *
     * <p>{@code lastResponseId} is pre-seeded to "" so iteration 1 sees an unchained start rather
     * than an unresolved placeholder.
     */
    private void registerChainedLoopWorkflow(String name) {
        ensureLLMChatCompleteTaskDef();

        // Pre-loop: seed workflow.variables.lastResponseId so iteration 1's chat sees an
        // empty string (unchained start) rather than an unresolved ${...} placeholder.
        WorkflowTask initVars = new WorkflowTask();
        initVars.setName("set_variable");
        initVars.setTaskReferenceName("init_vars");
        initVars.setWorkflowTaskType(TaskType.SET_VARIABLE);
        initVars.setInputParameters(Map.of("lastResponseId", ""));

        WorkflowTask chat = new WorkflowTask();
        chat.setName("LLM_CHAT_COMPLETE");
        chat.setTaskReferenceName("chat");
        chat.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> chatIn = new HashMap<>();
        chatIn.put("llmProvider", "${workflow.input.llmProvider}");
        chatIn.put("model", "${workflow.input.model}");
        chatIn.put(
                "instructions",
                "You are a careful note-taker. Reply concisely. Do not invent details that"
                        + " were not stated.");
        // ``loop.output.loopItem`` is the current item from the items list — injected by
        // DoWhile.injectLoopVariables on every iteration's scheduling pass.
        chatIn.put("userInput", "${loop.output.loopItem}");
        chatIn.put("previousResponseId", "${workflow.variables.lastResponseId}");
        chat.setInputParameters(chatIn);

        WorkflowTask updateState = new WorkflowTask();
        updateState.setName("set_variable");
        updateState.setTaskReferenceName("update_state");
        updateState.setWorkflowTaskType(TaskType.SET_VARIABLE);
        updateState.setInputParameters(Map.of("lastResponseId", "${chat.output.responseId}"));

        WorkflowTask loop = new WorkflowTask();
        loop.setName("loop");
        loop.setTaskReferenceName("loop");
        loop.setType(TaskType.DO_WHILE.name());
        // List iteration via the Orkes-compatible ``_items`` inputParameter — the same
        // ``DoWhile.isListIteration`` accepts either ``setItems(...)`` (newer OSS API) or
        // ``inputParameters._items`` (legacy). We go through the latter because the e2e
        // module depends on the published ``conductor-client:5.0.1`` JAR, which predates
        // ``WorkflowTask.setItems``. The server resolves this at scheduling time.
        loop.setInputParameters(Map.of("_items", "${workflow.input.prompts}"));
        // The workflow-def validator requires a non-blank loopCondition on every DO_WHILE.
        // List-iteration termination logic combines this with ``hasMoreItems`` (see
        // ``DoWhile.evaluateCondition`` under ``isListIteration``) — a permissive ``true``
        // here defers all stopping to the items list being consumed.
        loop.setLoopCondition("true");
        loop.setLoopOver(List.of(chat, updateState));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(initVars, loop));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    /**
     * Agentic DO_WHILE that hands a piece of "working state" forward across iterations using a
     * sibling {@code SET_VARIABLE} task. Each iteration:
     *
     * <ol>
     *   <li>{@code chat} — Opus 4.7 + thinking. The {@code instructions} field embeds {@code
     *       ${workflow.variables.workingState}} so the model can see what the prior turn produced.
     *   <li>{@code update_state} ({@code SET_VARIABLE}) — overwrites {@code workingState} with
     *       {@code ${chat.output.result}}.
     * </ol>
     *
     * <p>This is the manual-threading pattern documented in {@code AIReasoningEndToEndTest}'s
     * loop-history javadoc: providers that reject assistant prefill (Anthropic 4.6+) must receive
     * prior-iteration context through user-message templating, not via auto-injection.
     */
    private void registerAgentLoopWorkflow(String name, int iterations) {
        ensureLLMChatCompleteTaskDef();

        WorkflowTask initVars = new WorkflowTask();
        initVars.setName("set_variable");
        initVars.setTaskReferenceName("init_vars");
        initVars.setWorkflowTaskType(TaskType.SET_VARIABLE);
        initVars.setInputParameters(Map.of("workingState", ""));

        WorkflowTask chat = new WorkflowTask();
        chat.setName("LLM_CHAT_COMPLETE");
        chat.setTaskReferenceName("chat");
        chat.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> chatIn = new HashMap<>();
        chatIn.put("llmProvider", "${workflow.input.llmProvider}");
        chatIn.put("model", "${workflow.input.model}");
        chatIn.put("thinkingTokenLimit", "${workflow.input.thinkingTokenLimit}");
        chatIn.put(
                "instructions",
                "You are a step-by-step math agent. You will be asked to do ONE step of a"
                        + " calculation each turn. On the first turn ``workingState`` is empty;"
                        + " on later turns it carries the running total from the previous turn."
                        + " Reply with just the new running total — a single number, no prose.");
        chatIn.put(
                "userInput",
                "Compute (3 + 4) * 2 step by step. On turn 1 evaluate ``3 + 4``. On turn 2,"
                        + " take the running total in workingState and multiply by 2.\nworkingState:"
                        + " ${workflow.variables.workingState}");
        // Also surface workingState as a top-level input so the test can read it back
        // out of the task input for hand-off assertions.
        chatIn.put("workingState", "${workflow.variables.workingState}");
        chat.setInputParameters(chatIn);

        WorkflowTask updateState = new WorkflowTask();
        updateState.setName("set_variable");
        updateState.setTaskReferenceName("update_state");
        updateState.setWorkflowTaskType(TaskType.SET_VARIABLE);
        updateState.setInputParameters(Map.of("workingState", "${chat.output.result}"));

        WorkflowTask loop = new WorkflowTask();
        loop.setName("loop");
        loop.setTaskReferenceName("loop");
        loop.setType(TaskType.DO_WHILE.name());
        loop.setLoopCondition(
                "if ( $.loop['iteration'] < " + iterations + " ) { true; } else { false; }");
        loop.setLoopOver(List.of(chat, updateState));

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(initVars, loop));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    /**
     * Two-task workflow that exercises OpenAI's Responses-API server-side context store. Task 1
     * establishes context ("My name is Conductor"); task 2 references {@code
     * ${turn1.output.responseId}} via {@code previousResponseId} so OpenAI looks up turn 1
     * server-side rather than relying on a re-sent {@code messages} array.
     *
     * <p>Both tasks pull {@code llmProvider} + {@code model} from {@code workflow.input}, so the
     * same definition can be flipped to another OpenAI model without re-registering.
     */
    private void registerChainedWorkflow(String name) {
        ensureLLMChatCompleteTaskDef();

        WorkflowTask turn1 = new WorkflowTask();
        turn1.setName("LLM_CHAT_COMPLETE");
        turn1.setTaskReferenceName("turn1");
        turn1.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> turn1In = new HashMap<>();
        turn1In.put("llmProvider", "${workflow.input.llmProvider}");
        turn1In.put("model", "${workflow.input.model}");
        turn1In.put("instructions", "You are a helpful assistant. Keep replies very short.");
        turn1In.put(
                "userInput",
                "Please remember the following for the rest of this conversation: my name is"
                        + " Conductor.");
        turn1.setInputParameters(turn1In);

        WorkflowTask turn2 = new WorkflowTask();
        turn2.setName("LLM_CHAT_COMPLETE");
        turn2.setTaskReferenceName("turn2");
        turn2.setType("LLM_CHAT_COMPLETE");
        Map<String, Object> turn2In = new HashMap<>();
        turn2In.put("llmProvider", "${workflow.input.llmProvider}");
        turn2In.put("model", "${workflow.input.model}");
        turn2In.put("instructions", "You are a helpful assistant. Keep replies very short.");
        // No local history. The follow-up question only resolves if the server-side
        // Responses-API context store recalls turn 1 via previousResponseId.
        turn2In.put("userInput", "What is my name?");
        turn2In.put("previousResponseId", "${turn1.output.responseId}");
        turn2.setInputParameters(turn2In);

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        def.setOwnerEmail("ai-e2e@conductor.test");
        def.setTasks(List.of(turn1, turn2));
        metadataClient.updateWorkflowDefs(List.of(def));
    }

    /**
     * Chat task that pulls every field from {@code workflow.input.*}. This lets each test reuse the
     * same registration helper and vary inputs purely by what the {@link StartWorkflowRequest}
     * carries — including optional ones like {@code thinkingTokenLimit}, {@code reasoningEffort},
     * {@code reasoningSummary}, {@code jsonOutput}, and {@code tools}. Conductor's parameter
     * binding leaves unresolved ${...} placeholders as null/false/empty when the input field is
     * absent, so the inputs schema stays additive instead of combinatorial.
     */
    private WorkflowTask passThroughChatTask(String refName) {
        WorkflowTask chat = new WorkflowTask();
        chat.setName("LLM_CHAT_COMPLETE");
        chat.setTaskReferenceName(refName);
        chat.setType("LLM_CHAT_COMPLETE");

        Map<String, Object> in = new HashMap<>();
        in.put("llmProvider", "${workflow.input.llmProvider}");
        in.put("model", "${workflow.input.model}");
        in.put("instructions", "${workflow.input.instructions}");
        in.put("userInput", "${workflow.input.userInput}");
        in.put("thinkingTokenLimit", "${workflow.input.thinkingTokenLimit}");
        in.put("reasoningEffort", "${workflow.input.reasoningEffort}");
        in.put("reasoningSummary", "${workflow.input.reasoningSummary}");
        in.put("jsonOutput", "${workflow.input.jsonOutput}");
        in.put("tools", "${workflow.input.tools}");
        chat.setInputParameters(in);
        return chat;
    }

    private void ensureLLMChatCompleteTaskDef() {
        TaskDef def = new TaskDef();
        def.setName("LLM_CHAT_COMPLETE");
        def.setRetryCount(0);
        def.setTimeoutSeconds(180);
        def.setOwnerEmail("ai-e2e@conductor.test");
        try {
            metadataClient.registerTaskDefs(List.of(def));
        } catch (Exception ignore) {
            // already registered from a prior run / parallel fork
        }
    }

    // ====================================================================
    // Tool spec helper
    // ====================================================================

    /**
     * Standard weather tool used by both the Anthropic and OpenAI tool-calling tests so the
     * comparison is apples-to-apples. The schema matches the shape both providers' adapters convert
     * into their native tool definitions.
     */
    private static Map<String, Object> weatherTool() {
        return Map.of(
                "name", "get_weather",
                "description", "Get the current weather for a location",
                "inputSchema",
                        Map.of(
                                "type", "object",
                                "properties",
                                        Map.of(
                                                "location",
                                                Map.of(
                                                        "type", "string",
                                                        "description", "City name")),
                                "required", List.of("location")));
    }

    // ====================================================================
    // Workflow execution + assertion helpers
    // ====================================================================

    private Workflow runAndWait(String wfName, Map<String, Object> input, int maxSeconds) {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setName(wfName);
        req.setVersion(1);
        req.setInput(input);
        String workflowId = workflowClient.startWorkflow(req);
        log.info("Started workflow name={} id={}", wfName, workflowId);

        await().pollInterval(2, TimeUnit.SECONDS)
                .atMost(maxSeconds, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            Workflow wf = workflowClient.getWorkflow(workflowId, true);
                            assertNotNull(wf);
                            assertNotNull(wf.getStatus());
                            assertTrue(
                                    wf.getStatus().isTerminal(),
                                    "workflow not terminal yet: " + wf.getStatus());
                        });

        Workflow finished = workflowClient.getWorkflow(workflowId, true);
        assertEquals(
                Workflow.WorkflowStatus.COMPLETED,
                finished.getStatus(),
                "workflow must complete; tasks: " + finished.getTasks());
        return finished;
    }

    private static void assertChatCompletedWithAnswer(Workflow wf, String expectedSubstring) {
        Task chat =
                wf.getTasks().stream()
                        .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getTaskType()))
                        .findFirst()
                        .orElseThrow(
                                () -> new AssertionError("no LLM_CHAT_COMPLETE task in " + wf));
        assertEquals(Task.Status.COMPLETED, chat.getStatus(), "chat task must complete");
        Object result = chat.getOutputData().get("result");
        assertNotNull(result, "expected `result` on task output");
        assertTrue(
                result.toString().contains(expectedSubstring),
                "expected model output to contain '" + expectedSubstring + "'; got: " + result);
    }

    private static List<Task> chatTasks(Workflow wf) {
        return wf.getTasks().stream()
                .filter(t -> "LLM_CHAT_COMPLETE".equals(t.getTaskType()))
                .toList();
    }
}
