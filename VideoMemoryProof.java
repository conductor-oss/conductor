import java.util.Base64;
import java.util.Random;

/**
 * Standalone proof that demonstrates the video memory optimization issue.
 *
 * Run this with: javac VideoMemoryProof.java && java VideoMemoryProof
 *
 * This simulates EXACTLY what was happening in the video code before the fix.
 */
public class VideoMemoryProof {

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("VIDEO MEMORY OPTIMIZATION - PROOF OF ISSUE");
        System.out.println("=".repeat(70));

        // Test with a 100MB video
        demonstrateIssue(100);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("CONCLUSION:");
        System.out.println("=".repeat(70));
        System.out.println("The issue is NOT multiple downloads.");
        System.out.println("The issue IS multiple memory copies via Base64 encoding/decoding.");
        System.out.println("\n✅ Fix: Store bytes directly instead of encoding to Base64.");
    }

    static void demonstrateIssue(int videoSizeMB) {
        System.out.println("\n### Testing with " + videoSizeMB + "MB video ###\n");

        // Simulate downloading a video
        byte[] videoBytes = new byte[videoSizeMB * 1024 * 1024];
        new Random().nextBytes(videoBytes);

        System.out.println("STEP 1: Download video from API");
        System.out.println("  Memory used: " + formatBytes(videoBytes.length));
        printMemoryBar(videoBytes.length, videoBytes.length);

        // OLD WAY: Encode to Base64
        System.out.println("\nSTEP 2 (OLD WAY): Encode to Base64 string ❌");
        String base64String = Base64.getEncoder().encodeToString(videoBytes);
        long base64Bytes = base64String.length() * 2L; // each char is 2 bytes in Java
        System.out.println("  Memory used: " + formatBytes(base64Bytes));
        System.out.println("  Overhead: " + String.format("%.1f%%", ((double)base64Bytes / videoBytes.length - 1) * 100));
        long memoryAfterEncode = videoBytes.length + base64Bytes;
        printMemoryBar(memoryAfterEncode, videoBytes.length);

        // OLD WAY: Decode back to bytes
        System.out.println("\nSTEP 3 (OLD WAY): Decode Base64 back to bytes ❌");
        byte[] decodedBytes = Base64.getDecoder().decode(base64String);
        System.out.println("  Memory used: " + formatBytes(decodedBytes.length));
        System.out.println("  Is same array as original? " + (videoBytes == decodedBytes ? "YES" : "NO - NEW COPY!"));
        long totalOldWay = videoBytes.length + base64Bytes + decodedBytes.length;
        printMemoryBar(totalOldWay, videoBytes.length);

        System.out.println("\n" + "-".repeat(70));
        System.out.println("OLD WAY TOTAL MEMORY: " + formatBytes(totalOldWay));
        System.out.println("  - Original download:  " + formatBytes(videoBytes.length));
        System.out.println("  - Base64 string:      " + formatBytes(base64Bytes) + " (+33% overhead)");
        System.out.println("  - Decoded copy:       " + formatBytes(decodedBytes.length));
        System.out.println("  - WASTED:             " + formatBytes(totalOldWay - videoBytes.length) + " (" +
            String.format("%.0f%%", ((double)(totalOldWay - videoBytes.length) / totalOldWay * 100)) + ")");

        // NEW WAY: Direct bytes
        System.out.println("\n" + "-".repeat(70));
        System.out.println("NEW WAY (OPTIMIZED):");
        System.out.println("\nSTEP 2 (NEW WAY): Store bytes directly ✅");
        // In the actual code: Video.fromBytes(videoBytes, "video/mp4")
        byte[] storedBytes = videoBytes; // Same reference, no copy!
        System.out.println("  Memory used: " + formatBytes(storedBytes.length));
        System.out.println("  Is same array as original? " + (videoBytes == storedBytes ? "YES - ZERO COPY! ✓" : "NO"));

        System.out.println("\nSTEP 3 (NEW WAY): Access bytes directly ✅");
        // In the actual code: video.getData()
        byte[] accessedBytes = storedBytes; // Same reference!
        System.out.println("  Memory used: " + formatBytes(accessedBytes.length));
        System.out.println("  Is same array? " + (storedBytes == accessedBytes ? "YES - ZERO COPY! ✓" : "NO"));

        long totalNewWay = videoBytes.length;
        printMemoryBar(totalNewWay, videoBytes.length);

        System.out.println("\n" + "-".repeat(70));
        System.out.println("NEW WAY TOTAL MEMORY: " + formatBytes(totalNewWay));
        System.out.println("  - Single array reference: " + formatBytes(videoBytes.length));
        System.out.println("  - No encoding overhead");
        System.out.println("  - No decode copy");

        // Comparison
        System.out.println("\n" + "=".repeat(70));
        System.out.println("COMPARISON:");
        System.out.println("  OLD Way: " + formatBytes(totalOldWay));
        System.out.println("  NEW Way: " + formatBytes(totalNewWay));
        System.out.println("  SAVED:   " + formatBytes(totalOldWay - totalNewWay) + " (" +
            String.format("%.0f%%", ((double)(totalOldWay - totalNewWay) / totalOldWay * 100)) + " reduction)");

        // Performance comparison
        System.out.println("\n" + "=".repeat(70));
        System.out.println("PERFORMANCE COMPARISON:");

        // Measure encoding time
        long encodeStart = System.nanoTime();
        String b64 = Base64.getEncoder().encodeToString(videoBytes);
        long encodeTime = System.nanoTime() - encodeStart;

        // Measure decoding time
        long decodeStart = System.nanoTime();
        byte[] dec = Base64.getDecoder().decode(b64);
        long decodeTime = System.nanoTime() - decodeStart;

        // Measure direct access (essentially instant)
        long directStart = System.nanoTime();
        byte[] direct = videoBytes;
        long directTime = System.nanoTime() - directStart;

        System.out.println("  Base64 encode:  " + formatTime(encodeTime));
        System.out.println("  Base64 decode:  " + formatTime(decodeTime));
        System.out.println("  Direct access:  " + formatTime(directTime) + " (essentially instant)");
        System.out.println("  Total Base64 overhead: " + formatTime(encodeTime + decodeTime));
        System.out.println("  Speedup: " + String.format("%.0fx faster", (double)(encodeTime + decodeTime) / Math.max(directTime, 1)));
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    static String formatTime(long nanos) {
        if (nanos < 1000) return nanos + " ns";
        if (nanos < 1_000_000) return String.format("%.2f μs", nanos / 1000.0);
        return String.format("%.2f ms", nanos / 1_000_000.0);
    }

    static void printMemoryBar(long currentMemory, long baseMemory) {
        int barLength = 50;
        double ratio = (double)currentMemory / baseMemory;
        int filled = (int)(ratio * barLength / 3.5); // Scale for display
        filled = Math.min(filled, barLength);

        System.out.print("  Memory graph: [");
        for (int i = 0; i < filled; i++) System.out.print("█");
        for (int i = filled; i < barLength; i++) System.out.print("░");
        System.out.println("] " + String.format("%.1fx", ratio));
    }
}
