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

import java.time.Duration;
import java.util.List;

import org.conductoross.conductor.model.file.StorageType;

/**
 * Pluggable storage backend abstraction. Each implementation encapsulates a backend-specific
 * mechanism for granting temporary access to content (presigned URL, SAS token, signed URL, or
 * direct path) and for reading storage metadata. Supports Bring Your Own Storage via custom
 * implementations.
 */
public interface FileStorage {

    /** Returns the {@link StorageType} this backend handles; stamped onto file metadata. */
    StorageType getStorageType();

    /**
     * Returns a fresh presigned upload URL (or backend-equivalent) for {@code storagePath}. Callers
     * must not cache — a new URL is generated on every call.
     */
    String generateUploadUrl(String storagePath, Duration expiration);

    /** Returns a fresh presigned download URL (or backend-equivalent) for {@code storagePath}. */
    String generateDownloadUrl(String storagePath, Duration expiration);

    /**
     * Reads existence, content hash, and actual byte size from the storage backend in a single
     * call. Returns {@code null} if the object is not present. {@code contentHash} is {@code null}
     * for backends that do not expose one (e.g. local).
     */
    StorageFileInfo getStorageFileInfo(String storagePath);

    /** Starts a backend-native multipart upload and returns its backend-specific upload ID. */
    String initiateMultipartUpload(String storagePath);

    /**
     * Returns a signed URL for one multipart part. Provider-specific query parameters may be added
     * by the transfer client after this URL is issued.
     *
     * @param partNumber 1-based part number
     */
    String generatePartUploadUrl(
            String storagePath, String uploadId, int partNumber, Duration expiration);

    /**
     * Finalizes a multipart upload.
     *
     * @param partETags ordered provider completion tokens, such as S3 ETags or Azure block IDs
     */
    void completeMultipartUpload(String storagePath, String uploadId, List<String> partETags);

    /**
     * Cancels a multipart upload when the backend exposes an explicit abort operation. Backends
     * whose uncommitted parts expire automatically can use this default no-op.
     */
    default void abortMultipartUpload(String storagePath, String uploadId) {}
}
