package org.conductoross.conductor.ai.video;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify that Video class uses direct byte storage efficiently
 * without creating unnecessary copies through Base64 encoding/decoding.
 */
public class VideoMemoryTest {

    private static final int TEST_VIDEO_SIZE = 1024 * 1024; // 1MB

    private byte[] createTestVideoBytes() {
        byte[] data = new byte[TEST_VIDEO_SIZE];
        new Random(42).nextBytes(data); // Fixed seed for reproducibility
        return data;
    }

    @Test
    public void testFromBytes_StoresDirectReference() {
        byte[] videoBytes = createTestVideoBytes();

        Video video = Video.fromBytes(videoBytes, "video/mp4");

        assertSame(videoBytes, video.getData(),
            "fromBytes() should store the same array reference, not create a copy");
        assertNull(video.getB64Json(),
            "fromBytes() should not populate b64Json field");
        assertNull(video.getUrl(),
            "fromBytes() should not populate url field");
        assertEquals("video/mp4", video.getMimeType());
    }

    @Test
    public void testGetData_ReturnsDirectReference() {
        byte[] videoBytes = createTestVideoBytes();
        Video video = Video.fromBytes(videoBytes, "video/mp4");

        byte[] retrieved1 = video.getData();
        byte[] retrieved2 = video.getData();

        assertSame(videoBytes, retrieved1,
            "getData() should return the same array reference");
        assertSame(retrieved1, retrieved2,
            "Multiple getData() calls should return the same reference");
    }

    @Test
    public void testBase64Constructor_DoesNotPopulateData() {
        byte[] videoBytes = createTestVideoBytes();
        String base64 = Base64.getEncoder().encodeToString(videoBytes);

        Video video = new Video(null, base64, "video/mp4");

        assertNull(video.getData(),
            "Constructor with base64 should not populate data field");
        assertNotNull(video.getB64Json());
    }

    @Test
    public void testFromBytes_AvoidsBase64EncodingOverhead() {
        byte[] videoBytes = createTestVideoBytes();

        // Using fromBytes - direct storage
        Video optimizedVideo = Video.fromBytes(videoBytes, "video/mp4");

        // Using base64 - old way
        String base64 = Base64.getEncoder().encodeToString(videoBytes);

        // Base64 string consumes ~33% more memory (each char is 2 bytes in Java)
        long base64MemoryBytes = (long) base64.length() * 2;
        long directMemoryBytes = videoBytes.length;

        assertTrue(base64MemoryBytes > directMemoryBytes * 1.3,
            "Base64 encoding should consume at least 33% more memory");
        assertSame(videoBytes, optimizedVideo.getData(),
            "Optimized approach should use same array reference");
    }

    @Test
    public void testBase64Decoding_CreatesNewArray() {
        byte[] originalBytes = createTestVideoBytes();
        String base64 = Base64.getEncoder().encodeToString(originalBytes);

        byte[] decodedBytes = Base64.getDecoder().decode(base64);

        assertNotSame(originalBytes, decodedBytes,
            "Base64 decode creates a new array (wasteful copy)");
        assertArrayEquals(originalBytes, decodedBytes,
            "Content should be the same");
    }

    @Test
    public void testBackwardCompatibility_Base64StillSupported() {
        byte[] videoBytes = createTestVideoBytes();
        String base64 = Base64.getEncoder().encodeToString(videoBytes);

        Video oldFormatVideo = new Video(null, base64, "video/mp4");

        assertNotNull(oldFormatVideo.getB64Json());
        byte[] decoded = Base64.getDecoder().decode(oldFormatVideo.getB64Json());
        assertArrayEquals(videoBytes, decoded);
    }

    @Test
    public void testVideoEquality() {
        byte[] videoBytes = createTestVideoBytes();

        Video video1 = Video.fromBytes(videoBytes, "video/mp4");
        Video video2 = Video.fromBytes(videoBytes, "video/mp4");

        assertEquals(video1, video2);
        assertEquals(video1.hashCode(), video2.hashCode());
    }

    @Test
    public void testSettersAndGetters() {
        Video video = new Video(null, null, null, null);

        assertNull(video.getUrl());
        assertNull(video.getData());
        assertNull(video.getB64Json());
        assertNull(video.getMimeType());

        video.setUrl("https://example.com/video.mp4");
        video.setMimeType("video/mp4");
        byte[] data = new byte[100];
        video.setData(data);

        assertEquals("https://example.com/video.mp4", video.getUrl());
        assertEquals("video/mp4", video.getMimeType());
        assertSame(data, video.getData());
    }
}
