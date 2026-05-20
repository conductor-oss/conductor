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
package org.conductoross.conductor.ai.providers.gemini.api;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** DTO types for the Gemini REST API, plus an OkHttp-based HTTP client for all Gemini API calls. */
public class GeminiApi {

    // ---- HTTP client state ----

    private static final String AI_STUDIO_BASE = "https://generativelanguage.googleapis.com";
    private static final String AI_STUDIO_VERSION = "/v1beta";
    private static final okhttp3.MediaType JSON_MEDIA = okhttp3.MediaType.parse("application/json");

    private final okhttp3.OkHttpClient httpClient;
    private final String endpointBase; // e.g. "https://generativelanguage.googleapis.com/v1beta"
    private final String apiKey; // null for Vertex
    private final com.google.auth.oauth2.GoogleCredentials credentials; // null for API key mode
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** Factory: API-key mode (AI Studio / generativelanguage.googleapis.com). */
    public static GeminiApi forApiKey(
            okhttp3.OkHttpClient httpClient, String apiKey, String customBase) {
        String base =
                (customBase != null && !customBase.isBlank())
                        ? customBase + AI_STUDIO_VERSION
                        : AI_STUDIO_BASE + AI_STUDIO_VERSION;
        return new GeminiApi(httpClient, base, apiKey, null);
    }

    /** Factory: Vertex AI mode. */
    public static GeminiApi forVertex(
            okhttp3.OkHttpClient httpClient,
            String projectId,
            String location,
            com.google.auth.oauth2.GoogleCredentials credentials) {
        String base =
                "https://"
                        + location
                        + "-aiplatform.googleapis.com/v1"
                        + "/projects/"
                        + projectId
                        + "/locations/"
                        + location
                        + "/publishers/google";
        return new GeminiApi(httpClient, base, null, credentials);
    }

    private GeminiApi(
            okhttp3.OkHttpClient httpClient,
            String endpointBase,
            String apiKey,
            com.google.auth.oauth2.GoogleCredentials credentials) {
        this.httpClient = httpClient;
        this.endpointBase = endpointBase;
        this.apiKey = apiKey;
        this.credentials = credentials;
        this.objectMapper =
                new com.netflix.conductor.common.config.ObjectMapperProvider().getObjectMapper();
    }

    // ---- URL helpers ----

    private String modelUrl(String model, String aiStudioVerb, String vertexVerb) {
        String verb = apiKey != null ? aiStudioVerb : vertexVerb;
        String url = endpointBase + "/models/" + model + ":" + verb;
        return apiKey != null ? url + "?key=" + apiKey : url;
    }

    private String modelUrl(String model, String verb) {
        return modelUrl(model, verb, verb);
    }

    private String operationUrl(String operationName) {
        if (apiKey != null) {
            // AI Studio: operationName is the bare operation id
            return AI_STUDIO_BASE + AI_STUDIO_VERSION + "/" + operationName + "?key=" + apiKey;
        }
        // Vertex: operationName is a full resource path like "projects/.../operations/xxx"
        // Strip the version prefix from endpointBase to get the Vertex host
        String vertexBase = endpointBase.replaceAll("/v1/projects/.*", "");
        return vertexBase + "/v1/" + operationName;
    }

