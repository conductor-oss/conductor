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
package com.netflix.conductor.oracle.dao;

import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.core.sync.Lock;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OracleLockDAO extends OracleBaseDAO implements Lock {
    private final long DAY_MS = 24 * 60 * 60 * 1000;

    public OracleLockDAO(
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
            StringBuilder sqlBuilder = new StringBuilder("MERGE INTO locks l ");
            sqlBuilder.append(
                    "USING ( SELECT ? AS lock_id, CURRENT_TIMESTAMP + NUMTODSINTERVAL(?, 'MILLISECOND') AS lease_expiration ) src ");
            sqlBuilder.append("ON (l.lock_id = src.lock_id) ");
            sqlBuilder.append("WHEN MATCHED AND l.lease_expiration <= CURRENT_TIMESTAMP THEN ");
            sqlBuilder.append("UPDATE SET l.lease_expiration = src.lease_expiration ");
            sqlBuilder.append("WHEN NOT MATCHED THEN ");
            sqlBuilder.append(
                    "INSERT (lock_id, lease_expiration) VALUES (src.lock_id, src.lease_expiration) ");
            var sql = sqlBuilder.toString();
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
