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
package org.conductoross.conductor.gcs.storage;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.core.storage.StorageFileInfo;
import org.conductoross.conductor.gcs.config.GcsFileStorageProperties;
import org.conductoross.conductor.model.file.StorageType;

import com.google.cloud.storage.*;

/** {@link FileStorage} backed by Google Cloud Storage. */
public class GcsFileStorage implements FileStorage {

    private final String bucketName;
    private final Storage storage;

    public GcsFileStorage(GcsFileStorageProperties properties, Storage storage) {
        this.bucketName = properties.getBucketName();
        this.storage = storage;
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.GCS;
    }

    @Override
    public String generateUploadUrl(String storagePath, Duration expiration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storagePath)).build();
        URL url = getSignedUrl(blobInfo, expiration, HttpMethod.PUT);
        return url.toString();
    }

    @Override
    public String generateDownloadUrl(String storagePath, Duration expiration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, storagePath)).build();
        URL url = getSignedUrl(blobInfo, expiration, HttpMethod.GET);
        return url.toString();
    }

    @Override
    public StorageFileInfo getStorageFileInfo(String storagePath) {
        Blob blob = storage.get(BlobId.of(bucketName, storagePath));
        if (blob == null || !blob.exists()) {
            return null;
        }
        StorageFileInfo info = new StorageFileInfo();
        info.setExists(true);
        info.setContentHash(blob.getMd5());
        info.setContentSize(blob.getSize());
        return info;
    }

    @Override
    public String initiateMultipartUpload(String storagePath) {
        // GCS compose: create a signed URL for resumable upload
        // SDK handles the actual resumable session — return a signed PUT URL
        return generateUploadUrl(storagePath, Duration.ofHours(1));
    }

    @Override
    public String generatePartUploadUrl(
            String storagePath, String uploadId, int partNumber, Duration expiration) {
        // GCS reuses the resumable URI for all parts
        return uploadId;
    }

    @Override
    public void completeMultipartUpload(
            String storagePath, String uploadId, List<String> partETags) {
        // GCS resumable upload auto-completes on final byte range — no-op
    }

    private URL getSignedUrl(BlobInfo blobInfo, Duration expiration, HttpMethod httpMethod) {
        return storage.signUrl(
                blobInfo,
                expiration.getSeconds(),
                TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(httpMethod),
                Storage.SignUrlOption.withV4Signature());
    }
}
