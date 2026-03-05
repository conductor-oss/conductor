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

import org.springframework.ai.model.ModelResult;

/**
 * Represents a single video generation result.
 *
 * <p>Mirrors Spring AI's {@code ImageGeneration} pattern. Implements {@link ModelResult} with
 * {@link Video} as the output type, making it compatible with the Spring AI model abstraction.
 */
public class VideoGeneration implements ModelResult<Video> {

    private Video video;
    private VideoGenerationMetadata videoGenerationMetadata;

    public VideoGeneration(Video video) {
        this(video, null);
    }

    public VideoGeneration(Video video, VideoGenerationMetadata videoGenerationMetadata) {
        this.video = video;
        this.videoGenerationMetadata = videoGenerationMetadata;
    }

    @Override
    public Video getOutput() {
        return video;
    }

    @Override
    public VideoGenerationMetadata getMetadata() {
        return videoGenerationMetadata;
    }

    @Override
    public String toString() {
        return "VideoGeneration{video=%s, metadata=%s}".formatted(video, videoGenerationMetadata);
    }
}
