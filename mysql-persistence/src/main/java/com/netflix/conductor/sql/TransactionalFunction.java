package com.netflix.conductor.sql;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional interface for operations within a transactional context.
 *
 * @author mustafa
 */
@FunctionalInterface
public interface TransactionalFunction<R> {
	R apply(Connection tx) throws SQLException;
}
