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
package org.conductoross.conductor.azureblob.config;

import org.conductoross.conductor.azureblob.storage.AzureBlobFileStorage;
import org.conductoross.conductor.core.storage.FileStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.utils.IDGenerator;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AzureBlobFileStorageProperties.class)
@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
public class AzureBlobFileStorageConfiguration {

    @Bean(name = "fileStorageBlobServiceClient")
    @ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "azure-blob")
    public BlobServiceClient fileStorageBlobServiceClient(
            AzureBlobFileStorageProperties properties) {
        return new BlobServiceClientBuilder()
                .connectionString(properties.getConnectionString())
                .buildClient();
    }

    @Bean
    @ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "azure-blob")
    public FileStorage azureBlobFileStorage(
            IDGenerator idGenerator,
            AzureBlobFileStorageProperties properties,
            @Qualifier("fileStorageBlobServiceClient") BlobServiceClient blobServiceClient) {
        return new AzureBlobFileStorage(
                idGenerator,
                blobServiceClient.getBlobContainerClient(properties.getContainerName()));
    }
}
