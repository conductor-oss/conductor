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
package org.conductoross.conductor.ai.providers.openai.api;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the OpenAI Responses API ``reasoning`` parameter shape.
 *
 * <p>OpenAI's Responses API requires a nested {@code {"reasoning": {"effort": "..."}}} block on the
 * request body; the legacy flat {@code "reasoning_effort"} parameter is rejected with HTTP 400:
 * <em>"Unsupported parameter: 'reasoning_effort'. ... has moved to 'reasoning.effort'."</em>
 *
 * <p>An earlier version of {@link OpenAIResponsesApi.ResponseRequest} emitted the flat shape via
 * {@code @JsonProperty("reasoning_effort")}; the fix introduced a nested {@link
 * OpenAIResponsesApi.Reasoning} record. These tests pin the JSON shape so the bug cannot regress.
 */
class OpenAIResponsesApiTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reasoningEffortSerializesAsNestedReasoningBlock() throws Exception {
        OpenAIResponsesApi.ResponseRequest req =
                OpenAIResponsesApi.ResponseRequest.builder()
                        .model("gpt-5.3-codex")
                        .reasoningEffort("minimal")
                        .build();

        JsonNode json = mapper.valueToTree(req);

        assertTrue(json.has("reasoning"), "request body must carry a nested ``reasoning`` block");
        assertTrue(
                json.get("reasoning").has("effort"),
                "``reasoning`` block must carry an ``effort`` field");
        assertEquals("minimal", json.get("reasoning").get("effort").asText());

