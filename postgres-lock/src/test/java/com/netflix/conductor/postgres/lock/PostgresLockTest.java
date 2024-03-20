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
package com.netflix.conductor.postgres.lock;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.postgreslock.lock.PostgresLock;

public class PostgresLockTest {

    private static PostgresLockTestUtil testPostgres;
    private static final PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres"))
                    .withDatabaseName("conductor");
    private static PostgresLock postgresLock;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    public static void setup() throws Exception {
        postgreSQLContainer.start();
        testPostgres = new PostgresLockTestUtil(postgreSQLContainer);
        postgresLock = new PostgresLock(testPostgres.getDataSource());
        jdbcTemplate = new JdbcTemplate(testPostgres.getDataSource());
    }

    @AfterAll
    public static void teardown() throws SQLException {
        testPostgres.getDataSource().getConnection().close();
        postgreSQLContainer.stop();
    }

    @Test
    public void testLockAcquisitionAndRelease() {
        String lockId = "testLock1";
        Assertions.assertTrue(postgresLock.acquireLock(lockId, 500, TimeUnit.MILLISECONDS));

        postgresLock.releaseLock(lockId);

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM locks WHERE lock_id = ?", Integer.class, lockId);
        Assertions.assertEquals(0, count);
    }

    @Test
    public void testExpiredLockCanBeAcquiredAgain() throws Exception {
        String lockId = "testLock2";
        Assertions.assertTrue(postgresLock.acquireLock(lockId, 500, 500, TimeUnit.MILLISECONDS));

        Thread.sleep(1000);

        Assertions.assertTrue(postgresLock.acquireLock(lockId, 500, 500, TimeUnit.MILLISECONDS));

        postgresLock.releaseLock(lockId);
    }

    @Test
    public void testConcurrentLockAcquisition() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        String lockId = "testLock3";

        Future<Boolean> future1 =
                executorService.submit(
                        () -> postgresLock.acquireLock(lockId, 2000, TimeUnit.MILLISECONDS));
        Future<Boolean> future2 =
                executorService.submit(
                        () -> postgresLock.acquireLock(lockId, 2000, TimeUnit.MILLISECONDS));

        Assertions.assertTrue(
                future1.get() ^ future2.get()); // Nur einer der beiden sollte den Lock erfolgreich
        // erwerben

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        postgresLock.releaseLock(lockId);
    }

    @Test
    public void testLockReentryBySameOwner() {
        String lockId = "testLock4";

        Assertions.assertTrue(postgresLock.acquireLock(lockId, 1000, 1000, TimeUnit.MILLISECONDS));

        Assertions.assertTrue(postgresLock.acquireLock(lockId, 1000, 1000, TimeUnit.MILLISECONDS));

        postgresLock.releaseLock(lockId);
    }

    @Test
    public void testLockAcquisitionByAnotherOwnerFails() {
        String lockId = "testLock5";

        Assertions.assertTrue(postgresLock.acquireLock(lockId, 1000, 1000, TimeUnit.MILLISECONDS));

        PostgresLock postgresLock2 = new PostgresLock(testPostgres.getDataSource());
        Assertions.assertFalse(postgresLock2.acquireLock(lockId, 100, 1000, TimeUnit.MILLISECONDS));

        postgresLock.releaseLock(lockId);
    }

    @Test
    public void testLockReleaseByNonOwnerFails() {
        String lockId = "testLock6";

        postgresLock.acquireLock(lockId, 1000, 1000, TimeUnit.MILLISECONDS);

        PostgresLock postgresLock2 = new PostgresLock(testPostgres.getDataSource());
        postgresLock2.releaseLock(lockId);

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM locks WHERE lock_id = ?", Integer.class, lockId);
        Assertions.assertEquals(1, count);

        postgresLock.releaseLock(lockId);
    }

    @Test
    void testLockReentryFailsAfterLeaseTimeExpiredForSameOwner() throws InterruptedException {
        String lockId = "testLock7";

        PostgresLock postgresLock = new PostgresLock(testPostgres.getDataSource());
        Assertions.assertTrue(postgresLock.acquireLock(lockId, 500, 500, TimeUnit.MILLISECONDS));

        Thread.sleep(1000);

        Assertions.assertFalse(postgresLock.acquireLock(lockId, 500, 500, TimeUnit.MILLISECONDS));

        postgresLock.releaseLock(lockId);
    }
}
