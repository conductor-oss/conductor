/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.contribs.queue.amqp;

import com.google.common.collect.Maps;
import com.netflix.conductor.contribs.queue.amqp.config.AMQPEventQueueProperties;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPConstants;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPSettings;
import com.netflix.conductor.core.LifecycleAwareComponent;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.metrics.Monitors;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Ritu Parathody
 */
public class AMQPObservableQueue implements ObservableQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(AMQPObservableQueue.class);

    private final AMQPSettings settings;
    private final int batchSize;
    private final boolean useExchange;
    private int pollTimeInMS;

    private final ConnectionFactory factory;
    private Connection connection;
    private Channel channel;
    private final Address[] addresses;
    protected LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    private volatile boolean running;

    public AMQPObservableQueue(ConnectionFactory factory, Address[] addresses, boolean useExchange,
        AMQPSettings settings, int batchSize, int pollTimeInMS) {
        if (factory == null) {
            throw new IllegalArgumentException("Connection factory is undefined");
        }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Addresses are undefined");
        }
        if (settings == null) {
            throw new IllegalArgumentException("Settings are undefined");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than 0");
        }
        if (pollTimeInMS <= 0) {
            throw new IllegalArgumentException("Poll time must be greater than 0 ms");
        }
        this.factory = factory;
        this.addresses = addresses;
        this.useExchange = useExchange;
        this.settings = settings;
        this.batchSize = batchSize;
        this.setPollTimeInMS(pollTimeInMS);
    }

    private void connect() {
        if (connection != null) {
            return;
        }
        try {
            connection = factory.newConnection(addresses);

            if (connection == null || !connection.isOpen()) {
                throw new RuntimeException("Failed to open connection");
            }
        } catch (final IOException e) {

            final String error = "IO error while connecting to "
                    + Arrays.stream(addresses).map(Address::toString).collect(Collectors.joining(","));
            LOGGER.error(error, e);
            throw new RuntimeException(error, e);
        } catch (final TimeoutException e) {

            final String error = "Timeout while connecting to "
                    + Arrays.stream(addresses).map(Address::toString).collect(Collectors.joining(","));
            LOGGER.error(error, e);
            throw new RuntimeException(error, e);
        }
    }

    @Override
    public Observable<Message> observe() {
        receiveMessages();
        Observable.OnSubscribe<Message> onSubscribe = subscriber -> {
            Observable<Long> interval = Observable.interval(pollTimeInMS, TimeUnit.MILLISECONDS);
            interval.flatMap((Long x) -> {
                if (!isRunning()) {
                    LOGGER.debug("Component stopped, skip listening for messages from RabbitMQ");
                    return Observable.from(Collections.emptyList());
                } else {
                    List<Message> available = new LinkedList<>();
                    messages.drainTo(available);

                    if (!available.isEmpty()) {
                        AtomicInteger count = new AtomicInteger(0);
                        StringBuilder buffer = new StringBuilder();
                        available.forEach(msg -> {
                            buffer.append(msg.getId()).append("=").append(msg.getPayload());
                            count.incrementAndGet();

                            if (count.get() < available.size()) {
                                buffer.append(",");
                            }
                        });
                        LOGGER.info(String.format("Batch from %s to conductor is %s", settings.getQueueOrExchangeName(),
                            buffer.toString()));
                    }
                    return Observable.from(available);
                }
            }).subscribe(subscriber::onNext, subscriber::onError);
        };
        return Observable.create(onSubscribe);
    }

    @Override
    public String getType() {
        return useExchange ? AMQPConstants.AMQP_EXCHANGE_TYPE : AMQPConstants.AMQP_QUEUE_TYPE;
    }

    @Override
    public String getName() {
        return settings.getEventName();
    }

    @Override
    public String getURI() {
        return settings.getQueueOrExchangeName();
    }

    public int getBatchSize() {
        return batchSize;
    }

    public AMQPSettings getSettings() {
        return settings;
    }

    public Address[] getAddresses() {
        return addresses;
    }

    @Override
    public List<String> ack(List<Message> messages) {
        final List<String> processedDeliveryTags = new ArrayList<>();
        for (final Message message : messages) {
            try {
                LOGGER.info("ACK message with delivery tag {}", message.getReceipt());
                getOrCreateChannel().basicAck(Long.parseLong(message.getReceipt()), false);
                // Message ACKed
                processedDeliveryTags.add(message.getReceipt());
            } catch (final IOException e) {
                LOGGER.error("Cannot ACK message with delivery tag {}", message.getReceipt(), e);
            }
        }
        return processedDeliveryTags;
    }

    private static AMQP.BasicProperties buildBasicProperties(final Message message, final AMQPSettings settings) {
        return new AMQP.BasicProperties.Builder()
            .messageId(StringUtils.isEmpty(message.getId()) ? UUID.randomUUID().toString() : message.getId())
            .correlationId(
                StringUtils.isEmpty(message.getReceipt()) ? UUID.randomUUID().toString() : message.getReceipt())
            .contentType(settings.getContentType()).contentEncoding(settings.getContentEncoding())
            .deliveryMode(settings.getDeliveryMode()).build();
    }

    private void publishMessage(Message message, String exchange, String routingKey) {
        try {
            final String payload = message.getPayload();
            getOrCreateChannel().basicPublish(exchange, routingKey, buildBasicProperties(message, settings),
                payload.getBytes(settings.getContentEncoding()));
            LOGGER.info(String.format("Published message to %s: %s", exchange, payload));
        } catch (Exception ex) {
            LOGGER.error("Failed to publish message {} to {}", message.getPayload(), exchange, ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void publish(List<Message> messages) {
        try {
            final String exchange, routingKey;
            if (useExchange) {
                // Use exchange + routing key for publishing
                getOrCreateExchange(settings.getQueueOrExchangeName(), settings.getExchangeType(), settings.isDurable(),
                    settings.autoDelete(), settings.getArguments());
                exchange = settings.getQueueOrExchangeName();
                routingKey = settings.getRoutingKey();
            } else {
                // Use queue for publishing
                final AMQP.Queue.DeclareOk declareOk = getOrCreateQueue(settings.getQueueOrExchangeName(),
                    settings.isDurable(), settings.isExclusive(), settings.autoDelete(), settings.getArguments());
                exchange = StringUtils.EMPTY; // Empty exchange name for queue
                routingKey = declareOk.getQueue(); // Routing name is the name of queue
            }
            messages.forEach(message -> publishMessage(message, exchange, routingKey));
        } catch (final RuntimeException ex) {
            throw ex;
        } catch (final Exception ex) {
            LOGGER.error("Failed to publish messages: {}", ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setUnackTimeout(Message message, long unackTimeout) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long size() {
        try {
            return getOrCreateChannel().messageCount(settings.getQueueOrExchangeName());
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        closeChannel();
        closeConnection();
    }

    @Override
    public void start() {
        LOGGER.info("Started listening to {}:{}", getClass().getSimpleName(), settings.getQueueOrExchangeName());
        running = true;
    }

    @Override
    public void stop() {
        LOGGER.info("Stopped listening to {}:{}", getClass().getSimpleName(), settings.getQueueOrExchangeName());
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public static class Builder {

        private final Address[] addresses;
        private final int batchSize;
        private final int pollTimeInMS;
        private final ConnectionFactory factory;
        private final AMQPEventQueueProperties properties;

        public Builder(AMQPEventQueueProperties properties) {
            this.properties = properties;
            this.addresses = buildAddressesFromHosts();
            this.factory = buildConnectionFactory();
            // messages polling settings
            this.batchSize = properties.getBatchSize();
            this.pollTimeInMS = (int) properties.getPollTimeDuration().toMillis();
        }

        private Address[] buildAddressesFromHosts() {
            // Read hosts from config
            final String hosts = properties.getHosts();
            if (StringUtils.isEmpty(hosts)) {
                throw new IllegalArgumentException("Hosts are undefined");
            }
            return Address.parseAddresses(hosts);
        }

        private ConnectionFactory buildConnectionFactory() {
            final ConnectionFactory factory = new ConnectionFactory();
            // Get rabbitmq username from config
            final String username = properties.getUsername();
            if (StringUtils.isEmpty(username)) {
                throw new IllegalArgumentException("Username is null or empty");
            } else {
                factory.setUsername(username);
            }
            // Get rabbitmq password from config
            final String password = properties.getPassword();
            if (StringUtils.isEmpty(password)) {
                throw new IllegalArgumentException("Password is null or empty");
            } else {
                factory.setPassword(password);
            }
            // Get vHost from config
            final String virtualHost = properties.getVirtualHost();
            ;
            if (StringUtils.isEmpty(virtualHost)) {
                throw new IllegalArgumentException("Virtual host is null or empty");
            } else {
                factory.setVirtualHost(virtualHost);
            }
            // Get server port from config
            final int port = properties.getPort();
            if (port <= 0) {
                throw new IllegalArgumentException("Port must be greater than 0");
            } else {
                factory.setPort(port);
            }
            // Get connection timeout from config
            final int connectionTimeout = (int) properties.getConnectionTimeout().toMillis();
            if (connectionTimeout <= 0) {
                throw new IllegalArgumentException("Connection timeout must be greater than 0");
            } else {
                factory.setConnectionTimeout(connectionTimeout);
            }
            final boolean useNio = properties.isUseNio();
            if (useNio) {
                factory.useNio();
            }
            factory.setAutomaticRecoveryEnabled(true);
            factory.setTopologyRecoveryEnabled(true);
            return factory;
        }

        public AMQPObservableQueue build(final boolean useExchange, final String queueURI) {
            final AMQPSettings settings = new AMQPSettings(properties).fromURI(queueURI);
            return new AMQPObservableQueue(factory, addresses, useExchange, settings, batchSize, pollTimeInMS);
        }
    }

    private Channel getOrCreateChannel() throws IOException {
        // Return the existing channel if it was created
        if (channel != null) {
            if (channel.isOpen()) {
                return channel;
            }
            throw new IOException("Channel was created but is currently closed");
        }
        // Channel creation is required
        try {
            connect();
            channel = connection.createChannel();
            channel.addShutdownListener(cause -> {
                LOGGER.error("Channel has been shutdown: {}", cause.getMessage(), cause);
            });
        } catch (final IOException e) {
            throw new RuntimeException("Cannot open channel on "
                + Arrays.stream(addresses).map(address -> address.toString()).collect(Collectors.joining(",")), e);
        }

        if (channel == null) {
            throw new RuntimeException("Fail to open channel");
        }
        return channel;
    }

    private AMQP.Exchange.DeclareOk getOrCreateExchange() throws IOException {
        return getOrCreateExchange(settings.getQueueOrExchangeName(), settings.getExchangeType(), settings.isDurable(),
            settings.autoDelete(), settings.getArguments());
    }

    private AMQP.Exchange.DeclareOk getOrCreateExchange(final String name, final String type, final boolean isDurable,
        final boolean autoDelete, final Map<String, Object> arguments) throws IOException {
        if (StringUtils.isEmpty(name)) {
            throw new RuntimeException("Exchange name is undefined");
        }
        if (StringUtils.isEmpty(type)) {
            throw new RuntimeException("Exchange type is undefined");
        }

        try {
            return getOrCreateChannel().exchangeDeclare(name, type, isDurable, autoDelete, arguments);
        } catch (final IOException e) {
            LOGGER.warn("Failed to create exchange {} of type {}", name, type, e);
            throw e;
        }
    }

    private AMQP.Queue.DeclareOk getOrCreateQueue() throws IOException {
        return getOrCreateQueue(settings.getQueueOrExchangeName(), settings.isDurable(), settings.isExclusive(),
            settings.autoDelete(), settings.getArguments());
    }

    private AMQP.Queue.DeclareOk getOrCreateQueue(final String name, final boolean isDurable, final boolean isExclusive,
        final boolean autoDelete, final Map<String, Object> arguments) throws IOException {
        if (StringUtils.isEmpty(name)) {
            throw new RuntimeException("Queue name is undefined");
        }

        try {
            return  getOrCreateChannel().queueDeclare(name, isDurable, isExclusive, autoDelete, arguments);
        } catch (final IOException e) {
             LOGGER.warn("Failed to create queue {}", name, e);
            throw e;
        }
    }

    private void closeConnection() {
        if (connection == null) {
            LOGGER.warn("Connection is null. Do not close it");
        } else {
            try {
                if (connection.isOpen()) {
                    try {
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info("Close AMQP connection");
                        }
                        connection.close();
                    } catch (final IOException e) {
                        LOGGER.warn("Fail to close connection: {}", e.getMessage(), e);
                    }
                }
            } finally {
                connection = null;
            }
        }
    }

    private void closeChannel() {
        if (channel == null) {
            LOGGER.warn("Channel is null. Do not close it");
        } else {
            try {
                if (channel.isOpen()) {
                    try {
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info("Close AMQP channel");
                        }
                        channel.close();
                    } catch (final TimeoutException e) {
                        LOGGER.warn("Timeout while closing channel: {}", e.getMessage(), e);
                    } catch (final IOException e) {
                        LOGGER.warn("Fail to close channel: {}", e.getMessage(), e);
                    }
                }
            } finally {
                channel = null;
            }
        }
    }

    private static Message asMessage(AMQPSettings settings, GetResponse response) throws Exception {
        if (response == null) {
            return null;
        }
        final Message message = new Message();
        message.setId(response.getProps().getMessageId());
        message.setPayload(new String(response.getBody(), settings.getContentEncoding()));
        message.setReceipt(String.valueOf(response.getEnvelope().getDeliveryTag()));
        return message;
    }

    private void receiveMessagesFromQueue(String queueName) throws Exception {
        int nb = 0;
        Consumer consumer = new DefaultConsumer(channel) {

            @Override
            public void handleDelivery(final String consumerTag, final Envelope envelope,
                final AMQP.BasicProperties properties, final byte[] body) throws IOException {
                try {
                    Message message = asMessage(settings,
                        new GetResponse(envelope, properties, body, Integer.MAX_VALUE));
                    if (message != null) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Got message with ID {} and receipt {}", message.getId(),
                                message.getReceipt());
                        }
                        messages.add(message);
                        LOGGER.info("receiveMessagesFromQueue- End method {}", messages);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    //
                }
            }
        };

        getOrCreateChannel().basicConsume(queueName, false, consumer);
        Monitors.recordEventQueueMessagesProcessed(getType(), queueName, messages.size());
    }

    protected void receiveMessages() {
        try {
            getOrCreateChannel().basicQos(batchSize);
            String queueName;
            if (useExchange) {
                // Consume messages from an exchange
                getOrCreateExchange();
                /*
                 * Create queue if not present based on the settings provided in the queue URI or configuration properties.
                 * Sample URI format: amqp-exchange:myExchange?exchangeType=topic&routingKey=myRoutingKey&exclusive=false&autoDelete=false&durable=true
                 * Default settings if not provided in the queue URI or properties: isDurable: true, autoDelete: false, isExclusive: false
                 * The same settings are currently used during creation of exchange as well as queue.
                 * TODO: This can be enhanced further to get the settings separately for exchange and queue from the URI
                 */
                final AMQP.Queue.DeclareOk declareOk = getOrCreateQueue(
                    String.format("bound_to_%s", settings.getQueueOrExchangeName()), settings.isDurable(),
                    settings.isExclusive(), settings.autoDelete(),
                    Maps.newHashMap());
                // Bind the declared queue to exchange
                queueName = declareOk.getQueue();
                getOrCreateChannel().queueBind(queueName, settings.getQueueOrExchangeName(), settings.getRoutingKey());
            } else {
                // Consume messages from a queue
                queueName = getOrCreateQueue().getQueue();
            }
            // Consume messages
            LOGGER.info("Consuming from queue {}", queueName);
            receiveMessagesFromQueue(queueName);
        } catch (Exception exception) {
            LOGGER.error("Exception while getting messages from RabbitMQ", exception);
            Monitors.recordObservableQMessageReceivedErrors(getType());
        }
    }

    public int getPollTimeInMS() {
        return pollTimeInMS;
    }

    public void setPollTimeInMS(int pollTimeInMS) {
        this.pollTimeInMS = pollTimeInMS;
    }
}
