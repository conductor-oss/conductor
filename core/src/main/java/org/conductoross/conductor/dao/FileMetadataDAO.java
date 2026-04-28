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
package org.conductoross.conductor.dao;

import java.util.List;

import org.conductoross.conductor.model.FileModel;
import org.conductoross.conductor.model.file.FileUploadStatus;

/**
 * Persistence for {@link FileModel}. Implemented per supported DB (Postgres, MySQL, SQLite, Redis,
 * Cassandra). All methods take the bare {@code fileId} — the DAO never sees the prefixed {@code
 * fileHandleId}.
 */
public interface FileMetadataDAO {

    /** Inserts a new record. Typical initial status is {@code UPLOADING}. */
    void createFileMetadata(FileModel fileModel);

    /** Returns the record or {@code null} when not found. */
    FileModel getFileMetadata(String fileId);

    /**
     * Updates only the upload status. Used by the background audit to mark stale {@code UPLOADING}
     * records as {@code FAILED}. For the normal completion path use {@link #updateUploadComplete}.
     */
    void updateUploadStatus(String fileId, FileUploadStatus status);

    /**
     * Atomic transition to {@code UPLOADED} with storage-reported {@code contentHash} and {@code
     * contentSize}. {@code contentHash} may be {@code null} for backends that do not expose one.
     */
    void updateUploadComplete(
            String fileId, FileUploadStatus status, String contentHash, long contentSize);

    /**
     * Lookup for files owned by a workflow (files supplied as workflow input). Used for audit and
     * storage-usage reporting.
     */
    List<FileModel> getFilesByWorkflowId(String workflowId);

    /**
     * Lookup for files produced by a task (files in task output). Used for audit and storage-usage
     * reporting.
     */
    List<FileModel> getFilesByTaskId(String taskId);
}
