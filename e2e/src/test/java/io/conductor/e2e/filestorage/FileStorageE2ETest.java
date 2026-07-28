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
package io.conductor.e2e.filestorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.conductoross.conductor.client.FileClient;
import org.conductoross.conductor.client.model.file.FileMetadata;
import org.conductoross.conductor.client.model.file.FileUploadStatus;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;

import com.fasterxml.jackson.core.type.TypeReference;
import io.conductor.e2e.util.ApiUtil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * E2E tests for the file-storage feature (server with {@code conductor.file-storage.enabled=true},
 * {@code type=conductor}).
 *
 * <p>Anything a caller would really do — upload, download, read metadata — goes through {@link
 * FileClient} from the Java SDK, so these tests exercise the same code path as user applications
 * including its transfer adapters and retries. The Conductor backend gives the SDK HTTP upload and
 * download URLs that Conductor proxies to its own filesystem, so client and server do not need to
 * share a filesystem. The same current SDK flow runs against both the host server and the
 * containerized server.
 *
 * <p>The remaining tests drive {@link ConductorClient} directly on purpose: they assert the REST
 * contract for endpoints {@code FileClient} does not expose (single-step create, upload-url,
 * multipart init) or for request shapes the client validates before it would ever reach the server.
 *
 * <p>Run via: {@code e2e/run_tests-es8.sh}.
 */
