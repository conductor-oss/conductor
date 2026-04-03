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

import org.springframework.ai.model.ModelRequest;

/**
 * Prompt for video generation requests.
 *
 * <p>Mirrors Spring AI's {@code ImagePrompt} pattern. Implements {@link ModelRequest} with a list
 * of {@link VideoMessage} instructions and {@link VideoOptions} for model configuration.
 *
 * <p>Input images for image-to-video generation are specified via {@link
 * VideoOptions#getInputImage()} rather than as a field on the prompt itself.
 */
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

    public VideoPrompt(VideoMessage message, VideoOptions options) {
        this(List.of(message), options);
    }

    public VideoPrompt(String instructions, VideoOptions options) {
        this(List.of(new VideoMessage(instructions)), options);
    }

    public VideoPrompt(String instructions) {
        this(instructions, new VideoOptionsBuilder());
    }

    @Override
    public List<VideoMessage> getInstructions() {
        return messages;
    }

    @Override
    public VideoOptions getOptions() {
        return videoOptions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VideoPrompt that)) return false;
        return Objects.equals(messages, that.messages)
                && Objects.equals(videoOptions, that.videoOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, videoOptions);
    }

    @Override
    public String toString() {
        return "VideoPrompt{messages=%s, options=%s}".formatted(messages, videoOptions);
    }
}
