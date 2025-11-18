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
package com.netflix.conductor.postgres.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.postgres.config.PostgresConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            PostgresConfiguration.class,
            FlywayAutoConfiguration.class
        })
@TestPropertySource(
        properties = {
            "conductor.workflow-execution-lock.type=postgres",
            "spring.flyway.clean-disabled=false",
            "conductor.app.workflow.name-validation.enabled=true"
        })
@SpringBootTest
public class PostgresLockDAOTest {

    @Autowired private PostgresLockDAO postgresLock;

    @Autowired private DataSource dataSource;

    @Autowired private Flyway flyway;

    @Before
    public void before() {
        flyway.migrate(); // Clean and migrate the database before each test.
    }

    @Test
    public void testLockAcquisitionAndRelease() throws SQLException {
        String lockId = UUID.randomUUID().toString();
        Instant beforeAcquisitionTimeUtc = Instant.now();
        long leaseTime = 2000;

        try (var connection = dataSource.getConnection()) {
            assertTrue(
                    postgresLock.acquireLock(lockId, 500, leaseTime, TimeUnit.MILLISECONDS),
                    "Lock acquisition failed");
            Instant afterAcquisitionTimeUtc = Instant.now();

            try (var ps = connection.prepareStatement("SELECT * FROM locks WHERE lock_id = ?")) {
                ps.setString(1, lockId);
                var rs = ps.executeQuery();

                if (rs.next()) {
                    assertEquals(lockId, rs.getString("lock_id"));
                    long leaseExpirationTime = rs.getTimestamp("lease_expiration").getTime();
                    assertTrue(
                            leaseExpirationTime
                                    >= beforeAcquisitionTimeUtc
                                            .plusMillis(leaseTime)
                                            .toEpochMilli(),
                            "Lease expiration is too early");
                    assertTrue(
                            leaseExpirationTime
                                    <= afterAcquisitionTimeUtc.plusMillis(leaseTime).toEpochMilli(),
                            "Lease expiration is too late");
                } else {
                    Assertions.fail("Lock not found in the database");
                }
            }

            postgresLock.releaseLock(lockId);

            try (PreparedStatement ps =
                    connection.prepareStatement("SELECT * FROM locks WHERE lock_id = ?")) {
                ps.setString(1, lockId);
                var rs = ps.executeQuery();
                Assertions.assertFalse(rs.next(), "Lock was not released properly");
            }
        }
    }

    @Test
    public void testExpiredLockCanBeAcquiredAgain() throws InterruptedException {
        String lockId = UUID.randomUUID().toString();
        assertTrue(
                postgresLock.acquireLock(lockId, 500, 500, TimeUnit.MILLISECONDS),
                "First lock acquisition failed");

        Thread.sleep(1000); // Ensure the lock has expired.

        assertTrue(
                postgresLock.acquireLock(lockId, 500, 500, TimeUnit.MILLISECONDS),
                "Lock acquisition after expiration failed");

        postgresLock.releaseLock(lockId);
    }

    @Test
    public void testConcurrentLockAcquisition() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        String lockId = UUID.randomUUID().toString();

        Future<Boolean> future1 =
                executorService.submit(
                        () -> postgresLock.acquireLock(lockId, 2000, TimeUnit.MILLISECONDS));
        Future<Boolean> future2 =
                executorService.submit(
                        () -> postgresLock.acquireLock(lockId, 2000, TimeUnit.MILLISECONDS));

        assertTrue(
                future1.get()
                        ^ future2.get()); // One of the futures should hold the lock, the other
        // should get rejected

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        postgresLock.releaseLock(lockId);
    }

    @Test
    public void testDifferentLockCanBeAcquiredConcurrently() {
        String lockId1 = UUID.randomUUID().toString();
        String lockId2 = UUID.randomUUID().toString();

        assertTrue(postgresLock.acquireLock(lockId1, 2000, 10000, TimeUnit.MILLISECONDS));
        assertTrue(postgresLock.acquireLock(lockId2, 2000, 10000, TimeUnit.MILLISECONDS));
    }
}
