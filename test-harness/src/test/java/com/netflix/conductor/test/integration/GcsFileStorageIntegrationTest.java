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

import org.conductoross.conductor.dao.FileMetadataDAO;
import org.conductoross.conductor.model.file.FileDownloadUrlResponse;
import org.conductoross.conductor.model.file.FileIdToFileHandleIdConverter;
import org.conductoross.conductor.model.file.FileUploadCompleteResponse;
import org.conductoross.conductor.model.file.FileUploadResponse;
import org.conductoross.conductor.model.file.FileUploadStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.test.config.FakeGcsFileStorageConfiguration;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("file-storage-integration")
@TestPropertySource(
        properties = {
            "conductor.file-storage.enabled=true",
            "conductor.file-storage.type=gcs",
            "conductor.file-storage.gcs.bucket-name=" + GcsFileStorageIntegrationTest.BUCKET,
            "conductor.file-storage.gcs.project-id=fake"
        })
@Import(FakeGcsFileStorageConfiguration.class)
class GcsFileStorageIntegrationTest extends AbstractFileStorageIntegrationTest {

    static final String BUCKET = "conductor-file-storage-test";

    private static final int FAKE_GCS_PORT = 4443;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> FAKE_GCS =
            new GenericContainer<>(DockerImageName.parse("fsouza/fake-gcs-server:latest"))
                    .withExposedPorts(FAKE_GCS_PORT)
                    .withCommand("-scheme", "http", "-public-host", "localhost:" + FAKE_GCS_PORT)
                    .waitingFor(Wait.forLogMessage(".*server started at.*", 1));

    private static final Storage TEST_STORAGE;

    static {
        FAKE_GCS.start();
        String endpoint =
                "http://" + FAKE_GCS.getHost() + ":" + FAKE_GCS.getMappedPort(FAKE_GCS_PORT);
        FakeGcsFileStorageConfiguration.setEndpoint(endpoint);
        TEST_STORAGE =
                StorageOptions.newBuilder()
                        .setProjectId("fake")
                        .setHost(endpoint)
                        .setCredentials(NoCredentials.getInstance())
                        .build()
                        .getService();
    }

    @Autowired private FileMetadataDAO fileMetadataDAO;

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
    void fullLifecycleCreateUploadConfirmDownloadUrl() {
        byte[] payload = "hello GCS".getBytes();
        FileUploadResponse created =
                fileStorageService.createFile(newRequest("test.txt", "text/plain"));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());
        // Read storagePath from the DAO so the test stays format-agnostic.
        String storagePath = fileMetadataDAO.getFileMetadata(fileId).getStoragePath();
        // fake-gcs-server does not handle signed URLs on XML-API paths (signed PUT/GET at
        // /<bucket>/<object> returns 404), so we write the blob via the JSON API directly.
        // The assertion below still verifies that confirmUpload → getStorageFileInfo correctly
        // finds the object in the bucket.
        TEST_STORAGE.create(BlobInfo.newBuilder(BlobId.of(BUCKET, storagePath)).build(), payload);

        FileUploadCompleteResponse confirmed = fileStorageService.confirmUpload(fileId);
        FileDownloadUrlResponse download = fileStorageService.getDownloadUrl(fileId, "wf-test");

        assertEquals(FileUploadStatus.UPLOADED, confirmed.getUploadStatus());
        assertNotNull(download.getDownloadUrl());
        assertTrue(download.getDownloadUrl().contains("X-Goog-Signature"));
    }
}
