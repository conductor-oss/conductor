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

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conductor.workflow-status-listener.kafka")
public class KafkaWorkflowStatusPublisherProperties {

    private Map<String, Object> producer = new HashMap<>();

    /** Default Kafka topic where all workflow status events are published. */
    private String defaultTopic = "workflow-status-events";

    /**
     * A map of event types to Kafka topics. If an event type has a specific topic, it will be
     * published there instead of the default.
     */
    private Map<String, String> eventTopics = new HashMap<>();

    public Map<String, Object> getProducer() {
        return producer;
    }

    public void setProducer(Map<String, Object> producer) {
        this.producer = producer;
    }

    public String getDefaultTopic() {
        return defaultTopic;
    }

    public void setDefaultTopic(String defaultTopic) {
        this.defaultTopic = defaultTopic;
    }

    public Map<String, String> getEventTopics() {
        return eventTopics;
    }

    public void setEventTopics(Map<String, String> eventTopics) {
        this.eventTopics = eventTopics;
    }

    /**
     * Generates configuration properties for Kafka producers. Maps against `ProducerConfig` keys.
     */
    public Map<String, Object> toProducerConfig() {
        return mapProperties(ProducerConfig.configNames(), producer);
    }

    /**
     * Filters and maps properties based on the allowed keys for Kafka producer configuration.
     *
     * @param allowedKeys The allowed Kafka ProducerConfig keys.
     * @param inputProperties The user-specified properties from application config.
     * @return A filtered map containing only valid properties.
     */
    private Map<String, Object> mapProperties(
            Iterable<String> allowedKeys, Map<String, Object> inputProperties) {
        Map<String, Object> config = new HashMap<>();
        for (String key : allowedKeys) {
            if (inputProperties.containsKey(key)) {
                config.put(key, inputProperties.get(key));
            }
        }

        // Ensure bootstrapServers is always set
        setDefaultIfNullOrEmpty(config, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:29092");

        // Set required default serializers
        setDefaultIfNullOrEmpty(
                config,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        setDefaultIfNullOrEmpty(
                config,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");

        // Set default client ID
        setDefaultIfNullOrEmpty(
                config, ProducerConfig.CLIENT_ID_CONFIG, "workflow-status-producer");

        return config;
    }

    /** Sets a default value if the key is missing or empty. */
    private void setDefaultIfNullOrEmpty(
            Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        if (value == null || (value instanceof String && ((String) value).isBlank())) {
            config.put(key, defaultValue);
        }
    }
}
