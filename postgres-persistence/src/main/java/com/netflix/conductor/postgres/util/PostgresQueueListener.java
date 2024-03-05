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
package com.netflix.conductor.postgres.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.postgres.config.PostgresProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PostgresQueueListener {

    private PGConnection pgconn;

    private volatile Connection conn;

    private final Lock connectionLock = new ReentrantLock();

    private DataSource dataSource;

    private HashMap<String, QueueStats> queues;

    private volatile boolean connected = false;

    private long lastNotificationTime = 0;

    private Integer stalePeriod;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public PostgresQueueListener(DataSource dataSource, PostgresProperties properties) {
        logger.info("Using experimental PostgresQueueListener");
        this.dataSource = dataSource;
        this.stalePeriod = properties.getExperimentalQueueNotifyStalePeriod();
        connect();
    }

    public boolean hasMessagesReady(String queueName) {
        checkUpToDate();
        handleNotifications();
        if (notificationIsStale() || !connected) {
            connect();
            return true;
        }

        QueueStats queueStats = queues.get(queueName);
        if (queueStats == null) {
            return false;
        }

        if (queueStats.getNextDelivery() > System.currentTimeMillis()) {
            return false;
        }

        return true;
    }

    public Optional<Integer> getSize(String queueName) {
        checkUpToDate();
        handleNotifications();
        if (notificationIsStale() || !connected) {
            connect();
            return Optional.empty();
        }

        QueueStats queueStats = queues.get(queueName);
        if (queueStats == null) {
            return Optional.of(0);
        }

        return Optional.of(queueStats.getDepth());
    }

    private boolean notificationIsStale() {
        return System.currentTimeMillis() - lastNotificationTime > this.stalePeriod;
    }

    private void connect() {
        // Attempt to acquire the lock without waiting.
        if (!connectionLock.tryLock()) {
            // If the lock is not available, return early.
            return;
        }

        boolean newConnectedState = false;

        try {
            // Check if the connection is null or not valid.
            if (conn == null || !conn.isValid(1)) {
                // Close the old connection if it exists and is not valid.
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }

                // Establish a new connection.
                try {
                    this.conn = dataSource.getConnection();
                    this.pgconn = conn.unwrap(PGConnection.class);

                    boolean previousAutoCommitMode = conn.getAutoCommit();
                    conn.setAutoCommit(true);
                    try {
                        conn.prepareStatement("LISTEN conductor_queue_state").execute();
                        newConnectedState = true;
                    } catch (Throwable th) {
                        conn.rollback();
                        logger.error(th.getMessage());
                    } finally {
                        conn.setAutoCommit(previousAutoCommitMode);
                    }
                    requestStats();
                } catch (SQLException e) {
                    throw new NonTransientException(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            throw new NonTransientException(e.getMessage(), e);
        } finally {
            connected = newConnectedState;
            // Ensure the lock is always released.
            connectionLock.unlock();
        }
    }

    private void requestStats() {
        try {
            boolean previousAutoCommitMode = conn.getAutoCommit();
            conn.setAutoCommit(true);
            try {
                conn.prepareStatement("SELECT queue_notify()").execute();
                connected = true;
            } catch (Throwable th) {
                conn.rollback();
                logger.error(th.getMessage());
            } finally {
                conn.setAutoCommit(previousAutoCommitMode);
            }
        } catch (SQLException e) {
            if (!e.getSQLState().equals("08003")) {
                logger.error("Error fetching notifications {}", e.getSQLState());
            }
            connect();
        }
    }

    private void checkUpToDate() {
        if (System.currentTimeMillis() - lastNotificationTime > this.stalePeriod * 0.75) {
            requestStats();
        }
    }

    private void handleNotifications() {
        try {
            PGNotification[] notifications = pgconn.getNotifications();
            if (notifications == null || notifications.length == 0) {
                return;
            }
            processPayload(notifications[notifications.length - 1].getParameter());
        } catch (SQLException e) {
            if (e.getSQLState() != "08003") {
                logger.error("Error fetching notifications {}", e.getSQLState());
            }
            connect();
        }
    }

    private void processPayload(String payload) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode notification = objectMapper.readTree(payload);
            JsonNode lastNotificationTime = notification.get("__now__");
            if (lastNotificationTime != null) {
                this.lastNotificationTime = lastNotificationTime.asLong();
            }
            Iterator<String> iterator = notification.fieldNames();

            HashMap<String, QueueStats> queueStats = new HashMap<>();
            iterator.forEachRemaining(
                    key -> {
                        if (!key.equals("__now__")) {
                            try {
                                QueueStats stats =
                                        objectMapper.treeToValue(
                                                notification.get(key), QueueStats.class);
                                queueStats.put(key, stats);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
            this.queues = queueStats;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
