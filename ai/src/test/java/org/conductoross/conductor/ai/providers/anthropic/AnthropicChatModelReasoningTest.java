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
package org.conductoross.conductor.ai.providers.anthropic;

import java.util.List;

import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.MessagesRequest;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.MessagesResponse;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ResponseContentBlock;
import org.conductoross.conductor.ai.providers.anthropic.api.AnthropicMessagesApi.ResponseUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests for the {@link AnthropicChatModel} extended-thinking round-trip: when the caller
 * asks for reasoning via {@code reasoningSummary} on {@link AnthropicChatOptions}, thinking content
 * blocks returned in the response must be concatenated onto {@code
 * ChatResponseMetadata["reasoning"]}.
 *
 * <p>The HTTP layer is stubbed via Mockito; only the request building and response parsing in
 * {@link AnthropicChatModel} are exercised.
 */
class AnthropicChatModelReasoningTest {

    private static ResponseContentBlock textBlock(String text) {
        return new ResponseContentBlock("text", text, null, null, null, null, null, null);
    }

    private static ResponseContentBlock thinkingBlock(String thinking) {
        return new ResponseContentBlock(
                "thinking", null, null, null, null, thinking, "sig_" + thinking.hashCode(), null);
    }

    @Test
    void reasoningSummarySetAndThinkingPresent_surfacesOnMetadata() throws Exception {
        AnthropicMessagesApi api = mock(AnthropicMessagesApi.class);
        MessagesResponse canned =
                new MessagesResponse(
                        "msg_abc",
                        "message",
                        "assistant",
                        List.of(
                                thinkingBlock("First let me think about this carefully."),
                                thinkingBlock("On reflection, the answer is B."),
                                textBlock("The answer is B.")),
                        "claude-sonnet-4-5",
                        "end_turn",
                        null,
                        new ResponseUsage(10, 50, null, null));
        when(api.createMessage(any(MessagesRequest.class))).thenReturn(canned);

        AnthropicChatModel chatModel = new AnthropicChatModel(api);

        AnthropicChatOptions options =
                AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-5")
                        .maxTokens(1024)
                        .thinkingBudgetTokens(2000)
                        .reasoningSummary("auto")
                        .build();
        ChatResponse response = chatModel.call(new Prompt("Pick A or B.", options));

        // The visible text comes back as the generation output, thinking is filtered out.
        assertEquals("The answer is B.", response.getResult().getOutput().getText());

        // Thinking blocks get concatenated with \n\n into the reasoning metadata key.
        Object reasoning = response.getMetadata().get("reasoning");
        assertNotNull(reasoning, "thinking blocks must surface on metadata['reasoning']");
        String reasoningText = reasoning.toString();
        assertTrue(
                reasoningText.contains("First let me think about this carefully."),
                "first thinking block must appear: " + reasoningText);
        assertTrue(
                reasoningText.contains("On reflection, the answer is B."),
                "second thinking block must appear: " + reasoningText);

        // Anthropic does not break out a reasoning_tokens counter — thinking is
        // billed under output_tokens — so we must NOT write that key. Lying about
        // it would be worse than omitting.
        assertNull(
                response.getMetadata().get("reasoning_tokens"),
                "Anthropic has no separate reasoning_tokens counter; key must be absent");
    }

    @Test
    void reasoningSummaryNotSet_thinkingIsDropped() throws Exception {
        // Without an explicit opt-in, the response stays clean — even if the
        // model returns thinking blocks. Matches the OpenAI/Gemini gate: the
        // reasoningSummary field is the universal "I want to see the chain
        // of thought" flag across providers.
        AnthropicMessagesApi api = mock(AnthropicMessagesApi.class);
        MessagesResponse canned =
                new MessagesResponse(
                        "msg_def",
                        "message",
                        "assistant",
                        List.of(thinkingBlock("Ignored chain of thought."), textBlock("Hi.")),
                        "claude-sonnet-4-5",
                        "end_turn",
                        null,
                        new ResponseUsage(5, 4, null, null));
        when(api.createMessage(any(MessagesRequest.class))).thenReturn(canned);

        AnthropicChatModel chatModel = new AnthropicChatModel(api);
        AnthropicChatOptions options =
                AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-5")
                        .maxTokens(1024)
                        .thinkingBudgetTokens(2000)
                        // reasoningSummary intentionally unset
                        .build();

        ChatResponse response = chatModel.call(new Prompt("hi", options));

        assertEquals("Hi.", response.getResult().getOutput().getText());
        assertNull(
                response.getMetadata().get("reasoning"),
                "no reasoningSummary opt-in ⇒ thinking blocks must NOT leak onto metadata");
    }

    @Test
    void reasoningSummarySet_butNoThinkingBlocksReturned_metadataAbsent() throws Exception {
        // If the model returned no thinking blocks at all, we must not write an
        // empty reasoning entry. Symmetric with the OpenAI behavior.
        AnthropicMessagesApi api = mock(AnthropicMessagesApi.class);
        MessagesResponse canned =
                new MessagesResponse(
                        "msg_ghi",
                        "message",
                        "assistant",
                        List.of(textBlock("Plain answer.")),
                        "claude-sonnet-4-5",
                        "end_turn",
                        null,
                        new ResponseUsage(5, 3, null, null));
        when(api.createMessage(any(MessagesRequest.class))).thenReturn(canned);

        AnthropicChatModel chatModel = new AnthropicChatModel(api);
        AnthropicChatOptions options =
                AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-5")
                        .maxTokens(1024)
                        .reasoningSummary("detailed")
                        .build();

        ChatResponse response = chatModel.call(new Prompt("hi", options));

        assertEquals("Plain answer.", response.getResult().getOutput().getText());
        assertNull(
                response.getMetadata().get("reasoning"),
                "no thinking blocks ⇒ reasoning metadata must be absent");
    }

    @Test
    void blankThinkingTextDoesNotProduceTrailingDelimiter() throws Exception {
        // Edge case: a thinking block with blank text must not contribute to
        // the reasoning string or introduce an orphaned "\n\n" delimiter.
        AnthropicMessagesApi api = mock(AnthropicMessagesApi.class);
        MessagesResponse canned =
                new MessagesResponse(
                        "msg_jkl",
                        "message",
                        "assistant",
                        List.of(
                                thinkingBlock("Real thought."),
                                new ResponseContentBlock(
                                        "thinking", null, null, null, null, "  ", "sig", null),
                                textBlock("Out.")),
                        "claude-sonnet-4-5",
                        "end_turn",
                        null,
                        new ResponseUsage(5, 5, null, null));
        when(api.createMessage(any(MessagesRequest.class))).thenReturn(canned);

        AnthropicChatModel chatModel = new AnthropicChatModel(api);
        AnthropicChatOptions options =
                AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-5")
                        .maxTokens(512)
                        .reasoningSummary("auto")
                        .build();

        ChatResponse response = chatModel.call(new Prompt("hi", options));

        String reasoning = (String) response.getMetadata().get("reasoning");
        assertEquals(
                "Real thought.", reasoning, "blank thinking must be ignored, not concatenated");
    }
}
