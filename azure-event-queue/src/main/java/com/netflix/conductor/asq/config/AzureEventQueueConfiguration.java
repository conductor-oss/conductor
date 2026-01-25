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
package com.netflix.conductor.asq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.EventQueueProvider;

import com.azure.identity.WorkloadIdentityCredential;
import com.azure.identity.WorkloadIdentityCredentialBuilder;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.queue.*;
import com.azure.storage.queue.QueueServiceClient;
import rx.Scheduler;

@Configuration
@EnableConfigurationProperties(AzureEventQueueProperties.class)
@ConditionalOnProperty(name = "conductor.event-queues.azure.enabled", havingValue = "true")
public class AzureEventQueueConfiguration {

    @Autowired private AzureEventQueueProperties azureEventQueueProperties;

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureEventQueueProperties.class);

   @Bean
   @ConditionalOnProperty(name = "conductor.event-queues.azure.identity", havingValue = "defaultAzureCredential", matchIfMissing = true)
   public QueueServiceClient getDefaultAzureClient() throws Exception {
                try {
                    DefaultAzureCredential credential = new
     DefaultAzureCredentialBuilder().build();

                    String endpoint = azureEventQueueProperties.getEndpoint();
                    QueueServiceClient serviceClient =
                            new QueueServiceClientBuilder()
                                    .endpoint(endpoint)
                                    .credential(credential)
                                    .buildClient();
                    return serviceClient;
                } catch (Exception e) {
                    throw new Exception("Unable to initialize azure queue client " +
     e.getMessage());
                }
            }

    @Bean
    @ConditionalOnProperty(name = "conductor.event-queues.azure.identity", havingValue = "workloadIdentityCredential")
    public QueueServiceClient getWorkloadIdentityAzureClient(ConductorProperties conductorProperties)
            throws Exception {
        try {
            WorkloadIdentityCredential credential = new WorkloadIdentityCredentialBuilder().build();

            String endpoint = "";
            if (conductorProperties != null
                    && conductorProperties.getAll().get("conductor.storage-account-name") != null) {
                String storageAccount =
                        (String) conductorProperties.getAll().get("conductor.storage-account-name");
                endpoint = String.format("https://%s.queue.core.windows.net", storageAccount);
            }
            QueueServiceClient serviceClient =
                    new QueueServiceClientBuilder()
                            .endpoint(endpoint)
                            .credential(credential)
                            .buildClient();
            return serviceClient;
        } catch (Exception e) {
            throw new Exception("Unable to initialize azure queue client " + e.getMessage());
        }
    }

    @Bean
    public EventQueueProvider AzureEventQueueProvider(
            QueueServiceClient azureQueueServiceClient,
            AzureEventQueueProperties properties,
            Scheduler scheduler) {
        return new AzureEventQueueProvider(azureQueueServiceClient, properties, scheduler);
    }
}
