/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.contribs.storage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workflow", name = "external.payload.storage", havingValue = "S3")
public class S3Properties {

    @Value("${workflow.external.payload.storage.s3.bucket:conductor_payloads}")
    private String bucketName;

    @Value("${workflow.external.payload.storage.s3.signedurlexpirationseconds:5}")
    private int expirationSeconds;

    @Value("${workflow.external.payload.storage.s3.region:us-east-1}")
    private String region;

    public String getBucketName() {
        return bucketName;
    }

    public int getExpirationSeconds() {
        return expirationSeconds;
    }

    public String getRegion() {
        return region;
    }
}
