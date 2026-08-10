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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.conductoross.conductor.core.storage.converter.FileModelConverter;
import org.conductoross.conductor.dao.FileMetadataDAO;
import org.conductoross.conductor.model.FileModel;
import org.conductoross.conductor.model.file.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.netflix.conductor.core.exception.AccessForbiddenException;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.NotFoundException;

/**
 * Default {@link FileStorageService} implementation. Activated when {@code
 * conductor.file-storage.enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageServiceImpl implements FileStorageService {

    private static final String STORAGE_PATH = "conductor/%s/%s";

    private final FileStorage fileStorage;
    private final FileMetadataDAO fileMetadataDAO;
    private final FileStorageProperties properties;
    private final WorkflowFamilyResolver workflowFamilyResolver;

    public FileStorageServiceImpl(
            FileStorage fileStorage,
            FileMetadataDAO fileMetadataDAO,
            FileStorageProperties properties,
            WorkflowFamilyResolver workflowFamilyResolver) {
        this.fileStorage = fileStorage;
        this.fileMetadataDAO = fileMetadataDAO;
        this.properties = properties;
        this.workflowFamilyResolver = workflowFamilyResolver;
    }

    @Override
    public FileUploadResponse createFile(FileUploadRequest request) {
        if (request.getWorkflowId() == null || request.getWorkflowId().isBlank()) {
            throw new IllegalArgumentException("workflowId is required");
        }

        String fileId = UUID.randomUUID().toString();
        String storagePath = STORAGE_PATH.formatted(request.getWorkflowId(), fileId);

        FileModel model =
                FileModelConverter.toFileModel(
                        request, fileId, storagePath, fileStorage.getStorageType());
        fileMetadataDAO.createFileMetadata(model);

        String uploadUrl =
                fileStorage.generateUploadUrl(storagePath, properties.getSignedUrlExpiration());
        long expiresAt = Instant.now().plus(properties.getSignedUrlExpiration()).toEpochMilli();

        return FileModelConverter.toFileUploadResponse(model, uploadUrl, expiresAt);
    }

    @Override
    public FileUploadUrlResponse getUploadUrl(String fileId) {
        FileModel model = getFileModelOrThrow(fileId);
        String uploadUrl =
                fileStorage.generateUploadUrl(
                        model.getStoragePath(), properties.getSignedUrlExpiration());
        long expiresAt = Instant.now().plus(properties.getSignedUrlExpiration()).toEpochMilli();

        FileUploadUrlResponse response = new FileUploadUrlResponse();
        response.setFileHandleId(FileIdToFileHandleIdConverter.toFileHandleId(fileId));
        response.setUploadUrl(uploadUrl);
        response.setExpiresAt(expiresAt);
        return response;
    }

    @Override
    public FileUploadCompleteResponse confirmUpload(String fileId) {
        FileModel model = getFileModelOrThrow(fileId);
        if (model.getUploadStatus() == FileUploadStatus.UPLOADED) {
            throw new ConflictException("File already uploaded: " + fileId);
        }

        StorageFileInfo info = fileStorage.getStorageFileInfo(model.getStoragePath());
        if (info == null || !info.isExists()) {
            throw new NonTransientException("File not found on storage backend: " + fileId);
        }

        fileMetadataDAO.updateUploadComplete(
                fileId, FileUploadStatus.UPLOADED, info.getContentHash(), info.getContentSize());

        FileUploadCompleteResponse response = new FileUploadCompleteResponse();
        response.setFileHandleId(FileIdToFileHandleIdConverter.toFileHandleId(fileId));
        response.setUploadStatus(FileUploadStatus.UPLOADED);
        response.setContentHash(info.getContentHash());
        return response;
    }

    @Override
    public FileDownloadUrlResponse getDownloadUrl(String fileId, String workflowId) {
        FileModel model = getFileModel(fileId, workflowId);
        String downloadUrl =
                fileStorage.generateDownloadUrl(
                        model.getStoragePath(), properties.getSignedUrlExpiration());
        long expiresAt = Instant.now().plus(properties.getSignedUrlExpiration()).toEpochMilli();

        FileDownloadUrlResponse response = new FileDownloadUrlResponse();
        response.setFileHandleId(FileIdToFileHandleIdConverter.toFileHandleId(fileId));
        response.setDownloadUrl(downloadUrl);
        response.setExpiresAt(expiresAt);
        return response;
    }

    @Override
    public FileHandle getFileMetadata(String fileId) {
        FileModel model = getFileModelOrThrow(fileId);
        return FileModelConverter.toFileHandle(model);
    }

    @Override
    public MultipartInitResponse initiateMultipartUpload(String fileId) {
        FileModel model = getFileModelOrThrow(fileId);
        String uploadId = fileStorage.initiateMultipartUpload(model.getStoragePath());

        MultipartInitResponse response = new MultipartInitResponse();
        response.setFileHandleId(FileIdToFileHandleIdConverter.toFileHandleId(fileId));
        response.setUploadId(uploadId);
        return response;
    }

    @Override
    public FileUploadUrlResponse getPartUploadUrl(String fileId, String uploadId, int partNumber) {
        FileModel model = getFileModelOrThrow(fileId);
        String url =
                fileStorage.generatePartUploadUrl(
                        model.getStoragePath(),
                        uploadId,
                        partNumber,
                        properties.getSignedUrlExpiration());
        long expiresAt = Instant.now().plus(properties.getSignedUrlExpiration()).toEpochMilli();

        FileUploadUrlResponse response = new FileUploadUrlResponse();
        response.setFileHandleId(FileIdToFileHandleIdConverter.toFileHandleId(fileId));
        response.setUploadUrl(url);
        response.setExpiresAt(expiresAt);
        return response;
    }

    @Override
    public FileUploadCompleteResponse completeMultipartUpload(
            String fileId, String uploadId, List<String> partETags) {
        FileModel model = getFileModelOrThrow(fileId);
        fileStorage.completeMultipartUpload(model.getStoragePath(), uploadId, partETags);

        StorageFileInfo info = fileStorage.getStorageFileInfo(model.getStoragePath());
        if (info == null || !info.isExists()) {
            throw new NonTransientException(
                    "File not found on storage after multipart complete: " + fileId);
        }

        fileMetadataDAO.updateUploadComplete(
                fileId, FileUploadStatus.UPLOADED, info.getContentHash(), info.getContentSize());

        FileUploadCompleteResponse response = new FileUploadCompleteResponse();
        response.setFileHandleId(FileIdToFileHandleIdConverter.toFileHandleId(fileId));
        response.setUploadStatus(FileUploadStatus.UPLOADED);
        response.setContentHash(info.getContentHash());
        return response;
    }

    private @NonNull FileModel getFileModel(String fileId, String workflowId) {
        FileModel model = getFileModelOrThrow(fileId);
        if (model.getUploadStatus() != FileUploadStatus.UPLOADED) {
            throw new IllegalArgumentException(
                    "File not yet uploaded: " + fileId + ", status=" + model.getUploadStatus());
        }

        if (model.getWorkflowId() == null || model.getWorkflowId().isBlank()) {
            throw new AccessForbiddenException("File has no workflowId: " + fileId);
        }

        Set<String> family = workflowFamilyResolver.getFamily(workflowId);
        if (!family.contains(model.getWorkflowId())) {
            throw new AccessForbiddenException(
                    "workflowId %s is not in the workflow family of file %s"
                            .formatted(workflowId, fileId));
        }
        return model;
    }

    private FileModel getFileModelOrThrow(String fileId) {
        FileModel model = fileMetadataDAO.getFileMetadata(fileId);
        if (model == null) {
            throw new NotFoundException("File not found: " + fileId);
        }
        return model;
    }
}