class FileStorageE2ETest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String PREFIX = "conductor://file/";

    private static final ConductorClient client = ApiUtil.CLIENT;
    private static final FileClient fileClient = ApiUtil.FILE_CLIENT;

    // ── SDK client: the paths callers actually use ─────────────────────────────

    @Test
    void fullRoundTripThroughFileClient() throws Exception {
        byte[] payload = "the quick brown fox".getBytes();
        Path source = Files.createTempFile("conductor-e2e-", ".txt");
        Files.write(source, payload);

        // The caller's own workflowId is always in its family, so uploading and
        // downloading under the same id is allowed with no workflow in the DB.
        String fileHandleId = fileClient.upload("wf-rt", source);

        assertNotNull(fileHandleId);
        assertTrue(fileHandleId.startsWith(PREFIX), "expected a prefixed handle: " + fileHandleId);

        Path destination = Files.createTempDirectory("conductor-e2e-dl").resolve("rt.txt");
        Path downloaded = fileClient.download("wf-rt", fileHandleId, destination);

        assertArrayEquals(payload, Files.readAllBytes(downloaded));
    }

    @Test
    void metadataReflectsAnUploadedFile() throws Exception {
        byte[] payload = "metadata check".getBytes();
        Path source = Files.createTempFile("conductor-e2e-meta-", ".txt");
        Files.write(source, payload);

        String fileHandleId = fileClient.upload("wf-meta", source);
        FileMetadata metadata = fileClient.getMetadata("wf-meta", fileHandleId);

        assertEquals(fileHandleId, metadata.getFileHandleId());
        assertEquals(source.getFileName().toString(), metadata.getFileName());
        assertEquals(FileUploadStatus.UPLOADED, metadata.getUploadStatus());
        assertEquals(payload.length, metadata.getFileSize());
        // The SDK exposes storageType as a String so an older client keeps deserializing
        // when the server adds a backend; compare the wire value, not a server-side enum.
        assertEquals("CONDUCTOR", metadata.getStorageType());
    }

    @Test
    void metadataForUnknownFileFails() {
        String unknown = PREFIX + "nonexistent-file-id-" + UUID.randomUUID();

        Exception e = assertThrows(Exception.class, () -> fileClient.getMetadata("wf-1", unknown));
        assertTrue(
                e.getMessage() != null && e.getMessage().toLowerCase().contains("not found"),
                "Expected a not-found failure but got: " + e);
    }

    @Test
    void downloadByAnUnrelatedWorkflowIsForbidden() throws Exception {
        Path source = Files.createTempFile("conductor-e2e-scoped-", ".bin");
        Files.write(source, new byte[64]);

        String fileHandleId = fileClient.upload("wf-owner", source);
        Path destination = Files.createTempDirectory("conductor-e2e-scoped-dl").resolve("out.bin");

        Exception e =
                assertThrows(
                        Exception.class,
                        () -> fileClient.download("wf-unrelated", fileHandleId, destination));
        assertTrue(
                rootCauseMessage(e).contains("403")
                        || rootCauseMessage(e).toLowerCase().contains("cannot access")
                        || rootCauseMessage(e).toLowerCase().contains("forbidden"),
                "Expected a 403 for an unrelated workflow but got: " + rootCauseMessage(e));
    }

    // ── REST contract: endpoints and request shapes FileClient does not cover ──

    @Test
    void createFileReturnsFileHandleIdAndUploadUrl() {
        Map<String, Object> response = createFile("test.pdf", "application/pdf", 1024, "wf-1");

        assertNotNull(response.get("fileHandleId"));
        assertTrue(response.get("fileHandleId").toString().startsWith(PREFIX));
        assertEquals("test.pdf", response.get("fileName"));
        assertEquals("application/pdf", response.get("contentType"));
        assertEquals("CONDUCTOR", response.get("storageType"));
        assertEquals("UPLOADING", response.get("uploadStatus"));
        assertContentUrl("wf-1", fileIdFromResponse(response), response.get("uploadUrl"));
        assertNotNull(response.get("createdAt"));
    }

    @Test
    void getUploadUrlReturnsFreshUrl() {
        Map<String, Object> created =
                createFile("data.bin", "application/octet-stream", 512, "wf-1");
        String fileId = fileIdFromResponse(created);

        Map<String, Object> urlResponse = get("/files/wf-1/" + fileId + "/upload-url");

        assertEquals(created.get("fileHandleId"), urlResponse.get("fileHandleId"));
        assertContentUrl("wf-1", fileId, urlResponse.get("uploadUrl"));
    }

    @Test
    void metadataBeforeUploadReportsUploading() {
        Map<String, Object> created = createFile("doc.txt", "text/plain", 256, "wf-1");
        String fileId = fileIdFromResponse(created);

        Map<String, Object> handle = get("/files/wf-1/" + fileId);

        assertEquals(created.get("fileHandleId"), handle.get("fileHandleId"));
        assertEquals("doc.txt", handle.get("fileName"));
        assertEquals("text/plain", handle.get("contentType"));
        assertEquals("UPLOADING", handle.get("uploadStatus"));
        assertEquals("CONDUCTOR", handle.get("storageType"));
    }

    @Test
    void fileNotFoundReturns404() {
        try {
            get("/files/wf-1/nonexistent-file-id-" + UUID.randomUUID());
            fail("Expected 404");
        } catch (ConductorClientException e) {
            assertEquals(404, e.getStatusCode(), "Expected 404 but got: " + e);
        }
    }

    @Test
    void initiateMultipartUpload() {
        Map<String, Object> created =
                createFile("large.bin", "application/octet-stream", 200L * 1024 * 1024, "wf-1");
        String fileId = fileIdFromResponse(created);

        Map<String, Object> response =
                client.execute(
                                ConductorClientRequest.builder()
                                        .method(Method.POST)
                                        .path("/files/wf-1/" + fileId + "/multipart")
                                        .build(),
                                MAP_TYPE)
                        .getData();

        assertEquals(created.get("fileHandleId"), response.get("fileHandleId"));
        assertNotNull(response.get("uploadId"));
    }

    @Test
    void downloadUrlRequiresUploadedStatus() {
        Map<String, Object> created =
                createFile("pending.bin", "application/octet-stream", 100, "wf-1");
        String fileId = fileIdFromResponse(created);

        try {
            get("/files/wf-1/" + fileId + "/download-url");
            fail("Expected 400 — file not yet uploaded");
        } catch (ConductorClientException e) {
            assertEquals(400, e.getStatusCode(), "Expected 400 but got: " + e);
        }
    }

    @Test
    void createFileWithoutWorkflowIdIsRejected() {
        // FileClient requires a workflowId before it issues a request, so this
        // asserts the server's own validation.
        //
        // The status is 500, not the 400 the failure really is: `@Valid` on the
        // request body raises MethodArgumentNotValidException, which no mapper
        // handles, so ApplicationExceptionMapper's Throwable catch-all defaults it
        // to INTERNAL_SERVER_ERROR. That is server-wide behaviour for every
        // validated body, so this test pins what the server does rather than
        // asserting a contract it does not implement. Both are accepted so the
        // test survives a later fix to the mapper.
        Map<String, Object> body =
                Map.of(
                        "fileName", "no-wf.pdf",
                        "contentType", "application/pdf",
                        "fileSize", 1024);
        try {
            client.execute(
                    ConductorClientRequest.builder()
                            .method(Method.POST)
                            .path("/files")
                            .body(body)
                            .build(),
                    MAP_TYPE);
            fail("Expected the request to be rejected — workflowId missing");
        } catch (ConductorClientException e) {
            assertTrue(
                    e.getStatusCode() == 500 || e.getStatusCode() == 400,
                    "Expected 500 (current) or 400 but got: " + e.getStatusCode() + " — " + e);
            assertTrue(
                    e.getMessage().contains("workflowId"),
                    "Expected a workflowId error but got: " + e.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> get(String path) {
        return client.execute(
                        ConductorClientRequest.builder().method(Method.GET).path(path).build(),
                        MAP_TYPE)
                .getData();
    }

    private static Map<String, Object> createFile(
            String fileName, String contentType, long fileSize, String workflowId) {
        Map<String, Object> body =
                Map.of(
                        "fileName", fileName,
                        "contentType", contentType,
                        "fileSize", fileSize,
                        "workflowId", workflowId);
        return client.execute(
                        ConductorClientRequest.builder()
                                .method(Method.POST)
                                .path("/files")
                                .body(body)
                                .build(),
                        MAP_TYPE)
                .getData();
    }

    private static String fileIdFromResponse(Map<String, Object> response) {
        String fileHandleId = response.get("fileHandleId").toString();
        return fileHandleId.startsWith(PREFIX)
                ? fileHandleId.substring(PREFIX.length())
                : fileHandleId;
    }

    private static void assertContentUrl(String workflowId, String fileId, Object value) {
        assertNotNull(value);
        String url = value.toString();
        assertTrue(url.startsWith("http://") || url.startsWith("https://"));
        assertTrue(url.contains("/api/files/content/" + workflowId + "/" + fileId));
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? t.toString() : cause.getMessage();
    }
}
