/*
 * Copyright 2022 Netflix, Inc.
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

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
@ConditionalOnProperty(name = "conductor.external-payload-storage.type", havingValue = "s3")
public class S3Configuration {

    @Bean
    public ExternalPayloadStorage s3ExternalPayloadStorage(
            IDGenerator idGenerator, S3Properties properties, AmazonS3 s3Client) {
        return new S3PayloadStorage(idGenerator, properties, s3Client);
    }

    @ConditionalOnProperty(
            name = "conductor.external-payload-storage.s3.use_default_client",
            havingValue = "true",
            matchIfMissing = true)
    @Bean
    public AmazonS3 amazonS3(S3Properties properties) {
        return AmazonS3ClientBuilder.standard().withRegion(properties.getRegion()).build();
    }
}
