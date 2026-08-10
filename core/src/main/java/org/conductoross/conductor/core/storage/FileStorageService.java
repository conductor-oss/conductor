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

import java.util.List;

import org.conductoross.conductor.model.file.*;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Server-side service layer for file storage. Coordinates {@link FileStorage} (backend transfer)
 * with {@code FileMetadataDAO} (persistence). Consumed by the REST controller.
 *
 * <p>All methods take the bare {@code fileId} (no prefix); responses carry the prefixed {@code
 * fileHandleId}.
 */
@Validated
public interface FileStorageService {

    /**
     * Validates size, generates a new {@code fileId}, persists a {@code FileModel} with status
     * {@code UPLOADING}, and returns the response carrying a fresh presigned upload URL.
     *
     * <p>{@code workflowId} in the request is required. Throws {@link IllegalArgumentException} if
     * blank.
     */
    FileUploadResponse createFile(@NotNull @Valid FileUploadRequest request);

    /** Issues a fresh presigned upload URL for retry when the original has expired. */
    FileUploadUrlResponse getUploadUrl(@NotEmpty String fileId);

    /**
     * Verifies the object is present on the backend, reads and persists {@code contentHash} and
     * actual size, transitions the record to {@code UPLOADED}.
     */
    FileUploadCompleteResponse confirmUpload(@NotEmpty String fileId);

    /**
     * Issues a presigned download URL. Requires status {@code UPLOADED}.
     *
     * <p>Access control:
     *
     * <ul>
     *   <li>The file must have a {@code workflowId} (files without one are forbidden).
     *   <li>The caller's {@code workflowId} must belong to the same workflow family as the file's
     *       owner (self, ancestors, or descendants — no depth limit).
     * </ul>
     *
     * @throws com.netflix.conductor.core.exception.AccessForbiddenException if the caller is not in
     *     the file's workflow family
     */
    FileDownloadUrlResponse getDownloadUrl(@NotEmpty String fileId, @NotNull String workflowId);

    /** Returns full file metadata. The server-internal {@code storagePath} is not exposed. */
    FileHandle getFileMetadata(@NotEmpty String fileId);

    /**
     * Starts a backend-native multipart upload. Returns the backend upload ID, recommended part
     * size, and — for GCS/Azure — a resumable session URL. For S3 the response URL is {@code null}
     * and per-part URLs are obtained via {@link #getPartUploadUrl}.
     */
    MultipartInitResponse initiateMultipartUpload(@NotEmpty String fileId);

    /**
     * Issues a presigned URL for a single S3 multipart part. Not used for GCS/Azure.
     *
     * @param partNumber 1-based part number
     */
    FileUploadUrlResponse getPartUploadUrl(
            @NotEmpty String fileId, @NotEmpty String uploadId, int partNumber);

    /**
     * Finalizes a multipart upload and transitions the record to {@code UPLOADED}, persisting the
     * backend-reported {@code contentHash} and actual size.
     *
     * @param partETags ordered ETags (or backend equivalents) from each part upload
     */
    FileUploadCompleteResponse completeMultipartUpload(
            @NotEmpty String fileId, @NotEmpty String uploadId, @NotNull List<String> partETags);
}
