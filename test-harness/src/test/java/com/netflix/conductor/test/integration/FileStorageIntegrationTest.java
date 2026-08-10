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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.conductoross.conductor.model.file.FileDownloadUrlResponse;
import org.conductoross.conductor.model.file.FileHandle;
import org.conductoross.conductor.model.file.FileIdToFileHandleIdConverter;
import org.conductoross.conductor.model.file.FileUploadCompleteResponse;
import org.conductoross.conductor.model.file.FileUploadResponse;
import org.conductoross.conductor.model.file.FileUploadStatus;
import org.conductoross.conductor.model.file.MultipartInitResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.netflix.conductor.core.exception.NotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the file-storage feature backed by the local FS adapter. Inherits the Redis
 * testcontainer + Spring wiring from {@link AbstractFileStorageIntegrationTest} and only sets the
 * local-backend specific properties here.
 */
@TestPropertySource(
        properties = {
            "conductor.file-storage.enabled=true",
            "conductor.file-storage.type=local",
            "conductor.file-storage.local.directory=build/tmp/file-storage-spec"
        })
class FileStorageIntegrationTest extends AbstractFileStorageIntegrationTest {

    @Test
    void createFileReturnsPendingUploadResponseWithHandleId() {
        FileUploadResponse response =
                fileStorageService.createFile(newRequest("report.pdf", "application/pdf"));

        assertNotNull(response.getFileHandleId());
        assertEquals("report.pdf", response.getFileName());
        assertEquals(FileUploadStatus.UPLOADING, response.getUploadStatus());
        assertNotNull(response.getUploadUrl());
    }

    @Test
    void getFileMetadataReturnsCorrectFields() {
        FileUploadResponse created =
                fileStorageService.createFile(newRequest("doc.pdf", "application/pdf"));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());

        FileHandle handle = fileStorageService.getFileMetadata(fileId);

        assertEquals(created.getFileHandleId(), handle.getFileHandleId());
        assertEquals("doc.pdf", handle.getFileName());
        assertEquals("application/pdf", handle.getContentType());
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
    void fullLifecycleCreateConfirmDownloadUrl() throws Exception {
        FileUploadResponse created =
                fileStorageService.createFile(newRequest("test.txt", "text/plain"));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());
        // Resolve the upload target from the URL the service handed back (format-agnostic).
        Path storagePath = Path.of(URI.create(created.getUploadUrl()));
        Files.createDirectories(storagePath.getParent());
        Files.writeString(storagePath, "hello");

        FileUploadCompleteResponse confirmed = fileStorageService.confirmUpload(fileId);
        FileDownloadUrlResponse download = fileStorageService.getDownloadUrl(fileId, "wf-test");

        assertEquals(FileUploadStatus.UPLOADED, confirmed.getUploadStatus());
        assertNotNull(download.getDownloadUrl());
    }

    @Test
    void multipartLifecycleInitiatePartUrl() {
        FileUploadResponse created =
                fileStorageService.createFile(newRequest("large.bin", "application/octet-stream"));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());

        MultipartInitResponse init = fileStorageService.initiateMultipartUpload(fileId);

        assertNotNull(init.getUploadId());
        assertNotNull(
                fileStorageService.getPartUploadUrl(fileId, init.getUploadId(), 1).getUploadUrl());
    }
}
