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
package com.netflix.conductor.s3.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.s3.storage.S3PayloadStorage;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
@ConditionalOnProperty(name = "conductor.external-payload-storage.type", havingValue = "s3")
public class S3Configuration {

    @Bean
    public ExternalPayloadStorage s3ExternalPayloadStorage(
            IDGenerator idGenerator,
            S3Properties properties,
            S3Client s3Client,
            S3Presigner s3Presigner) {
        return new S3PayloadStorage(idGenerator, properties, s3Client, s3Presigner);
    }

    @ConditionalOnProperty(
            name = "conductor.external-payload-storage.s3.use_default_client",
            havingValue = "true",
            matchIfMissing = true)
    @Bean
    public S3Client s3Client(S3Properties properties) {
        return S3Client.builder().region(Region.of(properties.getRegion())).build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties properties) {
        return S3Presigner.builder().region(Region.of(properties.getRegion())).build();
    }
}
