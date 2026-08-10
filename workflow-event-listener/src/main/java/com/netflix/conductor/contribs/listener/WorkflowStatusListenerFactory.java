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
package com.netflix.conductor.contribs.listener;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
 * Factory for creating workflow status listener instances. Provides centralized listener creation
 * and validation logic to avoid duplication across different configuration classes.
 *
 * <p>This factory encapsulates the construction logic and validation rules for each listener type,
 * ensuring consistency whether listeners are used individually or as part of a composite.
 */
@Component
public class WorkflowStatusListenerFactory {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WorkflowStatusListenerFactory.class);

    private final ObjectMapper objectMapper;
    private final QueueDAO queueDAO;
    private final ExecutionDAOFacade executionDAOFacade;

    public WorkflowStatusListenerFactory(
            ObjectMapper objectMapper, QueueDAO queueDAO, ExecutionDAOFacade executionDAOFacade) {
        this.objectMapper = objectMapper;
        this.queueDAO = queueDAO;
        this.executionDAOFacade = executionDAOFacade;
    }

    /**
     * Creates a Kafka workflow status publisher.
     *
     * @param properties Kafka configuration properties
     * @return configured Kafka workflow status publisher
     * @throws IllegalStateException if bootstrap.servers is not configured
     */
    public WorkflowStatusListener createKafkaListener(
            KafkaWorkflowStatusPublisherProperties properties) {
        validateKafkaProperties(properties);
        LOGGER.debug(
                "Creating Kafka workflow status publisher with bootstrap servers: {}",
                properties.getProducer().get("bootstrap.servers"));
        return new KafkaWorkflowStatusPublisher(properties, objectMapper);
    }

    /**
     * Creates a Conductor queue status publisher.
     *
     * @param properties Queue publisher configuration properties
     * @return configured queue status publisher
     * @throws IllegalStateException if neither success nor failure queue is configured
     */
    public WorkflowStatusListener createQueuePublisherListener(
            ConductorQueueStatusPublisherProperties properties) {
        validateQueuePublisherProperties(properties);
        LOGGER.debug(
                "Creating Conductor queue status publisher with success queue: {}, failure queue: {}",
                properties.getSuccessQueue(),
                properties.getFailureQueue());
        return new ConductorQueueStatusPublisher(queueDAO, objectMapper, properties);
    }

    /**
     * Creates a workflow status change publisher (webhook).
     *
     * @param properties Status notifier configuration properties
     * @param subscribedWorkflowStatuses List of workflow statuses to subscribe to
     * @return configured status change publisher
     * @throws IllegalStateException if notification URL is not configured
     */
    public WorkflowStatusListener createWorkflowPublisherListener(
            StatusNotifierNotificationProperties properties,
            List<String> subscribedWorkflowStatuses) {
        validateWorkflowPublisherProperties(properties);
        LOGGER.debug(
                "Creating workflow status change publisher (webhook) with URL: {}, subscribed statuses: {}",
                properties.getUrl(),
                subscribedWorkflowStatuses);
        RestClientManager restClientManager = new RestClientManager(properties);
        return new StatusChangePublisher(
                restClientManager, executionDAOFacade, subscribedWorkflowStatuses);
    }

    /**
     * Creates an archiving workflow listener. Selects appropriate implementation based on TTL and
     * archival type configuration.
     *
     * @param properties Archival configuration properties
     * @return configured archiving workflow listener
     * @throws IllegalStateException if archival properties are invalid
     */
    public WorkflowStatusListener createArchiveListener(
            ArchivingWorkflowListenerProperties properties) {
        validateArchiveProperties(properties);

        if (properties.getTtlDuration().getSeconds() > 0) {
            LOGGER.debug(
                    "Creating archiving workflow listener with TTL: {} seconds",
                    properties.getTtlDuration().getSeconds());
            return new ArchivingWithTTLWorkflowStatusListener(executionDAOFacade, properties);
        } else if (properties.getWorkflowArchivalType()
                == ArchivingWorkflowListenerProperties.ArchivalType.S3) {
            LOGGER.debug("Creating S3 archiving workflow listener");
            return new ArchivingWorkflowToS3(executionDAOFacade, properties);
        } else {
            LOGGER.debug("Creating basic archiving workflow listener");
            return new ArchivingWorkflowStatusListener(executionDAOFacade);
        }
    }

    private void validateKafkaProperties(KafkaWorkflowStatusPublisherProperties properties) {
        if (properties == null) {
            throw new IllegalStateException(
                    "Kafka listener is enabled but properties are not configured. "
                            + "Please configure conductor.workflow-status-listener.kafka.* properties");
        }
        if (properties.getProducer() == null || properties.getProducer().isEmpty()) {
            throw new IllegalStateException(
                    "Kafka listener is enabled but producer properties are not configured. "
                            + "Please configure conductor.workflow-status-listener.kafka.producer[...] properties");
        }
        if (properties.getProducer().get("bootstrap.servers") == null
                || StringUtils.isBlank(
                        String.valueOf(properties.getProducer().get("bootstrap.servers")))) {
            throw new IllegalStateException(
                    "Kafka listener is enabled but 'bootstrap.servers' is not configured. "
                            + "Please set conductor.workflow-status-listener.kafka.producer[bootstrap.servers]");
        }
    }

    private void validateQueuePublisherProperties(
            ConductorQueueStatusPublisherProperties properties) {
        if (properties == null) {
            throw new IllegalStateException(
                    "Queue publisher is enabled but properties are not configured. "
                            + "Please configure conductor.workflow-status-listener.queue-publisher.* properties");
        }
        if (StringUtils.isBlank(properties.getSuccessQueue())
                && StringUtils.isBlank(properties.getFailureQueue())) {
            throw new IllegalStateException(
                    "Queue publisher is enabled but neither success nor failure queue is configured. "
                            + "Please set at least one of: conductor.workflow-status-listener.queue-publisher.successQueue "
                            + "or conductor.workflow-status-listener.queue-publisher.failureQueue");
        }
    }

    private void validateWorkflowPublisherProperties(
            StatusNotifierNotificationProperties properties) {
        if (properties == null) {
            throw new IllegalStateException(
                    "Workflow publisher is enabled but properties are not configured. "
                            + "Please configure conductor.status-notifier.notification.* properties");
        }
        if (StringUtils.isBlank(properties.getUrl())) {
            throw new IllegalStateException(
                    "Workflow publisher is enabled but notification URL is not configured. "
                            + "Please set conductor.status-notifier.notification.url");
        }
    }

    private void validateArchiveProperties(ArchivingWorkflowListenerProperties properties) {
        if (properties == null) {
            throw new IllegalStateException(
                    "Archive listener is enabled but properties are not configured. "
                            + "Please configure conductor.workflow-status-listener.archival.* properties");
        }
    }
}
