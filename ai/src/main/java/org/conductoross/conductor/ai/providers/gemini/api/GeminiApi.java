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

/**
 * DTO types for the Gemini REST API. HTTP client and request methods are added by subsequent tasks.
 */
public class GeminiApi {

    // --- Request DTOs ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerateContentRequest(
            List<Content> contents,
            @JsonProperty("systemInstruction") Content systemInstruction,
            List<Tool> tools,
            @JsonProperty("generationConfig") GenerationConfig generationConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(
            String role,
            List<Part> parts) {}

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
            String name,
            String description,
            @JsonProperty("parameters") Object parameters) {}

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
            @JsonProperty("speechConfig") SpeechConfig speechConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ThinkingConfig(
            @JsonProperty("thinkingBudget") Integer thinkingBudget,
            @JsonProperty("includeThoughts") Boolean includeThoughts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpeechConfig(
            @JsonProperty("voiceConfig") VoiceConfig voiceConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VoiceConfig(
            @JsonProperty("prebuiltVoiceConfig") PrebuiltVoiceConfig prebuiltVoiceConfig) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrebuiltVoiceConfig(
            @JsonProperty("voiceName") String voiceName) {}

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
    public record Candidate(
            Content content,
            @JsonProperty("finishReason") String finishReason) {}

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
    public record EmbedContentResponse(
            @JsonProperty("embedding") Embedding embedding) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embedding(
            @JsonProperty("values") List<Float> values) {}

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
    public record GenerateVideosResult(
            @JsonProperty("videos") List<GeneratedVideo> videos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratedVideo(
            @JsonProperty("bytesBase64Encoded") String bytesBase64Encoded,
            @JsonProperty("uri") String uri,
            @JsonProperty("mimeType") String mimeType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OperationError(Integer code, String message) {}
}
