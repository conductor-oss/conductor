/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.mysql.util;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.exception.ApplicationException.Code;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a {@link PreparedStatement} that is wrapped with convenience methods and utilities.
 *
 * <p>This class simulates a parameter building pattern and all {@literal addParameter(*)} methods
 * must be called in the proper order of their expected binding sequence.
 *
 * @author mustafa
 */
public class Query implements AutoCloseable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** The {@link ObjectMapper} instance to use for serializing/deserializing JSON. */
    protected final ObjectMapper objectMapper;

    /** The initial supplied query String that was used to prepare {@link #statement}. */
    private final String rawQuery;

    /**
     * Parameter index for the {@code ResultSet#set*(*)} methods, gets incremented every time a
     * parameter is added to the {@code PreparedStatement} {@link #statement}.
     */
    private final AtomicInteger index = new AtomicInteger(1);

    /** The {@link PreparedStatement} that will be managed and executed by this class. */
    private final PreparedStatement statement;

    public Query(ObjectMapper objectMapper, Connection connection, String query) {
        this.rawQuery = query;
        this.objectMapper = objectMapper;

        try {
            this.statement = connection.prepareStatement(query);
        } catch (SQLException ex) {
            throw new ApplicationException(
                    Code.BACKEND_ERROR,
                    "Cannot prepare statement for query: " + ex.getMessage(),
                    ex);
        }
    }

    /**
     * Generate a String with {@literal count} number of '?' placeholders for {@link
     * PreparedStatement} queries.
     *
     * @param count The number of '?' chars to generate.
     * @return a comma delimited string of {@literal count} '?' binding placeholders.
     */
    public static String generateInBindings(int count) {
        String[] questions = new String[count];
        for (int i = 0; i < count; i++) {
            questions[i] = "?";
        }

        return String.join(", ", questions);
    }

    public Query addParameter(final String value) {
        return addParameterInternal((ps, idx) -> ps.setString(idx, value));
    }

    public Query addParameter(final int value) {
        return addParameterInternal((ps, idx) -> ps.setInt(idx, value));
    }

    public Query addParameter(final boolean value) {
        return addParameterInternal(((ps, idx) -> ps.setBoolean(idx, value)));
    }

    public Query addParameter(final long value) {
        return addParameterInternal((ps, idx) -> ps.setLong(idx, value));
    }

    public Query addParameter(final double value) {
        return addParameterInternal((ps, idx) -> ps.setDouble(idx, value));
    }

    public Query addParameter(Date date) {
        return addParameterInternal((ps, idx) -> ps.setDate(idx, date));
    }

    public Query addParameter(Timestamp timestamp) {
        return addParameterInternal((ps, idx) -> ps.setTimestamp(idx, timestamp));
    }

    /**
     * Serializes {@literal value} to a JSON string for persistence.
     *
     * @param value The value to serialize.
     * @return {@literal this}
     */
    public Query addJsonParameter(Object value) {
        return addParameter(toJson(value));
    }

    /**
     * Bind the given {@link java.util.Date} to the PreparedStatement as a {@link java.sql.Date}.
     *
     * @param date The {@literal java.util.Date} to bind.
     * @return {@literal this}
     */
    public Query addDateParameter(java.util.Date date) {
        return addParameter(new Date(date.getTime()));
    }

    /**
     * Bind the given {@link java.util.Date} to the PreparedStatement as a {@link
     * java.sql.Timestamp}.
     *
     * @param date The {@literal java.util.Date} to bind.
     * @return {@literal this}
     */
    public Query addTimestampParameter(java.util.Date date) {
        return addParameter(new Timestamp(date.getTime()));
    }

    /**
     * Bind the given epoch millis to the PreparedStatement as a {@link java.sql.Timestamp}.
     *
     * @param epochMillis The epoch ms to create a new {@literal Timestamp} from.
     * @return {@literal this}
     */
    public Query addTimestampParameter(long epochMillis) {
        return addParameter(new Timestamp(epochMillis));
    }

    /**
     * Add a collection of primitive values at once, in the order of the collection.
     *
     * @param values The values to bind to the prepared statement.
     * @return {@literal this}
     * @throws IllegalArgumentException If a non-primitive/unsupported type is encountered in the
     *     collection.
     * @see #addParameters(Object...)
     */
    public Query addParameters(Collection values) {
        return addParameters(values.toArray());
    }

    /**
     * Add many primitive values at once.
     *
     * @param values The values to bind to the prepared statement.
     * @return {@literal this}
     * @throws IllegalArgumentException If a non-primitive/unsupported type is encountered.
     */
    public Query addParameters(Object... values) {
        for (Object v : values) {
            if (v instanceof String) {
                addParameter((String) v);
            } else if (v instanceof Integer) {
                addParameter((Integer) v);
            } else if (v instanceof Long) {
                addParameter((Long) v);
            } else if (v instanceof Double) {
                addParameter((Double) v);
            } else if (v instanceof Boolean) {
                addParameter((Boolean) v);
            } else if (v instanceof Date) {
                addParameter((Date) v);
            } else if (v instanceof Timestamp) {
                addParameter((Timestamp) v);
            } else {
                throw new IllegalArgumentException(
                        "Type "
                                + v.getClass().getName()
                                + " is not supported by automatic property assignment");
            }
        }

        return this;
    }

    /**
     * Utility method for evaluating the prepared statement as a query to check the existence of a
     * record using a numeric count or boolean return value.
     *
     * <p>The {@link #rawQuery} provided must result in a {@link Number} or {@link Boolean} result.
     *
     * @return {@literal true} If a count query returned more than 0 or an exists query returns
     *     {@literal true}.
     * @throws ApplicationException If an unexpected return type cannot be evaluated to a {@code
     *     Boolean} result.
     */
    public boolean exists() {
        Object val = executeScalar();
        if (null == val) {
            return false;
        }

        if (val instanceof Number) {
            return convertLong(val) > 0;
        }

        if (val instanceof Boolean) {
            return (Boolean) val;
        }

        if (val instanceof String) {
            return convertBoolean(val);
        }

        throw new ApplicationException(
                Code.BACKEND_ERROR,
                "Expected a Numeric or Boolean scalar return value from the query, received "
                        + val.getClass().getName());
    }

    /**
     * Convenience method for executing delete statements.
     *
     * @return {@literal true} if the statement affected 1 or more rows.
     * @see #executeUpdate()
     */
    public boolean executeDelete() {
        int count = executeUpdate();
        if (count > 1) {
            logger.trace("Removed {} row(s) for query {}", count, rawQuery);
        }

        return count > 0;
    }

    /**
     * Convenience method for executing statements that return a single numeric value, typically
     * {@literal SELECT COUNT...} style queries.
     *
     * @return The result of the query as a {@literal long}.
     */
    public long executeCount() {
        return executeScalar(Long.class);
    }

    /** @return The result of {@link PreparedStatement#executeUpdate()} */
    public int executeUpdate() {
        try {

            Long start = null;
            if (logger.isTraceEnabled()) {
                start = System.currentTimeMillis();
            }

            final int val = this.statement.executeUpdate();

            if (null != start && logger.isTraceEnabled()) {
                long end = System.currentTimeMillis();
                logger.trace("[{}ms] {}: {}", (end - start), val, rawQuery);
            }

            return val;
        } catch (SQLException ex) {
            throw new ApplicationException(Code.BACKEND_ERROR, ex.getMessage(), ex);
        }
    }

    /**
     * Execute a query from the PreparedStatement and return the ResultSet.
     *
     * <p><em>NOTE:</em> The returned ResultSet must be closed/managed by the calling methods.
     *
     * @return {@link PreparedStatement#executeQuery()}
     * @throws ApplicationException If any SQL errors occur.
     */
    public ResultSet executeQuery() {
        Long start = null;
        if (logger.isTraceEnabled()) {
            start = System.currentTimeMillis();
        }

        try {
            return this.statement.executeQuery();
        } catch (SQLException ex) {
            throw new ApplicationException(Code.BACKEND_ERROR, ex);
        } finally {
            if (null != start && logger.isTraceEnabled()) {
                long end = System.currentTimeMillis();
                logger.trace("[{}ms] {}", (end - start), rawQuery);
            }
        }
    }

    /** @return The single result of the query as an Object. */
    public Object executeScalar() {
        try (ResultSet rs = executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return rs.getObject(1);
        } catch (SQLException ex) {
            throw new ApplicationException(Code.BACKEND_ERROR, ex);
        }
    }

    /**
     * Execute the PreparedStatement and return a single 'primitive' value from the ResultSet.
     *
     * @param returnType The type to return.
     * @param <V> The type parameter to return a List of.
     * @return A single result from the execution of the statement, as a type of {@literal
     *     returnType}.
     * @throws ApplicationException {@literal returnType} is unsupported, cannot be cast to from the
     *     result, or any SQL errors occur.
     */
    public <V> V executeScalar(Class<V> returnType) {
        try (ResultSet rs = executeQuery()) {
            if (!rs.next()) {
                Object value = null;
                if (Integer.class == returnType) {
                    value = 0;
                } else if (Long.class == returnType) {
                    value = 0L;
                } else if (Boolean.class == returnType) {
                    value = false;
                }
                return returnType.cast(value);
            } else {
                return getScalarFromResultSet(rs, returnType);
            }
        } catch (SQLException ex) {
            throw new ApplicationException(Code.BACKEND_ERROR, ex);
        }
    }

    /**
     * Execute the PreparedStatement and return a List of 'primitive' values from the ResultSet.
     *
     * @param returnType The type Class return a List of.
     * @param <V> The type parameter to return a List of.
     * @return A {@code List<returnType>}.
     * @throws ApplicationException {@literal returnType} is unsupported, cannot be cast to from the
     *     result, or any SQL errors occur.
     */
    public <V> List<V> executeScalarList(Class<V> returnType) {
        try (ResultSet rs = executeQuery()) {
            List<V> values = new ArrayList<>();
            while (rs.next()) {
                values.add(getScalarFromResultSet(rs, returnType));
            }
            return values;
        } catch (SQLException ex) {
            throw new ApplicationException(Code.BACKEND_ERROR, ex);
        }
    }

    /**
     * Execute the statement and return only the first record from the result set.
     *
     * @param returnType The Class to return.
     * @param <V> The type parameter.
     * @return An instance of {@literal <V>} from the result set.
     */
    public <V> V executeAndFetchFirst(Class<V> returnType) {
        Object o = executeScalar();
        if (null == o) {
            return null;
        }
        return convert(o, returnType);
    }

    /**
     * Execute the PreparedStatement and return a List of {@literal returnType} values from the
     * ResultSet.
     *
     * @param returnType The type Class return a List of.
     * @param <V> The type parameter to return a List of.
     * @return A {@code List<returnType>}.
     * @throws ApplicationException {@literal returnType} is unsupported, cannot be cast to from the
     *     result, or any SQL errors occur.
     */
    public <V> List<V> executeAndFetch(Class<V> returnType) {
        try (ResultSet rs = executeQuery()) {
            List<V> list = new ArrayList<>();
            while (rs.next()) {
                list.add(convert(rs.getObject(1), returnType));
            }
            return list;
        } catch (SQLException ex) {
            throw new ApplicationException(Code.BACKEND_ERROR, ex);
        }
    }

    /**
     * Execute the query and pass the {@link ResultSet} to the given handler.
     *
     * @param handler The {@link ResultSetHandler} to execute.
     * @param <V> The return type of this method.
     * @return The results of {@link ResultSetHandler#apply(ResultSet)}.
     */
    public <V> V executeAndFetch(ResultSetHandler<V> handler) {
        try (ResultSet rs = executeQuery()) {
            return handler.apply(rs);
        } catch (SQLException ex) {
            throw new ApplicationException(Code.BACKEND_ERROR, ex);
        }
    }

    @Override
    public void close() {
        try {
            if (null != statement && !statement.isClosed()) {
                statement.close();
            }
        } catch (SQLException ex) {
            logger.warn("Error closing prepared statement: {}", ex.getMessage());
        }
    }

    protected final Query addParameterInternal(InternalParameterSetter setter) {
        int index = getAndIncrementIndex();
        try {
            setter.apply(this.statement, index);
            return this;
        } catch (SQLException ex) {
            throw new ApplicationException(
                    Code.BACKEND_ERROR, "Could not apply bind parameter at index " + index, ex);
        }
    }

    protected <V> V getScalarFromResultSet(ResultSet rs, Class<V> returnType) throws SQLException {
        Object value = null;

        if (Integer.class == returnType) {
            value = rs.getInt(1);
        } else if (Long.class == returnType) {
            value = rs.getLong(1);
        } else if (String.class == returnType) {
            value = rs.getString(1);
        } else if (Boolean.class == returnType) {
            value = rs.getBoolean(1);
        } else if (Double.class == returnType) {
            value = rs.getDouble(1);
        } else if (Date.class == returnType) {
            value = rs.getDate(1);
        } else if (Timestamp.class == returnType) {
            value = rs.getTimestamp(1);
        } else {
            value = rs.getObject(1);
        }

        if (null == value) {
            throw new NullPointerException(
                    "Cannot get value from ResultSet of type " + returnType.getName());
        }

        return returnType.cast(value);
    }

    protected <V> V convert(Object value, Class<V> returnType) {
        if (Boolean.class == returnType) {
            return returnType.cast(convertBoolean(value));
        } else if (Integer.class == returnType) {
            return returnType.cast(convertInt(value));
        } else if (Long.class == returnType) {
            return returnType.cast(convertLong(value));
        } else if (Double.class == returnType) {
            return returnType.cast(convertDouble(value));
        } else if (String.class == returnType) {
            return returnType.cast(convertString(value));
        } else if (value instanceof String) {
            return fromJson((String) value, returnType);
        }

        final String vName = value.getClass().getName();
        final String rName = returnType.getName();
        throw new ApplicationException(
                Code.BACKEND_ERROR, "Cannot convert type " + vName + " to " + rName);
    }

    protected Integer convertInt(Object value) {
        if (null == value) {
            return null;
        }

        if (value instanceof Integer) {
            return (Integer) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        return NumberUtils.toInt(value.toString());
    }

    protected Double convertDouble(Object value) {
        if (null == value) {
            return null;
        }

        if (value instanceof Double) {
            return (Double) value;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        return NumberUtils.toDouble(value.toString());
    }

    protected Long convertLong(Object value) {
        if (null == value) {
            return null;
        }

        if (value instanceof Long) {
            return (Long) value;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return NumberUtils.toLong(value.toString());
    }

    protected String convertString(Object value) {
        if (null == value) {
            return null;
        }

        if (value instanceof String) {
            return (String) value;
        }

        return value.toString().trim();
    }

    protected Boolean convertBoolean(Object value) {
        if (null == value) {
            return null;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }

        String text = value.toString().trim();
        return "Y".equalsIgnoreCase(text)
                || "YES".equalsIgnoreCase(text)
                || "TRUE".equalsIgnoreCase(text)
                || "T".equalsIgnoreCase(text)
                || "1".equalsIgnoreCase(text);
    }

    protected String toJson(Object value) {
        if (null == value) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ApplicationException(Code.BACKEND_ERROR, ex);
        }
    }

    protected <V> V fromJson(String value, Class<V> returnType) {
        if (null == value) {
            return null;
        }

        try {
            return objectMapper.readValue(value, returnType);
        } catch (IOException ex) {
            throw new ApplicationException(
                    Code.BACKEND_ERROR,
                    "Could not convert JSON '" + value + "' to " + returnType.getName(),
                    ex);
        }
    }

    protected final int getIndex() {
        return index.get();
    }

    protected final int getAndIncrementIndex() {
        return index.getAndIncrement();
    }

    @FunctionalInterface
    private interface InternalParameterSetter {

        void apply(PreparedStatement ps, int idx) throws SQLException;
    }
}
