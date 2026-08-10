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
package org.conductoross.conductor.model;

import java.time.Instant;

import org.conductoross.conductor.model.file.FileUploadStatus;
import org.conductoross.conductor.model.file.StorageType;

/**
 * Server-internal model for a file-metadata record. Holds the bare {@code fileId} (no prefix), the
 * server-internal {@code storagePath}, backend-reported {@code storageContentHash} / {@code
 * storageContentSize}, upload status, and owning workflow/task IDs. Converted to DTOs (where {@code
 * fileId} becomes {@code fileHandleId}) by {@code FileModelConverter}.
 */
public class FileModel {

    /** Bare identifier — no prefix. Wrapped as {@code conductor://file/<fileId>} on the wire. */
    private String fileId;

    private String fileName;
    private String contentType;

    /**
     * Set on upload completion from {@link
     * org.conductoross.conductor.core.storage.FileStorage#getStorageFileInfo}. {@code null} for
     * local backend.
     */
    private String storageContentHash;

    /** Actual bytes on the backend, populated on upload completion. */
    private long storageContentSize;

    private StorageType storageType;

    /** Server-internal storage key. Never exposed via the REST API. */
    private String storagePath;

    private FileUploadStatus uploadStatus;
    private String workflowId;
    private String taskId;
    private Instant createdAt;
    private Instant updatedAt;

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
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

    public String getStorageContentHash() {
        return storageContentHash;
    }

    public void setStorageContentHash(String storageContentHash) {
        this.storageContentHash = storageContentHash;
    }

    public long getStorageContentSize() {
        return storageContentSize;
    }

    public void setStorageContentSize(long storageContentSize) {
        this.storageContentSize = storageContentSize;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public FileUploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(FileUploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "FileModel{fileId='"
                + fileId
                + "', fileName='"
                + fileName
                + "', uploadStatus="
                + uploadStatus
                + "}";
    }
}
