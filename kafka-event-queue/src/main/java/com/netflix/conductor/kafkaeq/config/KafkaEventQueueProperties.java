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
package com.netflix.conductor.kafkaeq.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("conductor.event-queues.kafka")
@Validated
public class KafkaEventQueueProperties {

    /** Kafka bootstrap servers (comma-separated). */
    @NotBlank(message = "Bootstrap servers must not be blank")
    private String bootstrapServers = "kafka:29092";

    /** Dead Letter Queue (DLQ) topic for failed messages. */
    private String dlqTopic = "conductor-dlq";

    /** Prefix for dynamically created Kafka topics, if applicable. */
    private String listenerQueuePrefix = "";

    /** The polling interval for Kafka (in milliseconds). */
    private Duration pollTimeDuration = Duration.ofMillis(100);

    /** Additional properties for consumers, producers, and admin clients. */
    private Map<String, Object> consumer = new HashMap<>();

    private Map<String, Object> producer = new HashMap<>();
    private Map<String, Object> admin = new HashMap<>();

    // Getters and setters
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public void setDlqTopic(String dlqTopic) {
        this.dlqTopic = dlqTopic;
    }

    public String getListenerQueuePrefix() {
        return listenerQueuePrefix;
    }

    public void setListenerQueuePrefix(String listenerQueuePrefix) {
        this.listenerQueuePrefix = listenerQueuePrefix;
    }

    public Duration getPollTimeDuration() {
        return pollTimeDuration;
    }

    public void setPollTimeDuration(Duration pollTimeDuration) {
        this.pollTimeDuration = pollTimeDuration;
    }

    public Map<String, Object> getConsumer() {
        return consumer;
    }

    public void setConsumer(Map<String, Object> consumer) {
        this.consumer = consumer;
    }

    public Map<String, Object> getProducer() {
        return producer;
    }

    public void setProducer(Map<String, Object> producer) {
        this.producer = producer;
    }

    public Map<String, Object> getAdmin() {
        return admin;
    }

    public void setAdmin(Map<String, Object> admin) {
        this.admin = admin;
    }

    /**
     * Generates configuration properties for Kafka consumers. Maps against `ConsumerConfig` keys.
     */
    public Map<String, Object> toConsumerConfig() {
        Map<String, Object> config = mapProperties(ConsumerConfig.configNames(), consumer);
        // Ensure key.deserializer and value.deserializer are always set
        setDefaultIfNullOrEmpty(
                config,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        setDefaultIfNullOrEmpty(
                config,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        setDefaultIfNullOrEmpty(config, ConsumerConfig.GROUP_ID_CONFIG, "conductor-group");
        setDefaultIfNullOrEmpty(config, ConsumerConfig.CLIENT_ID_CONFIG, "consumer-client");
        return config;
    }

    /**
     * Generates configuration properties for Kafka producers. Maps against `ProducerConfig` keys.
     */
    public Map<String, Object> toProducerConfig() {
        Map<String, Object> config = mapProperties(ProducerConfig.configNames(), producer);
        setDefaultIfNullOrEmpty(
                config,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        setDefaultIfNullOrEmpty(
                config,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");

        setDefaultIfNullOrEmpty(config, ProducerConfig.CLIENT_ID_CONFIG, "admin-client");
        return config;
    }

    /**
     * Generates configuration properties for Kafka AdminClient. Maps against `AdminClientConfig`
     * keys.
     */
    public Map<String, Object> toAdminConfig() {
        Map<String, Object> config = mapProperties(AdminClientConfig.configNames(), admin);
        setDefaultIfNullOrEmpty(config, ConsumerConfig.CLIENT_ID_CONFIG, "admin-client");
        return config;
    }

    /**
     * Filters and maps properties based on the allowed keys for a specific Kafka client
     * configuration.
     *
     * @param allowedKeys The keys allowed for the specific Kafka client configuration.
     * @param inputProperties The user-specified properties to filter.
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

        setDefaultIfNullOrEmpty(
                config, AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers); // Ensure
        // bootstrapServers
        // is
        // always added
        return config;
    }

    private void setDefaultIfNullOrEmpty(
            Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        if (value == null || (value instanceof String && ((String) value).isBlank())) {
            config.put(key, defaultValue);
        }
    }
}
