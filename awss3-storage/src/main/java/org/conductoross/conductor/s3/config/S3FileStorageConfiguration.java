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
package org.conductoross.conductor.s3.config;

import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.s3.storage.S3FileStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3FileStorageProperties.class)
@ConditionalOnProperty(name = "conductor.file-storage.enabled", havingValue = "true")
public class S3FileStorageConfiguration {

    @Bean(name = "fileStorageS3Client")
    @ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "s3")
    public S3Client fileStorageS3Client(S3FileStorageProperties properties) {
        return S3Client.builder().region(Region.of(properties.getRegion())).build();
    }

    @Bean(name = "fileStorageS3Presigner")
    @ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "s3")
    public S3Presigner fileStorageS3Presigner(S3FileStorageProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.getRegion())).build();
    }

    @Bean
    @ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "s3")
    public FileStorage s3FileStorage(
            S3FileStorageProperties properties,
            @Qualifier("fileStorageS3Client") S3Client s3Client,
            @Qualifier("fileStorageS3Presigner") S3Presigner s3Presigner) {
        return new S3FileStorage(properties, s3Client, s3Presigner);
    }
}
