/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.test.config;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Test configuration that overrides production SQS beans to point to LocalStack. This configuration
 * is only active when SQS event queues are enabled and allows tests to run against LocalStack
 * instead of real AWS SQS.
 */
@TestConfiguration
@ConditionalOnProperty(name = "conductor.event-queues.sqs.enabled", havingValue = "true")
public class LocalStackSQSConfiguration {

    private static String localStackEndpoint;

    /**
     * Sets the LocalStack endpoint URL for SQS client configuration. This method should be called
     * from test setup before Spring context initialization.
     */
    public static void setLocalStackEndpoint(String endpoint) {
        localStackEndpoint = endpoint;
    }

    /**
     * Creates an SqsClient configured for LocalStack. This bean overrides the production SqsClient
     * bean during testing.
     */
    @Bean
    @Primary
    public SqsClient localStackSqsClient() {
        var builder =
                SqsClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test", "test")));

        // Configure LocalStack endpoint if available
        if (localStackEndpoint != null) {
            builder.endpointOverride(URI.create(localStackEndpoint));
        }

        return builder.build();
    }
}
