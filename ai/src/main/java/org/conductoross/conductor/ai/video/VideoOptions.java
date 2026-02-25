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

import org.springframework.ai.model.ModelOptions;

/**
 * Options for video generation requests.
 *
 * <p>Follows Spring AI's pattern for model options interfaces (e.g., ImageOptions). Extends {@link
 * ModelOptions} for compatibility with the Spring AI model abstraction.
 *
 * <p>Includes portable options that work across providers, plus provider-specific options (e.g.,
 * negativePrompt for Gemini Veo, size for OpenAI Sora).
 */
public interface VideoOptions extends ModelOptions {

    // Core parameters (mirrors ImageOptions pattern: getModel, getN, getWidth, getHeight, etc.)

    /** The model to use for video generation (e.g., "sora-2", "veo-2.0-generate-001"). */
    String getModel();

    /** Number of videos to generate. */
    Integer getN();

    /** Video width in pixels. */
    Integer getWidth();

    /** Video height in pixels. */
    Integer getHeight();

    /** Output format: mp4, webm, etc. */
    String getOutputFormat();

    /** Visual style: cinematic, animated, realistic, etc. */
    String getStyle();

    // Video-specific parameters

    /** Duration in seconds. */
    Integer getDuration();

    /** Frames per second. */
    Integer getFps();

    /** Aspect ratio: "16:9", "9:16", "1:1", "4:3". */
    String getAspectRatio();

    /** URL, base64-encoded, or data URI of an input image for image-to-video generation. */
    String getInputImage();

    /** Size as "WxH" string (e.g., "1280x720"). Used by OpenAI Sora. */
    String getSize();

    // Advanced parameters

    /** Motion intensity: slow, medium, fast, extreme. */
    String getMotion();

    /** Seed for reproducibility. */
    Integer getSeed();

    /** Guidance scale (1.0-20.0), controls prompt adherence. */
    Float getGuidanceScale();

    /** Text describing what to exclude from generated videos. Used by Gemini Veo. */
    String getNegativePrompt();

    /** Person generation policy: "dont_allow", "allow_adult". Used by Gemini Veo. */
    String getPersonGeneration();

    /** Output resolution: "720p", "1080p". Used by Gemini Veo. */
    String getResolution();

    /** Whether to generate audio along with video. Used by Gemini Veo 3+. */
    Boolean getGenerateAudio();

    // Thumbnail parameters

    /** Whether to generate a preview thumbnail. */
    Boolean getGenerateThumbnail();

    /** Timestamp (in seconds) to extract thumbnail from. */
    Integer getThumbnailTimestamp();
}
