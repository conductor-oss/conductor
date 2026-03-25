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
package com.netflix.conductor.contribs.listener.composite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.contribs.listener.StatusNotifierNotificationProperties;
import com.netflix.conductor.contribs.listener.WorkflowStatusListenerFactory;
import com.netflix.conductor.contribs.listener.archive.ArchivingWorkflowListenerProperties;
import com.netflix.conductor.contribs.listener.conductorqueue.ConductorQueueStatusPublisherProperties;
import com.netflix.conductor.contribs.listener.kafka.KafkaWorkflowStatusPublisherProperties;
import com.netflix.conductor.core.listener.WorkflowStatusListener;

/**
 * Configuration for composite workflow status listener. Enables multiple workflow status listeners
 * to be active simultaneously.
 */
@Configuration
@EnableConfigurationProperties({
    CompositeWorkflowStatusListenerProperties.class,
    StatusNotifierNotificationProperties.class,
    KafkaWorkflowStatusPublisherProperties.class,
    ConductorQueueStatusPublisherProperties.class,
    ArchivingWorkflowListenerProperties.class
})
@ConditionalOnProperty(name = "conductor.workflow-status-listener.type", havingValue = "composite")
public class CompositeWorkflowStatusListenerConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CompositeWorkflowStatusListenerConfiguration.class);

    @Bean
    public WorkflowStatusListener compositeWorkflowStatusListener(
            CompositeWorkflowStatusListenerProperties properties,
            WorkflowStatusListenerFactory factory,
            Optional<KafkaWorkflowStatusPublisherProperties> kafkaProperties,
            Optional<ConductorQueueStatusPublisherProperties> queueProperties,
            Optional<StatusNotifierNotificationProperties> notifierProperties,
            Optional<ArchivingWorkflowListenerProperties> archiveProperties) {
        List<WorkflowStatusListener> listeners = new ArrayList<>();

        LOGGER.info(
                "Initializing composite workflow status listener with types: {}",
                properties.getTypes());

        for (String type : properties.getTypes()) {
            WorkflowStatusListener listener =
                    createListener(
                            type.trim(),
                            factory,
                            kafkaProperties,
                            queueProperties,
                            notifierProperties,
                            archiveProperties);
            listeners.add(listener);
            LOGGER.info("Successfully added workflow listener: {}", type);
        }

        if (listeners.isEmpty()) {
            throw new IllegalStateException(
                    "No valid workflow listeners configured for composite type. "
                            + "Check conductor.workflow-status-listener.composite.types property. "
                            + "Valid values: workflow_publisher, queue_publisher, kafka, archive");
        }

        return new CompositeWorkflowStatusListener(listeners);
    }

    /**
     * Creates a workflow status listener based on the specified type. Delegates to factory for
     * construction and validation.
     *
     * @param type The listener type (kafka, queue_publisher, workflow_publisher, archive)
     * @param factory Factory for creating listeners
     * @param kafkaProperties Optional Kafka configuration
     * @param queueProperties Optional queue publisher configuration
     * @param notifierProperties Optional webhook notifier configuration
     * @param archiveProperties Optional archival configuration
     * @return Configured workflow status listener
     * @throws IllegalArgumentException if listener type is unknown
     * @throws IllegalStateException if required properties are missing (thrown by factory)
     */
    private WorkflowStatusListener createListener(
            String type,
            WorkflowStatusListenerFactory factory,
            Optional<KafkaWorkflowStatusPublisherProperties> kafkaProperties,
            Optional<ConductorQueueStatusPublisherProperties> queueProperties,
            Optional<StatusNotifierNotificationProperties> notifierProperties,
            Optional<ArchivingWorkflowListenerProperties> archiveProperties) {
        switch (type.toLowerCase()) {
            case "kafka":
                return factory.createKafkaListener(kafkaProperties.orElse(null));

            case "queue_publisher":
                return factory.createQueuePublisherListener(queueProperties.orElse(null));

            case "workflow_publisher":
                return factory.createWorkflowPublisherListener(
                        notifierProperties.orElse(null),
                        notifierProperties
                                .map(
                                        StatusNotifierNotificationProperties
                                                ::getSubscribedWorkflowStatuses)
                                .orElse(null));

            case "archive":
                return factory.createArchiveListener(archiveProperties.orElse(null));

            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Unknown workflow listener type: '%s'. "
                                        + "Valid values: workflow_publisher, queue_publisher, kafka, archive",
                                type));
        }
    }
}
