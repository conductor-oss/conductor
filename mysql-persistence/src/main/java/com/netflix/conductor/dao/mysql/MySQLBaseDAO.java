package com.netflix.conductor.dao.mysql;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sql2o.Connection;
import org.sql2o.Sql2o;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

abstract class MySQLBaseDAO {

	private static final List<String> EXCLUDED_STACKTRACE_CLASS = ImmutableList.of("com.netflix.conductor.dao.mysql.MySQLBaseDAO", "java.lang.Thread");

	protected final Sql2o sql2o;
	protected final ObjectMapper om;
	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected MySQLBaseDAO(ObjectMapper om, Sql2o sql2o) {
		this.om = om;
		this.sql2o = sql2o;
	}

	protected String toJson(Object value) {
		try {
			return om.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	protected <T>T readValue(String json, Class<T> clazz) {
		try {
			return om.readValue(json, clazz);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected <R> R getWithTransaction(Function<Connection, R> function) {
		Instant start = Instant.now();
		StackTraceElement caller = Arrays.stream(Thread.currentThread().getStackTrace()).filter(ste -> !EXCLUDED_STACKTRACE_CLASS.contains(ste.getClassName())).findFirst().get();
		logger.debug("{} : starting transaction", caller.getMethodName());
		try (Connection connection = sql2o.beginTransaction(TRANSACTION_READ_COMMITTED)) {
			final R result = function.apply(connection);
			connection.commit();
			return result;
		} finally {
			Instant end = Instant.now();
			logger.debug("{} : took {}ms", caller.getMethodName(), Duration.between(start, end).toMillis());
		}
	}

	protected void withTransaction(Consumer<Connection> consumer) {
		getWithTransaction(connection -> {
			consumer.accept(connection);
			return null;
		});
	}

	/**
	 * This will inject a series of p1, p2, ... placeholders in the given query template so it can then be used
	 * in conjunction with the withParams method on the Sql2o Query object.
	 *
	 * The withParams method in the Query class loops through each element in the given array and adds a prepared statement for each.
	 * For each element found in the array, a pN placeholder should exists in the query.
	 *
	 * This is useful for generating the IN clause since Sql2o does not support passing directly a list
	 *
	 * @param queryTemplate a query template with a %s placeholder where the variable size parameters placeholders should be injected
	 * @param numPlaceholders the number of placeholders to generated
	 * @return
	 */
	protected String generateQueryWithParametersListPlaceholders(String queryTemplate, int numPlaceholders) {
		String paramsPlaceholders = String.join(",", IntStream.rangeClosed(1, numPlaceholders).mapToObj(paramNumber -> ":p" + paramNumber).collect(Collectors.toList()));
		return String.format(queryTemplate, paramsPlaceholders);
	}
}
