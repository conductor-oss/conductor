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
package org.conductoross.conductor.ai;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.document.DocumentLoader;
import org.conductoross.conductor.ai.models.AudioGenRequest;
import org.conductoross.conductor.ai.models.ChatCompletion;
import org.conductoross.conductor.ai.models.ChatMessage;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.ImageGenRequest;
import org.conductoross.conductor.ai.models.LLMResponse;
import org.conductoross.conductor.ai.models.ToolCall;
import org.conductoross.conductor.ai.models.ToolSpec;
import org.conductoross.conductor.ai.models.VideoGenRequest;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.common.utils.StringTemplate;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.Task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SIMPLE;

import static org.conductoross.conductor.ai.MimeExtensionResolver.getExtension;
import static org.conductoross.conductor.ai.MimeExtensionResolver.getMimeTypeFromUrl;

@Component
@Slf4j
@RequiredArgsConstructor
@Conditional(AIIntegrationEnabledCondition.class)
public class LLMHelper {
    private static final TypeReference<Map<String, Object>> MAP_OF_STRING_TO_OBJ =
            new TypeReference<>() {};
    private static final Map<String, String> finishReasonMap =
            Map.of("end_turn", "STOP", "tool_use", "TOOL_CALLS", "refusal", "CONTENT_FILTER");
    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private final JsonSchemaValidator jsonSchemaValidator;
    private final List<DocumentLoader> documentLoaders;

    public LLMResponse chatComplete(
            Task task,
            AIModel llm,
            ChatCompletion chatCompletion,
            String payloadStoreLocation,
            Consumer<TokenUsageLog> tokenUsageLogger) {

        ChatModel chatModel = llm.getChatModel();
        ChatOptions chatOptions = llm.getChatOptions(chatCompletion);
        LLMResponse response = chatComplete(chatModel, chatOptions, chatCompletion);

        String prompt =
                replacePromptVariables(
                        chatCompletion.getInstructions(), chatCompletion.getPromptVariables());
        chatCompletion.setPrompt(prompt);
        extractResponse(response, chatCompletion);
        storeMedia(payloadStoreLocation, response.getMedia());

        TokenUsageLog usage =
                TokenUsageLog.builder()
                        .taskId(task.getTaskId())
                        .api(chatCompletion.getModel())
                        .integrationName(chatCompletion.getLlmProvider())
                        .completionTokens(response.getCompletionTokens())
                        .promptTokens(response.getPromptTokens())
                        .totalTokens(response.getTokenUsed())
                        .build();

        tokenUsageLogger.accept(usage);
        return response;
    }

    public LLMResponse generateImage(
            Task task,
            AIModel llm,
            ImageGenRequest imageGenRequest,
            String payloadStoreLocation,
            Consumer<TokenUsageLog> tokenUsageLogger) {

        String prompt =
                replacePromptVariables(
                        imageGenRequest.getPrompt(), imageGenRequest.getPromptVariables());
        imageGenRequest.setPrompt(prompt);
        ImageOptions options = llm.getImageOptions(imageGenRequest);
        ImageModel model = llm.getImageModel();
        LLMResponse response = generateImage(model, options, imageGenRequest);
        storeMedia(payloadStoreLocation, response.getMedia());

        TokenUsageLog usage =
                TokenUsageLog.builder()
                        .taskId(task.getTaskId())
                        .api(imageGenRequest.getModel())
                        .integrationName(imageGenRequest.getLlmProvider())
                        .completionTokens(response.getCompletionTokens())
                        .promptTokens(response.getPromptTokens())
                        .totalTokens(response.getTokenUsed())
                        .build();
        tokenUsageLogger.accept(usage);
        return response;
    }

    public List<Float> generateEmbeddings(
            Task task,
            AIModel llm,
            EmbeddingGenRequest embeddingGenRequest,
            Consumer<TokenUsageLog> tokenUsageLogger) {
        return llm.generateEmbeddings(embeddingGenRequest);
    }

    public LLMResponse generateAudio(
            Task task,
            AIModel llm,
            AudioGenRequest request,
            String payloadStoreLocation,
            Consumer<TokenUsageLog> tokenUsageLogger) {
        LLMResponse response = llm.generateAudio(request);
        storeMedia(payloadStoreLocation, response.getMedia());
        TokenUsageLog usage =
                TokenUsageLog.builder()
                        .taskId(task.getTaskId())
                        .api(request.getModel())
                        .integrationName(request.getLlmProvider())
                        .completionTokens(response.getCompletionTokens())
                        .promptTokens(response.getPromptTokens())
                        .totalTokens(response.getTokenUsed())
                        .build();
        tokenUsageLogger.accept(usage);
        return response;
    }

