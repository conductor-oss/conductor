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
package org.conductoross.conductor.ai.video;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.model.ModelResponse;

/**
 * Response from a video generation request.
 *
 * <p>Mirrors Spring AI's {@code ImageResponse} pattern. Implements {@link ModelResponse} with
 * {@link VideoGeneration} as the result type. Contains the list of generated videos and
 * response-level metadata including job status for async operations.
 */
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
    public List<VideoGeneration> getResults() {
        return videoGenerations;
    }

    @Override
    public VideoResponseMetadata getMetadata() {
        return videoResponseMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VideoResponse that)) return false;
        return Objects.equals(videoGenerations, that.videoGenerations)
                && Objects.equals(videoResponseMetadata, that.videoResponseMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoGenerations, videoResponseMetadata);
    }

    @Override
    public String toString() {
        return "VideoResponse{generations=%s, metadata=%s}"
                .formatted(videoGenerations, videoResponseMetadata);
    }
}
