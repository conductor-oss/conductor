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
package org.conductoross.conductor.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.model.EmbeddingGenRequest;
import org.conductoross.conductor.ai.model.LLMResponse;
import org.conductoross.conductor.ai.model.ToolCall;
import org.conductoross.conductor.ai.model.ToolSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImageModel;

import com.netflix.conductor.common.metadata.tasks.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of {@link LLMHelper#chatComplete(Task, AIModel, ChatCompletion, String,
 * java.util.function.Consumer)} using an in-process fake {@link AIModel} / {@link ChatModel}. The
 * existing {@link LLMHelperTest} pins the small pure helpers ({@code parseNestedJsonStrings},
 * {@code isJsonString}, {@code extractMethodFromInputParameters}, {@code
 * ensureLastMessageIsFromUser}); this file covers the larger code path around the chat round-trip —
 * reasoning / responseId extraction, tool-call assembly, finishReason mapping, JSON output parsing,
 * single-vs-multi generation reduction, and token-usage logging — without needing a live LLM.
 */
public class LLMHelperChatCompleteTest {

    private LLMHelper helper;

    @BeforeEach
    void setUp() {
        // ``storeMedia`` iterates over ``documentLoaders`` unconditionally, so a null list
        // would NPE before checking the location. An empty list is the right "no-op" wiring
        // for the unit-test path that never expects media to be stored.
        helper = new LLMHelper(null, new ArrayList<>());
    }

    private static Task task(String id) {
        Task t = new Task();
        t.setTaskId(id);
        return t;
    }

    /**
     * Most fake {@link AIModel}s in these tests only override {@link #getChatModel()}. Anything
     * that depends on provider-specific options ({@code getChatOptions}) falls back to the
     * interface default, which returns a {@code ToolCallingChatOptions} — exactly what production
     * adapters do for the generic path. That keeps each test focused on the helper logic rather
     * than provider plumbing.
     */
    static class FakeAIModel implements AIModel {
        private final ChatModel chatModel;

        FakeAIModel(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        @Override
        public String getModelProvider() {
            return "fake";
        }

        @Override
        public List<Float> generateEmbeddings(EmbeddingGenRequest req) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatModel getChatModel() {
            return chatModel;
        }

        @Override
        public ImageModel getImageModel() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Minimal {@link ChatModel} that returns a {@link ChatResponse} the test pre-stages. Optional
     * {@link #lastPrompt} capture lets assertions reach into the messages the helper actually built
     * — important for verifying instruction injection + {@code ensureLastMessageIsFromUser}.
     */
    static class StagedChatModel implements ChatModel {
        private ChatResponse staged;
        Prompt lastPrompt;

        void stage(ChatResponse response) {
            this.staged = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.lastPrompt = prompt;
            return staged;
        }
    }

    private static ChatResponse responseOf(
            AssistantMessage assistantMessage, String finishReason, ChatResponseMetadata metadata) {
        Generation g =
                new Generation(
                        assistantMessage,
                        ChatGenerationMetadata.builder().finishReason(finishReason).build());
        return new ChatResponse(List.of(g), metadata);
    }

    private static ChatResponseMetadata metaWithUsage(int prompt, int completion, int total) {
        return ChatResponseMetadata.builder()
                .usage(new DefaultUsage(prompt, completion, total))
                .build();
    }

    // =================================================================
    // chatComplete — happy path: single text result, no tools / reasoning
    // =================================================================

    @Test
    void chatComplete_singleTextResult_setsResultAndUsage_andLogsTokens() {
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(new AssistantMessage("Paris"), "stop", metaWithUsage(11, 22, 33)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.setInstructions("You are concise.");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "Capital of France?"));

        AtomicReference<TokenUsageLog> logged = new AtomicReference<>();

        LLMResponse out =
                helper.chatComplete(task("t1"), new FakeAIModel(model), in, null, logged::set);

        assertEquals("Paris", out.getResult(), "single response must be unwrapped to scalar");
        assertEquals("STOP", out.getFinishReason(), "finishReason 'stop' must uppercase to STOP");
        assertEquals(33, out.getTokenUsed());
        assertEquals(22, out.getCompletionTokens());
        assertEquals(11, out.getPromptTokens());
        assertNull(out.getReasoning());
        assertNull(out.getReasoningTokens());
        assertNull(out.getResponseId());
        assertNull(out.getToolCalls());

        // Token-usage logger callback must fire with the same accounting.
        TokenUsageLog log = logged.get();
        assertNotNull(log, "tokenUsageLogger consumer must be invoked");
        assertEquals("t1", log.getTaskId());
        assertEquals("fake-1", log.getApi());
        assertEquals("fake", log.getIntegrationName());
        assertEquals(33, log.getTotalTokens());

        // The helper prepended a system instruction; verify it survived to the chat model.
        Prompt sent = model.lastPrompt;
        assertNotNull(sent);
        List<Message> sentMessages = sent.getInstructions();
        assertTrue(
                sentMessages.stream().anyMatch(m -> "You are concise.".equals(m.getText())),
                "system instruction must reach the chat model");
    }

    // =================================================================
    // chatComplete — finishReason mapping for known synonyms
    // =================================================================

    @Test
    void chatComplete_finishReasonMap_endTurn_translatesToSTOP() {
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(new AssistantMessage("done"), "end_turn", metaWithUsage(1, 1, 2)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "ping"));

        LLMResponse out =
                helper.chatComplete(task("t2"), new FakeAIModel(model), in, null, x -> {});
        assertEquals("STOP", out.getFinishReason(), "Anthropic 'end_turn' must map to STOP");
    }