        // Hard guarantee against the historical bug: no flat ``reasoning_effort`` key
        // anywhere on the top-level body — that is exactly what the Responses API rejects.
        assertFalse(
                json.has("reasoning_effort"),
                "request body must NOT carry the legacy flat ``reasoning_effort`` field; "
                        + "OpenAI's Responses API rejects it. Saw: "
                        + json);
    }

    @Test
    void omittingReasoningEffortOmitsTheReasoningBlock() throws Exception {
        // When no reasoning_effort is configured (e.g. non-reasoning model), the
        // request body must omit the ``reasoning`` key entirely so OpenAI does not
        // see an empty {} (which it also rejects as invalid).
        OpenAIResponsesApi.ResponseRequest req =
                OpenAIResponsesApi.ResponseRequest.builder().model("gpt-4o").build();

        JsonNode json = mapper.valueToTree(req);

        assertFalse(
                json.has("reasoning"),
                "no reasoning_effort configured ⇒ ``reasoning`` block must be omitted");
        assertFalse(json.has("reasoning_effort"));
    }

    @Test
    void blankReasoningEffortOmitsTheReasoningBlock() throws Exception {
        // Blank/empty string is treated as "not set" by the Builder so the wire
        // shape stays clean.
        OpenAIResponsesApi.ResponseRequest req =
                OpenAIResponsesApi.ResponseRequest.builder()
                        .model("gpt-5.3-codex")
                        .reasoningEffort("")
                        .build();

        JsonNode json = mapper.valueToTree(req);
        assertFalse(json.has("reasoning"));
        assertFalse(json.has("reasoning_effort"));
    }

    @Test
    void allFourEffortLevelsRoundTripIntoTheNestedBlock() throws Exception {
        // OpenAI's documented effort levels for reasoning models.
        for (String level : new String[] {"minimal", "low", "medium", "high"}) {
            OpenAIResponsesApi.ResponseRequest req =
                    OpenAIResponsesApi.ResponseRequest.builder()
                            .model("o3")
                            .reasoningEffort(level)
                            .build();

            JsonNode json = mapper.valueToTree(req);
            assertEquals(
                    level,
                    json.path("reasoning").path("effort").asText(null),
                    "effort='" + level + "' must round-trip into reasoning.effort");
            assertFalse(json.has("reasoning_effort"), "level=" + level);
        }
    }

    @Test
    void reasoningRecordSerializesCorrectlyOnItsOwn() throws Exception {
        // Direct check of the Reasoning record so the nested shape is pinned even
        // without the surrounding ResponseRequest.
        OpenAIResponsesApi.Reasoning r = new OpenAIResponsesApi.Reasoning("high", null);
        String json = mapper.writeValueAsString(r);
        assertEquals("{\"effort\":\"high\"}", json);

        // Null effort/summary → fields omitted (NON_NULL).
        OpenAIResponsesApi.Reasoning empty = new OpenAIResponsesApi.Reasoning(null, null);
        assertEquals("{}", mapper.writeValueAsString(empty));

        // Both effort and summary serialize together.
        OpenAIResponsesApi.Reasoning both = new OpenAIResponsesApi.Reasoning("medium", "auto");
        JsonNode bothJson = mapper.readTree(mapper.writeValueAsString(both));
        assertEquals("medium", bothJson.get("effort").asText());
        assertEquals("auto", bothJson.get("summary").asText());
    }

    @Test
    void builderReasoningEffortAccessorIsStillAvailableForCallers() {
        // The Builder's public API is unchanged so callers compiled against the
        // earlier flat-field version do not need to change. Only the wire shape
        // changed.
        OpenAIResponsesApi.ResponseRequest req =
                OpenAIResponsesApi.ResponseRequest.builder().reasoningEffort("medium").build();
        assertEquals("medium", req.reasoning().effort());
        assertNull(req.reasoning().summary());
        assertNull(req.model());
    }

    @Test
    void reasoningSummarySerializesIntoNestedBlock() throws Exception {
        // The whole point of carrying ``summary`` on the request: gpt-5.x /
        // o-series models only emit chain-of-thought summaries on the response
        // when this field is set.
        OpenAIResponsesApi.ResponseRequest req =
                OpenAIResponsesApi.ResponseRequest.builder()
                        .model("gpt-5.3-codex")
                        .reasoningEffort("high")
                        .reasoningSummary("auto")
                        .build();

        JsonNode json = mapper.valueToTree(req);
        assertTrue(json.has("reasoning"));
        assertEquals("high", json.get("reasoning").get("effort").asText());
        assertEquals("auto", json.get("reasoning").get("summary").asText());
        // Still no flat parameter at the top level.
        assertFalse(json.has("reasoning_effort"));
        assertFalse(json.has("reasoning_summary"));
    }

    @Test
    void summaryWithoutEffortStillProducesReasoningBlock() throws Exception {
        // Caller wants summaries but accepts the model's default effort.
        OpenAIResponsesApi.ResponseRequest req =
                OpenAIResponsesApi.ResponseRequest.builder()
                        .model("o3")
                        .reasoningSummary("detailed")
                        .build();

        JsonNode json = mapper.valueToTree(req);
        assertTrue(json.has("reasoning"));
        assertFalse(
                json.get("reasoning").has("effort"),
                "effort omitted when not set; only summary should appear");
        assertEquals("detailed", json.get("reasoning").get("summary").asText());
    }

    @Test
    void blankReasoningSummaryOmitsTheReasoningBlockWhenEffortAlsoUnset() throws Exception {
        OpenAIResponsesApi.ResponseRequest req =
                OpenAIResponsesApi.ResponseRequest.builder()
                        .model("gpt-4o")
                        .reasoningSummary("")
                        .reasoningEffort("")
                        .build();

        JsonNode json = mapper.valueToTree(req);
        assertFalse(json.has("reasoning"));
    }

    @Test
    void blankPreviousResponseIdIsNormalizedToNullSoTheFieldIsOmitted() throws Exception {
        // OpenAI's Responses API rejects an empty-string previous_response_id with
        // HTTP 400: "Invalid 'previous_response_id': ''". The builder must coerce
        // "" and "   " to null so the field is dropped from the JSON entirely
        // (NON_NULL inclusion). Caught in a real workflow run during chain validation.
        for (String blank : new String[] {"", "   ", "\t\n"}) {
            OpenAIResponsesApi.ResponseRequest req =
                    OpenAIResponsesApi.ResponseRequest.builder()
                            .model("gpt-5-mini")
                            .previousResponseId(blank)
                            .build();

            assertNull(
                    req.previousResponseId(),
                    "blank previousResponseId='"
                            + blank.replace("\n", "\\n").replace("\t", "\\t")
                            + "' must be normalized to null");

            JsonNode json = mapper.valueToTree(req);
            assertFalse(
                    json.has("previous_response_id"),
                    "blank previousResponseId must be omitted from JSON; saw: " + json);
        }

        // Sanity: a real-looking response id passes through untouched.
        OpenAIResponsesApi.ResponseRequest req =
                OpenAIResponsesApi.ResponseRequest.builder()
                        .model("gpt-5-mini")
                        .previousResponseId("resp_0961ca71a07bb838006a0284fc31e481")
                        .build();
        JsonNode json = mapper.valueToTree(req);
        assertEquals(
                "resp_0961ca71a07bb838006a0284fc31e481", json.get("previous_response_id").asText());
    }

    @Test
    void outputItemDeserializesReasoningSummaryFromResponseJson() throws Exception {
        // The other half of the contract: when OpenAI sends a ``reasoning``
        // output item back with a populated ``summary`` array, our DTOs must
        // bind it. This pins the response-side wire shape.
        String responseJson =
                "{\n"
                        + "  \"id\": \"resp_test\",\n"
                        + "  \"object\": \"response\",\n"
                        + "  \"model\": \"gpt-5.3-codex\",\n"
                        + "  \"status\": \"completed\",\n"
                        + "  \"output\": [\n"
                        + "    {\n"
                        + "      \"type\": \"reasoning\",\n"
                        + "      \"id\": \"rs_1\",\n"
                        + "      \"summary\": [\n"
                        + "        {\"type\": \"summary_text\", \"text\": \"First thought.\"},\n"
                        + "        {\"type\": \"summary_text\", \"text\": \"Second thought.\"}\n"
                        + "      ]\n"
                        + "    },\n"
                        + "    {\n"
                        + "      \"type\": \"message\",\n"
                        + "      \"id\": \"msg_1\",\n"
                        + "      \"role\": \"assistant\",\n"
                        + "      \"content\": [{\"type\": \"output_text\", \"text\": \"Hello.\"}]\n"
                        + "    }\n"
                        + "  ],\n"
                        + "  \"usage\": {\n"
                        + "    \"input_tokens\": 10,\n"
                        + "    \"output_tokens\": 20,\n"
                        + "    \"total_tokens\": 30,\n"
                        + "    \"output_tokens_details\": {\"reasoning_tokens\": 17}\n"
                        + "  }\n"
                        + "}";

        OpenAIResponsesApi.ResponseResult result =
                mapper.readValue(responseJson, OpenAIResponsesApi.ResponseResult.class);

        assertEquals(2, result.output().size());
        OpenAIResponsesApi.OutputItem reasoning = result.output().get(0);
        assertEquals("reasoning", reasoning.type());
        assertEquals(2, reasoning.summary().size());
        assertEquals("First thought.", reasoning.summary().get(0).text());
        assertEquals("Second thought.", reasoning.summary().get(1).text());

        assertEquals(17, result.usage().outputTokensDetails().reasoningTokens());
    }
}
