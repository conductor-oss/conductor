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
package com.netflix.conductor.postgreslock.lock;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.netflix.conductor.core.sync.Lock;

public class PostgresLock implements Lock {

    private final JdbcTemplate jdbcTemplate;
    private final String lockOwnerId;

    public PostgresLock(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.lockOwnerId = UUID.randomUUID() + "-" + System.currentTimeMillis();
    }

    @Override
    @Transactional
    public void acquireLock(String lockId) {
        acquireLock(lockId, Long.MAX_VALUE, Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    @Override
    @Transactional
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        return acquireLock(lockId, timeToTry, Long.MAX_VALUE, unit);
    }

    @Override
    @Transactional
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeToTry);
        while (System.currentTimeMillis() < endTime) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp leaseExpiration =
                    new Timestamp(System.currentTimeMillis() - unit.toMillis(leaseTime));

            String sql =
                    "INSERT INTO locks(lock_id, owner_id, locked_at) VALUES (?, ?, ?) ON CONFLICT (lock_id) DO UPDATE SET owner_id = ?, locked_at = ? WHERE locks.lock_id = ? AND (locks.owner_id = ? OR locks.locked_at <= ?)";
            int rowsAffected =
                    jdbcTemplate.update(
                            sql,
                            lockId,
                            lockOwnerId,
                            now,
                            lockOwnerId,
                            now,
                            lockId,
                            lockOwnerId,
                            leaseExpiration);
            if (rowsAffected > 0) {
                return true;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void releaseLock(String lockId) {
        String sql = "DELETE FROM locks WHERE lock_id = ? AND owner_id = ?";
        jdbcTemplate.update(sql, lockId, lockOwnerId);
    }

    @Override
    @Transactional
    public void deleteLock(String lockId) {
        releaseLock(lockId);
    }
}