    private okhttp3.Request.Builder authRequest(String url) throws java.io.IOException {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url);
        if (credentials != null) {
            if (credentials.getAccessToken() == null) {
                credentials.refresh();
            } else {
                credentials.refreshIfExpired();
            }
            builder.header(
                    "Authorization", "Bearer " + credentials.getAccessToken().getTokenValue());
        }
        return builder;
    }

    private String executePost(String url, Object body) throws java.io.IOException {
        String json = objectMapper.writeValueAsString(body);
        okhttp3.Request request =
                authRequest(url).post(okhttp3.RequestBody.create(json, JSON_MEDIA)).build();
        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new java.io.IOException(
                        "Gemini API error %d: %s".formatted(response.code(), resp));
            }
            return resp;
        }
    }

    private String executeGet(String url) throws java.io.IOException {
        okhttp3.Request request = authRequest(url).get().build();
        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new java.io.IOException(
                        "Gemini API error %d: %s".formatted(response.code(), resp));
            }
            return resp;
        }
    }

    // ---- API methods ----

    public GenerateContentResponse generateContent(
            String model, List<Content> contents, GenerationConfig config)
            throws java.io.IOException {
        return generateContent(model, contents, null, null, config);
    }

    public GenerateContentResponse generateContent(
            String model,
            List<Content> contents,
            Content systemInstruction,
            List<Tool> tools,
            GenerationConfig config)
            throws java.io.IOException {
        GenerateContentRequest req =
                new GenerateContentRequest(contents, systemInstruction, tools, config);
        String url = modelUrl(model, "generateContent");
        String json = objectMapper.writeValueAsString(req);
        try (okhttp3.Response response = httpClient.newCall(authRequest(url)
                .post(okhttp3.RequestBody.create(json, JSON_MEDIA)).build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                // Some Gemini thinking models reject temperature — retry without it.
                if (response.code() == 400
                        && body.contains("temperature")
                        && config != null
                        && config.temperature() != null) {
                    return generateContent(model, contents, systemInstruction, tools,
                            config.withoutTemperature());
                }
                throw new java.io.IOException(
                        "Gemini API error %d: %s".formatted(response.code(), body));
            }
            return objectMapper.readValue(body, GenerateContentResponse.class);
        }
    }

    public EmbedContentResponse embedContent(
            String model, String text, Integer outputDimensionality) throws java.io.IOException {
        Content content = new Content(null, List.of(Part.text(text)));
        EmbedContentRequest req = new EmbedContentRequest(content, null, outputDimensionality);
        return objectMapper.readValue(
                executePost(modelUrl(model, "embedContent"), req), EmbedContentResponse.class);
    }

    public GenerateImagesResponse generateImages(
            String model, String promptText, GenerateImagesConfig config)
            throws java.io.IOException {
        GenerateImagesRequest req = new GenerateImagesRequest(new ImagePrompt(promptText), config);
        return objectMapper.readValue(
                executePost(modelUrl(model, "generateImages", "predict"), req),
                GenerateImagesResponse.class);
    }

    public GenerateVideosOperation generateVideos(
            String model,
            String text,
            byte[] inputImageBytes,
            String inputMimeType,
            GenerateVideosConfig config)
            throws java.io.IOException {
        java.util.Map<String, Object> instance = new java.util.LinkedHashMap<>();
        instance.put("prompt", text);
        if (inputImageBytes != null) {
            instance.put(
                    "image",
                    java.util.Map.of(
                            "bytesBase64Encoded",
                            java.util.Base64.getEncoder().encodeToString(inputImageBytes),
                            "mimeType",
                            inputMimeType != null ? inputMimeType : "image/png"));
        }
        GenerateVideosRequest req = new GenerateVideosRequest(List.of(instance), config);
        return objectMapper.readValue(
                executePost(modelUrl(model, "generateVideos", "predictLongRunning"), req),
                GenerateVideosOperation.class);
    }

    public GenerateVideosOperation getVideosOperation(String operationName)
            throws java.io.IOException {
        if (apiKey != null) {
            // AI Studio: GET the operation resource
            return objectMapper.readValue(
                    executeGet(operationUrl(operationName)), GenerateVideosOperation.class);
        } else {
            // Vertex AI: POST to fetchPredictOperation
            String vertexBase = endpointBase.replaceAll("/publishers/google$", "");
            String url = vertexBase + "/" + operationName + ":fetchPredictOperation";
            return objectMapper.readValue(
                    executePost(url, java.util.Map.of()), GenerateVideosOperation.class);
        }
    }

    // --- Request DTOs ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerateContentRequest(
            List<Content> contents,
            @JsonProperty("systemInstruction") Content systemInstruction,
            List<Tool> tools,
            @JsonProperty("generationConfig") GenerationConfig generationConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(String role, List<Part> parts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(
            String text,
            @JsonProperty("functionCall") FunctionCallPart functionCall,
            @JsonProperty("functionResponse") FunctionResponsePart functionResponse,
            @JsonProperty("inlineData") InlineData inlineData,
            Boolean thought) {

        public static Part text(String text) {
            return new Part(text, null, null, null, null);
        }

        public static Part functionCall(String name, Map<String, Object> args) {
            return new Part(null, new FunctionCallPart(name, args), null, null, null);
        }

        public static Part functionResponse(String name, Map<String, Object> response) {
            return new Part(null, null, new FunctionResponsePart(name, response), null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionCallPart(String name, Map<String, Object> args) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionResponsePart(String name, Map<String, Object> response) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InlineData(String mimeType, String data) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
            @JsonProperty("functionDeclarations") List<FunctionDeclaration> functionDeclarations,
            @JsonProperty("googleSearch") Map<String, Object> googleSearch,
            @JsonProperty("codeExecution") Map<String, Object> codeExecution) {

        public static Tool withGoogleSearch() {
            return new Tool(null, Map.of(), null);
        }

        public static Tool withCodeExecution() {
            return new Tool(null, null, Map.of());
        }

        public static Tool withFunctionDeclarations(List<FunctionDeclaration> decls) {
            return new Tool(decls, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FunctionDeclaration(
            String name, String description, @JsonProperty("parameters") Object parameters) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerationConfig(
            Double temperature,
            @JsonProperty("topP") Double topP,
            @JsonProperty("topK") Integer topK,
            @JsonProperty("maxOutputTokens") Integer maxOutputTokens,
            @JsonProperty("stopSequences") List<String> stopSequences,
            @JsonProperty("frequencyPenalty") Double frequencyPenalty,
            @JsonProperty("presencePenalty") Double presencePenalty,
            @JsonProperty("responseMimeType") String responseMimeType,
            @JsonProperty("responseModalities") List<String> responseModalities,
            @JsonProperty("thinkingConfig") ThinkingConfig thinkingConfig,
            @JsonProperty("speechConfig") SpeechConfig speechConfig) {

        public GenerationConfig withoutTemperature() {
            return new GenerationConfig(null, topP, topK, maxOutputTokens, stopSequences,
                    frequencyPenalty, presencePenalty, responseMimeType, responseModalities,
                    thinkingConfig, speechConfig);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ThinkingConfig(
            @JsonProperty("thinkingBudget") Integer thinkingBudget,
            @JsonProperty("includeThoughts") Boolean includeThoughts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpeechConfig(@JsonProperty("voiceConfig") VoiceConfig voiceConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VoiceConfig(
            @JsonProperty("prebuiltVoiceConfig") PrebuiltVoiceConfig prebuiltVoiceConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrebuiltVoiceConfig(@JsonProperty("voiceName") String voiceName) {}

    // --- Response DTOs ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateContentResponse(
            List<Candidate> candidates,
            @JsonProperty("usageMetadata") UsageMetadata usageMetadata,
            @JsonProperty("responseId") String responseId) {

        /** Concatenate text from non-thought parts, mirroring SDK result.text(). */
        public String text() {
            if (candidates == null || candidates.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Candidate c : candidates) {
                if (c.content() == null || c.content().parts() == null) continue;
                for (Part p : c.content().parts()) {
                    if (!Boolean.TRUE.equals(p.thought()) && p.text() != null) {
                        sb.append(p.text());
                    }
                }
            }
            return sb.toString();
        }

        /** Collect all functionCall parts from all candidates. */
        public List<FunctionCallPart> functionCalls() {
            if (candidates == null) return List.of();
            return candidates.stream()
                    .filter(c -> c.content() != null && c.content().parts() != null)
                    .flatMap(c -> c.content().parts().stream())
                    .filter(p -> p.functionCall() != null)
                    .map(Part::functionCall)
                    .toList();
        }

        /** Finish reason from first candidate. */
        public String finishReason() {
            if (candidates == null || candidates.isEmpty()) return null;
            return candidates.get(0).finishReason();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content, @JsonProperty("finishReason") String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageMetadata(
            @JsonProperty("promptTokenCount") Integer promptTokenCount,
            @JsonProperty("candidatesTokenCount") Integer candidatesTokenCount,
            @JsonProperty("thoughtsTokenCount") Integer thoughtsTokenCount) {}

    // --- Embeddings ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbedContentRequest(
            Content content,
            @JsonProperty("taskType") String taskType,
            @JsonProperty("outputDimensionality") Integer outputDimensionality) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbedContentResponse(@JsonProperty("embedding") Embedding embedding) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embedding(@JsonProperty("values") List<Float> values) {}

    // --- Image generation ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerateImagesRequest(
            @JsonProperty("prompt") ImagePrompt prompt,
            @JsonProperty("generationConfig") GenerateImagesConfig generationConfig) {}

    public record ImagePrompt(@JsonProperty("text") String text) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerateImagesConfig(
            @JsonProperty("numberOfImages") Integer numberOfImages,
            @JsonProperty("outputMimeType") String outputMimeType,
            @JsonProperty("includeSafetyAttributes") Boolean includeSafetyAttributes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateImagesResponse(
            @JsonProperty("predictions") List<ImagePrediction> predictions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImagePrediction(
            @JsonProperty("bytesBase64Encoded") String bytesBase64Encoded,
            @JsonProperty("mimeType") String mimeType) {}

    // --- Video generation ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerateVideosRequest(
            @JsonProperty("instances") List<Map<String, Object>> instances,
            @JsonProperty("parameters") GenerateVideosConfig parameters) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerateVideosConfig(
            @JsonProperty("numberOfVideos") Integer numberOfVideos,
            @JsonProperty("durationSeconds") Integer durationSeconds,
            @JsonProperty("aspectRatio") String aspectRatio,
            Integer seed,
            @JsonProperty("negativePrompt") String negativePrompt,
            @JsonProperty("personGeneration") String personGeneration,
            String resolution,
            @JsonProperty("generateAudio") Boolean generateAudio,
            Integer fps) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateVideosOperation(
            String name,
            Boolean done,
            @JsonProperty("error") OperationError error,
            @JsonProperty("response") GenerateVideosResult response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateVideosResult(@JsonProperty("videos") List<GeneratedVideo> videos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratedVideo(
            @JsonProperty("bytesBase64Encoded") String bytesBase64Encoded,
            @JsonProperty("uri") String uri,
            @JsonProperty("mimeType") String mimeType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OperationError(Integer code, String message) {}
}
