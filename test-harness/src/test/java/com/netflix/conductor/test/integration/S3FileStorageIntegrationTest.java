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
package com.netflix.conductor.test.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import org.conductoross.conductor.model.file.FileDownloadUrlResponse;
import org.conductoross.conductor.model.file.FileIdToFileHandleIdConverter;
import org.conductoross.conductor.model.file.FileUploadCompleteResponse;
import org.conductoross.conductor.model.file.FileUploadResponse;
import org.conductoross.conductor.model.file.FileUploadStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.test.config.LocalStackS3FileStorageConfiguration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("file-storage-integration")
@TestPropertySource(
        properties = {
            "conductor.file-storage.enabled=true",
            "conductor.file-storage.type=s3",
            "conductor.file-storage.s3.bucket-name=" + S3FileStorageIntegrationTest.BUCKET,
            "conductor.file-storage.s3.region=us-east-1"
        })
@Import(LocalStackS3FileStorageConfiguration.class)
class S3FileStorageIntegrationTest extends AbstractFileStorageIntegrationTest {

    static final String BUCKET = "conductor-file-storage-test";

    @SuppressWarnings("resource")
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
                    .withServices(LocalStackContainer.Service.S3);

    static {
        LOCALSTACK.start();
        String endpoint = LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString();
        LocalStackS3FileStorageConfiguration.setLocalStackEndpoint(endpoint);
    }

    @BeforeAll
    static void createBucket() {
        try (S3Client client =
                S3Client.builder()
                        .endpointOverride(
                                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                LOCALSTACK.getAccessKey(),
                                                LOCALSTACK.getSecretKey())))
                        .forcePathStyle(true)
                        .build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @Test
    void createFileReturnsUploadingHandle() {
        FileUploadResponse response =
                fileStorageService.createFile(newRequest("report.pdf", "application/pdf"));

        assertNotNull(response.getFileHandleId());
        assertEquals(FileUploadStatus.UPLOADING, response.getUploadStatus());
        assertNotNull(response.getUploadUrl());
    }

    @Test
    void getMetadataForUnknownFileThrowsNotFoundException() {
        assertThrows(
                NotFoundException.class, () -> fileStorageService.getFileMetadata("nonexistent"));
    }

    @Test
    void downloadUrlRequiresUploadedStatus() {
        FileUploadResponse created =
                fileStorageService.createFile(
                        newRequest("pending.bin", "application/octet-stream"));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());

        assertThrows(
                IllegalArgumentException.class,
                () -> fileStorageService.getDownloadUrl(fileId, "wf-test"));
    }

    @Test
    void fullLifecyclePutSignedConfirmGet() throws Exception {
        byte[] payload = "hello S3".getBytes();
        FileUploadResponse created =
                fileStorageService.createFile(newRequest("test.txt", "text/plain"));
        String fileId = FileIdToFileHandleIdConverter.toFileId(created.getFileHandleId());

        putToPresignedUrl(created.getUploadUrl(), payload);

        FileUploadCompleteResponse confirmed = fileStorageService.confirmUpload(fileId);
        FileDownloadUrlResponse download = fileStorageService.getDownloadUrl(fileId, "wf-test");
        byte[] fetched = getFromPresignedUrl(download.getDownloadUrl());

        assertEquals(FileUploadStatus.UPLOADED, confirmed.getUploadStatus());
        assertArrayEquals(payload, fetched);
    }

    private static void putToPresignedUrl(String url, byte[] body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        assertTrue(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300);
        conn.disconnect();
    }

    private static byte[] getFromPresignedUrl(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream()) {
            assertTrue(conn.getResponseCode() >= 200 && conn.getResponseCode() < 300);
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }
}
