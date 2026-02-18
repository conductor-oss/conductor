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
package org.conductoross.conductor.ai.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Request model for video generation tasks.
 *
 * <p>Contains all parameters needed for generating videos using AI providers like OpenAI Sora,
 * Google Gemini Veo, etc.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoGenRequest extends LLMWorkerInput {

    // Basic parameters
    private String prompt;
    private String inputImage; // Base64-encoded or URL of the input image (required for providers
    // like Stability AI that use image-to-video)
    @Builder.Default private Integer duration = 5; // seconds
    @Builder.Default private Integer width = 1280;
    @Builder.Default private Integer height = 720;
    @Builder.Default private Integer fps = 24;
    @Builder.Default private String outputFormat = "mp4";

    // Advanced parameters
    private String style; // cinematic, animated, realistic, etc.
    private String motion; // slow, medium, fast, extreme
    private Integer seed; // for reproducibility
    private Float guidanceScale; // 1.0-20.0, controls prompt adherence
    private String aspectRatio; // 16:9, 9:16, 1:1, 4:3

    // Provider-specific advanced parameters
    private String negativePrompt; // Gemini Veo: text describing what to exclude
    private String personGeneration; // Gemini: "dont_allow" / "allow_adult"
    private String resolution; // Gemini: "720p" / "1080p"
    private Boolean generateAudio; // Gemini Veo 3+: generate audio with video
    private String size; // OpenAI Sora: "1280x720" format string

    // Preview/thumbnail generation
    @Builder.Default private Boolean generateThumbnail = true;
    private Integer thumbnailTimestamp; // which second to extract thumbnail

    // Cost control
    private Integer maxDurationSeconds; // hard limit on duration
    private Float maxCostDollars; // estimated cost limit

    // Polling state (stored in task.outputData for stateful polling)
    // These fields are populated during async processing
    private String jobId; // provider's async job ID
    private String status; // SUBMITTED, PROCESSING, COMPLETED, FAILED
    private Integer pollCount; // number of times we've polled

    @Builder.Default private int n = 1;
}
