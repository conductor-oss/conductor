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

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.sqlite.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class SqliteBaseDAO {

    private static final List<String> EXCLUDED_STACKTRACE_CLASS =
            List.of(SqliteBaseDAO.class.getName(), Thread.class.getName());

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper;
    protected final DataSource dataSource;

    private final RetryTemplate retryTemplate;

    protected SqliteBaseDAO(
            RetryTemplate retryTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        this.retryTemplate = retryTemplate;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new NonTransientException(ex.getMessage(), ex);
        }
    }

    protected <T> T readValue(String json, Class<T> tClass) {
        try {
            return objectMapper.readValue(json, tClass);
        } catch (IOException ex) {
            throw new NonTransientException(ex.getMessage(), ex);
        }
    }

    protected <T> T readValue(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (IOException ex) {
            throw new NonTransientException(ex.getMessage(), ex);
        }
    }

    private <R> R getWithTransaction(final TransactionalFunction<R> function) {
        final Instant start = Instant.now();
        LazyToString callingMethod = getCallingMethod();
        logger.trace("{} : starting transaction", callingMethod);

        try (Connection tx = dataSource.getConnection()) {
            boolean previousAutoCommitMode = tx.getAutoCommit();
            tx.setAutoCommit(false);
            tx.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            try {
                // Enable foreign keys for SQLite
                try (Statement stmt = tx.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }

                R result = function.apply(tx);
                tx.commit();
                return result;
            } catch (Throwable th) {
                tx.rollback();
                if (th instanceof NonTransientException) {
                    throw th;
                }
                throw new NonTransientException(th.getMessage(), th);
            } finally {
                tx.setAutoCommit(previousAutoCommitMode);
            }
        } catch (SQLException ex) {
            throw new NonTransientException(ex.getMessage(), ex);
        } finally {
            logger.trace(
                    "{} : took {}ms",
                    callingMethod,
                    Duration.between(start, Instant.now()).toMillis());
        }
    }

    protected <R> R getWithRetriedTransactions(final TransactionalFunction<R> function) {
        try {
            return retryTemplate.execute(context -> getWithTransaction(function));
        } catch (Exception e) {
            throw new NonTransientException(e.getMessage(), e);
        }
    }

    protected <R> R getWithTransactionWithOutErrorPropagation(TransactionalFunction<R> function) {
        Instant start = Instant.now();
        LazyToString callingMethod = getCallingMethod();
        logger.trace("{} : starting transaction", callingMethod);

        try (Connection tx = dataSource.getConnection()) {
            boolean previousAutoCommitMode = tx.getAutoCommit();
            tx.setAutoCommit(false);
            tx.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            try {
                // Enable foreign keys for SQLite
                try (Statement stmt = tx.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }

                R result = function.apply(tx);
                tx.commit();
                return result;
            } catch (Throwable th) {
                tx.rollback();
                logger.info(th.getMessage());
                return null;
            } finally {
                tx.setAutoCommit(previousAutoCommitMode);
            }
        } catch (SQLException ex) {
            throw new NonTransientException(ex.getMessage(), ex);
        } finally {
            logger.trace(
                    "{} : took {}ms",
                    callingMethod,
                    Duration.between(start, Instant.now()).toMillis());
        }
    }

    protected void withTransaction(Consumer<Connection> consumer) {
        getWithRetriedTransactions(
                connection -> {
                    consumer.accept(connection);
                    return null;
                });
    }

    protected <R> R queryWithTransaction(String query, QueryFunction<R> function) {
        return getWithRetriedTransactions(tx -> query(tx, query, function));
    }

    protected <R> R query(Connection tx, String query, QueryFunction<R> function) {
        try (Query q = new Query(objectMapper, tx, query)) {
            return function.apply(q);
        } catch (SQLException ex) {
            throw new NonTransientException(ex.getMessage(), ex);
        }
    }

    protected void execute(Connection tx, String query, ExecuteFunction function) {
        try (Query q = new Query(objectMapper, tx, query)) {
            function.apply(q);
        } catch (SQLException ex) {
            throw new NonTransientException(ex.getMessage(), ex);
        }
    }

    protected void executeWithTransaction(String query, ExecuteFunction function) {
        withTransaction(tx -> execute(tx, query, function));
    }

    protected final LazyToString getCallingMethod() {
        return new LazyToString(
                () ->
                        Arrays.stream(Thread.currentThread().getStackTrace())
                                .filter(
                                        ste ->
                                                !EXCLUDED_STACKTRACE_CLASS.contains(
                                                        ste.getClassName()))
                                .findFirst()
                                .map(StackTraceElement::getMethodName)
                                .orElseThrow(() -> new NullPointerException("Cannot find Caller")));
    }
}
