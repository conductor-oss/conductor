/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.mysql;

import javax.inject.Singleton;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sql2o.Sql2o;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.zaxxer.hikari.HikariDataSource;

public class MySQLWorkflowModule extends AbstractModule {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	@Provides
	@Singleton
	public Sql2o getSql2o(Configuration config) {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(config.getProperty("jdbc.url", "jdbc:mysql://localhost:3306/conductor"));
		dataSource.setUsername(config.getProperty("jdbc.username", "conductor"));
		dataSource.setPassword(config.getProperty("jdbc.password", "password"));
		dataSource.setAutoCommit(false);
		flywayMigrate(dataSource);
		return new Sql2o(dataSource);
	}

	@Override
	protected void configure() {
		bind(MetadataDAO.class).to(MySQLMetadataDAO.class);
		bind(ExecutionDAO.class).to(MySQLExecutionDAO.class);
		bind(QueueDAO.class).to(MySQLQueueDAO.class);
	}

	private void flywayMigrate(DataSource dataSource) {
		Flyway flyway = new Flyway();
		flyway.setDataSource(dataSource);
		flyway.setPlaceholderReplacement(false);
		flyway.migrate();
	}
}
