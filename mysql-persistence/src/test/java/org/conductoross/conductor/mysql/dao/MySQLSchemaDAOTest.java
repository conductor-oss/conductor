/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.mysql.dao;

import javax.sql.DataSource;

import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.conductoross.conductor.dao.schema.SchemaDAOTest;
import org.conductoross.conductor.mysql.config.MySQLSchemaRegistryMigration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ContextConfiguration;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.mysql.config.MySQLConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Runs the {@link SchemaDAO} contract against a real MySQL container. */
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            MySQLConfiguration.class,
            FlywayAutoConfiguration.class
        })
@SpringBootTest(properties = "spring.flyway.clean-disabled=true")
public class MySQLSchemaDAOTest extends SchemaDAOTest {

    @Autowired private SchemaDAO schemaDAO;

    @Autowired private DataSource dataSource;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private Environment environment;

    @Autowired
    @Qualifier("mysqlRetryTemplate")
    private RetryTemplate retryTemplate;

    /**
     * Other tests in this module clean the database between their own cases, which drops the
     * registry's tables along with everything else. Re-running the migration here keeps this class
     * independent of the order Gradle happens to run test classes in.
     */
    @BeforeEach
    public void migrateSchemaRegistry() {
        MySQLSchemaRegistryMigration.migrate(dataSource);
    }

    @Override
    protected SchemaDAO getSchemaDAO() {
        return schemaDAO;
    }

    /**
     * A pool of this test's own against the same database, so the re-read crosses a new connection
     * rather than reusing the one the DAO under test holds.
     */
    @Override
    protected SchemaDAO reopenStore() {
        return new MySQLSchemaDAO(retryTemplate, objectMapper, reopenedDataSource());
    }

    private DataSource reopenedDataSource() {
        // The configured URL, not the live connection's own. For the container-backed backends
        // that is a Testcontainers alias, which reuses the container already running for it and
        // supplies its credentials; resolving it to a plain JDBC URL would need credentials this
        // test does not hold.
        return new DriverManagerDataSource(environment.getProperty("spring.datasource.url"));
    }
}
