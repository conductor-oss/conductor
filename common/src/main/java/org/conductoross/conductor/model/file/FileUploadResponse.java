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
package org.conductoross.conductor.model.file;

import java.util.Objects;

/**
 * Response to {@code POST /api/files}. Carries the new {@code fileHandleId} plus a presigned upload
 * URL and its expiry. Status is {@code UPLOADING}; client confirms via {@code POST
 * /api/files/{fileId}/upload-complete}.
 */
public class FileUploadResponse {

    /** Prefixed handle: {@code conductor://file/<fileId>}. */
    private String fileHandleId;

    private String fileName;

    private String contentType;

    private StorageType storageType;

    private FileUploadStatus uploadStatus;

    private String uploadUrl;

    private long uploadUrlExpiresAt;

    private long createdAt;

    public String getFileHandleId() {
        return fileHandleId;
    }

    public void setFileHandleId(String fileHandleId) {
        this.fileHandleId = fileHandleId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    public FileUploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(FileUploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public long getUploadUrlExpiresAt() {
        return uploadUrlExpiresAt;
    }

    public void setUploadUrlExpiresAt(long uploadUrlExpiresAt) {
        this.uploadUrlExpiresAt = uploadUrlExpiresAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileUploadResponse that)) return false;
        return Objects.equals(fileHandleId, that.fileHandleId)
                && Objects.equals(fileName, that.fileName)
                && Objects.equals(contentType, that.contentType)
                && storageType == that.storageType
                && uploadStatus == that.uploadStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileHandleId, fileName, contentType, storageType, uploadStatus);
    }

    @Override
    public String toString() {
        return "FileUploadResponse{fileHandleId='%s', uploadStatus=%s}"
                .formatted(fileHandleId, uploadStatus);
    }
}
