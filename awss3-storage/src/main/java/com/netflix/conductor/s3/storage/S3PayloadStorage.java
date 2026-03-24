/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.s3.storage;

import java.io.InputStream;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.s3.config.S3Properties;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * An implementation of {@link ExternalPayloadStorage} using AWS S3 for storing large JSON payload
 * data.
 *
 * <p><em>NOTE: The S3 client assumes that access to S3 is configured on the instance.</em>
 *
 * @see <a
 *     href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html">AWS
 *     SDK for Java v2 Credentials</a>
 */
public class S3PayloadStorage implements ExternalPayloadStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3PayloadStorage.class);
    private static final String CONTENT_TYPE = "application/json";

    private final IDGenerator idGenerator;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;
    private final long expirationSec;

    public S3PayloadStorage(
            IDGenerator idGenerator,
            S3Properties properties,
            S3Client s3Client,
            S3Presigner s3Presigner) {
        this.idGenerator = idGenerator;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = properties.getBucketName();
        this.expirationSec = properties.getSignedUrlExpirationDuration().getSeconds();
    }

    /**
     * @param operation the type of {@link Operation} to be performed
     * @param payloadType the {@link PayloadType} that is being accessed
     * @return a {@link ExternalStorageLocation} object which contains the pre-signed URL and the s3
     *     object key for the json payload
     */
    @Override
    public ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path) {
        try {
            ExternalStorageLocation externalStorageLocation = new ExternalStorageLocation();

            Duration signatureDuration = Duration.ofSeconds(expirationSec);

            String objectKey;
            if (StringUtils.isNotBlank(path)) {
                objectKey = path;
            } else {
                objectKey = getObjectKey(payloadType);
            }
            externalStorageLocation.setPath(objectKey);

            String presignedUrl;

            if (operation == Operation.WRITE) {
                // For PUT operations
                PutObjectRequest putObjectRequest =
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(objectKey)
                                .contentType(CONTENT_TYPE)
                                .build();

                PutObjectPresignRequest presignRequest =
                        PutObjectPresignRequest.builder()
                                .signatureDuration(signatureDuration)
                                .putObjectRequest(putObjectRequest)
                                .build();

                presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
            } else {
                // For GET operations
                GetObjectRequest getObjectRequest =
                        GetObjectRequest.builder().bucket(bucketName).key(objectKey).build();

                GetObjectPresignRequest presignRequest =
                        GetObjectPresignRequest.builder()
                                .signatureDuration(signatureDuration)
                                .getObjectRequest(getObjectRequest)
                                .build();

                presignedUrl = s3Presigner.presignGetObject(presignRequest).url().toString();
            }

            externalStorageLocation.setUri(presignedUrl);
            return externalStorageLocation;
        } catch (SdkException e) {
            String msg =
                    String.format(
                            "Error communicating with S3 - operation:%s, payloadType: %s, path: %s",
                            operation, payloadType, path);
            LOGGER.error(msg, e);
            throw new TransientException(msg, e);
        } catch (Exception e) {
            String msg = "Error generating presigned URL";
            LOGGER.error(msg, e);
            throw new NonTransientException(msg, e);
        }
    }

    /**
     * Uploads the payload to the given s3 object key. It is expected that the caller retrieves the
     * object key using {@link #getLocation(Operation, PayloadType, String)} before making this
     * call.
     *
     * @param path the s3 key of the object to be uploaded
     * @param payload an {@link InputStream} containing the json payload which is to be uploaded
     * @param payloadSize the size of the json payload in bytes
     */
    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
        try {
            PutObjectRequest request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(path)
                            .contentType(CONTENT_TYPE)
                            .contentLength(payloadSize)
                            .build();

            s3Client.putObject(request, RequestBody.fromInputStream(payload, payloadSize));
        } catch (SdkException e) {
            String msg =
                    String.format(
                            "Error uploading to S3 - path:%s, payloadSize: %d", path, payloadSize);
            LOGGER.error(msg, e);
            throw new TransientException(msg, e);
        }
    }

    /**
     * Downloads the payload stored in the s3 object.
     *
     * @param path the S3 key of the object
     * @return an input stream containing the contents of the object Caller is expected to close the
     *     input stream.
     */
    @Override
    public InputStream download(String path) {
        try {
            GetObjectRequest request =
                    GetObjectRequest.builder().bucket(bucketName).key(path).build();

            return s3Client.getObject(request);
        } catch (SdkException e) {
            String msg = String.format("Error downloading from S3 - path:%s", path);
            LOGGER.error(msg, e);
            throw new TransientException(msg, e);
        }
    }

    private String getObjectKey(PayloadType payloadType) {
        StringBuilder stringBuilder = new StringBuilder();
        switch (payloadType) {
            case WORKFLOW_INPUT:
                stringBuilder.append("workflow/input/");
                break;
            case WORKFLOW_OUTPUT:
                stringBuilder.append("workflow/output/");
                break;
            case TASK_INPUT:
                stringBuilder.append("task/input/");
                break;
            case TASK_OUTPUT:
                stringBuilder.append("task/output/");
                break;
        }
        stringBuilder.append(idGenerator.generate()).append(".json");
        return stringBuilder.toString();
    }
}
