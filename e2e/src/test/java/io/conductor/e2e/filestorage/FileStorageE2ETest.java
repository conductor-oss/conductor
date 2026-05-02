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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * E2E tests for the file-storage REST API (server with {@code conductor.file-storage.enabled=true},
 * {@code type=local}). A bind mount shares the server's storage directory with the host so the
 * {@code file:///...} URIs returned by the API resolve on both sides.
 *
 * <p>Run via: {@code e2e/run_tests-es8.sh}.
 */
class FileStorageE2ETest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String PREFIX = "conductor://file/";

    private static final ConductorClient client = ApiUtil.CLIENT;

    @Test
    void createFileReturnsFileHandleIdAndUploadUrl() {
        Map<String, Object> response = createFile("test.pdf", "application/pdf", 1024, "wf-1");

        assertNotNull(response.get("fileHandleId"));
        assertTrue(response.get("fileHandleId").toString().startsWith(PREFIX));
        assertEquals("test.pdf", response.get("fileName"));
        assertEquals("application/pdf", response.get("contentType"));
        assertEquals("UPLOADING", response.get("uploadStatus"));
        assertNotNull(response.get("uploadUrl"));
        assertNotNull(response.get("createdAt"));
    }

    @Test
    void getUploadUrlReturnsFreshUrl() {
        Map<String, Object> created =
                createFile("data.bin", "application/octet-stream", 512, "wf-1");
        String fileId = fileIdFromResponse(created);

        Map<String, Object> urlResponse =
                client.execute(
                                ConductorClientRequest.builder()
                                        .method(Method.GET)
                                        .path("/files/" + fileId + "/upload-url")
                                        .build(),
                                MAP_TYPE)
                        .getData();

        assertEquals(created.get("fileHandleId"), urlResponse.get("fileHandleId"));
        assertNotNull(urlResponse.get("uploadUrl"));
    }

    @Test
    void getFileMetadataReflectsCreate() {
        Map<String, Object> created = createFile("doc.txt", "text/plain", 256, "wf-1");
        String fileId = fileIdFromResponse(created);

        Map<String, Object> handle =
                client.execute(
                                ConductorClientRequest.builder()
                                        .method(Method.GET)
                                        .path("/files/" + fileId)
                                        .build(),
                                MAP_TYPE)
                        .getData();

        assertEquals(created.get("fileHandleId"), handle.get("fileHandleId"));
        assertEquals("doc.txt", handle.get("fileName"));
        assertEquals("text/plain", handle.get("contentType"));
        assertEquals("UPLOADING", handle.get("uploadStatus"));
        assertNotNull(handle.get("storageType"));
    }

    @Test
    void fileNotFoundReturns404() {
        try {
            client.execute(
                    ConductorClientRequest.builder()
                            .method(Method.GET)
                            .path("/files/nonexistent-file-id-" + UUID.randomUUID())
                            .build(),
                    MAP_TYPE);
            fail("Expected 404");
        } catch (Exception e) {
            assertTrue(
                    e.getMessage().contains("not found"),
                    "Expected 404 but got: " + e.getMessage());
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
                                        .path("/files/" + fileId + "/multipart")
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
            client.execute(
                    ConductorClientRequest.builder()
                            .method(Method.GET)
                            .path("/files/wf-1/" + fileId + "/download-url")
                            .build(),
                    MAP_TYPE);
            fail("Expected 400 — file not yet uploaded");
        } catch (Exception e) {
            assertTrue(
                    e.getMessage().contains("not yet uploaded"),
                    "Expected 400 but got: " + e.getMessage());
        }
    }

    @Test
    void fullRoundTripLocalStorage() throws Exception {
        byte[] payload = "the quick brown fox".getBytes();
        // Caller's own workflowId is always in its family, so creating + downloading with
        // the same workflowId is allowed even when no Conductor workflow exists in the DB.
        Map<String, Object> created = createFile("rt.txt", "text/plain", payload.length, "wf-rt");
        String fileId = fileIdFromResponse(created);
        String uploadUrl = created.get("uploadUrl").toString();

        // file:// URI → resolve on this host (shared mount with server)
        Path uploadPath = Path.of(URI.create(uploadUrl));
        Files.createDirectories(uploadPath.getParent());
        Files.write(uploadPath, payload);

        Map<String, Object> confirm =
                client.execute(
                                ConductorClientRequest.builder()
                                        .method(Method.POST)
                                        .path("/files/" + fileId + "/upload-complete")
                                        .build(),
                                MAP_TYPE)
                        .getData();
        assertEquals("UPLOADED", confirm.get("uploadStatus"));

        Map<String, Object> dl =
                client.execute(
                                ConductorClientRequest.builder()
                                        .method(Method.GET)
                                        .path("/files/wf-rt/" + fileId + "/download-url")
                                        .build(),
                                MAP_TYPE)
                        .getData();
        String downloadUrl = dl.get("downloadUrl").toString();
        assertTrue(downloadUrl.startsWith("file:///"), "expected file:// URI, got: " + downloadUrl);

        byte[] read = Files.readAllBytes(Path.of(URI.create(downloadUrl)));
        assertArrayEquals(payload, read);
    }

    // ── workflowId scoping ────────────────────────────────────────────────────

    @Test
    void createFileWithoutWorkflowIdReturns400() {
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
            fail("Expected 400 — workflowId missing");
        } catch (Exception e) {
            assertTrue(
                    e.getMessage().contains("workflowId"),
                    "Expected workflowId error but got: " + e.getMessage());
        }
    }

    @Test
    void downloadWithUnrelatedWorkflowReturns403() throws Exception {
        Map<String, Object> created =
                createFile("scoped.bin", "application/octet-stream", 64, "wf-owner");
        String fileId = fileIdFromResponse(created);
        String uploadUrl = created.get("uploadUrl").toString();

        // File must be UPLOADED before the family check is reached;
        // without this the server returns 400 (not yet uploaded) instead of 403.
        Path uploadPath = Path.of(URI.create(uploadUrl));
        Files.createDirectories(uploadPath.getParent());
        Files.write(uploadPath, new byte[64]);
        client.execute(
                ConductorClientRequest.builder()
                        .method(Method.POST)
                        .path("/files/" + fileId + "/upload-complete")
                        .build(),
                MAP_TYPE);

        try {
            client.execute(
                    ConductorClientRequest.builder()
                            .method(Method.GET)
                            .path("/files/wf-unrelated/" + fileId + "/download-url")
                            .build(),
                    MAP_TYPE);
            fail("Expected 403 — unrelated workflow");
        } catch (ConductorClientException e) {
            assertEquals(403, e.getStatusCode(), "Expected 403 but got: " + e);
        }
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
}
