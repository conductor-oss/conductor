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
package org.conductoross.conductor.ai.recording;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ToolSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Normalizes transport fields and verified generated tool summaries, preserving user payloads.
 * Create one instance per request to normalize IDs from its full history.
 */
public final class RecordedRequestNormalizer {
    private static final String FUNCTION_TOOL_TYPE = "function";
    private static final Set<String> TRANSPORT_RESULT_NAMES =
            Set.of(
                    "CALL_MCP_TOOL",
                    "GET",
                    "HEAD",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "OPTIONS",
                    "TRACE",
                    "CONNECT");

    /** An omitted tool schema describes an object with no declared parameters. */
    private static final Map<String, String> DEFAULT_TOOL_INPUT_SCHEMA = Map.of("type", "object");

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final Set<String> VOLATILE_HTTP_HEADERS =
            Set.of(
                    "date",
                    "x-request-id",
                    "x-github-request-id",
                    "x-github-edge-region",
                    "x-ratelimit-remaining",
                    "x-ratelimit-used",
                    "x-ratelimit-reset");
    private static final String TOOL_RESULTS_START = "[TOOL RESULTS]\n";
    private static final String TOOL_RESULTS_END = "\n[/TOOL RESULTS]";
    // stateMergeScript removes the final task index from tool reference names when a worker does
    // not supply a tool name.
    private static final Pattern GENERATED_RESULT_NAME =
            Pattern.compile(
                    "(?:call_[A-Za-z0-9]+|[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}_[0-9]+)_");

    private final Map<String, CallIdentity> callIdentities = new HashMap<>();

    private record CallIdentity(String reference, String name) {}

    public record RequestOptions(
            boolean jsonOutput,
            JsonNode outputSchema,
            List<LLMRecording.Tool> tools,
            LLMRecording.GenerationOptions generationOptions) {}

    public static RequestOptions options(ChatCompletion input) {
        if (StringUtils.isNotBlank(input.getPreviousResponseId())) {
            throw new IllegalArgumentException(
                    "LLM recordings require full history; previousResponseId is unsupported");
        }
        if (input.isWebSearch()
                || input.isCodeInterpreter()
                || input.isGoogleSearchRetrieval()
                || ObjectUtils.isNotEmpty(input.getFileSearchVectorStoreIds())) {
            throw new IllegalArgumentException(
                    "Provider-native tools are unsupported in LLM recordings");
        }
        // Providers with custom ChatOptions carry these same Conductor tool definitions.
        // Snapshot them now so later task mutations cannot change this call's recording.
        List<LLMRecording.Tool> tools = new ArrayList<>();
        if (input.getTools() != null) {
            for (ToolSpec tool : input.getTools()) {
                tools.add(
                        new LLMRecording.Tool(
                                tool.getName(),
                                tool.getDescription(),
                                tool.getInputSchema() == null
                                        ? MAPPER.valueToTree(DEFAULT_TOOL_INPUT_SCHEMA)
                                        : MAPPER.valueToTree(tool.getInputSchema())));
            }
        }
        return new RequestOptions(
                input.isJsonOutput(),
                MAPPER.valueToTree(input.getOutputSchema()),
                List.copyOf(tools),
                new LLMRecording.GenerationOptions(
                        input.getTemperature(),
                        input.getTopP(),
                        input.getTopK(),
                        input.getFrequencyPenalty(),
                        input.getPresencePenalty(),
                        input.getStopWords(),
                        input.getMaxTokens(),
                        input.getThinkingTokenLimit(),
                        input.getReasoningEffort(),
                        input.getReasoningSummary()));
    }

