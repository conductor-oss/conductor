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
package org.conductoross.conductor.gcs.config;

import java.io.FileInputStream;
import java.io.IOException;

import org.conductoross.conductor.core.exception.FileStorageException;
import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.gcs.storage.GcsFileStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GcsFileStorageProperties.class)
@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
public class GcsFileStorageConfiguration {

    @Bean(name = "fileStorageGcsStorage")
    @ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "gcs")
    public Storage fileStorageGcsStorage(GcsFileStorageProperties properties) {
        StorageOptions.Builder builder =
                StorageOptions.newBuilder().setProjectId(properties.getProjectId());
        GoogleCredentials credentials = getCredentials(properties);
        if (credentials != null) {
            builder.setCredentials(credentials);
        }
        return builder.build().getService();
    }

    @Bean
    @ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "gcs")
    public FileStorage gcsFileStorage(
            GcsFileStorageProperties properties,
            @Qualifier("fileStorageGcsStorage") Storage storage) {
        return new GcsFileStorage(properties, storage);
    }

    private static GoogleCredentials getCredentials(GcsFileStorageProperties properties) {
        if (properties.getCredentialsFile() == null || properties.getCredentialsFile().isBlank()) {
            return null;
        }
        try (FileInputStream is = new FileInputStream(properties.getCredentialsFile())) {
            return GoogleCredentials.fromStream(is);
        } catch (IOException e) {
            throw new FileStorageException(
                    "Failed to load GCS credentials from: " + properties.getCredentialsFile(), e);
        }
    }
}
