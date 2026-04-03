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
package org.conductoross.conductor.ai.providers.openai.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Low-level REST client for the OpenAI Video (Sora) API using OkHttp.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /v1/videos (multipart/form-data) - Submit a video generation job
 *   <li>GET /v1/videos/{id} (JSON) - Poll job status
 *   <li>GET /v1/videos/{id}/content (binary) - Download completed MP4
 *   <li>GET /v1/videos/{id}/content?variant=thumbnail (binary) - Download thumbnail
 * </ul>
 *
 * @see <a href="https://platform.openai.com/docs/guides/video-generation">OpenAI Video Generation
 *     Guide</a>
 */
@Slf4j
public class OpenAIVideoApi {

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIVideoApi(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com";
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.MINUTES)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build();
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    /**
     * Submit a video generation job via multipart/form-data POST.
     *
     * @param params The video creation parameters
     * @return The initial job status with id and status fields
     */
    public VideoStatusResponse submitVideoJob(VideoCreateParams params) throws IOException {
        MultipartBody.Builder bodyBuilder =
                new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("prompt", params.prompt())
                        .addFormDataPart("model", params.model());

        if (params.size() != null) {
            bodyBuilder.addFormDataPart("size", params.size());
        }
        if (params.seconds() != null) {
            bodyBuilder.addFormDataPart("seconds", params.seconds());
        }

        // Optional image reference (file upload)
        if (params.inputReference() != null && params.inputReference().length > 0) {
            String mimeType =
                    params.inputReferenceMimeType() != null
                            ? params.inputReferenceMimeType()
                            : "image/jpeg";
            String ext = extensionForMimeType(mimeType);
            RequestBody fileBody =
                    RequestBody.create(params.inputReference(), MediaType.parse(mimeType));
            bodyBuilder.addFormDataPart("input_reference", "input." + ext, fileBody);
        }

        Request request =
                new Request.Builder()
                        .url(baseUrl + "/v1/videos")
                        .header("Authorization", "Bearer " + apiKey)
                        .post(bodyBuilder.build())
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = readResponseBody(response);
            if (!response.isSuccessful()) {
                throw new IOException(
                        "OpenAI Video API submit failed with status %d: %s"
                                .formatted(response.code(), responseBody));
            }
            return objectMapper.readValue(responseBody, VideoStatusResponse.class);
        }
    }

    /**
     * Poll the status of a video generation job.
     *
     * @param videoId The video job ID
     * @return Current status including progress percentage
     */
    public VideoStatusResponse getVideoStatus(String videoId) throws IOException {
        Request request =
                new Request.Builder()
                        .url(baseUrl + "/v1/videos/" + videoId)
                        .header("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = readResponseBody(response);
            if (!response.isSuccessful()) {
                throw new IOException(
                        "OpenAI Video API status check failed with status %d: %s"
                                .formatted(response.code(), responseBody));
            }
            return objectMapper.readValue(responseBody, VideoStatusResponse.class);
        }
    }

    /**
     * Download the completed video as a streaming InputStream. The caller is responsible for
     * closing the returned stream.
     *
     * <p>Note: The underlying OkHttp response is not auto-closed here since the caller needs to
     * consume the stream. The stream wrapper closes the response when the stream is closed.
     *
     * @param videoId The video job ID
     * @return InputStream of the MP4 binary data
     */
    public InputStream downloadVideoStream(String videoId) throws IOException {
        Request request =
                new Request.Builder()
                        .url(baseUrl + "/v1/videos/" + videoId + "/content")
                        .header("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();

        // Do not use try-with-resources here: the caller owns the stream lifecycle
        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            String errorBody = readResponseBody(response);
            response.close();
            throw new IOException(
                    "OpenAI Video download failed with status %d: %s"
                            .formatted(response.code(), errorBody));
        }

        ResponseBody body = response.body();
        if (body == null) {
            response.close();
            throw new IOException("OpenAI Video download returned empty body");
        }
        return body.byteStream();
    }

    /**
     * Download the completed video as a byte array.
     *
     * @param videoId The video job ID
     * @return byte array of the MP4 binary data
     */
    public byte[] downloadVideo(String videoId) throws IOException {
        Request request =
                new Request.Builder()
                        .url(baseUrl + "/v1/videos/" + videoId + "/content")
                        .header("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(
                        "OpenAI Video download failed with status %d".formatted(response.code()));
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("OpenAI Video download returned empty body");
            }
            return body.bytes();
        }
    }

    /**
     * Download the thumbnail for a completed video.
     *
     * @param videoId The video job ID
     * @return byte array of the thumbnail image (webp format)
     */
    public byte[] downloadThumbnail(String videoId) throws IOException {
        Request request =
                new Request.Builder()
                        .url(baseUrl + "/v1/videos/" + videoId + "/content?variant=thumbnail")
                        .header("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(
                        "OpenAI Video thumbnail download failed with status %d"
                                .formatted(response.code()));
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("OpenAI Video thumbnail download returned empty body");
            }
            return body.bytes();
        }
    }

    // -- Helpers --

    /** Safely read the response body as a string, returning empty string if body is null. */
    private String readResponseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        return body != null ? body.string() : "";
    }

    /** Map a MIME type to a file extension for the multipart upload filename. */
    private String extensionForMimeType(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    // -- DTOs --

    /** Parameters for creating a video generation job. */
    public record VideoCreateParams(
            String prompt,
            String model,
            String size,
            String seconds,
            byte[] inputReference,
            String inputReferenceMimeType) {}

    /** Response from video status and creation endpoints. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoStatusResponse(
            String id,
            String object,
            @JsonProperty("created_at") long createdAt,
            String status,
            String model,
            int progress,
            String seconds,
            String size) {}
}
