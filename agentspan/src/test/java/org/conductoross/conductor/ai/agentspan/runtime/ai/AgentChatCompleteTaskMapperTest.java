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
package org.conductoross.conductor.ai.agentspan.runtime.ai;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.model.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.assertj.core.api.Assertions.*;

/** Tests for AgentChatCompleteTaskMapper. */
class AgentChatCompleteTaskMapperTest {

    private final AgentChatCompleteTaskMapper mapper = new AgentChatCompleteTaskMapper();

    @BeforeEach
    void setUp() {
        // @Value("${...}") is not injected outside of Spring; the constructor already sets 5,
        // but be explicit so tests are self-documenting.
        mapper.setRecentExchangesToKeep(5);
    }

    @Test
    void testExtractSubWorkflowResult_extractsResultField() throws Exception {
        Map<String, Object> outputData = new HashMap<>();
        outputData.put("subWorkflowId", "abc-123");
        outputData.put("result", "Afghanistan has a GDP of $20B and a population of 40M.");
        outputData.put("finishReason", "STOP");
        outputData.put("rejectionReason", null);

        Map<String, Object> result = invokeExtractResult(outputData);

        assertThat(result).containsOnlyKeys("result");
        assertThat(result.get("result"))
                .isEqualTo("Afghanistan has a GDP of $20B and a population of 40M.");
    }

    @Test
    void testExtractSubWorkflowResult_nullOutput() throws Exception {
        Map<String, Object> result = invokeExtractResult(null);
        assertThat(result).containsEntry("result", "");
    }

    @Test
    void testExtractSubWorkflowResult_noResultField() throws Exception {
        Map<String, Object> outputData = Map.of("subWorkflowId", "abc-123");
        Map<String, Object> result = invokeExtractResult(outputData);
        // Falls back to full output
        assertThat(result).containsKey("subWorkflowId");
    }

    @Test
    void testExtractSubWorkflowInput_extractsWorkflowInput() throws Exception {
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowDefinition", Map.of("name", "researcher_wf", "tasks", "..."));
        inputData.put("workflowInput", Map.of("prompt", "Afghanistan", "session_id", ""));

        Map<String, Object> result = invokeExtractInput(inputData);

        assertThat(result).containsEntry("prompt", "Afghanistan");
        assertThat(result).containsEntry("session_id", "");
        assertThat(result).doesNotContainKey("subWorkflowDefinition");
    }

    @Test
    void testExtractSubWorkflowInput_nullInput() throws Exception {
        Map<String, Object> result = invokeExtractInput(null);
        assertThat(result).isEmpty();
    }

    @Test
    void testExtractSubWorkflowInput_noWorkflowInput_removesDefinition() throws Exception {
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("subWorkflowDefinition", Map.of("name", "some_wf"));
        inputData.put("otherField", "value");

        Map<String, Object> result = invokeExtractInput(inputData);

        assertThat(result).doesNotContainKey("subWorkflowDefinition");
        assertThat(result).containsEntry("otherField", "value");
    }

    // ── Context condensation tests ─────────────────────────────────

