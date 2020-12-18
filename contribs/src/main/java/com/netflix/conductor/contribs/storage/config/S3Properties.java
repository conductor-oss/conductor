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

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.external-payload-storage.s3")
public class S3Properties {

    /**
     * The s3 bucket name where the payloads will be stored
     */
    private String bucketName = "conductor_payloads";

    /**
     * The time (in seconds) for which the signed url will be valid
     */
    private int signedUrlExpirationSeconds = 5;

    /**
     * The AWS region of the s3 bucket
     */
    private String region = "us-east-1";

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public int getSignedUrlExpirationSeconds() {
        return signedUrlExpirationSeconds;
    }

    public void setSignedUrlExpirationSeconds(int signedUrlExpirationSeconds) {
        this.signedUrlExpirationSeconds = signedUrlExpirationSeconds;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
