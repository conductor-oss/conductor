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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for video model interfaces. */
public class VideoModelTest {

    @Test
    public void testVideoOptionsBuilder() {
        VideoOptions options =
                VideoOptionsBuilder.builder()
                        .model("gen3a_turbo")
                        .duration(5)
                        .width(1280)
                        .height(720)
                        .fps(24)
                        .outputFormat("mp4")
                        .style("cinematic")
                        .motion("medium")
                        .seed(42)
                        .guidanceScale(7.5f)
                        .aspectRatio("16:9")
                        .generateThumbnail(true)
                        .thumbnailTimestamp(2)
                        .build();

        assertEquals("gen3a_turbo", options.getModel());
        assertEquals(5, options.getDuration());
        assertEquals(1280, options.getWidth());
        assertEquals(720, options.getHeight());
        assertEquals(24, options.getFps());
        assertEquals("mp4", options.getOutputFormat());
        assertEquals("cinematic", options.getStyle());
        assertEquals("medium", options.getMotion());
        assertEquals(42, options.getSeed());
        assertEquals(7.5f, options.getGuidanceScale());
        assertEquals("16:9", options.getAspectRatio());
        assertTrue(options.getGenerateThumbnail());
        assertEquals(2, options.getThumbnailTimestamp());
    }

    @Test
    public void testVideoOptionsBuilderDefaults() {
        VideoOptions options = VideoOptionsBuilder.builder().build();

        assertEquals(5, options.getDuration());
        assertEquals(1280, options.getWidth());
        assertEquals(720, options.getHeight());
        assertEquals(24, options.getFps());
        assertEquals("mp4", options.getOutputFormat());
        assertEquals(1, options.getN());
        assertTrue(options.getGenerateThumbnail());
    }

    @Test
    public void testVideoPromptCreation() {
        VideoPrompt prompt = new VideoPrompt("A serene beach at sunset");

        assertNotNull(prompt.getInstructions());
        assertEquals(1, prompt.getInstructions().size());
        assertEquals("A serene beach at sunset", prompt.getInstructions().get(0).getText());
    }

    @Test
    public void testVideoPromptWithOptions() {
        VideoOptions options = VideoOptionsBuilder.builder().duration(10).build();
        VideoPrompt prompt = new VideoPrompt("A serene beach at sunset", options);

        assertNotNull(prompt.getInstructions());
        assertEquals(1, prompt.getInstructions().size());
        assertEquals("A serene beach at sunset", prompt.getInstructions().get(0).getText());
        assertEquals(10, prompt.getOptions().getDuration());
    }

    @Test
    public void testVideoCreationWithUrl() {
        Video video = new Video("https://example.com/video.mp4", null, "video/mp4");

        assertEquals("https://example.com/video.mp4", video.getUrl());
        assertNull(video.getB64Json());
        assertEquals("video/mp4", video.getMimeType());
    }

    @Test
    public void testVideoCreationWithBase64() {
        String b64Data = "dmlkZW9fZGF0YQ=="; // "video_data" in base64
        Video video = new Video(null, b64Data, "video/mp4");

        assertNull(video.getUrl());
        assertEquals(b64Data, video.getB64Json());
        assertEquals("video/mp4", video.getMimeType());
    }

    @Test
    public void testVideoCreationBackwardCompatible() {
        // Test the 2-arg constructor for backward compatibility
        Video video = new Video("https://example.com/video.mp4", null);

        assertEquals("https://example.com/video.mp4", video.getUrl());
        assertNull(video.getB64Json());
        assertNull(video.getMimeType());
    }

    @Test
    public void testVideoEquality() {
        Video video1 = new Video("https://example.com/video.mp4", null, "video/mp4");
        Video video2 = new Video("https://example.com/video.mp4", null, "video/mp4");
        Video video3 = new Video("https://example.com/other.mp4", null, "video/mp4");

        assertEquals(video1, video2);
        assertNotEquals(video1, video3);
        assertEquals(video1.hashCode(), video2.hashCode());
    }

    @Test
    public void testVideoToString() {
        Video video = new Video("https://example.com/video.mp4", null, "video/mp4");
        String str = video.toString();

        assertTrue(str.contains("https://example.com/video.mp4"));
        assertTrue(str.contains("video/mp4"));
    }
}