    @Test
    void testCondenseHistory_belowThreshold_noOp() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(ChatMessage.Role.assistant, "Hello"));
        history.add(new ChatMessage(ChatMessage.Role.assistant, "World"));

        List<ChatMessage> result = mapper.condenseHistory(history);

        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(history);
    }

    @Test
    void testCondenseHistory_aboveThreshold_condensed() {
        // Create 12 exchanges (well above RECENT_EXCHANGES_TO_KEEP=5)
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            history.add(new ChatMessage(ChatMessage.Role.assistant, "Response " + i));
        }

        List<ChatMessage> result = mapper.condenseHistory(history);

        // Should have: 1 summary + 5 recent exchanges
        assertThat(result).hasSize(6);
        // First message is the summary
        assertThat(result.get(0).getRole()).isEqualTo(ChatMessage.Role.assistant);
        assertThat(result.get(0).getMessage()).contains("[Earlier conversation condensed]");
        // Last 5 are the recent messages
        assertThat(result.get(1).getMessage()).isEqualTo("Response 7");
        assertThat(result.get(5).getMessage()).isEqualTo("Response 11");
    }

    @Test
    void testGroupExchanges_toolCallWithResponses() {
        List<ChatMessage> history = new ArrayList<>();

        // tool_call message
        ChatMessage toolCallMsg = new ChatMessage();
        toolCallMsg.setRole(ChatMessage.Role.tool_call);
        toolCallMsg.setToolCalls(
                List.of(ToolCall.builder().name("search").taskReferenceName("ref1").build()));
        history.add(toolCallMsg);

        // tool response
        history.add(
                new ChatMessage(
                        ChatMessage.Role.tool,
                        ToolCall.builder()
                                .name("search")
                                .output(Map.of("result", "found"))
                                .build()));

        // assistant text
        history.add(new ChatMessage(ChatMessage.Role.assistant, "Based on the search..."));

        var exchanges = mapper.groupExchanges(history);

        assertThat(exchanges).hasSize(2);
        assertThat(exchanges.get(0).type())
                .isEqualTo(AgentChatCompleteTaskMapper.ExchangeType.TOOL_EXCHANGE);
        assertThat(exchanges.get(0).messages()).hasSize(2); // tool_call + tool
        assertThat(exchanges.get(1).type())
                .isEqualTo(AgentChatCompleteTaskMapper.ExchangeType.ASSISTANT_TEXT);
        assertThat(exchanges.get(1).messages()).hasSize(1);
    }

    @Test
    void testGroupExchanges_toolCallResponsePairsNeverSplit() {
        List<ChatMessage> history = new ArrayList<>();

        // tool_call with 3 parallel tool calls
        ChatMessage toolCallMsg = new ChatMessage();
        toolCallMsg.setRole(ChatMessage.Role.tool_call);
        toolCallMsg.setToolCalls(
                List.of(
                        ToolCall.builder().name("tool_a").taskReferenceName("ref1").build(),
                        ToolCall.builder().name("tool_b").taskReferenceName("ref2").build(),
                        ToolCall.builder().name("tool_c").taskReferenceName("ref3").build()));
        history.add(toolCallMsg);

        // 3 tool responses
        history.add(
                new ChatMessage(
                        ChatMessage.Role.tool,
                        ToolCall.builder().name("tool_a").output(Map.of("r", "a")).build()));
        history.add(
                new ChatMessage(
                        ChatMessage.Role.tool,
                        ToolCall.builder().name("tool_b").output(Map.of("r", "b")).build()));
        history.add(
                new ChatMessage(
                        ChatMessage.Role.tool,
                        ToolCall.builder().name("tool_c").output(Map.of("r", "c")).build()));

        var exchanges = mapper.groupExchanges(history);

        // All 4 messages should be in ONE exchange
        assertThat(exchanges).hasSize(1);
        assertThat(exchanges.get(0).messages()).hasSize(4);
        assertThat(exchanges.get(0).type())
                .isEqualTo(AgentChatCompleteTaskMapper.ExchangeType.TOOL_EXCHANGE);
    }

    @Test
    void testBuildSummary_format() {
        List<AgentChatCompleteTaskMapper.Exchange> exchanges = new ArrayList<>();

        // A tool exchange
        ChatMessage toolCallMsg = new ChatMessage();
        toolCallMsg.setRole(ChatMessage.Role.tool_call);
        toolCallMsg.setToolCalls(
                List.of(ToolCall.builder().name("run_command").taskReferenceName("ref1").build()));
        ChatMessage toolResp =
                new ChatMessage(
                        ChatMessage.Role.tool,
                        ToolCall.builder()
                                .name("run_command")
                                .output(Map.of("status", "success"))
                                .build());
        exchanges.add(
                new AgentChatCompleteTaskMapper.Exchange(
                        List.of(toolCallMsg, toolResp),
                        AgentChatCompleteTaskMapper.ExchangeType.TOOL_EXCHANGE));

        // An assistant text exchange
        exchanges.add(
                new AgentChatCompleteTaskMapper.Exchange(
                        List.of(
                                new ChatMessage(
                                        ChatMessage.Role.assistant,
                                        "Task completed successfully.")),
                        AgentChatCompleteTaskMapper.ExchangeType.ASSISTANT_TEXT));

        String summary = mapper.buildSummary(exchanges);

        assertThat(summary).contains("[Earlier conversation condensed]");
        assertThat(summary).contains("run_command");
        assertThat(summary).contains("Task completed successfully.");
        assertThat(summary).contains("1 tool exchange(s)");
        assertThat(summary).contains("1 assistant response(s)");
    }

    @Test
    void testCondenseHistory_emptyHistory_noOp() {
        List<ChatMessage> result = mapper.condenseHistory(new ArrayList<>());
        assertThat(result).isEmpty();
    }

    @Test
    void testCondenseHistory_fewExchanges_noCondensation() {
        // Only 3 exchanges — below RECENT_EXCHANGES_TO_KEEP (5)
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage(ChatMessage.Role.assistant, "One"));
        history.add(new ChatMessage(ChatMessage.Role.assistant, "Two"));
        history.add(new ChatMessage(ChatMessage.Role.assistant, "Three"));

        List<ChatMessage> result = mapper.condenseHistory(history);

        assertThat(result).hasSize(3);
    }

    @Test
    void testTruncate() {
        assertThat(AgentChatCompleteTaskMapper.truncate("short", 10)).isEqualTo("short");
        assertThat(AgentChatCompleteTaskMapper.truncate("a long string here", 6))
                .isEqualTo("a long...");
        assertThat(AgentChatCompleteTaskMapper.truncate(null, 10)).isEqualTo("");
    }

    @Test
    void testSanitizeMessages_dropsBlankTextOnlyMessages() {
        ChatCompletion cc = new ChatCompletion();
        cc.getMessages().add(new ChatMessage(ChatMessage.Role.system, "You are helpful."));
        cc.getMessages().add(new ChatMessage(ChatMessage.Role.user, "   "));

        mapper.sanitizeMessages(cc);

        assertThat(cc.getMessages()).hasSize(1);
        assertThat(cc.getMessages().get(0).getRole()).isEqualTo(ChatMessage.Role.system);
    }

    @Test
    void testSanitizeMessages_keepsMediaOnlyUserMessage() {
        ChatCompletion cc = new ChatCompletion();
        ChatMessage user = new ChatMessage(ChatMessage.Role.user, "   ");
        user.setMedia(List.of("https://example.com/cat.png"));
        cc.getMessages().add(user);

        mapper.sanitizeMessages(cc);

        assertThat(cc.getMessages()).hasSize(1);
        assertThat(cc.getMessages().get(0).getRole()).isEqualTo(ChatMessage.Role.user);
        assertThat(cc.getMessages().get(0).getMessage()).isNull();
        assertThat(cc.getMessages().get(0).getMedia())
                .containsExactly("https://example.com/cat.png");
    }

    @Test
    void testValidateRunnableConversation_rejectsMissingUserInput() {
        ChatCompletion cc = new ChatCompletion();
        cc.getMessages().add(new ChatMessage(ChatMessage.Role.system, "You are helpful."));

        assertThatThrownBy(() -> mapper.validateRunnableConversation(cc))
                .isInstanceOf(com.netflix.conductor.core.exception.TerminateWorkflowException.class)
                .hasMessageContaining("No non-empty user prompt or media");
    }

    @Test
    void testValidateRunnableConversation_acceptsMediaOnlyUserInput() {
        ChatCompletion cc = new ChatCompletion();
        ChatMessage user = new ChatMessage(ChatMessage.Role.user, "");
        user.setMedia(List.of("https://example.com/cat.png"));
        cc.getMessages().add(user);

        assertThatCode(() -> mapper.validateRunnableConversation(cc)).doesNotThrowAnyException();
    }

    // ── Token limit detection tests ─────────────────────────────────

    @Test
    void testPreviousIterationHitTokenLimit_maxTokens() {
        WorkflowModel workflow = new WorkflowModel();
        List<TaskModel> tasks = new ArrayList<>();

        // Previous iteration completed with MAX_TOKENS
        TaskModel prevTask = new TaskModel();
        prevTask.setStatus(TaskModel.Status.COMPLETED);
        prevTask.setOutputData(Map.of("finishReason", "MAX_TOKENS"));
        WorkflowTask prevWfTask = new WorkflowTask();
        prevWfTask.setTaskReferenceName("llm_call");
        prevTask.setWorkflowTask(prevWfTask);
        tasks.add(prevTask);

        // Current task (being mapped, not yet terminal)
        TaskModel currentTask = new TaskModel();
        currentTask.setStatus(TaskModel.Status.SCHEDULED);
        WorkflowTask currentWfTask = new WorkflowTask();
        currentWfTask.setTaskReferenceName("llm_call");
        currentTask.setWorkflowTask(currentWfTask);
        tasks.add(currentTask);

        workflow.setTasks(tasks);

        assertThat(mapper.previousIterationHitTokenLimit(currentTask, workflow)).isTrue();
    }

    @Test
    void testPreviousIterationHitTokenLimit_length() {
        WorkflowModel workflow = new WorkflowModel();
        List<TaskModel> tasks = new ArrayList<>();

        TaskModel prevTask = new TaskModel();
        prevTask.setStatus(TaskModel.Status.COMPLETED);
        prevTask.setOutputData(Map.of("finishReason", "LENGTH"));
        WorkflowTask prevWfTask = new WorkflowTask();
        prevWfTask.setTaskReferenceName("llm_call");
        prevTask.setWorkflowTask(prevWfTask);
        tasks.add(prevTask);

        TaskModel currentTask = new TaskModel();
        currentTask.setStatus(TaskModel.Status.SCHEDULED);
        WorkflowTask currentWfTask = new WorkflowTask();
        currentWfTask.setTaskReferenceName("llm_call");
        currentTask.setWorkflowTask(currentWfTask);
        tasks.add(currentTask);

        workflow.setTasks(tasks);

        assertThat(mapper.previousIterationHitTokenLimit(currentTask, workflow)).isTrue();
    }

    @Test
    void testPreviousIterationHitTokenLimit_normalStop() {
        WorkflowModel workflow = new WorkflowModel();
        List<TaskModel> tasks = new ArrayList<>();

        TaskModel prevTask = new TaskModel();
        prevTask.setStatus(TaskModel.Status.COMPLETED);
        prevTask.setOutputData(Map.of("finishReason", "STOP"));
        WorkflowTask prevWfTask = new WorkflowTask();
        prevWfTask.setTaskReferenceName("llm_call");
        prevTask.setWorkflowTask(prevWfTask);
        tasks.add(prevTask);

        TaskModel currentTask = new TaskModel();
        currentTask.setStatus(TaskModel.Status.SCHEDULED);
        WorkflowTask currentWfTask = new WorkflowTask();
        currentWfTask.setTaskReferenceName("llm_call");
        currentTask.setWorkflowTask(currentWfTask);
        tasks.add(currentTask);

        workflow.setTasks(tasks);

        assertThat(mapper.previousIterationHitTokenLimit(currentTask, workflow)).isFalse();
    }

    @Test
    void testPreviousIterationHitTokenLimit_firstIteration() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(new ArrayList<>());

        TaskModel currentTask = new TaskModel();
        currentTask.setStatus(TaskModel.Status.SCHEDULED);
        WorkflowTask currentWfTask = new WorkflowTask();
        currentWfTask.setTaskReferenceName("llm_call");
        currentTask.setWorkflowTask(currentWfTask);

        assertThat(mapper.previousIterationHitTokenLimit(currentTask, workflow)).isFalse();
    }

    @Test
    void testPreviousIterationHitTokenLimit_checksOnlyMostRecent() {
        WorkflowModel workflow = new WorkflowModel();
        List<TaskModel> tasks = new ArrayList<>();

        // Older iteration hit MAX_TOKENS
        TaskModel oldTask = new TaskModel();
        oldTask.setStatus(TaskModel.Status.COMPLETED);
        oldTask.setOutputData(Map.of("finishReason", "MAX_TOKENS"));
        WorkflowTask oldWfTask = new WorkflowTask();
        oldWfTask.setTaskReferenceName("llm_call");
        oldTask.setWorkflowTask(oldWfTask);
        tasks.add(oldTask);

        // Most recent iteration completed normally
        TaskModel recentTask = new TaskModel();
        recentTask.setStatus(TaskModel.Status.COMPLETED);
        recentTask.setOutputData(Map.of("finishReason", "STOP"));
        WorkflowTask recentWfTask = new WorkflowTask();
        recentWfTask.setTaskReferenceName("llm_call");
        recentTask.setWorkflowTask(recentWfTask);
        tasks.add(recentTask);

        // Current task
        TaskModel currentTask = new TaskModel();
        currentTask.setStatus(TaskModel.Status.SCHEDULED);
        WorkflowTask currentWfTask = new WorkflowTask();
        currentWfTask.setTaskReferenceName("llm_call");
        currentTask.setWorkflowTask(currentWfTask);
        tasks.add(currentTask);

        workflow.setTasks(tasks);

        // Most recent was STOP, not MAX_TOKENS — should be false
        assertThat(mapper.previousIterationHitTokenLimit(currentTask, workflow)).isFalse();
    }

    // ── Token estimation / proactive condensation tests ─────────────

    @Test
    void testEstimateTokenCount_messagesOnly() {
        ChatCompletion cc = new ChatCompletion();
        // 350 chars / 3.5 = 100 tokens
        cc.getMessages().add(new ChatMessage(ChatMessage.Role.assistant, "x".repeat(350)));

        int estimate = mapper.estimateTokenCount(cc);
        assertThat(estimate).isEqualTo(100);
    }

    @Test
    void testEstimateTokenCount_withInstructions() {
        ChatCompletion cc = new ChatCompletion();
        cc.setInstructions("y".repeat(175)); // 175 chars / 3.5 = 50 tokens
        cc.getMessages()
                .add(new ChatMessage(ChatMessage.Role.assistant, "x".repeat(350))); // 100 tokens

        int estimate = mapper.estimateTokenCount(cc);
        assertThat(estimate).isEqualTo(150);
    }

    @Test
    void testEstimateTokenCount_empty() {
        ChatCompletion cc = new ChatCompletion();
        assertThat(mapper.estimateTokenCount(cc)).isEqualTo(0);
    }

    @Test
    void testShouldCondenseProactively_belowThreshold() {
        ChatCompletion cc = new ChatCompletion();
        cc.getMessages()
                .add(
                        new ChatMessage(
                                ChatMessage.Role.assistant,
                                "x".repeat(400))); // ~114 tokens at 3.5 c/t
        // 128K context window, 0 maxTokens, 75% threshold = 96K. 114 tokens << 96K
        assertThat(mapper.shouldCondenseProactively(cc, 128_000, 0)).isFalse();
    }

    @Test
    void testShouldCondenseProactively_aboveThreshold() {
        ChatCompletion cc = new ChatCompletion();
        // 500K chars / 3.5 = ~142K tokens. Input budget = 128K. 142K > 128K → should condense
        cc.getMessages().add(new ChatMessage(ChatMessage.Role.assistant, "x".repeat(500_000)));
        assertThat(mapper.shouldCondenseProactively(cc, 128_000, 0)).isTrue();
    }

    @Test
    void testShouldCondenseProactively_exactlyAtBudget() {
        ChatCompletion cc = new ChatCompletion();
        // 128K tokens * 3.5 chars/token = 448K chars. Exactly at budget → should NOT condense
        // (strict >)
        cc.getMessages().add(new ChatMessage(ChatMessage.Role.assistant, "x".repeat(448_000)));
        assertThat(mapper.shouldCondenseProactively(cc, 128_000, 0)).isFalse();

        // One token over budget → should condense
        ChatCompletion cc2 = new ChatCompletion();
        cc2.getMessages()
                .add(
                        new ChatMessage(
                                ChatMessage.Role.assistant,
                                "x".repeat(448_004))); // +4 chars = +1 token
        assertThat(mapper.shouldCondenseProactively(cc2, 128_000, 0)).isTrue();
    }

    @Test
    void testShouldCondenseProactively_accountsForMaxTokens() {
        ChatCompletion cc = new ChatCompletion();
        // 200K chars / 3.5 = ~57K tokens. Input budget = 200K - 60K = 140K.
        // 57K < 140K → should NOT condense
        cc.getMessages().add(new ChatMessage(ChatMessage.Role.assistant, "x".repeat(200_000)));
        assertThat(mapper.shouldCondenseProactively(cc, 200_000, 60_000)).isFalse();

        // 600K chars / 3.5 = ~171K tokens. Input budget = 200K - 60K = 140K.
        // 171K > 140K → should condense
        ChatCompletion cc2 = new ChatCompletion();
        cc2.getMessages().add(new ChatMessage(ChatMessage.Role.assistant, "x".repeat(600_000)));
        assertThat(mapper.shouldCondenseProactively(cc2, 200_000, 60_000)).isTrue();
    }

    // ── Multi-round condensation tests ───────────────────────────────

    /**
     * Verifies that condensation can be applied three times in succession.
     *
     * <p>Simulates the server-side loop: 1. Agent accumulates many exchanges → condensation 1 fires
     * 2. Agent continues, accumulates more → condensation 2 fires 3. Agent continues again →
     * condensation 3 fires
     *
     * <p>Each round: feed condensed output + 10 new messages back into condenseHistory().
     */
    @Test
    void testCondensation_threeCycles() {
        // ── Round 1: 12 exchanges ──────────────────────────────────────────────
        List<ChatMessage> history1 = buildToolExchanges(12);
        List<ChatMessage> after1 = mapper.condenseHistory(history1);

        // 1 summary + 5 recent exchanges (2 msgs each) = 11
        assertThat(after1.get(0).getMessage()).contains("[Earlier conversation condensed]");
        assertThat(after1.get(0).getMessage()).contains("7 tool exchange(s)"); // 12 - 5 condensed
        int sizeAfter1 = after1.size();
        assertThat(sizeAfter1).isLessThan(history1.size());

        // ── Round 2: condensed output + 10 new exchanges ──────────────────────
        List<ChatMessage> history2 = new ArrayList<>(after1);
        history2.addAll(buildToolExchanges(10));
        List<ChatMessage> after2 = mapper.condenseHistory(history2);

        assertThat(after2.get(0).getMessage()).contains("[Earlier conversation condensed]");
        assertThat(after2.size()).isLessThan(history2.size());

        // ── Round 3: condensed output + 10 more new exchanges ─────────────────
        List<ChatMessage> history3 = new ArrayList<>(after2);
        history3.addAll(buildToolExchanges(10));
        List<ChatMessage> after3 = mapper.condenseHistory(history3);

        assertThat(after3.get(0).getMessage()).contains("[Earlier conversation condensed]");
        assertThat(after3.size()).isLessThan(history3.size());
        // Three consecutive condensation cycles must always converge to ≤ keepRecent exchanges +
        // summary
        assertThat(after3.size())
                .isLessThanOrEqualTo(1 + 5 * 2); // summary + 5 tool exchanges (2 msgs each)
    }

    @Test
    void testCondensation_recentExchangesConfigurable() {
        // Reconfigure to keep only 2 exchanges instead of 5
        mapper.setRecentExchangesToKeep(2);

        List<ChatMessage> history = buildToolExchanges(10); // 10 exchanges
        List<ChatMessage> result = mapper.condenseHistory(history);

        // Should keep: 1 summary + 2 recent exchanges (2 msgs each) = 5
        assertThat(result.get(0).getMessage()).contains("[Earlier conversation condensed]");
        assertThat(result.get(0).getMessage()).contains("8 tool exchange(s)"); // 10 - 2 condensed
        assertThat(result.size()).isEqualTo(5); // 1 summary + 2 * 2
    }

    @Test
    void testCondensation_stillOverBudgetAfterCondensation() {
        // Even after condensing to 5 recent exchanges, a tiny context window
        // may still be over budget. shouldCondenseProactively returns true for both
        // the large and the condensed version — the post-condensation warning path.
        ChatCompletion large = new ChatCompletion();
        large.getMessages().addAll(buildToolExchanges(30)); // very large history

        // Extremely small context window — 200 tokens
        assertThat(mapper.shouldCondenseProactively(large, 200, 0)).isTrue();

        // Even after condensation, the 5 kept exchanges would still exceed 200 tokens
        List<ChatMessage> condensed = mapper.condenseHistory(large.getMessages());
        ChatCompletion afterCondensation = new ChatCompletion();
        afterCondensation.getMessages().addAll(condensed);
        assertThat(mapper.shouldCondenseProactively(afterCondensation, 200, 0)).isTrue();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Build {@code n} tool exchanges (tool_call + tool_result message pairs), each with a ~300-char
     * tool output to simulate realistic context growth.
     */
    private List<ChatMessage> buildToolExchanges(int n) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ChatMessage toolCallMsg = new ChatMessage();
            toolCallMsg.setRole(ChatMessage.Role.tool_call);
            toolCallMsg.setToolCalls(
                    List.of(
                            ToolCall.builder()
                                    .name("search")
                                    .taskReferenceName("search_" + i)
                                    .build()));
            messages.add(toolCallMsg);

            String output =
                    "Search result for query " + i + ": " + "x".repeat(280); // ~300 chars total
            messages.add(
                    new ChatMessage(
                            ChatMessage.Role.tool,
                            ToolCall.builder()
                                    .name("search")
                                    .output(Map.of("result", output))
                                    .build()));
        }
        return messages;
    }

    // Use reflection to test private methods
    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeExtractResult(Map<String, Object> outputData)
            throws Exception {
        Method method =
                AgentChatCompleteTaskMapper.class.getDeclaredMethod(
                        "extractSubWorkflowResult", Map.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(mapper, outputData);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeExtractInput(Map<String, Object> inputData) throws Exception {
        Method method =
                AgentChatCompleteTaskMapper.class.getDeclaredMethod(
                        "extractSubWorkflowInput", Map.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(mapper, inputData);
    }
}