    public LLMResponse generateVideo(
            Task task,
            AIModel llm,
            VideoGenRequest videoGenRequest,
            String payloadStoreLocation,
            Consumer<TokenUsageLog> tokenUsageLogger) {

        return llm.generateVideo(videoGenRequest);
    }

    public LLMResponse checkVideoStatus(
            Task task, AIModel llm, VideoGenRequest videoGenRequest, String payloadStoreLocation) {

        LLMResponse response = llm.checkVideoStatus(videoGenRequest);

        // If completed, download and store media
        if ("COMPLETED".equals(response.getFinishReason())) {
            storeMedia(payloadStoreLocation, response.getMedia());
        }

        return response;
    }

    // Helper methods

    private String replacePromptVariables(String prompt, Map<String, Object> paramReplacement) {
        if (StringUtils.isBlank(prompt)) {
            return prompt;
        }
        if (paramReplacement != null) {
            prompt = StringTemplate.fString(prompt, paramReplacement);
        }
        return prompt;
    }

    @SneakyThrows
    @SuppressWarnings({"raw", "unchecked"})
    private void extractResponse(LLMResponse llmResponse, ChatCompletion input) {
        if (llmResponse.getResult() == null || llmResponse.getResult().toString().isEmpty()) {
            // empty response
            log.debug("empty response: finishReason: {}", llmResponse.getFinishReason());
            return;
        }
        Object result = llmResponse.getResult();
        switch (result) {
            case null -> llmResponse.setResult(Map.of());
            case String ignored ->
                    llmResponse.setResult(
                            tryToConvertToJSON(llmResponse.getResult().toString(), input));
            case List<?> resultList -> {
                List<String> errors = new ArrayList<>();
                List<Object> output = new ArrayList<>();
                boolean hasJsonOutput = false;
                for (Object o : resultList) {
                    String responseText = o.toString();
                    var responseObj = tryToConvertToJSON(responseText, input);
                    hasJsonOutput = true;
                    if (input.getOutputSchema() != null) {
                        String error = null;
                        if (!(responseObj instanceof Map)) {
                            error = "not a JSON response: %s".formatted(responseObj);
                        } else {
                            error =
                                    validateJsonSchema(
                                            input.getInputSchema(),
                                            (Map<String, Object>) responseObj);
                        }
                        if (error != null) {
                            errors.add(
                                    String.format(
                                            "Output does not confirm to the schema.  errors: %s",
                                            error));
                        }
                    }
                    output.add(responseObj);
                }
                llmResponse.setResult(output);
                if (input.isJsonOutput() && !hasJsonOutput && !llmResponse.hasToolCalls()) {
                    Map<String, Object> outputErrors = Map.of("error", errors, "response", output);
                    throw new RuntimeException(objectMapper.writeValueAsString(outputErrors));
                }
            }
            default -> llmResponse.setResult(result.toString());
        }
    }

    @SneakyThrows
    private Object tryToConvertToJSON(String responseText, ChatCompletion chatCompletion) {
        try {
            responseText = responseText.trim();
            if (responseText.startsWith("```json")) {
                responseText = responseText.substring("```json".length());
                responseText =
                        responseText.substring(0, responseText.length() - "```".length() - 1);
            }
            Map<String, Object> map = objectMapper.readValue(responseText, MAP_OF_STRING_TO_OBJ);
            if (chatCompletion.getOutputSchema() != null) {
                String error = validateJsonSchema(chatCompletion.getInputSchema(), map);
                if (error != null) {
                    throw new RuntimeException(
                            String.format(
                                    "Output does not confirm to the schema.  errors: %s", error));
                }
            }
            // llmResponse.setResult(map);
            return map;

        } catch (JsonProcessingException e) {
            if (chatCompletion.isJsonOutput()) {
                log.error(
                        "error converting to json, response: {}, error: {}",
                        responseText,
                        e.getMessage(),
                        e);
                Map<String, Object> outputErrors =
                        Map.of("error", e.getMessage(), "response", responseText);
                throw new RuntimeException(objectMapper.writeValueAsString(outputErrors));
            }
            return responseText;
        }
    }

