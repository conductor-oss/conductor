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
package org.conductoross.conductor.s3.storage;

import java.time.Duration;
import java.util.List;

import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.core.storage.StorageFileInfo;
import org.conductoross.conductor.model.file.StorageType;
import org.conductoross.conductor.s3.config.S3FileStorageProperties;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

public class S3FileStorage implements FileStorage {

    private final String bucketName;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3FileStorage(
            S3FileStorageProperties properties, S3Client s3Client, S3Presigner s3Presigner) {
        this.bucketName = properties.getBucketName();
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.S3;
    }

    @Override
    public String generateUploadUrl(String storagePath, Duration expiration) {
        PutObjectRequest putRequest =
                PutObjectRequest.builder().bucket(bucketName).key(storagePath).build();
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(expiration)
                        .putObjectRequest(putRequest)
                        .build();
        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    @Override
    public String generateDownloadUrl(String storagePath, Duration expiration) {
        GetObjectRequest getRequest =
                GetObjectRequest.builder().bucket(bucketName).key(storagePath).build();
        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(expiration)
                        .getObjectRequest(getRequest)
                        .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public StorageFileInfo getStorageFileInfo(String storagePath) {
        try {
            HeadObjectResponse head =
                    s3Client.headObject(
                            HeadObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(storagePath)
                                    .build());
            StorageFileInfo info = new StorageFileInfo();
            info.setExists(true);
            info.setContentHash(head.eTag());
            info.setContentSize(head.contentLength());
            return info;
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public String initiateMultipartUpload(String storagePath) {
        CreateMultipartUploadResponse response =
                s3Client.createMultipartUpload(
                        CreateMultipartUploadRequest.builder()
                                .bucket(bucketName)
                                .key(storagePath)
                                .build());
        return response.uploadId();
    }

    @Override
    public String generatePartUploadUrl(
            String storagePath, String uploadId, int partNumber, Duration expiration) {
        UploadPartRequest uploadPartRequest =
                UploadPartRequest.builder()
                        .bucket(bucketName)
                        .key(storagePath)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build();
        UploadPartPresignRequest presignRequest =
                UploadPartPresignRequest.builder()
                        .signatureDuration(expiration)
                        .uploadPartRequest(uploadPartRequest)
                        .build();
        return s3Presigner.presignUploadPart(presignRequest).url().toString();
    }

    @Override
    public void completeMultipartUpload(
            String storagePath, String uploadId, List<String> partETags) {
        List<CompletedPart> parts = new java.util.ArrayList<>();
        for (int i = 0; i < partETags.size(); i++) {
            parts.add(CompletedPart.builder().partNumber(i + 1).eTag(partETags.get(i)).build());
        }
        s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(storagePath)
                        .uploadId(uploadId)
                        .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                        .build());
    }
}
