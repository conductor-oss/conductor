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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GeminiChatModel#toSpringChatResponse(GenerateContentResponse, String)} —
 * the response-parsing path that lifts Gemini "thought" parts onto {@code
 * ChatResponseMetadata["reasoning"]} and the {@code thoughtsTokenCount} usage field onto {@code
 * ChatResponseMetadata["reasoning_tokens"]}.
 *
 * <p>The Google GenAI {@link com.google.genai.Client} is a concrete class with public field access,
 * so we exercise the response-parsing logic directly via the package-private method rather than
 * mocking the HTTP transport. The conversion is the part that diverges across providers; the SDK
 * call shape is uninteresting.
 */
class GeminiChatModelReasoningTest {

    private static GeminiChatModel newChatModel() {
        // Client is only used inside ``call()`` which we don't exercise here.
        // Constructor accepts the field as-is; null is safe for these tests.
        return new GeminiChatModel(null);
    }

    private static Part thoughtPart(String text) {
        return Part.builder().thought(true).text(text).build();
    }

    private static Part textPart(String text) {
        return Part.builder().text(text).build();
    }

    private static Content content(Part... parts) {
        return Content.builder().role("model").parts(List.of(parts)).build();
    }

    private static GenerateContentResponse responseWith(
            List<Part> parts, Integer thoughtsTokenCount) {
        Candidate candidate =
                Candidate.builder().content(content(parts.toArray(new Part[0]))).build();
        GenerateContentResponseUsageMetadata.Builder usage =
                GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(8)
                        .candidatesTokenCount(20);
        if (thoughtsTokenCount != null) {
            usage.thoughtsTokenCount(thoughtsTokenCount);
        }
        return GenerateContentResponse.builder()
                .responseId("resp_xyz")
                .candidates(candidate)
                .usageMetadata(usage.build())
                .build();
    }

    @Test
    void thoughtPartsAreConcatenatedIntoReasoningMetadata() {
        GenerateContentResponse response =
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
        GenerateContentResponse response = responseWith(List.of(textPart("Plain answer.")), null);

        ChatResponse chat = newChatModel().toSpringChatResponse(response, "gemini-2.5-flash");

        assertEquals("Plain answer.", chat.getResult().getOutput().getText());
        assertNull(
                chat.getMetadata().get("reasoning"),
                "no thought parts ⇒ metadata['reasoning'] must be absent");
        assertNull(
                chat.getMetadata().get("reasoning_tokens"),
                "no thoughtsTokenCount in usage ⇒ metadata['reasoning_tokens'] must be absent");
    }

    @Test
    void thoughtsTokenCount_surfacesEvenWithoutThoughtSummaryText() {
        // Caller may set ``thinking_budget`` (reasoning under the hood) without
        // asking for summaries. Gemini bills the reasoning tokens regardless,
        // and we want that visible to operators even when no summary text exists.
        GenerateContentResponse response = responseWith(List.of(textPart("Answer.")), 12);

        ChatResponse chat = newChatModel().toSpringChatResponse(response, "gemini-2.5-flash");

        assertEquals("Answer.", chat.getResult().getOutput().getText());
        assertNull(chat.getMetadata().get("reasoning"));
        assertEquals(Integer.valueOf(12), chat.getMetadata().get("reasoning_tokens"));
    }

    @Test
    void blankThoughtTextIsIgnored() {
        GenerateContentResponse response =
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
        Candidate c1 =
                Candidate.builder()
                        .content(
                                Content.builder()
                                        .role("model")
                                        .parts(
                                                List.of(
                                                        thoughtPart("Cand 1 thought."),
                                                        textPart("A.")))
                                        .build())
                        .build();
        Candidate c2 =
                Candidate.builder()
                        .content(
                                Content.builder()
                                        .role("model")
                                        .parts(List.of(thoughtPart("Cand 2 thought.")))
                                        .build())
                        .build();
        GenerateContentResponse response =
                GenerateContentResponse.builder()
                        .responseId("resp_multi")
                        .candidates(c1, c2)
                        .usageMetadata(
                                GenerateContentResponseUsageMetadata.builder()
                                        .promptTokenCount(1)
                                        .candidatesTokenCount(1)
                                        .thoughtsTokenCount(3)
                                        .build())
                        .build();

        ChatResponse chat = newChatModel().toSpringChatResponse(response, "gemini-2.5-pro");

        String reasoning = (String) chat.getMetadata().get("reasoning");
        assertNotNull(reasoning);
        assertTrue(reasoning.contains("Cand 1 thought."));
        assertTrue(reasoning.contains("Cand 2 thought."));
    }
}
