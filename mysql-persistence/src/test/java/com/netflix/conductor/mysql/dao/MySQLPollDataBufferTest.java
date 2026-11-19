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
package com.netflix.conductor.mysql.dao;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.ContextConfiguration;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.mysql.config.MySQLConfiguration;
import com.netflix.conductor.mysql.config.MySQLProperties;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            MySQLConfiguration.class,
            FlywayAutoConfiguration.class
        })
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
public class MySQLPollDataBufferTest {

    @Autowired private DataSource dataSource;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private QueueDAO queueDAO;

    @Autowired private Flyway flyway;

    @Autowired
    @Qualifier("mysqlRetryTemplate")
    private RetryTemplate retryTemplate;

    @BeforeEach
    public void before() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    public void pollDataFlushIntervalShouldDefaultToZero() {
        MySQLProperties properties = new MySQLProperties();

        assertEquals(Duration.ZERO, properties.getPollDataFlushInterval());
    }

    @Test
    public void shouldPersistImmediatelyWhenBufferingIsDisabled() throws SQLException {
        MySQLExecutionDAO dao = createDAO(Duration.ZERO, dataSource);

        dao.updateLastPollData("task-a", "domain-a", "worker-a");

        assertEquals(1, countPollDataRows("task-a", "domain-a"));

        PollData pollData = dao.getPollData("task-a", "domain-a");

        assertNotNull(pollData);
        assertEquals("worker-a", pollData.getWorkerId());
    }

    @Test
    public void shouldBufferUpdatesAndPersistOnlyLatestValue() throws SQLException {
        MySQLExecutionDAO dao = createDAO(Duration.ofSeconds(60), dataSource);

        dao.updateLastPollData("task-a", "domain-a", "worker-old");
        dao.updateLastPollData("task-a", "domain-a", "worker-new");

        // No database writes should happen before the flush.
        assertEquals(0, countPollDataRows("task-a", "domain-a"));

        // Reads must immediately see the latest buffered value.
        PollData buffered = dao.getPollData("task-a", "domain-a");

        assertNotNull(buffered);
        assertEquals("worker-new", buffered.getWorkerId());

        dao.flushData();

        // Only one record should be persisted.
        assertEquals(1, countPollDataRows("task-a", "domain-a"));

        PollData persisted = dao.getPollData("task-a", "domain-a");

        assertNotNull(persisted);
        assertEquals("worker-new", persisted.getWorkerId());
    }

    @Test
    public void shouldMergeBufferedAndPersistedDataWhenReadingByTask() {
        MySQLExecutionDAO immediateDAO = createDAO(Duration.ZERO, dataSource);
        MySQLExecutionDAO bufferedDAO = createDAO(Duration.ofSeconds(60), dataSource);

        // Existing database records.
        immediateDAO.updateLastPollData("task-a", "domain-a", "db-worker-a");
        immediateDAO.updateLastPollData("task-a", "domain-b", "db-worker-b");
        immediateDAO.updateLastPollData("task-b", "domain-x", "db-worker-x");

        // Buffered updates.
        bufferedDAO.updateLastPollData("task-a", "domain-a", "buffer-worker-a");
        bufferedDAO.updateLastPollData("task-a", "domain-c", "buffer-worker-c");

        List<PollData> pollData = bufferedDAO.getPollData("task-a");

        assertEquals(3, pollData.size());

        Map<String, PollData> byDomain =
                pollData.stream()
                        .collect(
                                Collectors.toMap(
                                        PollData::getDomain, pollDataEntry -> pollDataEntry));

        assertEquals("buffer-worker-a", byDomain.get("domain-a").getWorkerId());
        assertEquals("db-worker-b", byDomain.get("domain-b").getWorkerId());
        assertEquals("buffer-worker-c", byDomain.get("domain-c").getWorkerId());
    }

