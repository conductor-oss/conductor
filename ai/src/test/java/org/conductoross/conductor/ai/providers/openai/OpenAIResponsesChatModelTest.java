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
package org.conductoross.conductor.ai.providers.openai;

import java.util.List;

import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.OutputContent;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.OutputItem;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.OutputTokensDetails;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.ReasoningSummary;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.ResponseRequest;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.ResponseResult;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIResponsesApi.Usage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests for the {@link OpenAIResponsesChatModel} reasoning round-trip: the request must
 * carry a nested {@code reasoning.summary} when the caller asked for one, and the response's
 * reasoning summary text must be surfaced via {@code ChatResponseMetadata["reasoning"]} so it can
 * flow downstream to {@code LLMResponse}.
 *
 * <p>The HTTP layer is stubbed via Mockito so the test is deterministic and runs in CI without
 * network access — only the marshalling and parsing in {@link OpenAIResponsesChatModel} is
 * exercised.
 */
class OpenAIResponsesChatModelTest {

    @Test
    void requestCarriesReasoningSummaryAndResponseExposesReasoningTextOnMetadata()
            throws Exception {
        OpenAIResponsesApi api = mock(OpenAIResponsesApi.class);

        ResponseResult canned =
                new ResponseResult(
                        "resp_test_123",
                        "response",
                        "gpt-5.3-codex",
                        "completed",
                        List.of(
                                new OutputItem(
                                        "reasoning",
                                        "rs_1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of(
                                                new ReasoningSummary(
                                                        "summary_text",
                                                        "Considered approach A then chose B."),
                                                new ReasoningSummary(
                                                        "summary_text",
                                                        "Verified by simulating the loop."))),
                                new OutputItem(
                                        "message",
                                        "msg_1",
                                        "assistant",
                                        List.of(
                                                new OutputContent(
                                                        "output_text", "The answer is B.", null)),
                                        "completed",
                                        null,
                                        null,
                                        null,
                                        null)),
                        null,
                        new Usage(8, 42, 50, new OutputTokensDetails(31)),
                        null,
                        null,
                        null,
                        null);

        when(api.createResponse(any(ResponseRequest.class))).thenReturn(canned);

        OpenAIResponsesChatModel chatModel = new OpenAIResponsesChatModel(api);

        OpenAIResponsesChatOptions options =
                OpenAIResponsesChatOptions.builder()
                        .model("gpt-5.3-codex")
                        .reasoningEffort("high")
                        .reasoningSummary("auto")
                        .build();
        Prompt prompt = new Prompt("Pick A or B.", options);

        ChatResponse response = chatModel.call(prompt);

        // Capture the actual request the chat model built and confirm the nested
        // reasoning block reached the API layer with both effort and summary.
        ArgumentCaptor<ResponseRequest> reqCaptor = ArgumentCaptor.forClass(ResponseRequest.class);
        verify(api).createResponse(reqCaptor.capture());
        ResponseRequest sentRequest = reqCaptor.getValue();
        assertNotNull(sentRequest.reasoning(), "reasoning block must be set on the request");
        assertEquals("high", sentRequest.reasoning().effort());
        assertEquals("auto", sentRequest.reasoning().summary());

        // The visible message text comes back as the generation output.
        assertEquals("The answer is B.", response.getResult().getOutput().getText());

        // Reasoning summaries get concatenated into a single metadata field
        // so downstream consumers can show "what the model was thinking".
        Object reasoning = response.getMetadata().get("reasoning");
        assertNotNull(reasoning, "metadata['reasoning'] must be set when summaries are returned");
        String reasoningText = reasoning.toString();
        assertTrue(
                reasoningText.contains("Considered approach A then chose B."),
                "first summary must be present: " + reasoningText);
        assertTrue(
                reasoningText.contains("Verified by simulating the loop."),
                "second summary must be present: " + reasoningText);

        // Reasoning token count from output_tokens_details propagates too.
        assertEquals(Integer.valueOf(31), response.getMetadata().get("reasoning_tokens"));

        // And the response_id is captured for previous_response_id chaining.
        assertEquals("resp_test_123", response.getMetadata().get("response_id"));
    }

    @Test
    void noReasoningSummaryRequested_metadataOmitsReasoningKey() throws Exception {
        // When the caller did not opt in (no reasoningSummary on options) and the
        // response carries no reasoning summary text, metadata['reasoning'] must
        // be absent — we do not write empty strings.
        OpenAIResponsesApi api = mock(OpenAIResponsesApi.class);
        ResponseResult canned =
                new ResponseResult(
                        "resp_test_456",
                        "response",
                        "gpt-4o-mini",
                        "completed",
                        List.of(
                                new OutputItem(
                                        "message",
                                        "msg_1",
                                        "assistant",
                                        List.of(
                                                new OutputContent(
                                                        "output_text", "Plain answer.", null)),
                                        "completed",
                                        null,
                                        null,
                                        null,
                                        null)),
                        null,
                        new Usage(5, 3, 8, null),
                        null,
                        null,
                        null,
                        null);
        when(api.createResponse(any(ResponseRequest.class))).thenReturn(canned);

        OpenAIResponsesChatModel chatModel = new OpenAIResponsesChatModel(api);
        OpenAIResponsesChatOptions options =
                OpenAIResponsesChatOptions.builder().model("gpt-4o-mini").build();
        Prompt prompt = new Prompt("hi", options);

        ChatResponse response = chatModel.call(prompt);

        assertEquals("Plain answer.", response.getResult().getOutput().getText());
        assertTrue(
                response.getMetadata().get("reasoning") == null,
                "no summaries returned ⇒ metadata['reasoning'] must be absent / null");
    }
}
