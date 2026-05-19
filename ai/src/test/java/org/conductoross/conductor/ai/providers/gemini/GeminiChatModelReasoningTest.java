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
package org.conductoross.conductor.ai.providers.gemini;

import java.util.List;

import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GeminiChatModel#toSpringChatResponse(GeminiApi.GenerateContentResponse, String)} —
 * the response-parsing path that lifts Gemini "thought" parts onto {@code
 * ChatResponseMetadata["reasoning"]} and the {@code thoughtsTokenCount} usage field onto {@code
 * ChatResponseMetadata["reasoning_tokens"]}.
 *
 * <p>We exercise the response-parsing logic directly via the package-private method by constructing
 * {@link GeminiApi.GenerateContentResponse} records directly.
 */
class GeminiChatModelReasoningTest {

    private static GeminiChatModel newChatModel() {
        // api is only used inside call() which we don't exercise here.
        // null is safe for these tests.
        return new GeminiChatModel(null);
    }

    private static GeminiApi.Part thoughtPart(String text) {
        return new GeminiApi.Part(text, null, null, null, true);
    }

    private static GeminiApi.Part textPart(String text) {
        return GeminiApi.Part.text(text);
    }

    private static GeminiApi.Content content(GeminiApi.Part... parts) {
        return new GeminiApi.Content("model", List.of(parts));
    }

    private static GeminiApi.GenerateContentResponse responseWith(
            List<GeminiApi.Part> parts, Integer thoughtsTokenCount) {
        GeminiApi.Candidate candidate =
                new GeminiApi.Candidate(content(parts.toArray(new GeminiApi.Part[0])), "STOP");
        GeminiApi.UsageMetadata usage =
                new GeminiApi.UsageMetadata(8, 20, thoughtsTokenCount);
        return new GeminiApi.GenerateContentResponse(List.of(candidate), usage, "resp_xyz");
    }

    @Test
    void thoughtPartsAreConcatenatedIntoReasoningMetadata() {
        GeminiApi.GenerateContentResponse response =
                responseWith(
                        List.of(
                                thoughtPart("Consider option A."),
                                thoughtPart("Compare with option B."),
                                textPart("Final: B.")),
                        45);

        ChatResponse chat = newChatModel().toSpringChatResponse(response, "gemini-2.5-pro");

        // Visible text is only the non-thought part — result.text() filters thoughts.
        assertEquals("Final: B.", chat.getResult().getOutput().getText());

        Object reasoning = chat.getMetadata().get("reasoning");
        assertNotNull(reasoning, "thought parts must surface on metadata['reasoning']");
        String reasoningText = reasoning.toString();
        assertTrue(reasoningText.contains("Consider option A."));
        assertTrue(reasoningText.contains("Compare with option B."));
        assertEquals(
                "Consider option A.\n\nCompare with option B.",
                reasoningText,
                "thoughts must be joined with \\n\\n in order");

        // thoughtsTokenCount surfaces as reasoning_tokens.
        assertEquals(Integer.valueOf(45), chat.getMetadata().get("reasoning_tokens"));
        // response_id (Gemini calls it that too) propagates as id.
        assertEquals("resp_xyz", chat.getMetadata().getId());
    }

    @Test
    void noThoughtParts_metadataOmitsReasoningKey() {
        GeminiApi.GenerateContentResponse response = responseWith(List.of(textPart("Plain answer.")), null);

        ChatResponse chat = newChatModel().toSpringChatResponse(response, "gemini-2.5-flash");

        assertEquals("Plain answer.", chat.getResult().getOutput().getText());
        assertNull(
                chat.getMetadata().get("reasoning"),
                "no thought parts => metadata['reasoning'] must be absent");
        assertNull(
                chat.getMetadata().get("reasoning_tokens"),
                "no thoughtsTokenCount in usage => metadata['reasoning_tokens'] must be absent");
    }

    @Test
    void thoughtsTokenCount_surfacesEvenWithoutThoughtSummaryText() {
        // Caller may set ``thinking_budget`` (reasoning under the hood) without
        // asking for summaries. Gemini bills the reasoning tokens regardless,
        // and we want that visible to operators even when no summary text exists.
        GeminiApi.GenerateContentResponse response = responseWith(List.of(textPart("Answer.")), 12);

        ChatResponse chat = newChatModel().toSpringChatResponse(response, "gemini-2.5-flash");

        assertEquals("Answer.", chat.getResult().getOutput().getText());
        assertNull(chat.getMetadata().get("reasoning"));
        assertEquals(Integer.valueOf(12), chat.getMetadata().get("reasoning_tokens"));
    }

    @Test
    void blankThoughtTextIsIgnored() {
        GeminiApi.GenerateContentResponse response =
                responseWith(
                        List.of(thoughtPart("Real thought."), thoughtPart("   "), textPart("Out.")),
                        7);

        ChatResponse chat = newChatModel().toSpringChatResponse(response, "gemini-2.5-pro");

        assertEquals(
                "Real thought.",
                chat.getMetadata().get("reasoning"),
                "blank thought text must not contribute or introduce orphan \\n\\n");
    }

    @Test
    void multipleCandidates_thoughtsFromAllAreCollected() {
        // Gemini can return more than one candidate; reasoning parts from each
        // candidate's content should all flow through. (Spring AI's ChatResponse
        // model still gets a single generation in the existing code path because
        // result.text() merges, but reasoning should not be dropped on the floor
        // for non-first candidates.)
        GeminiApi.Candidate c1 =
                new GeminiApi.Candidate(
                        new GeminiApi.Content(
                                "model",
                                List.of(
                                        thoughtPart("Cand 1 thought."),
                                        textPart("A."))),
                        "STOP");
        GeminiApi.Candidate c2 =
                new GeminiApi.Candidate(
                        new GeminiApi.Content(
                                "model",
                                List.of(thoughtPart("Cand 2 thought."))),
                        "STOP");
        GeminiApi.GenerateContentResponse response =
                new GeminiApi.GenerateContentResponse(
                        List.of(c1, c2),
                        new GeminiApi.UsageMetadata(1, 1, 3),
                        "resp_multi");

        ChatResponse chat = newChatModel().toSpringChatResponse(response, "gemini-2.5-pro");

        String reasoning = (String) chat.getMetadata().get("reasoning");
        assertNotNull(reasoning);
        assertTrue(reasoning.contains("Cand 1 thought."));
        assertTrue(reasoning.contains("Cand 2 thought."));
    }
}
