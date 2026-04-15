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

import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.core.sync.Lock;

import com.fasterxml.jackson.databind.ObjectMapper;

public class PostgresLockDAO extends PostgresBaseDAO implements Lock {
    private final long DAY_MS = 24 * 60 * 60 * 1000;

    public PostgresLockDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void acquireLock(String lockId) {
        acquireLock(lockId, DAY_MS, DAY_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        return acquireLock(lockId, timeToTry, DAY_MS, unit);
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeToTry);
        while (System.currentTimeMillis() < endTime) {
            var sql =
                    "INSERT INTO locks(lock_id, lease_expiration) VALUES (?, now() + (?::text || ' milliseconds')::interval) ON CONFLICT (lock_id) DO UPDATE SET lease_expiration = EXCLUDED.lease_expiration WHERE locks.lease_expiration <= now()";

            int rowsAffected =
                    queryWithTransaction(
                            sql,
                            q ->
                                    q.addParameter(lockId)
                                            .addParameter(unit.toMillis(leaseTime))
                                            .executeUpdate());

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
    public void releaseLock(String lockId) {
        var sql = "DELETE FROM locks WHERE lock_id = ?";
        queryWithTransaction(sql, q -> q.addParameter(lockId).executeDelete());
    }

    @Override
    public void deleteLock(String lockId) {
        releaseLock(lockId);
    }
}
