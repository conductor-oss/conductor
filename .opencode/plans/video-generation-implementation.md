# Video Generation Implementation Plan - Phases 2-5

## Decisions Made
1. **Full Spring AI mirror** for the video framework (PR-able to spring-ai)
2. **InputStream support** in DocumentLoader for streaming large video files
3. **Download bytes always** for Gemini Veo (don't pass through GCS URIs)
4. Phase 2 must complete first; then Phases 3, 4, 5 can proceed in any order

---

## Phase 2: Restructure Video Framework (Spring AI Pattern)

All files in `ai/src/main/java/org/conductoross/conductor/ai/video/`.

### Type Hierarchy (mirroring Spring AI Image*)

```
ModelOptions (spring-ai marker)
  +-- VideoOptions (interface)

ResultMetadata (spring-ai marker)
  +-- VideoGenerationMetadata (marker interface)

MutableResponseMetadata (spring-ai class)
  +-- VideoResponseMetadata (class: jobId, status, errorMessage)

ModelResult<Video>
  +-- VideoGeneration (class)

ModelRequest<List<VideoMessage>>
  +-- VideoPrompt (class)

ModelResponse<VideoGeneration>
  +-- VideoResponse (class)

Model<VideoPrompt, VideoResponse>
  +-- VideoModel (@FunctionalInterface)
      +-- AsyncVideoModel (adds checkStatus)
```

### 2a. REWRITE: `VideoModel.java`

Current: has `call()` + `default checkStatus()`.
Target: `@FunctionalInterface extends Model<VideoPrompt, VideoResponse>`, only `call()`.

```java
package org.conductoross.conductor.ai.video;

import org.springframework.ai.model.Model;

@FunctionalInterface
public interface VideoModel extends Model<VideoPrompt, VideoResponse> {
    @Override
    VideoResponse call(VideoPrompt prompt);
}
```

### 2b. NEW: `AsyncVideoModel.java`

```java
package org.conductoross.conductor.ai.video;

public interface AsyncVideoModel extends VideoModel {
    VideoResponse checkStatus(String jobId);
}
```

### 2c. REWRITE: `VideoOptions.java`

Current: standalone interface with video getters.
Target: `extends ModelOptions`, add new fields for OpenAI/Gemini.

```java
package org.conductoross.conductor.ai.video;

import org.springframework.ai.model.ModelOptions;

public interface VideoOptions extends ModelOptions {
    // Core params (mirror ImageOptions: getN, getModel, getWidth, getHeight, getResponseFormat, getStyle)
    String getModel();
    Integer getN();
    Integer getWidth();
    Integer getHeight();
    String getOutputFormat();
    String getStyle();

    // Video-specific
    Integer getDuration();
    Integer getFps();
    String getAspectRatio();
    String getInputImage();        // URL or base64 for image-to-video
    String getSize();              // "1280x720" format (OpenAI Sora)

    // Advanced
    String getMotion();
    Integer getSeed();
    Float getGuidanceScale();
    String getNegativePrompt();    // Gemini Veo
    String getPersonGeneration();  // Gemini: "dont_allow" / "allow_adult"
    String getResolution();        // Gemini: "720p" / "1080p"
    Boolean getGenerateAudio();    // Gemini Veo 3+

    // Thumbnail
    Boolean getGenerateThumbnail();
    Integer getThumbnailTimestamp();
}
```

### 2d. EDIT: `VideoOptionsBuilder.java`

Add new fields to existing Lombok @Data @Builder class:

```java
// ADD these fields after existing ones:
private String inputImage;
private String negativePrompt;
private String personGeneration;
private String resolution;
private Boolean generateAudio;
private String size;
```

### 2e. NEW: `VideoMessage.java`

Extract from `VideoPrompt.VideoMessage` inner class. Mirror `ImageMessage`:

```java
package org.conductoross.conductor.ai.video;

public class VideoMessage {
    private String text;
    private Float weight;

    public VideoMessage(String text) { this(text, null); }
    public VideoMessage(String text, Float weight) {
        this.text = text;
        this.weight = weight;
    }

    public String getText() { return text; }
    public Float getWeight() { return weight; }
}
```

### 2f. REWRITE: `VideoPrompt.java`

Implement `ModelRequest<List<VideoMessage>>`. Remove `inputImage` (now in VideoOptions).

```java
package org.conductoross.conductor.ai.video;

import java.util.Collections;
import java.util.List;
import org.springframework.ai.model.ModelRequest;

public class VideoPrompt implements ModelRequest<List<VideoMessage>> {
    private final List<VideoMessage> messages;
    private VideoOptions videoOptions;

    public VideoPrompt(List<VideoMessage> messages) {
        this(messages, new VideoOptionsBuilder());
    }
    public VideoPrompt(List<VideoMessage> messages, VideoOptions options) {
        this.messages = List.copyOf(messages);
        this.videoOptions = options;
    }
    public VideoPrompt(String instructions) {
        this(instructions, new VideoOptionsBuilder());
    }
    public VideoPrompt(String instructions, VideoOptions options) {
        this(List.of(new VideoMessage(instructions)), options);
    }

    @Override
    public List<VideoMessage> getInstructions() { return messages; }

    @Override
    public VideoOptions getOptions() { return videoOptions; }
}
```

### 2g. NEW: `VideoGenerationMetadata.java`

```java
package org.conductoross.conductor.ai.video;

import org.springframework.ai.model.ResultMetadata;

public interface VideoGenerationMetadata extends ResultMetadata {
    // Marker interface, same pattern as ImageGenerationMetadata
}
```

### 2h. NEW: `VideoGeneration.java`

Extract from `VideoResponse.VideoGeneration`. Implement `ModelResult<Video>`:

```java
package org.conductoross.conductor.ai.video;

import org.springframework.ai.model.ModelResult;

public class VideoGeneration implements ModelResult<Video> {
    private Video video;
    private VideoGenerationMetadata videoGenerationMetadata;

    public VideoGeneration(Video video) {
        this(video, null);
    }
    public VideoGeneration(Video video, VideoGenerationMetadata metadata) {
        this.video = video;
        this.videoGenerationMetadata = metadata;
    }

    @Override
    public Video getOutput() { return video; }

    @Override
    public VideoGenerationMetadata getMetadata() { return videoGenerationMetadata; }
}
```

### 2i. NEW: `VideoResponseMetadata.java`

Extract from `VideoResponse.VideoResponseMetadata`. Extend Spring AI's `MutableResponseMetadata`:

```java
package org.conductoross.conductor.ai.video;

import org.springframework.ai.model.MutableResponseMetadata;

public class VideoResponseMetadata extends MutableResponseMetadata {
    private final Long created;

    public static final String KEY_JOB_ID = "jobId";
    public static final String KEY_STATUS = "status";
    public static final String KEY_ERROR_MESSAGE = "errorMessage";

    public VideoResponseMetadata() { this(System.currentTimeMillis()); }
    public VideoResponseMetadata(Long created) { this.created = created; }

    public Long getCreated() { return created; }

    // Convenience accessors for common video metadata
    public String getJobId() { return get(KEY_JOB_ID); }
    public void setJobId(String jobId) { put(KEY_JOB_ID, jobId); }

    public String getStatus() { return get(KEY_STATUS); }
    public void setStatus(String status) { put(KEY_STATUS, status); }

    public String getErrorMessage() { return get(KEY_ERROR_MESSAGE); }
    public void setErrorMessage(String msg) { put(KEY_ERROR_MESSAGE, msg); }
}
```

### 2j. REWRITE: `VideoResponse.java`

Implement `ModelResponse<VideoGeneration>`. Remove inner classes.

```java
package org.conductoross.conductor.ai.video;

import java.util.List;
import org.springframework.ai.model.ModelResponse;

public class VideoResponse implements ModelResponse<VideoGeneration> {
    private final List<VideoGeneration> videoGenerations;
    private final VideoResponseMetadata videoResponseMetadata;

    public VideoResponse(List<VideoGeneration> generations) {
        this(generations, new VideoResponseMetadata());
    }
    public VideoResponse(List<VideoGeneration> generations, VideoResponseMetadata metadata) {
        this.videoGenerations = List.copyOf(generations);
        this.videoResponseMetadata = metadata;
    }

    @Override
    public VideoGeneration getResult() {
        return videoGenerations.isEmpty() ? null : videoGenerations.getFirst();
    }

    @Override
    public List<VideoGeneration> getResults() { return videoGenerations; }

    @Override
    public VideoResponseMetadata getMetadata() { return videoResponseMetadata; }
}
```

### 2k. REWRITE: `Video.java`

Simplify to match `Image(url, b64Json)`:

```java
package org.conductoross.conductor.ai.video;

public class Video {
    private String url;
    private String b64Json;

    public Video(String url, String b64Json) {
        this.url = url;
        this.b64Json = b64Json;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getB64Json() { return b64Json; }
    public void setB64Json(String b64Json) { this.b64Json = b64Json; }
}
```

### 2l. EDIT: `AIModel.java` (line 119-136)

Update `getVideoOptions()` to include new fields:

```java
default VideoOptions getVideoOptions(VideoGenRequest input) {
    return VideoOptionsBuilder.builder()
            .model(input.getModel())
            .duration(input.getDuration())
            .width(input.getWidth())
            .height(input.getHeight())
            .fps(input.getFps())
            .outputFormat(input.getOutputFormat())
            .n(input.getN())
            .style(input.getStyle())
            .motion(input.getMotion())
            .seed(input.getSeed())
            .guidanceScale(input.getGuidanceScale())
            .aspectRatio(input.getAspectRatio())
            .generateThumbnail(input.getGenerateThumbnail())
            .thumbnailTimestamp(input.getThumbnailTimestamp())
            // New fields for OpenAI Sora and Gemini Veo
            .inputImage(input.getInputImage())
            .negativePrompt(input.getNegativePrompt())
            .personGeneration(input.getPersonGeneration())
            .resolution(input.getResolution())
            .generateAudio(input.getGenerateAudio())
            .size(input.getSize())
            .build();
}
```

### 2m. EDIT: `VideoGenRequest.java`

Add new fields after existing ones (around line 49):

```java
// New fields for OpenAI Sora and Gemini Veo
private String negativePrompt;     // Gemini Veo: what to exclude
private String personGeneration;   // Gemini: "dont_allow" / "allow_adult"
private String resolution;         // Gemini: "720p" / "1080p"
private Boolean generateAudio;     // Gemini Veo 3+
private String size;               // OpenAI: "1280x720" format string
```

Update class javadoc: replace "Stability AI" references with "OpenAI Sora, Gemini Veo".

### 2n. ADD InputStream support to DocumentLoader

**EDIT: `DocumentLoader.java`** - Add default method:

```java
// Add after existing upload method:
default String upload(Map<String, String> headers, String contentType,
                      java.io.InputStream data, String fileURI) {
    try {
        return upload(headers, contentType, data.readAllBytes(), fileURI);
    } catch (java.io.IOException e) {
        throw new RuntimeException("Failed to read InputStream", e);
    }
}
```

**EDIT: `FileSystemDocumentLoader.java`** - Override with streaming implementation:

```java
@Override
public String upload(Map<String, String> headers, String contentType,
                     java.io.InputStream data, String fileURI) {
    try {
        if (data == null) {
            return null;
        }
        Path path = Path.of(fileURI.replace("file://", ""));
        var result = path.toFile().getParentFile().mkdirs();
        Files.copy(data, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return path.toAbsolutePath().toString();
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}
```

**EDIT: `LLMHelper.java`** - Add InputStream-aware `storeMedia` overload:

```java
// New method alongside existing storeMedia:
void storeMediaStream(String location, String mimeType,
                      java.io.InputStream stream,
                      org.conductoross.conductor.ai.models.Media mediaRef) {
    Optional<DocumentLoader> docLoader = documentLoaders.stream()
            .filter(dl -> dl.supports(location))
            .findFirst();
    docLoader.ifPresent(loader -> {
        String ext = getExtensionFromMimeType(mimeType);
        String uniqueLocation = location + "_" + java.util.UUID.randomUUID() + ext;
        loader.upload(Map.of(), mimeType, stream, uniqueLocation);
        mediaRef.setLocation(uniqueLocation);
    });
}
```

### Phase 2 File Summary

| File | Action | Lines Changed |
|------|--------|--------------|
| `video/VideoModel.java` | REWRITE | Remove checkStatus, add @FunctionalInterface, extends Model |
| `video/AsyncVideoModel.java` | **NEW** | Interface with checkStatus(String) |
| `video/VideoOptions.java` | REWRITE | extends ModelOptions, add 6 new getters |
| `video/VideoOptionsBuilder.java` | EDIT | Add 6 new fields |
| `video/VideoMessage.java` | **NEW** | Top-level class (text, weight) |
| `video/VideoPrompt.java` | REWRITE | implements ModelRequest, remove inputImage |
| `video/VideoGenerationMetadata.java` | **NEW** | Marker interface extends ResultMetadata |
| `video/VideoGeneration.java` | **NEW** | implements ModelResult<Video> |
| `video/VideoResponseMetadata.java` | **NEW** | extends MutableResponseMetadata |
| `video/VideoResponse.java` | REWRITE | implements ModelResponse, remove inner classes |
| `video/Video.java` | REWRITE | Simplify to (url, b64Json) |
| `AIModel.java` | EDIT | Update getVideoOptions() for new fields |
| `models/VideoGenRequest.java` | EDIT | Add 5 new fields |
| `document/DocumentLoader.java` | EDIT | Add default InputStream upload method |
| `document/FileSystemDocumentLoader.java` | EDIT | Override with Files.copy() |
| `LLMHelper.java` | EDIT | Add storeMediaStream() method |

---

## Phase 3: OpenAI Sora Video Provider

### 3a. NEW: `providers/openai/api/OpenAIVideoApi.java`

REST client using `java.net.http.HttpClient` for the OpenAI Video API.

**Endpoints (from official docs, verified):**
- `POST https://api.openai.com/v1/videos` (multipart/form-data) - Submit job
- `GET https://api.openai.com/v1/videos/{id}` (JSON) - Poll status
- `GET https://api.openai.com/v1/videos/{id}/content` (binary stream) - Download MP4
- `GET https://api.openai.com/v1/videos/{id}/content?variant=thumbnail` (binary) - Download thumbnail

**Inner records:**
```java
record VideoCreateParams(
    String prompt,
    String model,             // sora-2, sora-2-pro
    String size,              // "1280x720"
    String seconds,           // "5", "8", "10" (NOTE: string, not int)
    byte[] inputReference,    // optional image bytes
    String inputReferenceMimeType
)

record VideoStatusResponse(
    String id,
    String object,
    long created_at,
    String status,            // queued, in_progress, completed, failed
    String model,
    int progress,             // 0-100
    String seconds,
    String size
)
```

**Methods:**
```java
public class OpenAIVideoApi {
    private final String apiKey;
    private final String baseUrl;     // e.g., "https://api.openai.com"
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIVideoApi(String apiKey, String baseUrl)

    // Submit - builds multipart/form-data, returns status
    public VideoStatusResponse submitVideoJob(VideoCreateParams params)

    // Poll - GET JSON
    public VideoStatusResponse getVideoStatus(String videoId)

    // Download MP4 - returns InputStream for streaming to DocumentLoader
    public InputStream downloadVideoStream(String videoId)

    // Download thumbnail - returns byte[]
    public byte[] downloadThumbnail(String videoId)
}
```

Key implementation details:
- `submitVideoJob`: Build multipart/form-data with boundary. Fields: `prompt`, `model`, `size`, `seconds`. If `inputReference` present, add as file part with Content-Type.
- `downloadVideoStream`: Returns `HttpResponse<InputStream>` body directly (streaming, not buffered). This feeds into `DocumentLoader.upload(InputStream)`.
- Auth header: `Authorization: Bearer {apiKey}`

### 3b. NEW: `providers/openai/OpenAIVideoModel.java`

Implements `AsyncVideoModel`:

```java
public class OpenAIVideoModel implements AsyncVideoModel {
    private final OpenAIVideoApi api;
    private final List<DocumentLoader> documentLoaders; // For streaming storage

    @Override
    public VideoResponse call(VideoPrompt prompt) {
        // 1. Extract text + options
        // 2. Resolve inputImage if present (download URL / decode base64)
        // 3. Build size string from options
        // 4. Submit via api.submitVideoJob()
        // 5. Return VideoResponse with jobId in metadata, status = "PROCESSING"
    }

    @Override
    public VideoResponse checkStatus(String jobId) {
        // 1. Poll via api.getVideoStatus(jobId)
        // 2. If completed:
        //    - Get InputStream via api.downloadVideoStream(jobId)
        //    - Create Video with b64Json = null (will be stored via streaming)
        //    - Store the InputStream reference for LLMHelper to handle
        //    Actually: download as byte[], create Media with byte[] data
        //    (DocumentLoader InputStream support is an optimization path;
        //     for now we can download to byte[] and let storeMedia handle it)
        // 3. If failed: set error in metadata
        // 4. If still processing: return processing status
    }
}
```

**Revised approach for video download:** Since the existing `LLMHelper.storeMedia()` flow expects `Media` objects with `byte[] data`, and we're adding InputStream support as an enhancement, the OpenAIVideoModel will:
1. Download video bytes via `api.downloadVideo(jobId)` (returns byte[])  
2. Optionally download thumbnail bytes
3. Build `VideoGeneration` with `Video(null, base64encoded)`
4. The caller (OpenAI.checkVideoStatus) converts to `Media` with byte[] data
5. `LLMHelper.storeMedia()` writes to disk

For the InputStream optimization: `OpenAIVideoApi.downloadVideoStream()` returns InputStream. The provider can call `documentLoader.upload(InputStream)` directly instead of buffering. This can be wired through `LLMHelper.storeMediaStream()`.

### 3c. MODIFY: `providers/openai/OpenAI.java`

Add after line 63 (field declarations):
```java
private final OpenAIVideoModel videoModel;
```

Add in constructor after `this.speechModel = ...`:
```java
this.videoModel = createVideoModel();
```

Add new methods:
```java
@Override
public VideoModel getVideoModel() {
    return this.videoModel;
}

@Override
public LLMResponse generateVideo(VideoGenRequest request) {
    VideoOptions options = getVideoOptions(request);
    VideoPrompt videoPrompt = new VideoPrompt(request.getPrompt(), options);
    VideoResponse response = videoModel.call(videoPrompt);

    return LLMResponse.builder()
            .result(response.getMetadata().getJobId())
            .finishReason(response.getMetadata().getStatus())
            .build();
}

@Override
public LLMResponse checkVideoStatus(VideoGenRequest request) {
    VideoResponse response = videoModel.checkStatus(request.getJobId());
    String status = response.getMetadata().getStatus();

    LLMResponse.LLMResponseBuilder builder = LLMResponse.builder()
            .finishReason(status);

    if ("COMPLETED".equals(status)) {
        List<Media> mediaList = new ArrayList<>();
        for (VideoGeneration gen : response.getResults()) {
            Video video = gen.getOutput();
            if (video.getB64Json() != null) {
                mediaList.add(Media.builder()
                        .data(Base64.getDecoder().decode(video.getB64Json()))
                        .mimeType("video/mp4")
                        .build());
            }
        }
        builder.media(mediaList);
    }

    return builder.build();
}

private OpenAIVideoModel createVideoModel() {
    // Remove /v1 suffix for base URL (OpenAIVideoApi adds its own paths)
    String baseUrl = config.getBaseURL();
    if (baseUrl != null && baseUrl.endsWith("/v1")) {
        baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
    }
    OpenAIVideoApi videoApi = new OpenAIVideoApi(config.getApiKey(), baseUrl);
    return new OpenAIVideoModel(videoApi);
}
```

New imports:
```java
import org.conductoross.conductor.ai.models.VideoGenRequest;
import org.conductoross.conductor.ai.video.*;
import org.conductoross.conductor.ai.providers.openai.api.OpenAIVideoApi;
```

### Phase 3 File Summary

| File | Action |
|------|--------|
| `providers/openai/api/OpenAIVideoApi.java` | **NEW** (~200 lines) |
| `providers/openai/OpenAIVideoModel.java` | **NEW** (~150 lines) |
| `providers/openai/OpenAI.java` | EDIT (add ~60 lines) |

---

## Phase 4: Gemini Veo Video Provider

### 4a. NEW: `providers/gemini/GeminiVideoModel.java`

Implements `AsyncVideoModel` using the Google GenAI SDK (already in dependencies).

```java
package org.conductoross.conductor.ai.providers.gemini;

import com.google.genai.Client;
import com.google.genai.types.*;
import org.conductoross.conductor.ai.video.*;
import java.util.*;

public class GeminiVideoModel implements AsyncVideoModel {
    private final Client client;

    public GeminiVideoModel(Client client) { this.client = client; }

    @Override
    public VideoResponse call(VideoPrompt prompt) {
        VideoOptions opts = prompt.getOptions();
        String text = prompt.getInstructions().getFirst().getText();

        // Build GenerateVideosConfig from VideoOptions
        GenerateVideosConfig.Builder configBuilder = GenerateVideosConfig.builder()
                .numberOfVideos(opts.getN() != null ? opts.getN() : 1);

        if (opts.getDuration() != null) configBuilder.durationSeconds(opts.getDuration());
        if (opts.getAspectRatio() != null) configBuilder.aspectRatio(opts.getAspectRatio());
        if (opts.getSeed() != null) configBuilder.seed(opts.getSeed());
        if (opts.getNegativePrompt() != null) configBuilder.negativePrompt(opts.getNegativePrompt());
        if (opts.getPersonGeneration() != null) configBuilder.personGeneration(opts.getPersonGeneration());
        if (opts.getResolution() != null) configBuilder.resolution(opts.getResolution());
        if (opts.getGenerateAudio() != null) configBuilder.generateAudio(opts.getGenerateAudio());
        if (opts.getFps() != null) configBuilder.fps(opts.getFps());

        // Resolve input image for image-to-video
        Image inputImage = null;
        if (opts.getInputImage() != null) {
            inputImage = resolveGeminiImage(opts.getInputImage());
        }

        // Submit async operation
        GenerateVideosOperation operation = client.models.generateVideos(
                opts.getModel(), text, inputImage, configBuilder.build());

        // Return operation name as job ID
        String operationName = operation.name().orElse(null);
        VideoResponseMetadata metadata = new VideoResponseMetadata();
        metadata.setJobId(operationName);
        metadata.setStatus("PROCESSING");
        return new VideoResponse(List.of(), metadata);
    }

    @Override
    public VideoResponse checkStatus(String jobId) {
        // Build operation reference for polling
        GenerateVideosOperation opRef = GenerateVideosOperation.builder()
                .name(jobId).build();

        GenerateVideosOperation operation = client.operations.getVideosOperation(opRef, null);
        boolean isDone = operation.done().orElse(false);

        VideoResponseMetadata metadata = new VideoResponseMetadata();
        metadata.setJobId(jobId);

        if (isDone) {
            if (operation.error().isPresent()) {
                metadata.setStatus("FAILED");
                metadata.setErrorMessage(operation.error().get().toString());
                return new VideoResponse(List.of(), metadata);
            }

            // Extract videos
            GenerateVideosResponse response = operation.response().orElse(null);
            List<VideoGeneration> generations = new ArrayList<>();
            if (response != null) {
                for (GeneratedVideo gv : response.generatedVideos().orElse(List.of())) {
                    gv.video().ifPresent(video -> {
                        byte[] bytes = video.videoBytes().orElse(null);
                        String b64 = bytes != null ? Base64.getEncoder().encodeToString(bytes) : null;
                        String url = video.uri().orElse(null);

                        // If we got a GCS URI but no bytes, download the bytes
                        if (b64 == null && url != null && url.startsWith("gs://")) {
                            // Download from GCS URI via the SDK or HTTP
                            // The SDK typically returns bytes for Vertex AI
                            // For now, pass the URL and handle download in the provider
                        }

                        generations.add(new VideoGeneration(new Video(url, b64)));
                    });
                }
            }
            metadata.setStatus("COMPLETED");
            return new VideoResponse(generations, metadata);
        } else {
            metadata.setStatus("PROCESSING");
            return new VideoResponse(List.of(), metadata);
        }
    }

    private Image resolveGeminiImage(String inputImage) {
        if (inputImage.startsWith("data:")) {
            String base64Part = inputImage.substring(inputImage.indexOf(",") + 1);
            byte[] bytes = Base64.getDecoder().decode(base64Part);
            String mimeType = inputImage.substring(5, inputImage.indexOf(";"));
            return Image.builder().imageBytes(bytes).mimeType(mimeType).build();
        } else if (inputImage.startsWith("http://") || inputImage.startsWith("https://")) {
            byte[] bytes = downloadFromUrl(inputImage);
            return Image.builder().imageBytes(bytes).mimeType("image/png").build();
        } else {
            // Assume raw base64
            byte[] bytes = Base64.getDecoder().decode(inputImage);
            return Image.builder().imageBytes(bytes).mimeType("image/png").build();
        }
    }

    private byte[] downloadFromUrl(String url) {
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url)).GET().build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download image from " + url, e);
        }
    }
}
```

### 4b. MODIFY: `providers/gemini/GeminiVertex.java`

Extract `Client` creation to reusable method (currently duplicated in `getImageModel()` and `generateAudio()`):

```java
// Add private helper (refactoring existing duplication):
private Client createGenAIClient() {
    return Client.builder()
            .vertexAI(true)
            .credentials(config.getGoogleCredentials())
            .location(config.getLocation())
            .project(config.getProjectId())
            .build();
}
```

Update existing `getImageModel()` (line 158-166) and `generateAudio()` (lines 170-175) to use `createGenAIClient()`.

Add new video methods:

```java
@Override
public VideoModel getVideoModel() {
    return new GeminiVideoModel(createGenAIClient());
}

@Override
public LLMResponse generateVideo(VideoGenRequest request) {
    VideoOptions options = getVideoOptions(request);
    VideoPrompt videoPrompt = new VideoPrompt(request.getPrompt(), options);
    GeminiVideoModel videoModel = new GeminiVideoModel(createGenAIClient());
    VideoResponse response = videoModel.call(videoPrompt);

    return LLMResponse.builder()
            .result(response.getMetadata().getJobId())
            .finishReason(response.getMetadata().getStatus())
            .build();
}

@Override
public LLMResponse checkVideoStatus(VideoGenRequest request) {
    GeminiVideoModel videoModel = new GeminiVideoModel(createGenAIClient());
    VideoResponse response = videoModel.checkStatus(request.getJobId());
    String status = response.getMetadata().getStatus();

    LLMResponse.LLMResponseBuilder builder = LLMResponse.builder().finishReason(status);

    if ("COMPLETED".equals(status)) {
        List<org.conductoross.conductor.ai.models.Media> mediaList = new ArrayList<>();
        for (VideoGeneration gen : response.getResults()) {
            Video video = gen.getOutput();
            if (video.getB64Json() != null) {
                mediaList.add(org.conductoross.conductor.ai.models.Media.builder()
                        .data(Base64.getDecoder().decode(video.getB64Json()))
                        .mimeType("video/mp4")
                        .build());
            } else if (video.getUrl() != null) {
                // Download from URL (e.g., GCS URI) to get bytes
                byte[] bytes = downloadFromUrl(video.getUrl());
                mediaList.add(org.conductoross.conductor.ai.models.Media.builder()
                        .data(bytes)
                        .mimeType("video/mp4")
                        .build());
            }
        }
        builder.media(mediaList);
    }

    return builder.build();
}

private byte[] downloadFromUrl(String url) {
    try {
        var httpClient = java.net.http.HttpClient.newHttpClient();
        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url)).GET().build();
        var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
    } catch (Exception e) {
        throw new RuntimeException("Failed to download from " + url, e);
    }
}
```

New imports:
```java
import org.conductoross.conductor.ai.models.VideoGenRequest;
import org.conductoross.conductor.ai.video.*;
import java.util.Base64;
```

### Phase 4 File Summary

| File | Action |
|------|--------|
| `providers/gemini/GeminiVideoModel.java` | **NEW** (~150 lines) |
| `providers/gemini/GeminiVertex.java` | EDIT (~80 lines added, refactor Client creation) |

---

## Phase 5: Stability AI Image Provider (via Spring AI)

### 5a. EDIT: `ai/build.gradle`

Add after line 29 (`api "org.springframework.ai:spring-ai-ollama:${revSpringAI}"`):

```gradle
    api "org.springframework.ai:spring-ai-stability-ai:${revSpringAI}"
```

### 5b. NEW: `providers/stabilityai/StabilityAIConfiguration.java`

```java
package org.conductoross.conductor.ai.providers.stabilityai;

import org.conductoross.conductor.ai.ModelConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Component
@ConfigurationProperties(prefix = "conductor.ai.stabilityai")
@NoArgsConstructor
@AllArgsConstructor
public class StabilityAIConfiguration implements ModelConfiguration<StabilityAI> {

    private String apiKey;

    @Override
    public StabilityAI get() {
        return new StabilityAI(this);
    }
}
```

### 5c. NEW: `providers/stabilityai/StabilityAI.java`

Image-only provider:

```java
package org.conductoross.conductor.ai.providers.stabilityai;

import java.util.List;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.models.EmbeddingGenRequest;
import org.conductoross.conductor.ai.models.ImageGenRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.stabilityai.StabilityAiImageModel;
import org.springframework.ai.stabilityai.api.StabilityAiApi;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;

public class StabilityAI implements AIModel {

    public static final String NAME = "stabilityai";
    private final StabilityAiImageModel imageModel;

    public StabilityAI(StabilityAIConfiguration config) {
        StabilityAiApi api = new StabilityAiApi(config.getApiKey());
        this.imageModel = new StabilityAiImageModel(api);
    }

    @Override
    public String getModelProvider() { return NAME; }

    @Override
    public ImageModel getImageModel() { return this.imageModel; }

    @Override
    public ImageOptions getImageOptions(ImageGenRequest input) {
        return StabilityAiImageOptions.builder()
                .model(input.getModel())
                .N(input.getN())
                .width(input.getWidth())
                .height(input.getHeight())
                .responseFormat("b64_json")
                .stylePreset(input.getStyle())
                .build();
    }

    @Override
    public ChatModel getChatModel() {
        throw new UnsupportedOperationException(
                "Chat completion not supported by Stability AI provider");
    }

    @Override
    public List<Float> generateEmbeddings(EmbeddingGenRequest req) {
        throw new UnsupportedOperationException(
                "Embeddings not supported by Stability AI provider");
    }
}
```

### Phase 5 File Summary

| File | Action |
|------|--------|
| `ai/build.gradle` | EDIT (add 1 line) |
| `providers/stabilityai/StabilityAIConfiguration.java` | **NEW** (~25 lines) |
| `providers/stabilityai/StabilityAI.java` | **NEW** (~50 lines) |

---

## Execution Order

1. **Phase 2** first (framework restructure) - all other phases depend on this
2. **Phases 3, 4, 5** can be done in parallel after Phase 2
3. Run `./gradlew :conductor-ai:spotlessApply :conductor-ai:compileJava` after each phase
4. Final full build verification: `./gradlew :conductor-ai:spotlessApply && ./gradlew :conductor-ai:compileJava`

## Total New/Modified Files

| Category | New Files | Modified Files |
|----------|-----------|----------------|
| Phase 2 (Framework) | 5 | 11 |
| Phase 3 (OpenAI) | 2 | 1 |
| Phase 4 (Gemini) | 1 | 1 |
| Phase 5 (Stability) | 2 | 1 |
| **Total** | **10** | **14** |
