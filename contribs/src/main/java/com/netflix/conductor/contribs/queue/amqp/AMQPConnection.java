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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.contribs.queue.amqp.util.ConnectionType;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

public class AMQPConnection {

    private static Logger LOGGER = LoggerFactory.getLogger(AMQPConnection.class);
    private volatile Connection publisherConnection = null;
    private volatile Connection subscriberConnection = null;
    private ConnectionFactory factory = null;
    private Address[] addresses = null;
    private static AMQPConnection amqpConnection = null;
    private static final String PUBLISHER = "Publisher";
    private static final String SUBSCRIBER = "Subscriber";
    private static final String SEPARATOR = ":";
    Map<String, Channel> queueNameToChannel = new ConcurrentHashMap<String, Channel>();

    private AMQPConnection() {}

    private AMQPConnection(final ConnectionFactory factory, final Address[] address) {
        this.factory = factory;
        this.addresses = address;
    }

    public static synchronized AMQPConnection getInstance(
            final ConnectionFactory factory, final Address[] address) {
        if (AMQPConnection.amqpConnection == null) {
            AMQPConnection.amqpConnection = new AMQPConnection(factory, address);
        }

        return AMQPConnection.amqpConnection;
    }

    // Exposed for UT
    public static void setAMQPConnection(AMQPConnection amqpConnection) {
        AMQPConnection.amqpConnection = amqpConnection;
    }

    public Address[] getAddresses() {
        return addresses;
    }

    private Connection createConnection(String connectionPrefix) {

        try {
            Connection connection =
                    factory.newConnection(
                            addresses, System.getenv("HOSTNAME") + "-" + connectionPrefix);
            if (connection == null || !connection.isOpen()) {
                throw new RuntimeException("Failed to open connection");
            }

            connection.addShutdownListener(
                    new ShutdownListener() {
                        @Override
                        public void shutdownCompleted(ShutdownSignalException cause) {
                            LOGGER.error(
                                    "Received a shutdown exception for the connection {}. reason {} cause{}",
                                    connection.getClientProvidedName(),
                                    cause.getMessage(),
                                    cause);
                        }
                    });

            connection.addBlockedListener(
                    new BlockedListener() {
                        @Override
                        public void handleUnblocked() throws IOException {
                            LOGGER.info(
                                    "Connection {} is unblocked",
                                    connection.getClientProvidedName());
                        }

                        @Override
                        public void handleBlocked(String reason) throws IOException {
                            LOGGER.error(
                                    "Connection {} is blocked. reason: {}",
                                    connection.getClientProvidedName(),
                                    reason);
                        }
                    });

            return connection;
        } catch (final IOException e) {
            final String error =
                    "IO error while connecting to "
                            + Arrays.stream(addresses)
                                    .map(address -> address.toString())
                                    .collect(Collectors.joining(","));
            LOGGER.error(error, e);
            throw new RuntimeException(error, e);
        } catch (final TimeoutException e) {
            final String error =
                    "Timeout while connecting to "
                            + Arrays.stream(addresses)
                                    .map(address -> address.toString())
                                    .collect(Collectors.joining(","));
            LOGGER.error(error, e);
            throw new RuntimeException(error, e);
        }
    }

    public Channel getOrCreateChannel(ConnectionType connectionType, String queueOrExchangeName) {
        LOGGER.debug(
                "Accessing the channel for queueOrExchange {} with type {} ",
                queueOrExchangeName,
                connectionType);
        switch (connectionType) {
            case SUBSCRIBER:
                return getOrCreateSubscriberChannel(queueOrExchangeName);

            case PUBLISHER:
                return getOrCreatePublisherChannel(queueOrExchangeName);
            default:
                return null;
        }
    }

    private Channel getOrCreateSubscriberChannel(String queueOrExchangeName) {

        String prefix = SUBSCRIBER + SEPARATOR;
        // Return the existing channel if it's still opened
        Channel subscriberChannel = queueNameToChannel.get(prefix + queueOrExchangeName);
        if (subscriberChannel != null) {
            return subscriberChannel;
        }
        // Channel creation is required
        try {
            synchronized (this) {
                if (subscriberConnection == null) {
                    subscriberConnection = createConnection(SUBSCRIBER);
                }
                LOGGER.debug("Creating a channel for subscriber");
                subscriberChannel = subscriberConnection.createChannel();
                subscriberChannel.addShutdownListener(
                        cause -> {
                            LOGGER.error(
                                    "subscription Channel has been shutdown: {}",
                                    cause.getMessage(),
                                    cause);
                        });
                if (subscriberChannel == null || !subscriberChannel.isOpen()) {
                    throw new RuntimeException("Fail to open  subscription channel");
                }
                queueNameToChannel.putIfAbsent(prefix + queueOrExchangeName, subscriberChannel);
            }
        } catch (final IOException e) {
            throw new RuntimeException(
                    "Cannot open subscription channel on "
                            + Arrays.stream(addresses)
                                    .map(address -> address.toString())
                                    .collect(Collectors.joining(",")),
                    e);
        }

        return subscriberChannel;
    }

    private Channel getOrCreatePublisherChannel(String queueOrExchangeName) {

        String prefix = PUBLISHER + SEPARATOR;
        Channel publisherChannel = queueNameToChannel.get(prefix + queueOrExchangeName);
        if (publisherChannel != null) {
            return publisherChannel;
        }
        // Channel creation is required
        try {

            synchronized (this) {
                if (publisherConnection == null) {
                    publisherConnection = createConnection(PUBLISHER);
                }

                LOGGER.debug("Creating a channel for publisher");
                publisherChannel = publisherConnection.createChannel();
                publisherChannel.addShutdownListener(
                        cause -> {
                            LOGGER.error(
                                    "Publish Channel has been shutdown: {}",
                                    cause.getMessage(),
                                    cause);
                        });

                if (publisherChannel == null || !publisherChannel.isOpen()) {
                    throw new RuntimeException("Fail to open publish channel");
                }
                queueNameToChannel.putIfAbsent(prefix + queueOrExchangeName, publisherChannel);
            }

        } catch (final IOException e) {
            throw new RuntimeException(
                    "Cannot open channel on "
                            + Arrays.stream(addresses)
                                    .map(address -> address.toString())
                                    .collect(Collectors.joining(",")),
                    e);
        }
        return publisherChannel;
    }

    public void close() {
        LOGGER.info("Closing all connections and channels");
        try {
            for (Map.Entry<String, Channel> entry : queueNameToChannel.entrySet()) {
                closeChannel(entry.getValue());
            }
            closeConnection(publisherConnection);
            closeConnection(subscriberConnection);
        } finally {
            queueNameToChannel.clear();
            publisherConnection = null;
            subscriberConnection = null;
        }
    }

    private void closeConnection(Connection connection) {
        if (connection == null) {
            LOGGER.warn("Connection is null. Do not close it");
        } else {
            try {
                connection.close();
            } catch (Exception e) {
                LOGGER.warn("Fail to close connection: {}", e.getMessage(), e);
            }
        }
    }

    private void closeChannel(Channel channel) {
        if (channel == null) {
            LOGGER.warn("Channel is null. Do not close it");
        } else {
            try {
                channel.close();
            } catch (Exception e) {
                LOGGER.warn("Fail to close channel: {}", e.getMessage(), e);
            }
        }
    }
}
