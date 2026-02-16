package org.conductoross.conductor.ai.video;

import org.conductoross.conductor.ai.models.Media;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that verify video provider flows use direct byte references
 * instead of creating copies through Base64 encoding/decoding.
 */
public class VideoProviderMemoryTest {

    private static final int TEST_VIDEO_SIZE = 1024 * 1024; // 1MB

    private byte[] createTestVideoBytes() {
        byte[] data = new byte[TEST_VIDEO_SIZE];
        new Random(42).nextBytes(data);
        return data;
    }

    @Test
    public void testOptimizedFlow_NoCopiesCreated() {
        byte[] downloadedBytes = createTestVideoBytes();

        // Step 1: Store using fromBytes (optimized way)
        Video video = Video.fromBytes(downloadedBytes, "video/mp4");

        // Step 2: Access bytes
        byte[] accessedBytes = video.getData();

        // Step 3: Store in Media
        Media media = Media.builder()
            .data(accessedBytes)
            .mimeType("video/mp4")
            .build();

        // Verify: All references should be the same (zero copy)
        assertSame(downloadedBytes, video.getData(),
            "Video should reference original bytes");
        assertSame(downloadedBytes, accessedBytes,
            "Accessed bytes should be same reference");
        assertSame(downloadedBytes, media.getData(),
            "Media should reference original bytes");
    }

    @Test
    public void testOldFlow_CreatesMultipleCopies() {
        byte[] downloadedBytes = createTestVideoBytes();

        // Old way: encode to base64
        String base64 = Base64.getEncoder().encodeToString(downloadedBytes);
        Video video = new Video(null, base64, "video/mp4");

        // Old way: decode from base64 (creates copy)
        byte[] decodedBytes = Base64.getDecoder().decode(video.getB64Json());

        assertNotSame(downloadedBytes, decodedBytes,
            "Decoding creates a new array (inefficient)");
    }

    @Test
    public void testOpenAIProviderFlow_UsesDirectBytes() {
        byte[] mockDownload = createTestVideoBytes();

        // Simulate OpenAIVideoModel.checkStatus()
        Video video = Video.fromBytes(mockDownload, "video/mp4");
        VideoGeneration generation = new VideoGeneration(video);
        List<VideoGeneration> generations = List.of(generation);
        VideoResponse response = new VideoResponse(generations);

        // Simulate OpenAI.checkVideoStatus()
        List<Media> mediaList = new ArrayList<>();
        for (VideoGeneration gen : response.getResults()) {
            Video v = gen.getOutput();
            String mimeType = v.getMimeType() != null ? v.getMimeType() : "video/mp4";

            if (v.getData() != null) {
                mediaList.add(Media.builder()
                    .data(v.getData())
                    .mimeType(mimeType)
                    .build());
            }
        }

        assertEquals(1, mediaList.size());
        assertSame(mockDownload, mediaList.get(0).getData(),
            "OpenAI flow should preserve original byte reference");
    }

    @Test
    public void testGeminiProviderFlow_PrioritizesBytesOverUrl() {
        byte[] mockSdkBytes = createTestVideoBytes();

        // Simulate GeminiVideoModel.checkStatus() - bytes available
        Video video = Video.fromBytes(mockSdkBytes, "video/mp4");
        VideoGeneration generation = new VideoGeneration(video);
        List<VideoGeneration> generations = List.of(generation);
        VideoResponse response = new VideoResponse(generations);

        // Simulate GeminiVertex.checkVideoStatus()
        List<Media> mediaList = new ArrayList<>();
        for (VideoGeneration gen : response.getResults()) {
            Video v = gen.getOutput();
            String mimeType = v.getMimeType() != null ? v.getMimeType() : "video/mp4";

            // Three-tier fallback logic
            if (v.getData() != null) {
                mediaList.add(Media.builder()
                    .data(v.getData())
                    .mimeType(mimeType)
                    .build());
            } else if (v.getB64Json() != null) {
                mediaList.add(Media.builder()
                    .data(Base64.getDecoder().decode(v.getB64Json()))
                    .mimeType(mimeType)
                    .build());
            }
        }

        assertEquals(1, mediaList.size());
        assertSame(mockSdkBytes, mediaList.get(0).getData(),
            "Gemini flow should use TIER 1 (direct bytes)");
    }

    @Test
    public void testGeminiProviderFlow_FallbackToBase64() {
        byte[] originalBytes = createTestVideoBytes();
        String base64 = Base64.getEncoder().encodeToString(originalBytes);

        // Simulate old format video (only base64, no direct bytes)
        Video video = new Video(null, base64, "video/mp4");
        VideoGeneration generation = new VideoGeneration(video);
        VideoResponse response = new VideoResponse(List.of(generation));

        // Process with fallback logic
        List<Media> mediaList = new ArrayList<>();
        for (VideoGeneration gen : response.getResults()) {
            Video v = gen.getOutput();
            String mimeType = v.getMimeType() != null ? v.getMimeType() : "video/mp4";

            if (v.getData() != null) {
                mediaList.add(Media.builder().data(v.getData()).mimeType(mimeType).build());
            } else if (v.getB64Json() != null) {
                mediaList.add(Media.builder()
                    .data(Base64.getDecoder().decode(v.getB64Json()))
                    .mimeType(mimeType)
                    .build());
            }
        }

        assertEquals(1, mediaList.size());
        assertArrayEquals(originalBytes, mediaList.get(0).getData(),
            "Fallback to base64 should work correctly");
    }

    @Test
    public void testProviderFlow_WithThumbnail() {
        byte[] videoBytes = createTestVideoBytes();
        byte[] thumbnailBytes = new byte[50 * 1024]; // 50KB thumbnail
        new Random(43).nextBytes(thumbnailBytes);

        Video video = Video.fromBytes(videoBytes, "video/mp4");
        Video thumbnail = Video.fromBytes(thumbnailBytes, "image/webp");

        List<VideoGeneration> generations = List.of(
            new VideoGeneration(video),
            new VideoGeneration(thumbnail)
        );

        VideoResponse response = new VideoResponse(generations);

        List<Media> mediaList = new ArrayList<>();
        for (VideoGeneration gen : response.getResults()) {
            Video v = gen.getOutput();
            if (v.getData() != null) {
                mediaList.add(Media.builder()
                    .data(v.getData())
                    .mimeType(v.getMimeType())
                    .build());
            }
        }

        assertEquals(2, mediaList.size());
        assertSame(videoBytes, mediaList.get(0).getData());
        assertSame(thumbnailBytes, mediaList.get(1).getData());
    }

    @Test
    public void testVideoGeneration_PreservesVideoReference() {
        byte[] videoBytes = createTestVideoBytes();
        Video video = Video.fromBytes(videoBytes, "video/mp4");

        VideoGeneration generation = new VideoGeneration(video);

        assertSame(video, generation.getOutput());
        assertSame(videoBytes, generation.getOutput().getData());
    }

    @Test
    public void testVideoResponse_PreservesReferences() {
        byte[] videoBytes = createTestVideoBytes();
        Video video = Video.fromBytes(videoBytes, "video/mp4");
        VideoGeneration generation = new VideoGeneration(video);

        VideoResponse response = new VideoResponse(List.of(generation));

        assertEquals(1, response.getResults().size());
        assertSame(generation, response.getResult());
        assertSame(videoBytes, response.getResult().getOutput().getData());
    }
}
