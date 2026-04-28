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

/** Response to {@code POST /api/files/{fileId}/multipart} — initiates a multipart upload. */
public class MultipartInitResponse {

    private String fileHandleId;

    /** Backend-specific multipart identifier (S3 {@code UploadId}, GCS resumable session ID). */
    private String uploadId;

    /** Resumable session URL for GCS/Azure; {@code null} for S3 (clients use per-part URLs). */
    private String uploadUrl;

    public String getFileHandleId() {
        return fileHandleId;
    }

    public void setFileHandleId(String fileHandleId) {
        this.fileHandleId = fileHandleId;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultipartInitResponse that)) return false;
        return Objects.equals(fileHandleId, that.fileHandleId)
                && Objects.equals(uploadId, that.uploadId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileHandleId, uploadId);
    }

    @Override
    public String toString() {
        return "MultipartInitResponse{fileHandleId='%s', uploadId='%s'}"
                .formatted(fileHandleId, uploadId);
    }
}
