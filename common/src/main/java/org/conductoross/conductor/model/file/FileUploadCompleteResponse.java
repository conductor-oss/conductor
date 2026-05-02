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
 * Response to {@code POST /api/files/{fileId}/upload-complete}. {@code contentHash} is the
 * backend-reported hash, or {@code null} for backends that do not expose one.
 */
public class FileUploadCompleteResponse {

    private String fileHandleId;

    private FileUploadStatus uploadStatus;

    private String contentHash;

    public String getFileHandleId() {
        return fileHandleId;
    }

    public void setFileHandleId(String fileHandleId) {
        this.fileHandleId = fileHandleId;
    }

    public FileUploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(FileUploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileUploadCompleteResponse that)) return false;
        return Objects.equals(fileHandleId, that.fileHandleId)
                && uploadStatus == that.uploadStatus
                && Objects.equals(contentHash, that.contentHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileHandleId, uploadStatus, contentHash);
    }

    @Override
    public String toString() {
        return "FileUploadCompleteResponse{fileHandleId='%s', uploadStatus=%s}"
                .formatted(fileHandleId, uploadStatus);
    }
}
