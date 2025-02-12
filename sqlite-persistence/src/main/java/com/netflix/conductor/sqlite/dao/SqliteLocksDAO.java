/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.sqlite.dao;

import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.core.sync.Lock;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SqliteLocksDAO extends SqliteBaseDAO implements Lock {

    public SqliteLocksDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }

    @Override
    public void acquireLock(String lockId) {}

    @Override
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        return false;
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        return false;
    }

    @Override
    public void releaseLock(String lockId) {}

    @Override
    public void deleteLock(String lockId) {}
}
