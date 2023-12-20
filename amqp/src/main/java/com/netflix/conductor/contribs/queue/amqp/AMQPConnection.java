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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.contribs.queue.amqp.config.AMQPRetryPattern;
import com.netflix.conductor.contribs.queue.amqp.util.AMQPConstants;
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
    private static final Map<ConnectionType, Set<Channel>> availableChannelPool =
            new ConcurrentHashMap<ConnectionType, Set<Channel>>();
    private static final Map<String, Channel> subscriberReservedChannelPool =
            new ConcurrentHashMap<String, Channel>();
    private static AMQPRetryPattern retrySettings = null;

    private AMQPConnection() {}

    private AMQPConnection(final ConnectionFactory factory, final Address[] address) {
        this.factory = factory;
        this.addresses = address;
    }

    public static synchronized AMQPConnection getInstance(
            final ConnectionFactory factory,
            final Address[] address,
            final AMQPRetryPattern retrySettings) {
        if (AMQPConnection.amqpConnection == null) {
            AMQPConnection.amqpConnection = new AMQPConnection(factory, address);
        }
        AMQPConnection.retrySettings = retrySettings;
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
        int retryIndex = 1;
        while (true) {
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
                AMQPRetryPattern retry = retrySettings;
                if (retry == null) {
                    final String error =
                            "IO error while connecting to "
                                    + Arrays.stream(addresses)
                                            .map(address -> address.toString())
                                            .collect(Collectors.joining(","));
                    LOGGER.error(error, e);
                    throw new RuntimeException(error, e);
                }
                try {
                    retry.continueOrPropogate(e, retryIndex);
                } catch (Exception ex) {
                    final String error =
                            "Retries completed. IO error while connecting to "
                                    + Arrays.stream(addresses)
                                            .map(address -> address.toString())
                                            .collect(Collectors.joining(","));
                    LOGGER.error(error, e);
                    throw new RuntimeException(error, e);
                }
                retryIndex++;
            } catch (final TimeoutException e) {
                AMQPRetryPattern retry = retrySettings;
                if (retry == null) {
                    final String error =
                            "Timeout while connecting to "
                                    + Arrays.stream(addresses)
                                            .map(address -> address.toString())
                                            .collect(Collectors.joining(","));
                    LOGGER.error(error, e);
                    throw new RuntimeException(error, e);
                }
                try {
                    retry.continueOrPropogate(e, retryIndex);
                } catch (Exception ex) {
                    final String error =
                            "Retries completed. Timeout while connecting to "
                                    + Arrays.stream(addresses)
                                            .map(address -> address.toString())
                                            .collect(Collectors.joining(","));
                    LOGGER.error(error, e);
                    throw new RuntimeException(error, e);
                }
                retryIndex++;
            }
        }
    }

    public Channel getOrCreateChannel(ConnectionType connectionType, String queueOrExchangeName)
            throws Exception {
        LOGGER.debug(
                "Accessing the channel for queueOrExchange {} with type {} ",
                queueOrExchangeName,
                connectionType);
        switch (connectionType) {
            case SUBSCRIBER:
                String subChnName = connectionType + ";" + queueOrExchangeName;
                if (subscriberReservedChannelPool.containsKey(subChnName)) {
                    Channel locChn = subscriberReservedChannelPool.get(subChnName);
                    if (locChn != null && locChn.isOpen()) {
                        return locChn;
                    }
                }
                synchronized (this) {
                    if (subscriberConnection == null || !subscriberConnection.isOpen()) {
                        subscriberConnection = createConnection(SUBSCRIBER);
                    }
                }
                Channel subChn = borrowChannel(connectionType, subscriberConnection);
                // Add the subscribed channels to Map to avoid messages being acknowledged on
                // different from the subscribed one
                subscriberReservedChannelPool.put(subChnName, subChn);
                return subChn;
            case PUBLISHER:
                synchronized (this) {
                    if (publisherConnection == null || !publisherConnection.isOpen()) {
                        publisherConnection = createConnection(PUBLISHER);
                    }
                }
                return borrowChannel(connectionType, publisherConnection);
            default:
                return null;
        }
    }

    private Channel getOrCreateChannel(ConnectionType connType, Connection rmqConnection) {
        // Channel creation is required
        Channel locChn = null;
        int retryIndex = 1;
        while (true) {
            try {
                LOGGER.debug("Creating a channel for " + connType);
                locChn = rmqConnection.createChannel();
                if (locChn == null || !locChn.isOpen()) {
                    throw new RuntimeException("Fail to open " + connType + " channel");
                }
                locChn.addShutdownListener(
                        cause -> {
                            LOGGER.error(
                                    connType + " Channel has been shutdown: {}",
                                    cause.getMessage(),
                                    cause);
                        });
                return locChn;
            } catch (final IOException e) {
                AMQPRetryPattern retry = retrySettings;
                if (retry == null) {
                    throw new RuntimeException(
                            "Cannot open "
                                    + connType
                                    + " channel on "
                                    + Arrays.stream(addresses)
                                            .map(address -> address.toString())
                                            .collect(Collectors.joining(",")),
                            e);
                }
                try {
                    retry.continueOrPropogate(e, retryIndex);
                } catch (Exception ex) {
                    throw new RuntimeException(
                            "Retries completed. Cannot open "
                                    + connType
                                    + " channel on "
                                    + Arrays.stream(addresses)
                                            .map(address -> address.toString())
                                            .collect(Collectors.joining(",")),
                            e);
                }
                retryIndex++;
            } catch (final Exception e) {
                AMQPRetryPattern retry = retrySettings;
                if (retry == null) {
                    throw new RuntimeException(
                            "Cannot open "
                                    + connType
                                    + " channel on "
                                    + Arrays.stream(addresses)
                                            .map(address -> address.toString())
                                            .collect(Collectors.joining(",")),
                            e);
                }
                try {
                    retry.continueOrPropogate(e, retryIndex);
                } catch (Exception ex) {
                    throw new RuntimeException(
                            "Retries completed. Cannot open "
                                    + connType
                                    + " channel on "
                                    + Arrays.stream(addresses)
                                            .map(address -> address.toString())
                                            .collect(Collectors.joining(",")),
                            e);
                }
                retryIndex++;
            }
        }
    }

    public void close() {
        LOGGER.info("Closing all connections and channels");
        try {
            closeChannelsInMap(ConnectionType.PUBLISHER);
            closeChannelsInMap(ConnectionType.SUBSCRIBER);
            closeConnection(publisherConnection);
            closeConnection(subscriberConnection);
        } finally {
            availableChannelPool.clear();
            publisherConnection = null;
            subscriberConnection = null;
        }
    }

    private void closeChannelsInMap(ConnectionType conType) {
        Set<Channel> channels = availableChannelPool.get(conType);
        if (channels != null && !channels.isEmpty()) {
            Iterator<Channel> itr = channels.iterator();
            while (itr.hasNext()) {
                Channel channel = itr.next();
                closeChannel(channel);
            }
            channels.clear();
        }
    }

    private void closeConnection(Connection connection) {
        if (connection == null || !connection.isOpen()) {
            LOGGER.warn("Connection is null or closed already. Not closing it again");
        } else {
            try {
                connection.close();
            } catch (Exception e) {
                LOGGER.warn("Fail to close connection: {}", e.getMessage(), e);
            }
        }
    }

    private void closeChannel(Channel channel) {
        if (channel == null || !channel.isOpen()) {
            LOGGER.warn("Channel is null or closed already. Not closing it again");
        } else {
            try {
                channel.close();
            } catch (Exception e) {
                LOGGER.warn("Fail to close channel: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Gets the channel for specified connectionType.
     *
     * @param connectionType holds the multiple channels for different connection types for thread
     *     safe operation.
     * @param rmqConnection publisher or subscriber connection instance
     * @return channel instance
     * @throws Exception
     */
    private synchronized Channel borrowChannel(
            ConnectionType connectionType, Connection rmqConnection) throws Exception {
        if (!availableChannelPool.containsKey(connectionType)) {
            Channel channel = getOrCreateChannel(connectionType, rmqConnection);
            LOGGER.info(String.format(AMQPConstants.INFO_CHANNEL_CREATION_SUCCESS, connectionType));
            return channel;
        }
        Set<Channel> channels = availableChannelPool.get(connectionType);
        if (channels != null && channels.isEmpty()) {
            Channel channel = getOrCreateChannel(connectionType, rmqConnection);
            LOGGER.info(String.format(AMQPConstants.INFO_CHANNEL_CREATION_SUCCESS, connectionType));
            return channel;
        }
        Iterator<Channel> itr = channels.iterator();
        while (itr.hasNext()) {
            Channel channel = itr.next();
            if (channel != null && channel.isOpen()) {
                itr.remove();
                LOGGER.info(
                        String.format(AMQPConstants.INFO_CHANNEL_BORROW_SUCCESS, connectionType));
                return channel;
            } else {
                itr.remove();
            }
        }
        Channel channel = getOrCreateChannel(connectionType, rmqConnection);
        LOGGER.info(String.format(AMQPConstants.INFO_CHANNEL_RESET_SUCCESS, connectionType));
        return channel;
    }

    /**
     * Returns the channel to connection pool for specified connectionType.
     *
     * @param connectionType
     * @param channel
     * @throws Exception
     */
    public synchronized void returnChannel(ConnectionType connectionType, Channel channel)
            throws Exception {
        if (channel == null || !channel.isOpen()) {
            channel = null; // channel is reset.
        }
        Set<Channel> channels = availableChannelPool.get(connectionType);
        if (channels == null) {
            channels = new HashSet<Channel>();
            availableChannelPool.put(connectionType, channels);
        }
        channels.add(channel);
        LOGGER.info(String.format(AMQPConstants.INFO_CHANNEL_RETURN_SUCCESS, connectionType));
    }
}
