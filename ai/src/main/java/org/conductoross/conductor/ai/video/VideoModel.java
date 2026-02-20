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

import org.springframework.ai.model.Model;

/**
 * Interface for video generation models.
 *
 * <p>Follows Spring AI's pattern for model interfaces (e.g., ChatModel, ImageModel). This is a
 * functional interface with a single {@code call} method that accepts a {@link VideoPrompt} and
 * returns a {@link VideoResponse}.
 *
 * <p>For providers that use asynchronous job submission and polling (most video providers), see
 * {@link AsyncVideoModel} which extends this interface with status-checking capability.
 *
 * @see AsyncVideoModel
 * @see Model
 */
@FunctionalInterface
public interface VideoModel extends Model<VideoPrompt, VideoResponse> {

    /**
     * Generate a video based on the provided prompt.
     *
     * @param prompt The video generation prompt containing instructions and options
     * @return The video generation response
     */
    @Override
    VideoResponse call(VideoPrompt prompt);
}
