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
package org.conductoross.conductor.core.storage.converter;

import java.time.Instant;

import org.conductoross.conductor.model.FileModel;
import org.conductoross.conductor.model.file.*;
import org.junit.Test;

import static org.junit.Assert.*;

public class FileModelConverterTest {

    @Test
    public void testToFileModel() {
        FileUploadRequest request = new FileUploadRequest();
        request.setFileName("doc.pdf");
        request.setContentType("application/pdf");
        request.setWorkflowId("wf-1");
        request.setTaskId("task-1");

        FileModel model =
                FileModelConverter.toFileModel(request, "abc", "files/abc/doc.pdf", StorageType.S3);

        assertEquals("abc", model.getFileId());
        assertEquals("doc.pdf", model.getFileName());
        assertEquals("application/pdf", model.getContentType());
        assertEquals(StorageType.S3, model.getStorageType());
        assertEquals("files/abc/doc.pdf", model.getStoragePath());
        assertEquals(FileUploadStatus.UPLOADING, model.getUploadStatus());
        assertEquals("wf-1", model.getWorkflowId());
        assertEquals("task-1", model.getTaskId());
        assertNotNull(model.getCreatedAt());
        assertNotNull(model.getUpdatedAt());
    }

    @Test
    public void testToFileHandleDoesNotExposeStoragePath() {
        Instant now = Instant.now();
        FileModel model = new FileModel();
        model.setFileId("abc");
        model.setFileName("doc.pdf");
        model.setContentType("application/pdf");
        model.setStorageContentHash("hash123");
        model.setStorageType(StorageType.S3);
        model.setUploadStatus(FileUploadStatus.UPLOADED);
        model.setStoragePath("files/abc/doc.pdf");
        model.setWorkflowId("wf-1");
        model.setCreatedAt(now);
        model.setUpdatedAt(now);

        FileHandle handle = FileModelConverter.toFileHandle(model);

        assertEquals(FileIdToFileHandleIdConverter.PREFIX + "abc", handle.getFileHandleId());
        assertEquals("doc.pdf", handle.getFileName());
        assertEquals("hash123", handle.getContentHash());
        assertEquals(StorageType.S3, handle.getStorageType());
        assertEquals("wf-1", handle.getWorkflowId());
        assertEquals(now.toEpochMilli(), handle.getCreatedAt());
        assertEquals(now.toEpochMilli(), handle.getUpdatedAt());
    }

    @Test
    public void testToFileUploadResponse() {
        Instant now = Instant.now();
        FileModel model = new FileModel();
        model.setFileId("abc");
        model.setFileName("doc.pdf");
        model.setContentType("application/pdf");
        model.setStorageType(StorageType.S3);
        model.setUploadStatus(FileUploadStatus.UPLOADING);
        model.setCreatedAt(now);

        long expiry = Instant.now().plusSeconds(60).toEpochMilli();
        FileUploadResponse response =
                FileModelConverter.toFileUploadResponse(model, "https://s3/presigned", expiry);

        assertEquals(FileIdToFileHandleIdConverter.PREFIX + "abc", response.getFileHandleId());
        assertEquals("https://s3/presigned", response.getUploadUrl());
        assertEquals(expiry, response.getUploadUrlExpiresAt());
        assertEquals(FileUploadStatus.UPLOADING, response.getUploadStatus());
        assertEquals(now.toEpochMilli(), response.getCreatedAt());
    }
}