    @Test
    void chatComplete_finishReasonMap_refusal_translatesToCONTENT_FILTER() {
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(new AssistantMessage(""), "refusal", metaWithUsage(1, 0, 1)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "..."));

        LLMResponse out =
                helper.chatComplete(task("t3"), new FakeAIModel(model), in, null, x -> {});
        assertEquals(
                "CONTENT_FILTER",
                out.getFinishReason(),
                "'refusal' must map onto OpenAI-style CONTENT_FILTER");
    }

    // =================================================================
    // chatComplete — reasoning + responseId surfaced from metadata
    // =================================================================

    @Test
    void chatComplete_extractsReasoningAndResponseIdFromMetadata() {
        ChatResponseMetadata meta =
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(5, 10, 15))
                        .keyValue("response_id", "resp_abc")
                        .keyValue("reasoning", "Think hard, answer plainly.")
                        .keyValue("reasoning_tokens", 42)
                        .build();
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(new AssistantMessage("answer"), "stop", meta));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-reasoning");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "go"));

        LLMResponse out =
                helper.chatComplete(task("t4"), new FakeAIModel(model), in, null, x -> {});

        assertEquals("resp_abc", out.getResponseId());
        assertEquals("Think hard, answer plainly.", out.getReasoning());
        assertEquals(42, out.getReasoningTokens());
    }

    @Test
    void chatComplete_blankReasoning_isNormalizedToNull() {
        // The helper must treat blank/empty reasoning strings as absent — surfacing them onto
        // metadata["reasoning"] would lie to downstream code that uses non-null as the
        // "model returned a summary" signal.
        ChatResponseMetadata meta =
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(1, 1, 2))
                        .keyValue("reasoning", "   ")
                        .build();
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(new AssistantMessage("x"), "stop", meta));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "go"));

        LLMResponse out =
                helper.chatComplete(task("t5"), new FakeAIModel(model), in, null, x -> {});
        assertNull(out.getReasoning(), "blank reasoning must collapse to null");
    }

    @Test
    void chatComplete_reasoningTokensAcceptsLongFromMetadataRoundtrip() {
        // ChatResponseMetadata stores arbitrary values; Jackson round-trips can land an int as
        // a Long. The helper must accept any Number, not just Integer.
        ChatResponseMetadata meta =
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(1, 1, 2))
                        .keyValue("reasoning_tokens", 99L)
                        .build();
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(new AssistantMessage("x"), "stop", meta));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "go"));

        LLMResponse out =
                helper.chatComplete(task("t6"), new FakeAIModel(model), in, null, x -> {});
        assertEquals(99, out.getReasoningTokens());
    }

    // =================================================================
    // chatComplete — tool_use round-trip
    // =================================================================

    @Test
    void chatComplete_toolUseResponse_producesToolCallsAndMapsFinishReason() {
        // Model returns an assistant message with a tool call instead of text. The helper
        // must surface ``toolCalls`` on the LLMResponse, parse JSON arguments, attach a
        // "method" key, look up the ToolSpec by name (to pick up integrationNames + type),
        // and translate finishReason ``tool_use`` to TOOL_CALLS.
        AssistantMessage.ToolCall call =
                new AssistantMessage.ToolCall(
                        "call_1", "function", "get_weather", "{\"city\":\"Tokyo\"}");
        AssistantMessage assistant =
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(assistant, "tool_use", metaWithUsage(5, 5, 10)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "weather?"));
        ToolSpec spec = new ToolSpec();
        spec.setName("get_weather");
        spec.setType("HTTP");
        spec.setIntegrationNames(Map.of("weather_api", "openweathermap"));
        in.setTools(List.of(spec));

        LLMResponse out =
                helper.chatComplete(task("t7"), new FakeAIModel(model), in, null, x -> {});

        assertEquals("TOOL_CALLS", out.getFinishReason());
        assertNotNull(out.getToolCalls(), "tool calls must be surfaced");
        assertEquals(1, out.getToolCalls().size());
        ToolCall tc = out.getToolCalls().getFirst();
        assertEquals("get_weather", tc.getName());
        assertEquals("call_1", tc.getTaskReferenceName(), "ToolCall id must propagate");
        assertEquals("HTTP", tc.getType(), "type must come from the matching ToolSpec");
        assertEquals("Tokyo", tc.getInputParameters().get("city"));
        assertEquals(
                "get_weather",
                tc.getInputParameters().get("method"),
                "helper must inject the method name into the tool args");
        assertEquals(
                "openweathermap",
                tc.getIntegrationNames().get("weather_api"),
                "ToolSpec.integrationNames must be propagated");
    }

    @Test
    void chatComplete_toolUseWithBlankId_generatesUuidTaskReferenceName() {
        AssistantMessage.ToolCall call =
                new AssistantMessage.ToolCall("", "function", "noop", "{}");
        AssistantMessage assistant =
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(assistant, "tool_use", metaWithUsage(1, 1, 2)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "do nothing"));

        LLMResponse out =
                helper.chatComplete(task("t8"), new FakeAIModel(model), in, null, x -> {});
        assertNotNull(out.getToolCalls());
        String ref = out.getToolCalls().getFirst().getTaskReferenceName();
        assertNotNull(ref);
        // UUID.toString() form is 36 chars including 4 dashes.
        assertEquals(36, ref.length(), "blank tool-call id must be replaced by a UUID");
    }

    @Test
    void chatComplete_toolUseWithMalformedArgsJson_yieldsArgsWithOnlyMethodKey() {
        // Helper parses tool args via Jackson; a parse failure must be swallowed and the
        // args reduced to just the synthetic ``method`` entry rather than blowing up the
        // whole task.
        AssistantMessage.ToolCall call =
                new AssistantMessage.ToolCall("c1", "function", "noop", "not-json");
        AssistantMessage assistant =
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(assistant, "tool_use", metaWithUsage(1, 1, 2)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "noop"));

        LLMResponse out =
                helper.chatComplete(task("t9"), new FakeAIModel(model), in, null, x -> {});
        Map<String, Object> args = out.getToolCalls().getFirst().getInputParameters();
        assertEquals(1, args.size());
        assertEquals("noop", args.get("method"));
    }

    // =================================================================
    // chatComplete — JSON output path
    // =================================================================

    @Test
    void chatComplete_jsonOutputTrueAndModelReturnsJson_parsesIntoMap() {
        // jsonOutput=true is a hint to the LLM that drives extractResponse to parse text into
        // a Map. The helper must surface the parsed map as ``result`` (single response is
        // unwrapped to a single value).
        StagedChatModel model = new StagedChatModel();
        model.stage(
                responseOf(
                        new AssistantMessage("{\"name\":\"Conductor\",\"value\":42}"),
                        "stop",
                        metaWithUsage(1, 5, 6)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.setJsonOutput(true);
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "Return JSON."));

        LLMResponse out =
                helper.chatComplete(task("t10"), new FakeAIModel(model), in, null, x -> {});
        Object result = out.getResult();
        assertTrue(result instanceof Map, "json result must be parsed into a Map; was " + result);
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap = (Map<String, Object>) result;
        assertEquals("Conductor", asMap.get("name"));
        assertEquals(42, asMap.get("value"));
    }

    @Test
    void chatComplete_jsonFenced_codeFence_isStripped() {
        // Models often wrap JSON in ```json …``` fences. The helper must strip those before
        // parsing — otherwise downstream code receives the raw fenced string.
        StagedChatModel model = new StagedChatModel();
        model.stage(
                responseOf(
                        new AssistantMessage("```json\n{\"k\":1}\n```"),
                        "stop",
                        metaWithUsage(1, 5, 6)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.setJsonOutput(true);
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "Return JSON in a fence."));

        LLMResponse out =
                helper.chatComplete(task("t11"), new FakeAIModel(model), in, null, x -> {});
        assertTrue(
                out.getResult() instanceof Map,
                "fenced JSON must still parse cleanly; result was: " + out.getResult());
        assertEquals(1, ((Map<?, ?>) out.getResult()).get("k"));
    }

    @Test
    void chatComplete_jsonOutputFalse_nonJsonText_isReturnedVerbatim() {
        StagedChatModel model = new StagedChatModel();
        model.stage(
                responseOf(new AssistantMessage("hello, world"), "stop", metaWithUsage(1, 5, 6)));

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "Say hi."));

        LLMResponse out =
                helper.chatComplete(task("t12"), new FakeAIModel(model), in, null, x -> {});
        assertEquals("hello, world", out.getResult());
    }

    // =================================================================
    // chatComplete — message construction edge case (tool_call role)
    // =================================================================

    @Test
    void chatComplete_priorToolCallMessageInHistory_isForwardedAsAssistantWithToolCalls() {
        // Conductor's loop-history injection sometimes attaches a prior iteration's tool_call
        // frame followed by a user turn back into the next request. The helper must convert
        // that ChatMessage into a Spring AI AssistantMessage with toolCalls populated (rather
        // than dropping it or treating it as a user turn).
        //
        // Note we place a real user message *after* the tool_call frame so the assistant
        // frame isn't the last message — otherwise ``ensureLastMessageIsFromUser`` would
        // strip it (a behavior we cover separately in LLMHelperTest).
        StagedChatModel model = new StagedChatModel();
        model.stage(responseOf(new AssistantMessage("ok"), "stop", metaWithUsage(1, 1, 2)));

        ToolCall priorCall =
                ToolCall.builder()
                        .taskReferenceName("call_99")
                        .name("get_weather")
                        .inputParameters(
                                new HashMap<>(Map.of("city", "Tokyo", "method", "get_weather")))
                        .build();
        ChatMessage toolCallMsg = new ChatMessage(ChatMessage.Role.tool_call, priorCall);

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.setMessages(
                new ArrayList<>(
                        List.of(
                                new ChatMessage(ChatMessage.Role.user, "What's the weather?"),
                                toolCallMsg,
                                new ChatMessage(ChatMessage.Role.user, "Anything else?"))));

        // Calling the helper drives constructMessage(tool_call) via the public chatComplete.
        LLMResponse out =
                helper.chatComplete(task("t13"), new FakeAIModel(model), in, null, x -> {});
        assertNotNull(out);

        // Pick the assistant frame out of the sent prompt and verify the conversion worked.
        List<Message> sent = model.lastPrompt.getInstructions();
        AssistantMessage asst =
                sent.stream()
                        .filter(m -> m instanceof AssistantMessage)
                        .map(m -> (AssistantMessage) m)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "expected at least one AssistantMessage in sent"
                                                        + " prompt; was: "
                                                        + sent));
        assertTrue(
                asst.hasToolCalls(),
                "tool_call role must be converted into an AssistantMessage with toolCalls;"
                        + " got: "
                        + asst);
        AssistantMessage.ToolCall converted = asst.getToolCalls().getFirst();
        assertEquals("call_99", converted.id(), "ToolCall.taskReferenceName must become id");
        assertEquals(
                "get_weather",
                converted.name(),
                "tool name must surface either from the 'method' key or the ToolCall.name");
    }

    // =================================================================
    // chatComplete — empty response result-set
    // =================================================================

    @Test
    void chatComplete_emptyGenerationList_returnsSerializedResponseAsResult() {
        // Spring AI may emit a ChatResponse with zero generations (e.g. content-filter
        // refusal). The helper must not NPE on getFirst(); it should serialize the
        // ChatResponse JSON and surface that as a string ``result`` plus usage.
        StagedChatModel model = new StagedChatModel();
        ChatResponse empty = new ChatResponse(List.of(), metaWithUsage(2, 0, 2));
        model.stage(empty);

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "go"));

        AtomicReference<TokenUsageLog> logged = new AtomicReference<>();
        LLMResponse out =
                helper.chatComplete(task("t14"), new FakeAIModel(model), in, null, logged::set);
        assertNotNull(out.getResult());
        assertEquals(2, out.getTokenUsed());
        assertNotNull(logged.get(), "token-usage logger must still fire on empty-result path");
    }

    // =================================================================
    // chatComplete — null ChatResponse path
    // =================================================================

    @Test
    void chatComplete_chatClientReturnsNull_propagatesAsRuntimeException() {
        // Defensive contract: if the underlying ChatClient returns no response object at all,
        // the helper must raise a clear RuntimeException rather than silently producing an
        // LLMResponse with an empty/null result.
        StagedChatModel model = new StagedChatModel();
        model.stage(null);

        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "ping"));

        RuntimeException thrown =
                org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () ->
                                helper.chatComplete(
                                        task("t15"), new FakeAIModel(model), in, null, x -> {}));
        assertTrue(
                thrown.getMessage().contains("No response generated"),
                "expected the 'No response generated' guard; got: " + thrown.getMessage());
    }

    // =================================================================
    // generateEmbeddings — passthrough to the underlying AIModel
    // =================================================================

    @Test
    void generateEmbeddings_delegatesToAIModel() {
        // Helper is a thin shell around AIModel.generateEmbeddings — but the wrapper is still
        // worth pinning so a future refactor that adds caching / batching has a baseline
        // contract to compare against.
        List<Float> staged = List.of(0.1f, 0.2f, 0.3f);
        AIModel fake =
                new FakeAIModel(new StagedChatModel()) {
                    @Override
                    public List<Float> generateEmbeddings(EmbeddingGenRequest req) {
                        return staged;
                    }
                };
        EmbeddingGenRequest req = new EmbeddingGenRequest();
        req.setText("hello");
        List<Float> got = helper.generateEmbeddings(task("t16"), fake, req, x -> {});
        assertSame(staged, got);
    }
}
