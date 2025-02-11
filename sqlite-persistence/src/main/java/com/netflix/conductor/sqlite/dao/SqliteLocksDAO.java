package com.netflix.conductor.sqlite.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.core.sync.Lock;
import org.springframework.retry.support.RetryTemplate;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

public class SqliteLocksDAO extends SqliteBaseDAO implements Lock {

    public SqliteLocksDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        super(retryTemplate, objectMapper, dataSource);
    }


    @Override
    public void acquireLock(String lockId) {

    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, TimeUnit unit) {
        return false;
    }

    @Override
    public boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit) {
        return false;
    }

    @Override
    public void releaseLock(String lockId) {

    }

    @Override
    public void deleteLock(String lockId) {

    }
}
