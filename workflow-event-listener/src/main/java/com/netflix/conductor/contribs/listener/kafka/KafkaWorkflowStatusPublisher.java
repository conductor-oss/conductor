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
package com.netflix.conductor.contribs.listener.kafka;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.producer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.common.run.WorkflowSummaryExtended;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Kafka-based publisher for workflow status events. */
public class KafkaWorkflowStatusPublisher implements WorkflowStatusListener, DisposableBean {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(KafkaWorkflowStatusPublisher.class);
    private final KafkaProducer<String, String> producer;
    private final Map<String, String> eventTopics;
    private final String defaultTopic;
    private final KafkaWorkflowStatusPublisherProperties properties;
    private final ObjectMapper objectMapper;

    public KafkaWorkflowStatusPublisher(
            KafkaWorkflowStatusPublisherProperties properties, ObjectMapper objectMapper) {
        this.eventTopics = properties.getEventTopics();
        this.defaultTopic = properties.getDefaultTopic();
        this.properties = properties;
        this.objectMapper = objectMapper;

        // Configure Kafka Producer
        Map<String, Object> producerConfig = properties.toProducerConfig();
        this.producer = new KafkaProducer<>(producerConfig);
    }

    @Override
    public void destroy() {
        if (producer != null) {
            try {
                producer.close(Duration.ofSeconds(10)); // Allow graceful shutdown
                LOGGER.info("Kafka producer shut down gracefully.");
            } catch (Exception e) {
                LOGGER.error("Error shutting down Kafka producer", e);
            }
        }
    }

    @Override
    public void onWorkflowStarted(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.STARTED, workflow);
    }

    @Override
    public void onWorkflowRestarted(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.RESTARTED, workflow);
    }

    @Override
    public void onWorkflowRerun(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.RERAN, workflow);
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.COMPLETED, workflow);
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.TERMINATED, workflow);
    }

    @Override
    public void onWorkflowFinalized(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.FINALIZED, workflow);
    }

    @Override
    public void onWorkflowPaused(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.PAUSED, workflow);
    }

    @Override
    public void onWorkflowResumed(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.RESUMED, workflow);
    }

    @Override
    public void onWorkflowRetried(WorkflowModel workflow) {
        publishEvent(WorkflowEventType.RETRIED, workflow);
    }

    private void publishEvent(WorkflowEventType eventType, WorkflowModel workflow) {
        try {
            // Determine the correct topic
            String topic = eventTopics.getOrDefault(eventType.toString(), defaultTopic);
            LOGGER.debug("Publish event {} to topic {}", eventType.toString(), topic);

            // Convert workflow to summary
            WorkflowSummary workflowSummary = new WorkflowSummaryExtended(workflow.toWorkflow());

            // Construct JSON message
            Map<String, Object> message =
                    Map.of(
                            "workflowName", workflow.getWorkflowName(),
                            "eventType", eventType.toString(),
                            "payload", workflowSummary);

            String jsonMessage = objectMapper.writeValueAsString(message);

            // Create Kafka record
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, workflow.getWorkflowId(), jsonMessage);

            // Send message asynchronously
            producer.send(
                    record,
                    (metadata, exception) -> {
                        if (exception != null) {
                            LOGGER.error(
                                    "Failed to publish workflow event: {}", jsonMessage, exception);
                        } else {
                            LOGGER.debug(
                                    "Published event: {}, Topic: {}, Partition: {}, Offset: {}",
                                    eventType,
                                    metadata.topic(),
                                    metadata.partition(),
                                    metadata.offset());
                        }
                    });

        } catch (JsonProcessingException e) {
            LOGGER.error(
                    "Error serializing workflow event for {}: {}", eventType, e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error publishing event {}: {}", eventType, e.getMessage(), e);
        }
    }
}
