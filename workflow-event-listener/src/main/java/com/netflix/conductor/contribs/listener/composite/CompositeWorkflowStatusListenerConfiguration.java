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

import com.netflix.conductor.contribs.listener.RestClientManager;
import com.netflix.conductor.contribs.listener.StatusNotifierNotificationProperties;
import com.netflix.conductor.contribs.listener.archive.ArchivingWithTTLWorkflowStatusListener;
import com.netflix.conductor.contribs.listener.archive.ArchivingWorkflowListenerProperties;
import com.netflix.conductor.contribs.listener.archive.ArchivingWorkflowStatusListener;
import com.netflix.conductor.contribs.listener.archive.ArchivingWorkflowToS3;
import com.netflix.conductor.contribs.listener.conductorqueue.ConductorQueueStatusPublisher;
import com.netflix.conductor.contribs.listener.conductorqueue.ConductorQueueStatusPublisherProperties;
import com.netflix.conductor.contribs.listener.kafka.KafkaWorkflowStatusPublisher;
import com.netflix.conductor.contribs.listener.kafka.KafkaWorkflowStatusPublisherProperties;
import com.netflix.conductor.contribs.listener.statuschange.StatusChangePublisher;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.dao.QueueDAO;

import com.fasterxml.jackson.databind.ObjectMapper;

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
    public RestClientManager getRestClientManager(StatusNotifierNotificationProperties config) {
        return new RestClientManager(config);
    }

    @Bean
    public WorkflowStatusListener compositeWorkflowStatusListener(
            CompositeWorkflowStatusListenerProperties properties,
            ObjectMapper objectMapper,
            QueueDAO queueDAO,
            ExecutionDAOFacade executionDAOFacade,
            Optional<RestClientManager> restClientManager,
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
                            objectMapper,
                            queueDAO,
                            executionDAOFacade,
                            restClientManager,
                            kafkaProperties,
                            queueProperties,
                            notifierProperties,
                            archiveProperties);
            if (listener != null) {
                listeners.add(listener);
                LOGGER.info("Successfully added workflow listener: {}", type);
            }
        }

        if (listeners.isEmpty()) {
            throw new IllegalStateException(
                    "No valid workflow listeners configured for composite type. "
                            + "Check conductor.workflow-status-listener.composite.types property. "
                            + "Valid values: workflow_publisher, queue_publisher, kafka, archive");
        }

        return new CompositeWorkflowStatusListener(listeners);
    }

    private WorkflowStatusListener createListener(
            String type,
            ObjectMapper objectMapper,
            QueueDAO queueDAO,
            ExecutionDAOFacade executionDAOFacade,
            Optional<RestClientManager> restClientManager,
            Optional<KafkaWorkflowStatusPublisherProperties> kafkaProperties,
            Optional<ConductorQueueStatusPublisherProperties> queueProperties,
            Optional<StatusNotifierNotificationProperties> notifierProperties,
            Optional<ArchivingWorkflowListenerProperties> archiveProperties) {
        switch (type.toLowerCase()) {
            case "kafka":
                return kafkaProperties
                        .map(
                                props -> {
                                    LOGGER.debug("Creating Kafka workflow status publisher");
                                    return new KafkaWorkflowStatusPublisher(props, objectMapper);
                                })
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Kafka listener requested but kafka properties not configured. "
                                                        + "Please configure conductor.workflow-status-listener.kafka.* properties"));

            case "queue_publisher":
                return queueProperties
                        .map(
                                props -> {
                                    LOGGER.debug("Creating Conductor queue status publisher");
                                    return new ConductorQueueStatusPublisher(
                                            queueDAO, objectMapper, props);
                                })
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Queue publisher requested but queue properties not configured. "
                                                        + "Please configure conductor.workflow-status-listener.queue-publisher.* properties"));

            case "workflow_publisher":
                return restClientManager
                        .map(
                                rcm -> {
                                    LOGGER.debug(
                                            "Creating workflow status change publisher (webhook)");
                                    return new StatusChangePublisher(rcm, executionDAOFacade);
                                })
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Workflow publisher requested but notification properties not configured. "
                                                        + "Please configure conductor.status-notifier.notification.* properties"));

            case "archive":
                if (!archiveProperties.isPresent()) {
                    throw new IllegalStateException(
                            "Archive listener requested but archive properties not configured. "
                                    + "Please configure conductor.workflow-status-listener.archival.* properties");
                }

                ArchivingWorkflowListenerProperties props = archiveProperties.get();
                LOGGER.debug(
                        "Creating archiving workflow listener (TTL: {})", props.getTtlDuration());

                if (props.getTtlDuration().getSeconds() > 0) {
                    return new ArchivingWithTTLWorkflowStatusListener(executionDAOFacade, props);
                } else if (props.getWorkflowArchivalType()
                        == ArchivingWorkflowListenerProperties.ArchivalType.S3) {
                    return new ArchivingWorkflowToS3(executionDAOFacade, props);
                } else {
                    return new ArchivingWorkflowStatusListener(executionDAOFacade);
                }

            default:
                LOGGER.warn(
                        "Unknown workflow listener type: '{}'. Skipping. "
                                + "Valid values: workflow_publisher, queue_publisher, kafka, archive",
                        type);
                return null;
        }
    }
}