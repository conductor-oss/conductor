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
package org.conductoross.conductor.azureblob.storage;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.core.storage.StorageFileInfo;
import org.conductoross.conductor.model.file.StorageType;

import com.netflix.conductor.core.utils.IDGenerator;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.BlockBlobClient;

public class AzureBlobFileStorage implements FileStorage {

    private final BlobContainerClient containerClient;
    private final IDGenerator idGenerator;

    public AzureBlobFileStorage(IDGenerator idGenerator, BlobContainerClient blobContainerClient) {
        this.idGenerator = idGenerator;
        this.containerClient = blobContainerClient;
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.AZURE_BLOB;
    }

    @Override
    public String generateUploadUrl(String storagePath, Duration expiration) {
        return generateSasUrl(storagePath, expiration, true);
    }

    @Override
    public String generateDownloadUrl(String storagePath, Duration expiration) {
        return generateSasUrl(storagePath, expiration, false);
    }

    @Override
    public StorageFileInfo getStorageFileInfo(String storagePath) {
        try {
            BlobProperties props = containerClient.getBlobClient(storagePath).getProperties();
            StorageFileInfo info = new StorageFileInfo();
            info.setExists(true);
            byte[] md5 = props.getContentMd5();
            info.setContentHash(
                    md5 != null ? java.util.Base64.getEncoder().encodeToString(md5) : null);
            info.setContentSize(props.getBlobSize());
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String initiateMultipartUpload(String storagePath) {
        // Azure uses block IDs — return SAS URL as the "upload ID"
        return idGenerator.generate();
    }

    @Override
    public String generatePartUploadUrl(
            String storagePath, String uploadId, int partNumber, Duration expiration) {
        return generateSasUrl(storagePath, expiration, true);
    }

    @Override
    public void completeMultipartUpload(
            String storagePath, String uploadId, List<String> partETags) {
        BlockBlobClient blockBlobClient =
                containerClient.getBlobClient(storagePath).getBlockBlobClient();
        blockBlobClient.commitBlockList(partETags);
    }

    private String generateSasUrl(String storagePath, Duration expiration, boolean write) {
        BlobContainerSasPermission permission = new BlobContainerSasPermission();
        if (write) {
            permission.setWritePermission(true).setCreatePermission(true);
        } else {
            permission.setReadPermission(true);
        }
        BlobServiceSasSignatureValues values =
                new BlobServiceSasSignatureValues(
                        OffsetDateTime.now().plus(expiration), permission);
        var blobClient = containerClient.getBlobClient(storagePath);
        return blobClient.getBlobUrl() + "?" + blobClient.generateSas(values);
    }
}
