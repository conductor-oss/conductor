/*
 * Copyright 2023 Conductor Authors.
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

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.contribs.queue.amqp.config.AMQPEventQueueProperties;
import com.netflix.conductor.contribs.queue.amqp.config.AMQPRetryPattern;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPConstants;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPSettings;
import com.netflix.conductor.contribs.queue.amqp.util.ConnectionType;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.metrics.Monitors;

import com.google.common.collect.Maps;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import rx.Observable;
import rx.Subscriber;

/**
 * @author Ritu Parathody
 */
public class AMQPObservableQueue implements ObservableQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger(AMQPObservableQueue.class);

    private final AMQPSettings settings;
    private final AMQPRetryPattern retrySettings;
    private final String QUEUE_TYPE = "x-queue-type";
    private final int batchSize;
    private final boolean useExchange;
    private int pollTimeInMS;
    private AMQPConnection amqpConnection;

    protected LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    private volatile boolean running;

    public AMQPObservableQueue(
            ConnectionFactory factory,
            Address[] addresses,
            boolean useExchange,
            AMQPSettings settings,
            AMQPRetryPattern retrySettings,
            int batchSize,
            int pollTimeInMS) {
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
        this.useExchange = useExchange;
        this.settings = settings;
        this.batchSize = batchSize;
        this.amqpConnection = AMQPConnection.getInstance(factory, addresses, retrySettings);
        this.retrySettings = retrySettings;
        this.setPollTimeInMS(pollTimeInMS);
    }

    @Override
    public Observable<Message> observe() {
        Observable.OnSubscribe<Message> onSubscribe = null;
        // This will enabled the messages to be processed one after the other as per the
        // observable next behavior.
        if (settings.isSequentialProcessing()) {
            LOGGER.info("Subscribing for the message processing on schedule basis");
            receiveMessages();
            onSubscribe =
                    subscriber -> {
                        Observable<Long> interval =
                                Observable.interval(pollTimeInMS, TimeUnit.MILLISECONDS);
                        interval.flatMap(
                                        (Long x) -> {
                                            if (!isRunning()) {
                                                LOGGER.debug(
                                                        "Component stopped, skip listening for messages from RabbitMQ");
                                                return Observable.from(Collections.emptyList());
                                            } else {
                                                List<Message> available = new LinkedList<>();
                                                messages.drainTo(available);

                                                if (!available.isEmpty()) {
                                                    AtomicInteger count = new AtomicInteger(0);
                                                    StringBuilder buffer = new StringBuilder();
                                                    available.forEach(
                                                            msg -> {
                                                                buffer.append(msg.getId())
                                                                        .append("=")
                                                                        .append(msg.getPayload());
                                                                count.incrementAndGet();

                                                                if (count.get()
                                                                        < available.size()) {
                                                                    buffer.append(",");
                                                                }
                                                            });
                                                    LOGGER.info(
                                                            String.format(
                                                                    "Batch from %s to conductor is %s",
                                                                    settings
                                                                            .getQueueOrExchangeName(),
                                                                    buffer.toString()));
                                                }
                                                return Observable.from(available);
                                            }
                                        })
                                .subscribe(subscriber::onNext, subscriber::onError);
                    };
            LOGGER.info("Subscribed for the message processing on schedule basis");
        } else {
            onSubscribe =
                    subscriber -> {
                        LOGGER.info("Subscribing for the event based AMQP message processing");
                        receiveMessages(subscriber);
                        LOGGER.info("Subscribed for the event based AMQP message processing");
                    };
        }
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
        return amqpConnection.getAddresses();
    }

    public List<String> ack(List<Message> messages) {
        final List<String> failedMessages = new ArrayList<>();
        for (final Message message : messages) {
            try {
                ackMsg(message);
            } catch (final Exception e) {
                LOGGER.error("Cannot ACK message with delivery tag {}", message.getReceipt(), e);
                failedMessages.add(message.getReceipt());
            }
        }
        return failedMessages;
    }

    public void ackMsg(Message message) throws Exception {
        int retryIndex = 1;
        while (true) {
            try {
                LOGGER.info("ACK message with delivery tag {}", message.getReceipt());
                Channel chn =
                        amqpConnection.getOrCreateChannel(
                                ConnectionType.SUBSCRIBER, getSettings().getQueueOrExchangeName());
                chn.basicAck(Long.parseLong(message.getReceipt()), false);
                LOGGER.info("Ack'ed the message with delivery tag {}", message.getReceipt());
                break;
            } catch (final Exception e) {
                AMQPRetryPattern retry = retrySettings;
                if (retry == null) {
                    LOGGER.error(
                            "Cannot ACK message with delivery tag {}", message.getReceipt(), e);
                    throw e;
                }
                try {
                    retry.continueOrPropogate(e, retryIndex);
                } catch (Exception ex) {
                    LOGGER.error(
                            "Retries completed. Cannot ACK message with delivery tag {}",
                            message.getReceipt(),
                            e);
                    throw ex;
                }
                retryIndex++;
            }
        }
    }

    @Override
    public void nack(List<Message> messages) {
        for (final Message message : messages) {
            int retryIndex = 1;
            while (true) {
                try {
                    LOGGER.info("NACK message with delivery tag {}", message.getReceipt());
                    Channel chn =
                            amqpConnection.getOrCreateChannel(
                                    ConnectionType.SUBSCRIBER,
                                    getSettings().getQueueOrExchangeName());
                    chn.basicNack(Long.parseLong(message.getReceipt()), false, false);
                    LOGGER.info("Nack'ed the message with delivery tag {}", message.getReceipt());
                    break;
                } catch (final Exception e) {
                    AMQPRetryPattern retry = retrySettings;
                    if (retry == null) {
                        LOGGER.error(
                                "Cannot NACK message with delivery tag {}",
                                message.getReceipt(),
                                e);
                    }
                    try {
                        retry.continueOrPropogate(e, retryIndex);
                    } catch (Exception ex) {
                        LOGGER.error(
                                "Retries completed. Cannot NACK message with delivery tag {}",
                                message.getReceipt(),
                                e);
                        break;
                    }
                    retryIndex++;
                }
            }
        }
    }

    private static AMQP.BasicProperties buildBasicProperties(
            final Message message, final AMQPSettings settings) {
        return new AMQP.BasicProperties.Builder()
                .messageId(
                        StringUtils.isEmpty(message.getId())
                                ? UUID.randomUUID().toString()
                                : message.getId())
                .correlationId(
                        StringUtils.isEmpty(message.getReceipt())
                                ? UUID.randomUUID().toString()
                                : message.getReceipt())
                .contentType(settings.getContentType())
                .contentEncoding(settings.getContentEncoding())
                .deliveryMode(settings.getDeliveryMode())
                .build();
    }

    private void publishMessage(Message message, String exchange, String routingKey) {
        Channel chn = null;
        int retryIndex = 1;
        while (true) {
            try {
                final String payload = message.getPayload();
                chn =
                        amqpConnection.getOrCreateChannel(
                                ConnectionType.PUBLISHER, getSettings().getQueueOrExchangeName());
                chn.basicPublish(
                        exchange,
                        routingKey,
                        buildBasicProperties(message, settings),
                        payload.getBytes(settings.getContentEncoding()));
                LOGGER.info(String.format("Published message to %s: %s", exchange, payload));
                break;
            } catch (Exception ex) {
                AMQPRetryPattern retry = retrySettings;
                if (retry == null) {
                    LOGGER.error(
                            "Failed to publish message {} to {}",
                            message.getPayload(),
                            exchange,
                            ex);
                    throw new RuntimeException(ex);
                }
                try {
                    retry.continueOrPropogate(ex, retryIndex);
                } catch (Exception e) {
                    LOGGER.error(
                            "Retries completed. Failed to publish message {} to {}",
                            message.getPayload(),
                            exchange,
                            ex);
                    throw new RuntimeException(ex);
                }
                retryIndex++;
            } finally {
                if (chn != null) {
                    try {
                        amqpConnection.returnChannel(ConnectionType.PUBLISHER, chn);
                    } catch (Exception e) {
                        LOGGER.error(
                                "Failed to return the channel of {}. {}",
                                ConnectionType.PUBLISHER,
                                e);
                    }
                }
            }
        }
    }

    @Override
    public void publish(List<Message> messages) {
        try {
            final String exchange, routingKey;
            if (useExchange) {
                // Use exchange + routing key for publishing
                getOrCreateExchange(
                        ConnectionType.PUBLISHER,
                        settings.getQueueOrExchangeName(),
                        settings.getExchangeType(),
                        settings.isDurable(),
                        settings.autoDelete(),
                        settings.getArguments());
                exchange = settings.getQueueOrExchangeName();
                routingKey = settings.getRoutingKey();
            } else {
                // Use queue for publishing
                final AMQP.Queue.DeclareOk declareOk =
                        getOrCreateQueue(
                                ConnectionType.PUBLISHER,
                                settings.getQueueOrExchangeName(),
                                settings.isDurable(),
                                settings.isExclusive(),
                                settings.autoDelete(),
                                settings.getArguments());
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
        Channel chn = null;
        try {
            chn =
                    amqpConnection.getOrCreateChannel(
                            ConnectionType.SUBSCRIBER, getSettings().getQueueOrExchangeName());

            return switch (settings.getType()) {
                case EXCHANGE -> chn.messageCount(settings.getExchangeBoundQueueName());
                case QUEUE -> chn.messageCount(settings.getQueueOrExchangeName());
            };
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (chn != null) {
                try {
                    amqpConnection.returnChannel(ConnectionType.SUBSCRIBER, chn);
                } catch (Exception e) {
                    LOGGER.error(
                            "Failed to return the channel of {}. {}", ConnectionType.SUBSCRIBER, e);
                }
            }
        }
    }

    @Override
    public void close() {
        amqpConnection.close();
    }

    @Override
    public void start() {
        LOGGER.info(
                "Started listening to {}:{}",
                getClass().getSimpleName(),
                settings.getQueueOrExchangeName());
        running = true;
    }

    @Override
    public void stop() {
        LOGGER.info(
                "Stopped listening to {}:{}",
                getClass().getSimpleName(),
                settings.getQueueOrExchangeName());
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
            final boolean useNio = properties.isUseNio();
            if (useNio) {
                factory.useNio();
            }
            final boolean useSslProtocol = properties.isUseSslProtocol();
            if (useSslProtocol) {
                try {
                    factory.useSslProtocol();
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    throw new IllegalArgumentException("Invalid sslProtocol ", e);
                }
            }
            factory.setConnectionTimeout(properties.getConnectionTimeoutInMilliSecs());
            factory.setRequestedHeartbeat(properties.getRequestHeartbeatTimeoutInSecs());
            factory.setNetworkRecoveryInterval(properties.getNetworkRecoveryIntervalInMilliSecs());
            factory.setHandshakeTimeout(properties.getHandshakeTimeoutInMilliSecs());
            factory.setAutomaticRecoveryEnabled(true);
            factory.setTopologyRecoveryEnabled(true);
            factory.setRequestedChannelMax(properties.getMaxChannelCount());
            return factory;
        }

        public AMQPObservableQueue build(
                final boolean useExchange, final String queueURI, final String queueType) {
            final AMQPSettings settings = new AMQPSettings(properties, queueType).fromURI(queueURI);
            final AMQPRetryPattern retrySettings =
                    new AMQPRetryPattern(
                            properties.getLimit(), properties.getDuration(), properties.getType());
            return new AMQPObservableQueue(
                    factory,
                    addresses,
                    useExchange,
                    settings,
                    retrySettings,
                    batchSize,
                    pollTimeInMS);
        }
    }

    private AMQP.Exchange.DeclareOk getOrCreateExchange(ConnectionType connectionType)
            throws Exception {
        return getOrCreateExchange(
                connectionType,
                settings.getQueueOrExchangeName(),
                settings.getExchangeType(),
                settings.isDurable(),
                settings.autoDelete(),
                settings.getArguments());
    }

    private AMQP.Exchange.DeclareOk getOrCreateExchange(
            ConnectionType connectionType,
            String name,
            final String type,
            final boolean isDurable,
            final boolean autoDelete,
            final Map<String, Object> arguments)
            throws Exception {
        if (StringUtils.isEmpty(name)) {
            throw new RuntimeException("Exchange name is undefined");
        }
        if (StringUtils.isEmpty(type)) {
            throw new RuntimeException("Exchange type is undefined");
        }
        Channel chn = null;
        try {
            LOGGER.debug("Creating exchange {} of type {}", name, type);
            chn =
                    amqpConnection.getOrCreateChannel(
                            connectionType, getSettings().getQueueOrExchangeName());
            return chn.exchangeDeclare(name, type, isDurable, autoDelete, arguments);
        } catch (final Exception e) {
            LOGGER.warn("Failed to create exchange {} of type {}", name, type, e);
            throw e;
        } finally {
            if (chn != null) {
                try {
                    amqpConnection.returnChannel(connectionType, chn);
                } catch (Exception e) {
                    LOGGER.error("Failed to return the channel of {}. {}", connectionType, e);
                }
            }
        }
    }

    private AMQP.Queue.DeclareOk getOrCreateQueue(ConnectionType connectionType) throws Exception {
        return getOrCreateQueue(
                connectionType,
                settings.getQueueOrExchangeName(),
                settings.isDurable(),
                settings.isExclusive(),
                settings.autoDelete(),
                settings.getArguments());
    }

    private AMQP.Queue.DeclareOk getOrCreateQueue(
            ConnectionType connectionType,
            final String name,
            final boolean isDurable,
            final boolean isExclusive,
            final boolean autoDelete,
            final Map<String, Object> arguments)
            throws Exception {
        if (StringUtils.isEmpty(name)) {
            throw new RuntimeException("Queue name is undefined");
        }
        arguments.put(QUEUE_TYPE, settings.getQueueType());
        Channel chn = null;
        try {
            LOGGER.debug("Creating queue {}", name);
            chn =
                    amqpConnection.getOrCreateChannel(
                            connectionType, getSettings().getQueueOrExchangeName());
            return chn.queueDeclare(name, isDurable, isExclusive, autoDelete, arguments);
        } catch (final Exception e) {
            LOGGER.warn("Failed to create queue {}", name, e);
            throw e;
        } finally {
            if (chn != null) {
                try {
                    amqpConnection.returnChannel(connectionType, chn);
                } catch (Exception e) {
                    LOGGER.error("Failed to return the channel of {}. {}", connectionType, e);
                }
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
        LOGGER.debug("Accessing channel for queue {}", queueName);

        Consumer consumer =
                new DefaultConsumer(
                        amqpConnection.getOrCreateChannel(
                                ConnectionType.SUBSCRIBER,
                                getSettings().getQueueOrExchangeName())) {

                    @Override
                    public void handleDelivery(
                            final String consumerTag,
                            final Envelope envelope,
                            final AMQP.BasicProperties properties,
                            final byte[] body)
                            throws IOException {
                        try {
                            Message message =
                                    asMessage(
                                            settings,
                                            new GetResponse(
                                                    envelope, properties, body, Integer.MAX_VALUE));
                            if (message != null) {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug(
                                            "Got message with ID {} and receipt {}",
                                            message.getId(),
                                            message.getReceipt());
                                }
                                messages.add(message);
                                LOGGER.info("receiveMessagesFromQueue- End method {}", messages);
                            }
                        } catch (InterruptedException e) {
                            LOGGER.error(
                                    "Issue in handling the mesages for the subscriber with consumer tag {}. {}",
                                    consumerTag,
                                    e);
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            LOGGER.error(
                                    "Issue in handling the mesages for the subscriber with consumer tag {}. {}",
                                    consumerTag,
                                    e);
                        }
                    }

                    public void handleCancel(String consumerTag) throws IOException {
                        LOGGER.error(
                                "Recieved a consumer cancel notification for subscriber {}",
                                consumerTag);
                    }
                };

        amqpConnection
                .getOrCreateChannel(
                        ConnectionType.SUBSCRIBER, getSettings().getQueueOrExchangeName())
                .basicConsume(queueName, false, consumer);
        Monitors.recordEventQueueMessagesProcessed(getType(), queueName, messages.size());
    }

    private void receiveMessagesFromQueue(String queueName, Subscriber<? super Message> subscriber)
            throws Exception {
        LOGGER.debug("Accessing channel for queue {}", queueName);

        Consumer consumer =
                new DefaultConsumer(
                        amqpConnection.getOrCreateChannel(
                                ConnectionType.SUBSCRIBER,
                                getSettings().getQueueOrExchangeName())) {

                    @Override
                    public void handleDelivery(
                            final String consumerTag,
                            final Envelope envelope,
                            final AMQP.BasicProperties properties,
                            final byte[] body)
                            throws IOException {
                        try {
                            Message message =
                                    asMessage(
                                            settings,
                                            new GetResponse(
                                                    envelope, properties, body, Integer.MAX_VALUE));
                            if (message == null) {
                                return;
                            }
                            LOGGER.info(
                                    "Got message with ID {} and receipt {}",
                                    message.getId(),
                                    message.getReceipt());
                            LOGGER.debug("Message content {}", message);
                            // Not using thread-pool here as the number of concurrent threads are
                            // controlled
                            // by the number of messages delivery using pre-fetch count in RabbitMQ
                            Thread newThread =
                                    new Thread(
                                            () -> {
                                                LOGGER.info(
                                                        "Spawning a new thread for message with ID {}",
                                                        message.getId());
                                                subscriber.onNext(message);
                                            });
                            newThread.start();
                        } catch (InterruptedException e) {
                            LOGGER.error(
                                    "Issue in handling the mesages for the subscriber with consumer tag {}. {}",
                                    consumerTag,
                                    e);
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            LOGGER.error(
                                    "Issue in handling the mesages for the subscriber with consumer tag {}. {}",
                                    consumerTag,
                                    e);
                        }
                    }

                    public void handleCancel(String consumerTag) throws IOException {
                        LOGGER.error(
                                "Recieved a consumer cancel notification for subscriber {}",
                                consumerTag);
                    }
                };
        amqpConnection
                .getOrCreateChannel(
                        ConnectionType.SUBSCRIBER, getSettings().getQueueOrExchangeName())
                .basicConsume(queueName, false, consumer);
    }

    protected void receiveMessages() {
        try {
            amqpConnection
                    .getOrCreateChannel(
                            ConnectionType.SUBSCRIBER, getSettings().getQueueOrExchangeName())
                    .basicQos(batchSize);
            String queueName;
            if (useExchange) {
                // Consume messages from an exchange
                getOrCreateExchange(ConnectionType.SUBSCRIBER);
                /*
                 * Create queue if not present based on the settings provided in the queue URI
                 * or configuration properties. Sample URI format:
                 * amqp_exchange:myExchange?bindQueueName=myQueue&exchangeType=topic&routingKey=myRoutingKey&exclusive
                 * =false&autoDelete=false&durable=true Default settings if not provided in the
                 * queue URI or properties: isDurable: true, autoDelete: false, isExclusive:
                 * false The same settings are currently used during creation of exchange as
                 * well as queue. TODO: This can be enhanced further to get the settings
                 * separately for exchange and queue from the URI
                 */
                final AMQP.Queue.DeclareOk declareOk =
                        getOrCreateQueue(
                                ConnectionType.SUBSCRIBER,
                                settings.getExchangeBoundQueueName(),
                                settings.isDurable(),
                                settings.isExclusive(),
                                settings.autoDelete(),
                                Maps.newHashMap());
                // Bind the declared queue to exchange
                queueName = declareOk.getQueue();
                amqpConnection
                        .getOrCreateChannel(
                                ConnectionType.SUBSCRIBER, getSettings().getQueueOrExchangeName())
                        .queueBind(
                                queueName,
                                settings.getQueueOrExchangeName(),
                                settings.getRoutingKey());
            } else {
                // Consume messages from a queue
                queueName = getOrCreateQueue(ConnectionType.SUBSCRIBER).getQueue();
            }
            // Consume messages
            LOGGER.info("Consuming from queue {}", queueName);
            receiveMessagesFromQueue(queueName);
        } catch (Exception exception) {
            LOGGER.error("Exception while getting messages from RabbitMQ", exception);
            Monitors.recordObservableQMessageReceivedErrors(getType());
        }
    }

    protected void receiveMessages(Subscriber<? super Message> subscriber) {
        try {
            amqpConnection
                    .getOrCreateChannel(
                            ConnectionType.SUBSCRIBER, getSettings().getQueueOrExchangeName())
                    .basicQos(batchSize);
            String queueName;
            if (useExchange) {
                // Consume messages from an exchange
                getOrCreateExchange(ConnectionType.SUBSCRIBER);
                /*
                 * Create queue if not present based on the settings provided in the queue URI
                 * or configuration properties. Sample URI format:
                 * amqp_exchange:myExchange?bindQueueName=myQueue&exchangeType=topic&routingKey=myRoutingKey&exclusive
                 * =false&autoDelete=false&durable=true Default settings if not provided in the
                 * queue URI or properties: isDurable: true, autoDelete: false, isExclusive:
                 * false The same settings are currently used during creation of exchange as
                 * well as queue. TODO: This can be enhanced further to get the settings
                 * separately for exchange and queue from the URI
                 */
                final AMQP.Queue.DeclareOk declareOk =
                        getOrCreateQueue(
                                ConnectionType.SUBSCRIBER,
                                settings.getExchangeBoundQueueName(),
                                settings.isDurable(),
                                settings.isExclusive(),
                                settings.autoDelete(),
                                Maps.newHashMap());
                // Bind the declared queue to exchange
                queueName = declareOk.getQueue();
                amqpConnection
                        .getOrCreateChannel(
                                ConnectionType.SUBSCRIBER, settings.getQueueOrExchangeName())
                        .queueBind(
                                queueName,
                                settings.getQueueOrExchangeName(),
                                settings.getRoutingKey());
            } else {
                // Consume messages from a queue
                queueName = getOrCreateQueue(ConnectionType.SUBSCRIBER).getQueue();
            }
            // Consume messages
            LOGGER.info("Consuming from queue {}", queueName);
            receiveMessagesFromQueue(queueName, subscriber);
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
