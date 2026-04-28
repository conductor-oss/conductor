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
package org.conductoross.conductor.core.storage;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.conductoross.conductor.model.FileModel;
import org.conductoross.conductor.model.file.FileDownloadUrlResponse;
import org.conductoross.conductor.model.file.FileHandle;
import org.conductoross.conductor.model.file.FileIdToFileHandleIdConverter;
import org.conductoross.conductor.model.file.FileUploadCompleteResponse;
import org.conductoross.conductor.model.file.FileUploadRequest;
import org.conductoross.conductor.model.file.FileUploadResponse;
import org.conductoross.conductor.model.file.FileUploadStatus;
import org.conductoross.conductor.model.file.FileUploadUrlResponse;
import org.conductoross.conductor.model.file.MultipartInitResponse;
import org.conductoross.conductor.model.file.StorageType;
import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.core.exception.AccessForbiddenException;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.junit.Assert.*;

public class FileStorageServiceImplTest {

    private StubFileStorage fileStorage;
    private StubFileMetadataDAO fileMetadataDAO;
    private FileStorageProperties properties;
    private FileStorageServiceImpl service;

    private static final String WORKFLOW_ID = "wf-test";

    @Before
    public void setUp() {
        fileStorage = new StubFileStorage();
        fileMetadataDAO = new StubFileMetadataDAO();
        properties = new FileStorageProperties();
        service =
                new FileStorageServiceImpl(
                        fileStorage, fileMetadataDAO, properties, new StubWorkflowFamilyResolver());
    }

    @Test
    public void testCreateFile() {
        FileUploadRequest request = newRequest("report.pdf", "application/pdf");

        FileUploadResponse response = service.createFile(request);

        assertNotNull(response.getFileHandleId());
        assertTrue(response.getFileHandleId().startsWith(FileIdToFileHandleIdConverter.PREFIX));
        assertEquals("report.pdf", response.getFileName());
        assertEquals("application/pdf", response.getContentType());
        assertEquals(StorageType.LOCAL, response.getStorageType());
        assertEquals(FileUploadStatus.UPLOADING, response.getUploadStatus());
        assertNotNull(response.getUploadUrl());
        assertTrue(response.getUploadUrlExpiresAt() > 0);
        assertTrue(response.getCreatedAt() > 0);
    }

    @Test
    public void testGetUploadUrl() {
        String fileId = createTestFileId();
        FileUploadUrlResponse response = service.getUploadUrl(fileId);

        assertEquals(
                FileIdToFileHandleIdConverter.toFileHandleId(fileId), response.getFileHandleId());
        assertNotNull(response.getUploadUrl());
        assertTrue(response.getExpiresAt() > 0);
    }

    @Test(expected = NotFoundException.class)
    public void testGetUploadUrlNotFound() {
        service.getUploadUrl("nonexistent");
    }

    @Test
    public void testConfirmUpload() {
        String fileId = createTestFileId();
        simulateFileOnStorage(fileId);

        FileUploadCompleteResponse response = service.confirmUpload(fileId);

        assertEquals(
                FileIdToFileHandleIdConverter.toFileHandleId(fileId), response.getFileHandleId());
        assertEquals(FileUploadStatus.UPLOADED, response.getUploadStatus());
    }

    @Test(expected = ConflictException.class)
    public void testConfirmUploadAlreadyUploaded() {
        String fileId = createTestFileId();
        simulateFileOnStorage(fileId);

        service.confirmUpload(fileId);
        service.confirmUpload(fileId);
    }

    @Test(expected = NonTransientException.class)
    public void testConfirmUploadFileNotOnStorage() {
        String fileId = createTestFileId();
        service.confirmUpload(fileId);
    }

    @Test
    public void testGetDownloadUrl() {
        String fileId = createTestFileId();
        simulateFileOnStorage(fileId);
        service.confirmUpload(fileId);

        FileDownloadUrlResponse response = service.getDownloadUrl(fileId, WORKFLOW_ID);

        assertEquals(
                FileIdToFileHandleIdConverter.toFileHandleId(fileId), response.getFileHandleId());
        assertNotNull(response.getDownloadUrl());
        assertTrue(response.getExpiresAt() > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDownloadUrlNotUploaded() {
        String fileId = createTestFileId();
        service.getDownloadUrl(fileId, WORKFLOW_ID);
    }

    // ── Upload enforcement ────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testCreateFileRequiresWorkflowId() {
        FileUploadRequest request = new FileUploadRequest();
        request.setFileName("f.pdf");
        request.setContentType("application/pdf");
        // workflowId intentionally omitted
        service.createFile(request);
    }

    // ── Download enforcement ──────────────────────────────────────────────────

    @Test(expected = AccessForbiddenException.class)
    public void testDownloadForbiddenWhenFileHasNoWorkflowId() {
        String fileId = UUID.randomUUID().toString();
        FileModel model = uploadedModelWithNoWorkflowId(fileId);
        fileMetadataDAO.createFileMetadata(model);
        fileStorage.putFile(model.getStoragePath(), new byte[] {1});

        service.getDownloadUrl(fileId, WORKFLOW_ID);
    }

    @Test(expected = AccessForbiddenException.class)
    public void testDownloadForbiddenWhenCallerNotInFamily() {
        String fileId = createTestFileId();
        simulateFileOnStorage(fileId);
        service.confirmUpload(fileId);

        service.getDownloadUrl(fileId, "unrelated-workflow");
    }

    @Test
    public void testDownloadAllowedForFamilyMember() {
        // StubWorkflowFamilyResolver includes WORKFLOW_ID in the family
        String fileId = createTestFileId();
        simulateFileOnStorage(fileId);
        service.confirmUpload(fileId);

        FileDownloadUrlResponse response = service.getDownloadUrl(fileId, WORKFLOW_ID);
        assertNotNull(response.getDownloadUrl());
    }

    @Test
    public void testDownloadAllowedForChildWorkflowAccessingParentFile() {
        // File owned by the parent; child workflow requests it.
        // Resolver says child's family = {child, parent} → access granted.
        String parentId = "wf-parent";
        String childId = "wf-child";
        FileStorageServiceImpl svc =
                new FileStorageServiceImpl(
                        fileStorage,
                        fileMetadataDAO,
                        properties,
                        wfId -> wfId.equals(childId) ? Set.of(childId, parentId) : Set.of(wfId));

        FileUploadResponse created =
                svc.createFile(newRequestWithWorkflow("parent-file.bin", parentId));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());
        simulateFileOnStorage(fileId);
        svc.confirmUpload(fileId);

        FileDownloadUrlResponse response = svc.getDownloadUrl(fileId, childId);
        assertNotNull(response.getDownloadUrl());
    }

