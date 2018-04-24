package com.netflix.conductor.dao.mysql;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Functional interface for {@link Query#executeAndFetch(ResultSetHandler)}.
 * @author mustafa
 */
@FunctionalInterface
public interface ResultSetHandler<R> {
	R apply(ResultSet resultSet) throws SQLException;
}