    public LLMRecording.Request normalize(Prompt prompt, RequestOptions input) {
        List<LLMRecording.Message> messages =
                prompt.getInstructions().stream().map(this::toSavedMessage).toList();
        List<LLMRecording.Tool> tools = input.tools();
        // Prefer resolved callbacks when the provider exposes them through Spring AI options.
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            tools = new ArrayList<>();
            if (ObjectUtils.isNotEmpty(options.getToolNames())) {
                throw new IllegalArgumentException(
                        "LLM recordings require resolved tool definitions");
            }
            if (Boolean.TRUE.equals(options.getInternalToolExecutionEnabled())) {
                throw new IllegalArgumentException(
                        "LLM recordings require external tool execution");
            }
            if (ObjectUtils.isNotEmpty(options.getToolCallbacks())) {
                for (ToolCallback callback : options.getToolCallbacks()) {
                    ToolDefinition definition = callback.getToolDefinition();
                    tools.add(
                            new LLMRecording.Tool(
                                    definition.name(),
                                    definition.description(),
                                    toolInputSchema(definition.inputSchema())));
                }
            }
        }
        return normalizeTransportHistory(
                new LLMRecording.Request(
                        messages,
                        tools,
                        input.jsonOutput(),
                        input.outputSchema(),
                        input.generationOptions()));
    }

    private LLMRecording.Message toSavedMessage(Message message) {
        if (message instanceof MediaContent media && ObjectUtils.isNotEmpty(media.getMedia())) {
            throw new IllegalArgumentException("Media is unsupported in LLM recordings");
        }
        List<LLMRecording.ToolCall> calls = new ArrayList<>();
        List<LLMRecording.ToolResult> results = new ArrayList<>();
        if (message instanceof AssistantMessage assistant) {
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                Validate.isTrue(
                        FUNCTION_TOOL_TYPE.equals(call.type()),
                        "Only function tool calls are supported in LLM recordings");
                calls.add(
                        new LLMRecording.ToolCall(
                                reference(call.id(), call.name()),
                                call.name(),
                                parseObject(call.arguments(), "tool arguments")));
            }
        } else if (message instanceof ToolResponseMessage tool) {
            for (ToolResponseMessage.ToolResponse result : tool.getResponses()) {
                CallIdentity call = callIdentities.get(result.id());
                Validate.isTrue(
                        call != null
                                && (call.name().equals(result.name())
                                        || (result.name() != null
                                                && TRANSPORT_RESULT_NAMES.contains(result.name()))),
                        "Tool result has no matching call in the recorded history");
                // MCP/HTTP history can label results with a task type or HTTP method. Match by
                // the existing call ID and retain its function name for recording/playback.
                results.add(
                        new LLMRecording.ToolResult(
                                call.reference(), call.name(), parseResult(result.responseData())));
            }
        }
        return new LLMRecording.Message(
                message.getMessageType().getValue(), message.getText(), calls, results);
    }

    /** Applies the same matching rules to legacy saved requests and newly recorded requests. */
    public static LLMRecording.Request normalizeTransportHistory(LLMRecording.Request request) {
        Map<String, Integer> turns = new HashMap<>();
        List<String> callOrder = new ArrayList<>();
        Map<String, LLMRecording.ToolResult> results = new HashMap<>();
        int turn = 0;
        for (LLMRecording.Message message : request.messages()) {
            if (!message.toolCalls().isEmpty()) {
                for (LLMRecording.ToolCall call : message.toolCalls()) {
                    turns.put(call.reference(), turn);
                    callOrder.add(call.reference());
                }
                turn++;
            }
            for (LLMRecording.ToolResult result : message.toolResults()) {
                results.put(result.reference(), result);
            }
        }
        List<LLMRecording.ToolResult> history =
                callOrder.stream().filter(results::containsKey).map(results::get).toList();
        List<LLMRecording.Message> messages =
                request.messages().stream()
                        .map(
                                message ->
                                        new LLMRecording.Message(
                                                message.role(),
                                                "user".equals(message.role())
                                                        ? normalizeToolSummary(
                                                                message.text(), history, turns)
                                                        : message.text(),
                                                message.toolCalls(),
                                                message.toolResults().stream()
                                                        .map(
                                                                result ->
                                                                        new LLMRecording.ToolResult(
                                                                                result.reference(),
                                                                                result.name(),
                                                                                normalizeHttpResponse(
                                                                                        result
                                                                                                .value())))
                                                        .toList()))
                        .toList();
        return new LLMRecording.Request(
                messages,
                request.tools(),
                request.jsonOutput(),
                request.outputSchema(),
                request.generationOptions());
    }

    private static String normalizeToolSummary(
            String text, List<LLMRecording.ToolResult> history, Map<String, Integer> turns) {
        if (text == null || history.isEmpty()) return text;
        int start = text.indexOf(TOOL_RESULTS_START);
        if (start < 0 || (start > 0 && !text.substring(0, start).endsWith("\n\n"))) return text;
        int end = text.indexOf(TOOL_RESULTS_END, start + TOOL_RESULTS_START.length());
        if (end < 0 || !text.substring(end + TOOL_RESULTS_END.length()).startsWith("\n\n"))
            return text;
        JsonNode entries;
        try {
            entries = MAPPER.readTree(text.substring(start + TOOL_RESULTS_START.length(), end));
        } catch (JsonProcessingException exception) {
            return text;
        }
        if (entries == null || !entries.isArray() || entries.size() != history.size()) return text;
        // Only rewrite the generated duplicate when every observation agrees with structured
        // history. Never discard unknown fields, unmatched results, or arbitrary prompt text.
        List<Integer> matched = new ArrayList<>();
        int previousTurn = -1;
        for (JsonNode entry : entries) {
            if (!entry.isObject()
                    || entry.size() != 2
                    || !entry.path("name").isTextual()
                    || !entry.has("output")) return text;
            String name = entry.get("name").textValue();
            int index = -1;
            for (int i = 0; i < history.size(); i++) {
                LLMRecording.ToolResult result = history.get(i);
                boolean matchesName =
                        name.equals(result.name())
                                || name.equals(result.reference())
                                || GENERATED_RESULT_NAME.matcher(name).matches();
                if (!matched.contains(i)
                        && matchesName
                        && sameSummaryValue(
                                normalizeHttpResponse(entry.get("output")),
                                normalizeHttpResponse(result.value()))) {
                    index = i;
                    break;
                }
            }
            if (index < 0) return text;
            int currentTurn = turns.get(history.get(index).reference());
            // Completion order may vary within a parallel turn, never across sequential turns.
            if (currentTurn < previousTurn) return text;
            previousTurn = currentTurn;
            matched.add(index);
        }
        ArrayNode normalized = MAPPER.createArrayNode();
        matched.sort(Comparator.naturalOrder());
        for (int index : matched) {
            LLMRecording.ToolResult result = history.get(index);
            normalized
                    .addObject()
                    .put("name", result.reference())
                    .set("output", sortedObjectKeys(normalizeHttpResponse(result.value())));
        }
        return text.substring(0, start + TOOL_RESULTS_START.length())
                + normalized
                + text.substring(end);
    }

    private static boolean sameSummaryValue(JsonNode summary, JsonNode result) {
        if (summary == null || result == null) return summary == result;
        // JavaScript's generated summary renders 54.0 as 54. Compare numeric values only for
        // this duplicate-history check; retain exact strings, array order, and structured values.
        return summary.equals(
                (left, right) -> {
                    if (left.isNumber() && right.isNumber()) {
                        return left.decimalValue().compareTo(right.decimalValue());
                    }
                    return left.equals(right) ? 0 : 1;
                },
                result);
    }

    private static boolean isHttpResponse(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 1) return false;
        JsonNode response = value.path("response");
        return response.isObject()
                && response.path("statusCode").isIntegralNumber()
                && response.path("headers").isObject()
                && response.has("body")
                && response.path("reasonPhrase").isTextual();
    }

    private static JsonNode normalizeHttpResponse(JsonNode value) {
        if (!isHttpResponse(value)) return value;
        // Limit exclusions to transport headers in the HTTP task envelope. Body fields, status,
        // Retry-After, and all other headers remain part of the matching key.
        JsonNode copy = value.deepCopy();
        ObjectNode headers = (ObjectNode) copy.get("response").get("headers");
        List<String> remove = new ArrayList<>();
        headers.fieldNames()
                .forEachRemaining(
                        name -> {
                            if (VOLATILE_HTTP_HEADERS.contains(name.toLowerCase(Locale.ROOT)))
                                remove.add(name);
                        });
        headers.remove(remove);
        return copy;
    }

    private static JsonNode sortedObjectKeys(JsonNode value) {
        if (value == null) return NullNode.instance;
        if (value.isObject()) {
            ObjectNode sorted = MAPPER.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            names.forEach(name -> sorted.set(name, sortedObjectKeys(value.get(name))));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode array = MAPPER.createArrayNode();
            value.forEach(item -> array.add(sortedObjectKeys(item)));
            return array;
        }
        return value;
    }

    private String reference(String id, String name) {
        if (StringUtils.isAnyBlank(id, name)) {
            throw new IllegalArgumentException("Recorded tool calls require a name and ID");
        }
        CallIdentity call =
                callIdentities.computeIfAbsent(
                        id, ignored -> new CallIdentity("call_" + callIdentities.size(), name));
        Validate.isTrue(call.name().equals(name), "Tool-call ID was reused for a different tool");
        return call.reference();
    }

    /** Providers serialize an absent {@code ToolSpec} schema as the JSON literal {@code null}. */
    private static JsonNode toolInputSchema(String json) {
        if (StringUtils.isBlank(json) || "null".equals(json.strip())) {
            return MAPPER.valueToTree(DEFAULT_TOOL_INPUT_SCHEMA);
        }
        return parseObject(json, "tool input schema");
    }

    private static JsonNode parseObject(String json, String field) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            // Report the contract field, not a potentially sensitive payload or parser exception.
        }
        throw new IllegalArgumentException("Expected a JSON object for " + field);
    }

    private static JsonNode parseResult(String value) {
        if (value == null) return NullNode.instance;
        try {
            JsonNode parsed = MAPPER.readTree(value);
            if (parsed != null) return parsed;
        } catch (JsonProcessingException ignored) {
            // Tool results can also be plain text. Preserve their exact content.
        }
        return TextNode.valueOf(value);
    }
}