    @Test
    public void testDownloadAllowedForParentWorkflowAccessingChildFile() {
        // File owned by the child; parent workflow requests it.
        // Resolver says parent's family = {parent, child} → access granted.
        String parentId = "wf-parent";
        String childId = "wf-child";
        FileStorageServiceImpl svc =
                new FileStorageServiceImpl(
                        fileStorage,
                        fileMetadataDAO,
                        properties,
                        wfId -> wfId.equals(parentId) ? Set.of(parentId, childId) : Set.of(wfId));

        FileUploadResponse created =
                svc.createFile(newRequestWithWorkflow("child-file.bin", childId));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());
        simulateFileOnStorage(fileId);
        svc.confirmUpload(fileId);

        FileDownloadUrlResponse response = svc.getDownloadUrl(fileId, parentId);
        assertNotNull(response.getDownloadUrl());
    }

    @Test
    public void testGetFileMetadata() {
        String fileId = createTestFileId();

        FileHandle handle = service.getFileMetadata(fileId);

        assertEquals(
                FileIdToFileHandleIdConverter.toFileHandleId(fileId), handle.getFileHandleId());
        assertEquals("report.pdf", handle.getFileName());
        assertEquals(StorageType.LOCAL, handle.getStorageType());
    }

    @Test(expected = NotFoundException.class)
    public void testGetFileMetadataNotFound() {
        service.getFileMetadata("nonexistent");
    }

    @Test
    public void testInitiateMultipartUpload() {
        String fileId = createTestFileId();
        MultipartInitResponse response = service.initiateMultipartUpload(fileId);

        assertEquals(
                FileIdToFileHandleIdConverter.toFileHandleId(fileId), response.getFileHandleId());
        assertNotNull(response.getUploadId());
    }

    @Test
    public void testGetPartUploadUrl() {
        String fileId = createTestFileId();
        FileUploadUrlResponse response = service.getPartUploadUrl(fileId, "upload-123", 1);

        assertEquals(
                FileIdToFileHandleIdConverter.toFileHandleId(fileId), response.getFileHandleId());
        assertNotNull(response.getUploadUrl());
        assertTrue(response.getExpiresAt() > 0);
    }

    @Test
    public void testCompleteMultipartUpload() {
        String fileId = createTestFileId();
        FileUploadCompleteResponse response =
                service.completeMultipartUpload(fileId, "upload-123", List.of("etag1", "etag2"));

        assertEquals(
                FileIdToFileHandleIdConverter.toFileHandleId(fileId), response.getFileHandleId());
        assertEquals(FileUploadStatus.UPLOADED, response.getUploadStatus());
    }

    private String createTestFileId() {
        FileUploadResponse created =
                service.createFile(newRequest("report.pdf", "application/pdf"));
        return FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());
    }

    private FileUploadRequest newRequest(String name, String contentType) {
        FileUploadRequest request = new FileUploadRequest();
        request.setFileName(name);
        request.setContentType(contentType);
        request.setWorkflowId(WORKFLOW_ID);
        return request;
    }

    private FileUploadRequest newRequestWithWorkflow(String name, String workflowId) {
        FileUploadRequest request = new FileUploadRequest();
        request.setFileName(name);
        request.setContentType("application/octet-stream");
        request.setWorkflowId(workflowId);
        return request;
    }

    private void simulateFileOnStorage(String fileId) {
        String storagePath = fileMetadataDAO.getFileMetadata(fileId).getStoragePath();
        fileStorage.putFile(storagePath, new byte[] {1, 2, 3});
    }

    private FileModel uploadedModelWithNoWorkflowId(String fileId) {
        FileModel model = new FileModel();
        model.setFileId(fileId);
        model.setFileName("test.bin");
        model.setContentType("application/octet-stream");
        model.setStorageType(StorageType.LOCAL);
        model.setStoragePath("files/" + fileId + "/test.bin");
        model.setUploadStatus(FileUploadStatus.UPLOADED);
        model.setWorkflowId(null);
        model.setCreatedAt(Instant.now());
        model.setUpdatedAt(Instant.now());
        return model;
    }
}