    @Test
    public void shouldMergeBufferedAndPersistedDataWhenReadingAllPollData() {
        MySQLExecutionDAO immediateDAO = createDAO(Duration.ZERO, dataSource);
        MySQLExecutionDAO bufferedDAO = createDAO(Duration.ofSeconds(60), dataSource);

        immediateDAO.updateLastPollData("task-a", "domain-a", "db-worker-a");
        immediateDAO.updateLastPollData("task-b", "domain-b", "db-worker-b");

        bufferedDAO.updateLastPollData("task-a", "domain-a", "buffer-worker-a");
        bufferedDAO.updateLastPollData("task-c", "domain-c", "buffer-worker-c");

        List<PollData> pollData = bufferedDAO.getAllPollData();

        assertEquals(3, pollData.size());

        Map<String, PollData> byKey =
                pollData.stream()
                        .collect(
                                Collectors.toMap(
                                        entry -> entry.getQueueName() + ":" + entry.getDomain(),
                                        entry -> entry));

        assertEquals("buffer-worker-a", byKey.get("task-a:domain-a").getWorkerId());

        assertEquals("db-worker-b", byKey.get("task-b:domain-b").getWorkerId());

        assertEquals("buffer-worker-c", byKey.get("task-c:domain-c").getWorkerId());
    }

    @Test
    public void shouldUseSingleTransactionForBatchFlush() throws SQLException {
        TrackingDataSource trackingDataSource = new TrackingDataSource(dataSource);

        MySQLExecutionDAO dao = createDAO(Duration.ofSeconds(60), trackingDataSource);

        dao.updateLastPollData("task-a", "domain-a", "worker-a");
        dao.updateLastPollData("task-b", "domain-b", "worker-b");
        dao.updateLastPollData("task-c", "domain-c", "worker-c");

        assertEquals(0, trackingDataSource.getCommitCount());

        dao.flushData();

        // All three rows should be written in one transaction.
        assertEquals(1, trackingDataSource.getCommitCount());

        assertEquals(1, countPollDataRows("task-a", "domain-a"));
        assertEquals(1, countPollDataRows("task-b", "domain-b"));
        assertEquals(1, countPollDataRows("task-c", "domain-c"));
    }

    @Test
    public void shouldRetainBufferedEntriesWhenFlushFails() throws SQLException {
        TrackingDataSource trackingDataSource = new TrackingDataSource(dataSource);
        trackingDataSource.failNextCommit();

        MySQLExecutionDAO dao = createDAO(Duration.ofSeconds(60), trackingDataSource);

        dao.updateLastPollData("task-a", "domain-a", "worker-a");
        dao.updateLastPollData("task-b", "domain-b", "worker-b");

        // The first transaction will fail at commit.
        dao.flushData();

        assertEquals(1, trackingDataSource.getCommitCount());

        // The failed transaction must not persist partial data.
        assertEquals(0, countPollDataRows("task-a", "domain-a"));
        assertEquals(0, countPollDataRows("task-b", "domain-b"));

        // Buffered data must remain available.
        assertEquals("worker-a", dao.getPollData("task-a", "domain-a").getWorkerId());

        assertEquals("worker-b", dao.getPollData("task-b", "domain-b").getWorkerId());

        // A subsequent flush should successfully persist the entries.
        dao.flushData();

        assertEquals(2, trackingDataSource.getCommitCount());

        assertEquals(1, countPollDataRows("task-a", "domain-a"));
        assertEquals(1, countPollDataRows("task-b", "domain-b"));
    }

    @Test
    public void shouldPreserveNewerUpdatesArrivingDuringFlush() throws SQLException {
        MySQLExecutionDAO dao = createDAO(Duration.ofSeconds(60), dataSource);

        dao.updateLastPollData("task-a", "domain-a", "worker-old");

        // First flush persists the original update.
        dao.flushData();

        assertEquals("worker-old", dao.getPollData("task-a", "domain-a").getWorkerId());

        // A newer update after the first flush must be buffered independently.
        dao.updateLastPollData("task-a", "domain-a", "worker-new");

        assertEquals("worker-new", dao.getPollData("task-a", "domain-a").getWorkerId());

        dao.flushData();

        assertEquals(1, countPollDataRows("task-a", "domain-a"));

        assertEquals("worker-new", dao.getPollData("task-a", "domain-a").getWorkerId());
    }

    @Test
    public void shouldPreserveNullDomainWhileUsingDefaultDatabaseKey() throws SQLException {
        MySQLExecutionDAO dao = createDAO(Duration.ofSeconds(60), dataSource);

        dao.updateLastPollData("task-a", null, "worker-a");

        PollData buffered = dao.getPollData("task-a", null);

        assertNotNull(buffered);
        assertNull(buffered.getDomain());
        assertEquals("worker-a", buffered.getWorkerId());

        dao.flushData();

        assertEquals(1, countPollDataRows("task-a", "DEFAULT"));

        PollData persisted = dao.getPollData("task-a", null);

        assertNotNull(persisted);
        assertNull(persisted.getDomain());
        assertEquals("worker-a", persisted.getWorkerId());
    }