    private String validateJsonSchema(final SchemaDef schema, Map<String, Object> data) {
        try {
            // Order in which we use the schema
            // 1. If there is data -- inline schema def, we use that
            // 2. Else use name + version to lookup
            // 3. externalRef if present, in future we will use it -- currently not supported
            String schemaContent = objectMapper.writeValueAsString(schema.getData());
            if (schemaContent == null) {
                return null;
            }

            Set<ValidationMessage> validationMessages =
                    jsonSchemaValidator.validate(schemaContent, data);

            if (validationMessages != null && !validationMessages.isEmpty()) {
                return String.format(
                        "Schema validation failed %s",
                        validationMessages.stream()
                                .map(ValidationMessage::getMessage)
                                .collect(Collectors.joining(", ")));
            }
            return null;
        } catch (JsonSchemaException jpe) {
            throw new RuntimeException(
                    "Bad/Unsupported schema? : " + jpe.getValidationMessages().toString());
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException("Error parsing the json schema : " + jpe.getMessage(), jpe);
        }
    }

    @SneakyThrows
    private LLMResponse chatComplete(
            ChatModel chatModel, ChatOptions chatOptions, ChatCompletion input) {
        ChatClient chatClient = ChatClient.create(chatModel);
        if (StringUtils.isNotBlank(input.getInstructions())) {
            input.getMessages()
                    .addFirst(new ChatMessage(ChatMessage.Role.system, input.getInstructions()));
        }

        List<Message> messages = input.getMessages().stream().map(this::constructMessage).toList();

        Prompt prompt = new Prompt(messages, chatOptions);
        ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();
        if (chatResponse == null) {
            throw new RuntimeException("No response generated");
        }
        if (chatResponse.getResults().isEmpty()) {
            String result = objectMapper.writeValueAsString(chatResponse);
            return LLMResponse.builder()
                    .result(result)
                    .completionTokens(chatResponse.getMetadata().getUsage().getCompletionTokens())
                    .promptTokens(chatResponse.getMetadata().getUsage().getPromptTokens())
                    .tokenUsed(chatResponse.getMetadata().getUsage().getTotalTokens())
                    .build();
        }

        List<ToolCall> tools = null;
        String finishReason = null;
        List<String> responses = new ArrayList<>();
        List<org.conductoross.conductor.ai.models.Media> media = new ArrayList<>();
        for (Generation result : chatResponse.getResults()) {
            if (result.getOutput().hasToolCalls()) {
                List<AssistantMessage.ToolCall> toolCalls = result.getOutput().getToolCalls();
                tools = new ArrayList<>();
                for (AssistantMessage.ToolCall toolCall : toolCalls) {
                    String name = toolCall.name();
                    String id = toolCall.id();
                    String argsAsString = toolCall.arguments();
                    Map<String, Object> args = Map.of();
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parsedArgs =
                                objectMapper.readValue(argsAsString, Map.class);
                        // Recursively parse any nested JSON strings
                        args = parseNestedJsonStrings(parsedArgs);
                    } catch (JsonProcessingException ignored) {
                        log.warn(ignored.getMessage(), ignored);
                    }
                    args.put("method", name);

                    Optional<ToolSpec> matched =
                            input.getTools().stream()
                                    .filter(toolSpec -> toolSpec.getName().equals(name))
                                    .findFirst();

                    String integrationName =
                            (String)
                                    matched.map(ToolSpec::getConfigParams)
                                            .orElse(Collections.emptyMap())
                                            .get("integrationName");
                    args.put("integrationName", integrationName);
                    String type = matched.map(ToolSpec::getType).orElse(TASK_TYPE_SIMPLE);
                    tools.add(
                            ToolCall.builder()
                                    .taskReferenceName(id)
                                    .name(name)
                                    .inputParameters(args)
                                    .integrationNames(getIntegrationNames(name, input.getTools()))
                                    .type(type)
                                    .build());
                }
                finishReason = result.getMetadata().getFinishReason();
            } else {
                responses.add(result.getOutput().getText());
                result.getOutput()
                        .getMedia()
                        .forEach(
                                m ->
                                        media.add(
                                                org.conductoross.conductor.ai.models.Media.builder()
                                                        .data(m.getDataAsByteArray())
                                                        .mimeType(m.getMimeType().toString())
                                                        .build()));
                // storeMedia(outputLocation, result.getOutput().getMedia());
                if (finishReason == null) {
                    finishReason = result.getMetadata().getFinishReason();
                }
            }
        }
        Object result = responses;
        if (responses.size() == 1) {
            result = responses.getFirst();
        }
        finishReason = finishReasonMap.getOrDefault(finishReason, finishReason).toUpperCase();
        return LLMResponse.builder()
                .result(result)
                .media(media)
                .toolCalls(tools)
                .finishReason(finishReason)
                .completionTokens(chatResponse.getMetadata().getUsage().getCompletionTokens())
                .promptTokens(chatResponse.getMetadata().getUsage().getPromptTokens())
                .tokenUsed(chatResponse.getMetadata().getUsage().getTotalTokens())
                .build();
    }

    private LLMResponse generateImage(
            ImageModel imageModel, ImageOptions options, ImageGenRequest request) {
        ImageMessage imageMessage = new ImageMessage(request.getPrompt(), request.getWeight());
        ImagePrompt prompt = new ImagePrompt(List.of(imageMessage), options);
        ImageResponse response = imageModel.call(prompt);
        LLMResponse mediaGenResponse = new LLMResponse();
        List<org.conductoross.conductor.ai.models.Media> mediaList = new ArrayList<>();
        for (ImageGeneration result : response.getResults()) {
            var image = result.getOutput();
            String url = image.getUrl();
            String base64 = image.getB64Json();

            // Determine the mime type from the output format
            String mimeType =
                    "image/"
                            + (request.getOutputFormat() != null
                                    ? request.getOutputFormat()
                                    : "png");

            if (base64 != null) {
                // Base64 data provided - decode and store
                mediaList.add(
                        org.conductoross.conductor.ai.models.Media.builder()
                                .data(Base64.getDecoder().decode(base64))
                                .mimeType(mimeType)
                                .build());
            } else if (url != null) {
                // URL provided - download the image bytes so we can store locally
                // This ensures the image is persisted even after the provider's URL expires
                byte[] imageBytes = downloadImageFromUrl(url);
                if (imageBytes != null) {
                    // Detect mime type from URL extension if possible
                    String detectedMimeType = getMimeTypeFromUrl(url, mimeType);
                    mediaList.add(
                            org.conductoross.conductor.ai.models.Media.builder()
                                    .data(imageBytes)
                                    .mimeType(detectedMimeType)
                                    .build());
                } else {
                    // Fallback: if download fails, keep the URL (will expire but better than
                    // nothing)
                    log.warn("Failed to download image from URL, keeping external URL: {}", url);
                    mediaList.add(
                            org.conductoross.conductor.ai.models.Media.builder()
                                    .location(url)
                                    .mimeType(mimeType)
                                    .build());
                }
            }
        }

        mediaGenResponse.setMedia(mediaList);

        return mediaGenResponse;
    }

    /**
     * Downloads image bytes from a URL. Used to persist images locally instead of relying on
     * provider-hosted URLs that may expire.
     *
     * @param url The image URL to download from
     * @return The image bytes, or null if download failed
     */
    private byte[] downloadImageFromUrl(String url) {
        try {
            OkHttpClient client =
                    new OkHttpClient.Builder()
                            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                            .build();

            Request request = new Request.Builder().url(url).get().build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error(
                            "Failed to download image from URL {}: HTTP {}", url, response.code());
                    return null;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    log.error("Empty response body when downloading image from URL: {}", url);
                    return null;
                }
                byte[] bytes = body.bytes();
                log.debug("Downloaded {} bytes from image URL: {}", bytes.length, url);
                return bytes;
            }
        } catch (Exception e) {
            log.error("Exception downloading image from URL {}: {}", url, e.getMessage());
            return null;
        }
    }

    @SneakyThrows
    private Message constructMessage(ChatMessage chatMessage) {
        return switch (chatMessage.getRole()) {
            case user -> getMessage(chatMessage);
            case assistant -> new AssistantMessage(chatMessage.getMessage());
            case system -> new SystemMessage(chatMessage.getMessage());
            case tool_call ->
                    AssistantMessage.builder()
                            .content("{}")
                            .toolCalls(
                                    chatMessage.getToolCalls().stream()
                                            .map(
                                                    tc -> {
                                                        var name =
                                                                extractMethodFromInputParameters(
                                                                        tc.getInputParameters());
                                                        return new AssistantMessage.ToolCall(
                                                                tc.getTaskReferenceName(),
                                                                "function",
                                                                name == null ? tc.getName() : name,
                                                                toJSON(tc.getInputParameters()));
                                                    })
                                            .toList())
                            .build();
            case tool -> {
                List<ToolCall> toolCalls = chatMessage.getToolCalls();
                if (toolCalls == null) {
                    log.warn("chat message role: {}, but toolCalls is null", chatMessage.getRole());
                    toolCalls = new ArrayList<>();
                }
                try {
                    List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                    if (toolCalls.isEmpty()) {
                        log.info("toolCalls is empty for {}", chatMessage);
                    }
                    for (ToolCall toolCall : toolCalls) {
                        Map<String, Object> inputJson = toolCall.getInputParameters();
                        var name = extractMethodFromInputParameters(inputJson);
                        String outputJSON = objectMapper.writeValueAsString(toolCall.getOutput());

                        log.trace("outputJSON for {} is {}", toolCall.getName(), outputJSON);
                        log.info("tool: {}", toolCall);
                        log.info("tool.getTaskReferenceName: {}", toolCall.getTaskReferenceName());
                        responses.add(
                                new ToolResponseMessage.ToolResponse(
                                        toolCall.getTaskReferenceName(),
                                        name == null ? toolCall.getName() : name,
                                        outputJSON));
                    }
                    log.trace("responses: {}", responses);
                    yield ToolResponseMessage.builder().responses(responses).build();
                } catch (Exception e) {
                    log.error("error: {}", e.getMessage(), e);
                    yield new SystemMessage(chatMessage.getMessage());
                }
            }
        };
    }

    private String toJSON(Object input) {
        try {
            if (input == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException jpe) {
            return String.valueOf(input);
        }
    }

    /**
     * Extracts the "method" value from a map structure where the outer key is dynamic. The map
     * structure is expected to be: { "dynamicKey": { "method": "methodName", ... } }
     *
     * @param inputParameters The map containing the nested structure
     * @return The method value as a String, or null if not found
     */
    @SuppressWarnings("unchecked")
    @VisibleForTesting
    String extractMethodFromInputParameters(Map<String, Object> inputParameters) {
        if (inputParameters == null || inputParameters.isEmpty()) {
            return null;
        }

        // Iterate through all entries to find the nested map with "method" key
        for (Map.Entry<String, Object> entry : inputParameters.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                if (nestedMap.containsKey("method")) {
                    Object methodValue = nestedMap.get("method");
                    return methodValue != null ? methodValue.toString() : null;
                }
            }
        }

        return null;
    }

    private Message getMessage(ChatMessage msg) {
        List<Media> media =
                msg.getMedia().stream()
                        .map(m -> getMedia(msg.getMimeType(), m))
                        .filter(Objects::nonNull)
                        .toList();
        return UserMessage.builder().text(msg.getMessage()).media(media).build();
    }

    private Media getMedia(String mimeType, String content) {
        // content can be a URL or base64 encoded data
        URI uri = AIModel.getURI(content);
        if (uri == null) {
            return Media.builder().data(content).mimeType(MimeType.valueOf(mimeType)).build();
        }
        Optional<byte[]> data =
                documentLoaders.stream()
                        .filter(documentLoader -> documentLoader.supports(content))
                        .findFirst()
                        .map(loader -> loader.download(content));
        final String mimeTypeResolved =
                Optional.ofNullable(mimeType)
                        .orElse(MimeExtensionResolver.getMimeTypeFromUrl(content, ""));
        return data.map(
                        bytes ->
                                Media.builder()
                                        .data(bytes)
                                        .mimeType(MimeType.valueOf(mimeTypeResolved))
                                        .build())
                .orElse(null);
    }

    private void storeMedia(
            String location, List<org.conductoross.conductor.ai.models.Media> media) {
        Optional<DocumentLoader> docLoader =
                documentLoaders.stream()
                        .filter(documentLoader -> documentLoader.supports(location))
                        .findFirst();
        docLoader.ifPresent(
                loader -> {
                    media.stream()
                            .filter(m1 -> m1.getData() != null)
                            .forEach(
                                    m -> {
                                        // Each media item gets a unique path with file extension
                                        // to prevent overwriting when multiple items exist
                                        // (e.g., video + thumbnail)
                                        String ext = getExtension(m.getMimeType());
                                        String uniqueLocation =
                                                location + "_" + java.util.UUID.randomUUID() + ext;
                                        String uploadLocation =
                                                loader.upload(
                                                        Map.of(),
                                                        m.getMimeType(),
                                                        m.getData(),
                                                        uniqueLocation);
                                        m.setLocation(uploadLocation);
                                        m.setData(null);
                                    });
                });
    }

    /**
     * Stores media from an InputStream, streaming directly to the DocumentLoader without buffering
     * the full content in memory. Intended for large media files such as video.
     *
     * @param location Base storage location (e.g., file:///path/to/storage)
     * @param mimeType MIME type of the media (e.g., "video/mp4")
     * @param stream InputStream containing the media data
     * @return The storage location where the media was written, or null if no loader was found
     */
    public String storeMediaStream(String location, String mimeType, java.io.InputStream stream) {
        Optional<DocumentLoader> docLoader =
                documentLoaders.stream()
                        .filter(documentLoader -> documentLoader.supports(location))
                        .findFirst();
        if (docLoader.isPresent()) {
            String ext = getExtension(mimeType);
            String uniqueLocation = location + "_" + java.util.UUID.randomUUID() + ext;
            docLoader.get().upload(Map.of(), mimeType, stream, uniqueLocation);
            return uniqueLocation;
        }
        return null;
    }

    private Map<String, String> getIntegrationNames(String toolCallName, List<ToolSpec> toolSpecs) {
        Optional<ToolSpec> matched =
                toolSpecs.stream()
                        .filter(toolSpec -> toolSpec.getName().equals(toolCallName))
                        .findFirst();
        return matched.map(ToolSpec::getIntegrationNames).orElse(Collections.emptyMap());
    }

    /**
     * Recursively parses JSON strings within a Map structure. This handles cases where the LLM
     * generates nested JSON strings like "{\"value\":\"page\"}".
     *
     * @param input The input Map that may contain JSON strings
     * @return A new Map with JSON strings parsed into their object representations
     */
    @SuppressWarnings("unchecked")
    @VisibleForTesting
    Map<String, Object> parseNestedJsonStrings(Map<String, Object> input) {
        if (input == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();

        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                String stringValue = (String) value;
                if (isJsonString(stringValue)) {
                    try {
                        // Parse the JSON string into an object
                        Object parsedValue = objectMapper.readValue(stringValue, Object.class);
                        // Recursively parse any nested JSON strings in the parsed object
                        if (parsedValue instanceof Map) {
                            parsedValue = parseNestedJsonStrings((Map<String, Object>) parsedValue);
                        } else if (parsedValue instanceof List) {
                            parsedValue = parseNestedJsonStringsInList((List<Object>) parsedValue);
                        }
                        result.put(key, parsedValue);
                    } catch (Exception e) {
                        // If parsing fails, keep the original string value
                        log.debug(
                                "Failed to parse JSON string for key {}: {}", key, e.getMessage());
                        result.put(key, value);
                    }
                } else {
                    result.put(key, value);
                }
            } else if (value instanceof Map) {
                // Recursively parse nested Maps
                result.put(key, parseNestedJsonStrings((Map<String, Object>) value));
            } else if (value instanceof List) {
                // Recursively parse nested Lists
                result.put(key, parseNestedJsonStringsInList((List<Object>) value));
            } else {
                result.put(key, value);
            }
        }

        return result;
    }

    /** Recursively parses JSON strings within a List structure. */
    @SuppressWarnings("unchecked")
    private List<Object> parseNestedJsonStringsInList(List<Object> input) {
        if (input == null) {
            return null;
        }

        List<Object> result = new ArrayList<>();

        for (Object item : input) {
            if (item instanceof String) {
                String stringValue = (String) item;
                if (isJsonString(stringValue)) {
                    try {
                        Object parsedValue = objectMapper.readValue(stringValue, Object.class);
                        if (parsedValue instanceof Map) {
                            parsedValue = parseNestedJsonStrings((Map<String, Object>) parsedValue);
                        } else if (parsedValue instanceof List) {
                            parsedValue = parseNestedJsonStringsInList((List<Object>) parsedValue);
                        }
                        result.add(parsedValue);
                    } catch (Exception e) {
                        log.debug("Failed to parse JSON string in list: {}", e.getMessage());
                        result.add(item);
                    }
                } else {
                    result.add(item);
                }
            } else if (item instanceof Map) {
                result.add(parseNestedJsonStrings((Map<String, Object>) item));
            } else if (item instanceof List) {
                result.add(parseNestedJsonStringsInList((List<Object>) item));
            } else {
                result.add(item);
            }
        }

        return result;
    }

    /** Checks if a string looks like JSON */
    @VisibleForTesting
    boolean isJsonString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
