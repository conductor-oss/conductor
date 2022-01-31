/*
 * Copyright 2022 Netflix, Inc.
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

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.utils.RetryUtil;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.postgres.util.ExecuteFunction;
import com.netflix.conductor.postgres.util.LazyToString;
import com.netflix.conductor.postgres.util.Query;
import com.netflix.conductor.postgres.util.QueryFunction;
import com.netflix.conductor.postgres.util.TransactionalFunction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import static com.netflix.conductor.core.exception.ApplicationException.Code.BACKEND_ERROR;
import static com.netflix.conductor.core.exception.ApplicationException.Code.CONFLICT;
import static com.netflix.conductor.core.exception.ApplicationException.Code.INTERNAL_ERROR;

import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;

public abstract class PostgresBaseDAO {

    private static final String ER_LOCK_DEADLOCK = "40P01";
    private static final String ER_SERIALIZATION_FAILURE = "40001";
    private static final String MAX_RETRY_ON_DEADLOCK_PROPERTY_NAME =
            "conductor.postgres.deadlock.retry.max";
    private static final String MAX_RETRY_ON_DEADLOCK_PROPERTY_DEFAULT_VALUE = "3";
    private static final int MAX_RETRY_ON_DEADLOCK = getMaxRetriesOnDeadLock();
    private static final List<String> EXCLUDED_STACKTRACE_CLASS =
            ImmutableList.of(PostgresBaseDAO.class.getName(), Thread.class.getName());

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper;
    protected final DataSource dataSource;

    protected PostgresBaseDAO(ObjectMapper objectMapper, DataSource dataSource) {
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
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

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApplicationException(INTERNAL_ERROR, ex);
        }
    }

    protected <T> T readValue(String json, Class<T> tClass) {
        try {
            return objectMapper.readValue(json, tClass);
        } catch (IOException ex) {
            throw new ApplicationException(INTERNAL_ERROR, ex);
        }
    }

    protected <T> T readValue(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (IOException ex) {
            throw new ApplicationException(INTERNAL_ERROR, ex);
        }
    }

    /**
     * Initialize a new transactional {@link Connection} from {@link #dataSource} and pass it to
     * {@literal function}.
     *
     * <p>Successful executions of {@literal function} will result in a commit and return of {@link
     * TransactionalFunction#apply(Connection)}.
     *
     * <p>If any {@link Throwable} thrown from {@code TransactionalFunction#apply(Connection)} will
     * result in a rollback of the transaction and will be wrapped in an {@link
     * ApplicationException} if it is not already one.
     *
     * <p>Generally this is used to wrap multiple {@link #execute(Connection, String,
     * ExecuteFunction)} or {@link #query(Connection, String, QueryFunction)} invocations that
     * produce some expected return value.
     *
     * @param function The function to apply with a new transactional {@link Connection}
     * @param <R> The return type.
     * @return The result of {@code TransactionalFunction#apply(Connection)}
     * @throws ApplicationException If any errors occur.
     */
    private <R> R getWithTransaction(final TransactionalFunction<R> function) {
        final Instant start = Instant.now();
        LazyToString callingMethod = getCallingMethod();
        logger.trace("{} : starting transaction", callingMethod);

        try (Connection tx = dataSource.getConnection()) {
            boolean previousAutoCommitMode = tx.getAutoCommit();
            tx.setAutoCommit(false);
            try {
                R result = function.apply(tx);
                tx.commit();
                return result;
            } catch (Throwable th) {
                tx.rollback();
                if (th instanceof ApplicationException) {
                    throw th;
                }
                throw new ApplicationException(BACKEND_ERROR, th.getMessage(), th);
            } finally {
                tx.setAutoCommit(previousAutoCommitMode);
            }
        } catch (SQLException ex) {
            throw new ApplicationException(BACKEND_ERROR, ex.getMessage(), ex);
        } finally {
            logger.trace(
                    "{} : took {}ms",
                    callingMethod,
                    Duration.between(start, Instant.now()).toMillis());
        }
    }

    <R> R getWithRetriedTransactions(final TransactionalFunction<R> function) {
        try {
            return new RetryUtil<R>()
                    .retryOnException(
                            () -> getWithTransaction(function),
                            this::isDeadLockError,
                            null,
                            MAX_RETRY_ON_DEADLOCK,
                            "retry on deadlock",
                            "transactional");
        } catch (RuntimeException e) {
            throw (ApplicationException) e.getCause();
        }
    }

    protected <R> R getWithTransactionWithOutErrorPropagation(TransactionalFunction<R> function) {
        Instant start = Instant.now();
        LazyToString callingMethod = getCallingMethod();
        logger.trace("{} : starting transaction", callingMethod);

        try (Connection tx = dataSource.getConnection()) {
            boolean previousAutoCommitMode = tx.getAutoCommit();
            tx.setAutoCommit(false);
            try {
                R result = function.apply(tx);
                tx.commit();
                return result;
            } catch (Throwable th) {
                tx.rollback();
                logger.info(CONFLICT + " " + th.getMessage());
                return null;
            } finally {
                tx.setAutoCommit(previousAutoCommitMode);
            }
        } catch (SQLException ex) {
            throw new ApplicationException(BACKEND_ERROR, ex.getMessage(), ex);
        } finally {
            logger.trace(
                    "{} : took {}ms",
                    callingMethod,
                    Duration.between(start, Instant.now()).toMillis());
        }
    }

    /**
     * Wraps {@link #getWithRetriedTransactions(TransactionalFunction)} with no return value.
     *
     * <p>Generally this is used to wrap multiple {@link #execute(Connection, String,
     * ExecuteFunction)} or {@link #query(Connection, String, QueryFunction)} invocations that
     * produce no expected return value.
     *
     * @param consumer The {@link Consumer} callback to pass a transactional {@link Connection} to.
     * @throws ApplicationException If any errors occur.
     * @see #getWithRetriedTransactions(TransactionalFunction)
     */
    protected void withTransaction(Consumer<Connection> consumer) {
        getWithRetriedTransactions(
                connection -> {
                    consumer.accept(connection);
                    return null;
                });
    }

    /**
     * Initiate a new transaction and execute a {@link Query} within that context, then return the
     * results of {@literal function}.
     *
     * @param query The query string to prepare.
     * @param function The functional callback to pass a {@link Query} to.
     * @param <R> The expected return type of {@literal function}.
     * @return The results of applying {@literal function}.
     */
    protected <R> R queryWithTransaction(String query, QueryFunction<R> function) {
        return getWithRetriedTransactions(tx -> query(tx, query, function));
    }

    /**
     * Execute a {@link Query} within the context of a given transaction and return the results of
     * {@literal function}.
     *
     * @param tx The transactional {@link Connection} to use.
     * @param query The query string to prepare.
     * @param function The functional callback to pass a {@link Query} to.
     * @param <R> The expected return type of {@literal function}.
     * @return The results of applying {@literal function}.
     */
    protected <R> R query(Connection tx, String query, QueryFunction<R> function) {
        try (Query q = new Query(objectMapper, tx, query)) {
            return function.apply(q);
        } catch (SQLException ex) {
            throw new ApplicationException(BACKEND_ERROR, ex);
        }
    }

    /**
     * Execute a statement with no expected return value within a given transaction.
     *
     * @param tx The transactional {@link Connection} to use.
     * @param query The query string to prepare.
     * @param function The functional callback to pass a {@link Query} to.
     */
    protected void execute(Connection tx, String query, ExecuteFunction function) {
        try (Query q = new Query(objectMapper, tx, query)) {
            function.apply(q);
        } catch (SQLException ex) {
            throw new ApplicationException(BACKEND_ERROR, ex);
        }
    }

    /**
     * Instantiates a new transactional connection and invokes {@link #execute(Connection, String,
     * ExecuteFunction)}
     *
     * @param query The query string to prepare.
     * @param function The functional callback to pass a {@link Query} to.
     */
    protected void executeWithTransaction(String query, ExecuteFunction function) {
        withTransaction(tx -> execute(tx, query, function));
    }

    private boolean isDeadLockError(Throwable throwable) {
        SQLException sqlException = findCauseSQLException(throwable);
        if (sqlException == null) {
            return false;
        }
        return ER_LOCK_DEADLOCK.equals(sqlException.getSQLState())
                || ER_SERIALIZATION_FAILURE.equals(sqlException.getSQLState());
    }

    private SQLException findCauseSQLException(Throwable throwable) {
        Throwable causeException = throwable;
        while (null != causeException && !(causeException instanceof SQLException)) {
            causeException = causeException.getCause();
        }
        return (SQLException) causeException;
    }

    private static int getMaxRetriesOnDeadLock() {
        try {
            return parseInt(
                    getProperty(
                            MAX_RETRY_ON_DEADLOCK_PROPERTY_NAME,
                            MAX_RETRY_ON_DEADLOCK_PROPERTY_DEFAULT_VALUE));
        } catch (Exception e) {
            return parseInt(MAX_RETRY_ON_DEADLOCK_PROPERTY_DEFAULT_VALUE);
        }
    }
}