    @Test
    public void shouldFlushPendingEntriesDuringShutdown() throws SQLException {
        MySQLExecutionDAO dao = createDAO(Duration.ofSeconds(60), dataSource);

        dao.updateLastPollData("task-a", "domain-a", "worker-a");
        dao.updateLastPollData("task-b", "domain-b", "worker-b");

        assertEquals(0, countPollDataRows("task-a", "domain-a"));
        assertEquals(0, countPollDataRows("task-b", "domain-b"));

        dao.shutdown();

        assertEquals(1, countPollDataRows("task-a", "domain-a"));
        assertEquals(1, countPollDataRows("task-b", "domain-b"));
    }

    private MySQLExecutionDAO createDAO(Duration flushInterval, DataSource dataSource) {

        MySQLProperties properties = new MySQLProperties();
        properties.setPollDataFlushInterval(flushInterval);

        return new MySQLExecutionDAO(retryTemplate, objectMapper, dataSource, queueDAO, properties);
    }

    @SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
    private int countPollDataRows(String queueName, String domain) throws SQLException {

        String sql = "SELECT COUNT(*) FROM poll_data WHERE queue_name = ? AND domain = ?";

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, queueName);
            statement.setString(2, domain);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    /**
     * Wraps database connections to track commits and optionally inject a commit failure for
     * testing transaction rollback and retry behavior.
     */
    private static class TrackingDataSource extends DelegatingDataSource {

        private final AtomicInteger commitCount = new AtomicInteger();
        private final AtomicBoolean failNextCommit = new AtomicBoolean();

        TrackingDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        void failNextCommit() {
            failNextCommit.set(true);
        }

        int getCommitCount() {
            return commitCount.get();
        }

        @Override
        @NonNull
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();

            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (ignored, method, args) -> {
                                if ("commit".equals(method.getName())
                                        && (args == null || args.length == 0)) {

                                    commitCount.incrementAndGet();

                                    if (failNextCommit.compareAndSet(true, false)) {
                                        throw new SQLException("Injected commit failure");
                                    }
                                }

                                try {
                                    return method.invoke(connection, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
        }
    }

    @Test
    public void shouldNotLoseUpdatesThatArriveWhileAFlushIsInProgress() throws Exception {
        CountDownLatch flushStartedLatch = new CountDownLatch(1);
        CountDownLatch blockFlushLatch = new CountDownLatch(1);

        DataSource blockingDataSource =
                new DelegatingDataSource(dataSource) {
                    @Override
                    @NonNull
                    public Connection getConnection() throws SQLException {
                        Connection connection = super.getConnection();
                        return (Connection)
                                Proxy.newProxyInstance(
                                        Connection.class.getClassLoader(),
                                        new Class<?>[] {Connection.class},
                                        (ignored, method, args) -> {
                                            if ("commit".equals(method.getName())) {
                                                flushStartedLatch.countDown();
                                                blockFlushLatch.await();
                                            }
                                            try {
                                                return method.invoke(connection, args);
                                            } catch (InvocationTargetException e) {
                                                throw e.getCause();
                                            }
                                        });
                    }
                };

        MySQLExecutionDAO dao = createDAO(Duration.ofSeconds(60), blockingDataSource);

        dao.updateLastPollData("task-a", "domain-a", "worker-old");

        Thread flushThread = new Thread(dao::flushData);
        flushThread.start();
        flushStartedLatch.await();

        dao.updateLastPollData("task-a", "domain-a", "worker-new");

        blockFlushLatch.countDown();
        flushThread.join();

        String sql = "SELECT json_data FROM poll_data WHERE queue_name = ? AND domain = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "task-a");
            stmt.setString(2, "domain-a");
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next());
            PollData rawDbValue = objectMapper.readValue(rs.getString(1), PollData.class);
            assertEquals("worker-old", rawDbValue.getWorkerId());
        }

        assertEquals("worker-new", dao.getPollData("task-a", "domain-a").getWorkerId());

        dao.flushData();

        assertEquals("worker-new", dao.getPollData("task-a", "domain-a").getWorkerId());
    }
}
