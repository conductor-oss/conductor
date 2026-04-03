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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Builder implementation for {@link VideoOptions}.
 *
 * <p>Follows Spring AI's pattern for options builders (e.g., ImageOptionsBuilder). Provides default
 * values for common video generation parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoOptionsBuilder implements VideoOptions {

    // Core parameters
    private String model;
    @Builder.Default private Integer n = 1;
    @Builder.Default private Integer width = 1280;
    @Builder.Default private Integer height = 720;
    @Builder.Default private String outputFormat = "mp4";
    private String style;

    // Video-specific parameters
    @Builder.Default private Integer duration = 5;
    @Builder.Default private Integer fps = 24;
    private String aspectRatio;
    private String inputImage;
    private String size;

    // Advanced parameters
    private String motion;
    private Integer seed;
    private Float guidanceScale;
    private String negativePrompt;
    private String personGeneration;
    private String resolution;
    private Boolean generateAudio;

    // Thumbnail parameters
    @Builder.Default private Boolean generateThumbnail = true;
    private Integer thumbnailTimestamp;
}
