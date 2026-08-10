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
package com.netflix.conductor.test.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import org.conductoross.conductor.model.file.FileDownloadUrlResponse;
import org.conductoross.conductor.model.file.FileIdToFileHandleIdConverter;
import org.conductoross.conductor.model.file.FileUploadCompleteResponse;
import org.conductoross.conductor.model.file.FileUploadResponse;
import org.conductoross.conductor.model.file.FileUploadStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.test.config.AzuriteFileStorageConfiguration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("file-storage-integration")
@TestPropertySource(
        properties = {
            "conductor.file-storage.enabled=true",
            "conductor.file-storage.type=azure-blob",
            "conductor.file-storage.azure-blob.container-name=conductor-file-storage-test"
        })
@Import(AzuriteFileStorageConfiguration.class)
class AzureBlobFileStorageIntegrationTest extends AbstractFileStorageIntegrationTest {

    // Well-known Azurite credentials.
    private static final String AZURITE_ACCOUNT_NAME = "devstoreaccount1";
    private static final String AZURITE_ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> AZURITE =
            new GenericContainer<>(
                            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
                    .withCommand("azurite-blob --blobHost 0.0.0.0 --skipApiVersionCheck")
                    .withExposedPorts(10000);

    static {
        AZURITE.start();
        String blobEndpoint =
                "http://%s:%d/%s"
                        .formatted(
                                AZURITE.getHost(),
                                AZURITE.getMappedPort(10000),
                                AZURITE_ACCOUNT_NAME);
        String connectionString =
                "DefaultEndpointsProtocol=http;AccountName=%s;AccountKey=%s;BlobEndpoint=%s;"
                        .formatted(AZURITE_ACCOUNT_NAME, AZURITE_ACCOUNT_KEY, blobEndpoint);
        AzuriteFileStorageConfiguration.setConnectionString(connectionString);
    }

    @Test
    void createFileReturnsUploadingHandle() {
        FileUploadResponse response =
                fileStorageService.createFile(newRequest("report.pdf", "application/pdf"));

        assertNotNull(response.getFileHandleId());
        assertEquals(FileUploadStatus.UPLOADING, response.getUploadStatus());
        assertNotNull(response.getUploadUrl());
    }

    @Test
    void getMetadataForUnknownFileThrowsNotFoundException() {
        assertThrows(
                NotFoundException.class, () -> fileStorageService.getFileMetadata("nonexistent"));
    }

    @Test
    void downloadUrlRequiresUploadedStatus() {
        FileUploadResponse created =
                fileStorageService.createFile(
                        newRequest("pending.bin", "application/octet-stream"));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());

        assertThrows(
                IllegalArgumentException.class,
                () -> fileStorageService.getDownloadUrl(fileId, "wf-test"));
    }

    @Test
    void fullLifecyclePutSasConfirmGet() throws Exception {
        byte[] payload = "hello Azure".getBytes();
        FileUploadResponse created =
                fileStorageService.createFile(newRequest("test.txt", "text/plain"));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());

        putToSasUrl(created.getUploadUrl(), payload);

        FileUploadCompleteResponse confirmed = fileStorageService.confirmUpload(fileId);
        FileDownloadUrlResponse download = fileStorageService.getDownloadUrl(fileId, "wf-test");
        byte[] fetched = getFromSasUrl(download.getDownloadUrl());

        assertEquals(FileUploadStatus.UPLOADED, confirmed.getUploadStatus());
        assertArrayEquals(payload, fetched);
    }

    private static void putToSasUrl(String url, byte[] body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        // Azure block-blob PUT requires this header.
        conn.setRequestProperty("x-ms-blob-type", "BlockBlob");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        assertTrue(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300);
        conn.disconnect();
    }

    private static byte[] getFromSasUrl(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream()) {
            assertTrue(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300);
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }
}
