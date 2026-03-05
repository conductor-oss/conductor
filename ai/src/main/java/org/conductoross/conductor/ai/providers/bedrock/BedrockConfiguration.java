/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.providers.bedrock;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.ModelConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@Data
@Component
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "conductor.ai.bedrock")
public class BedrockConfiguration implements ModelConfiguration<Bedrock> {

    private AwsCredentialsProvider awsCredentialsProvider;
    private String accessKey;
    private String secretKey;
    private String bearerToken;
    private String region = "us-east-1";

    @Override
    public Bedrock get() {
        return new Bedrock(this);
    }

    public AwsCredentialsProvider getAwsCredentialsProvider() {
        // If bearer token is configured, return null (bearer auth handled separately)
        if (isBearerTokenConfigured()) {
            // Use anonymous credentials as placeholder - bearer token will be used via HTTP
            // client
            return AnonymousCredentialsProvider.create();
        }
        return awsCredentialsProvider == null
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(getAccessKey(), getSecretKey()))
                : awsCredentialsProvider;
    }

    /** Check if bearer token authentication is configured. */
    public boolean isBearerTokenConfigured() {
        return StringUtils.isNotBlank(bearerToken);
    }
}
