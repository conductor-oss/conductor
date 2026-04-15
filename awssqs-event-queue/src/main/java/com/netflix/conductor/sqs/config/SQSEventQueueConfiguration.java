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
package com.netflix.conductor.sqs.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.model.TaskModel.Status;
import com.netflix.conductor.sqs.eventqueue.SQSObservableQueue.Builder;

import rx.Scheduler;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration
@EnableConfigurationProperties(SQSEventQueueProperties.class)
@ConditionalOnProperty(name = "conductor.event-queues.sqs.enabled", havingValue = "true")
public class SQSEventQueueConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SQSEventQueueConfiguration.class);
    @Autowired private SQSEventQueueProperties sqsProperties;

    @Bean
    AwsCredentialsProvider createAWSCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    @ConditionalOnMissingBean
    @Bean
    public SqsClient getSQSClient(AwsCredentialsProvider credentialsProvider) {
        SqsClientBuilder builder = SqsClient.builder().credentialsProvider(credentialsProvider);

        // Set region - try to get from environment or properties
        String region = System.getenv("AWS_REGION");
        if (region != null && !region.isEmpty()) {
            builder.region(Region.of(region));
        } else {
            // Fallback to default region if not specified
            builder.region(Region.US_EAST_1);
        }

        if (!sqsProperties.getEndpoint().isEmpty()) {
            LOGGER.info("Setting custom SQS endpoint to {}", sqsProperties.getEndpoint());
            builder.endpointOverride(URI.create(sqsProperties.getEndpoint()));
        }

        return builder.build();
    }

    @Bean
    public EventQueueProvider sqsEventQueueProvider(
            SqsClient sqsClient, SQSEventQueueProperties properties, Scheduler scheduler) {
        return new SQSEventQueueProvider(sqsClient, properties, scheduler);
    }

    @ConditionalOnProperty(
            name = "conductor.default-event-queue.type",
            havingValue = "sqs",
            matchIfMissing = true)
    @Bean
    public Map<Status, ObservableQueue> getQueues(
            ConductorProperties conductorProperties,
            SQSEventQueueProperties properties,
            SqsClient sqsClient) {
        String stack = "";
        if (conductorProperties.getStack() != null && conductorProperties.getStack().length() > 0) {
            stack = conductorProperties.getStack() + "_";
        }
        Status[] statuses = new Status[] {Status.COMPLETED, Status.FAILED};
        Map<Status, ObservableQueue> queues = new HashMap<>();
        for (Status status : statuses) {
            String queuePrefix =
                    StringUtils.isBlank(properties.getListenerQueuePrefix())
                            ? conductorProperties.getAppId() + "_sqs_notify_" + stack
                            : properties.getListenerQueuePrefix();

            String queueName = queuePrefix + status.name();

            Builder builder = new Builder().withClient(sqsClient).withQueueName(queueName);

            String auth = properties.getAuthorizedAccounts();
            String[] accounts = auth.split(",");
            for (String accountToAuthorize : accounts) {
                accountToAuthorize = accountToAuthorize.trim();
                if (accountToAuthorize.length() > 0) {
                    builder.addAccountToAuthorize(accountToAuthorize.trim());
                }
            }
            ObservableQueue queue = builder.build();
            queues.put(status, queue);
        }

        return queues;
    }
}
