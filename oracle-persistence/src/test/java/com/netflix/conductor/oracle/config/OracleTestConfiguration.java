/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.oracle.config;

import java.nio.file.Paths;
import java.time.Duration;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.oracle.constants.OraclePersistenceTestConstants;

import com.zaxxer.hikari.HikariDataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class OracleTestConfiguration {

    private final OracleProperties properties = mock(OracleProperties.class);

    @Bean(OraclePersistenceTestConstants.ORACLE_CONTAINER)
    public OracleContainer oracleContainer() {

        System.setProperty(
                OraclePersistenceTestConstants.ORACLE_JDBC_TIMEZONE_AS_REGION,
                OraclePersistenceTestConstants.FALSE);
        System.setProperty(
                OraclePersistenceTestConstants.ORACLE_JDBC_FAN_ENABLED,
                OraclePersistenceTestConstants.FALSE);

        OracleContainer oracleContainer =
                new OracleContainer(
                        DockerImageName.parse(
                                // "oracleinanutshell/oracle-xe-11g"));
                                OraclePersistenceTestConstants.ORAGE_DOCKER_IMAGE_11G_XE));
        // OraclePersistenceTestConstants.ORAGE_DOCKER_IMAGE_18C_XE)); // To be enabled once Github
        // Actions supports Oracle 18 XE based CICD
        oracleContainer
                .withStartupTimeoutSeconds(900)
                .withConnectTimeoutSeconds(900)
                // .withPassword(OraclePersistenceTestConstants.STRONG_PASSWORD)  // To be enabled
                // once Github Actions supports Oracle 18 XE based CICD
                .withInitScript(OraclePersistenceTestConstants.INIT_SCRIPT);

        starOracleContainer(oracleContainer);

        return oracleContainer;
    }

    private void starOracleContainer(OracleContainer oracleContainer) {
        oracleContainer.start();
    }

    @Bean
    @DependsOn(OraclePersistenceTestConstants.ORACLE_CONTAINER)
    public DataSource dataSource(@Autowired OracleContainer oracleContainer) {
        HikariDataSource dataSource = new HikariDataSource();

        dataSource.setJdbcUrl(
                "jdbc:oracle:thin:@//"
                        + oracleContainer.getHost()
                        + ":"
                        + oracleContainer.getOraclePort()
                        + "/"
                        + oracleContainer.getSid());

        dataSource.setUsername(OraclePersistenceTestConstants.JUNIT_USER);
        dataSource.setPassword(OraclePersistenceTestConstants.JUNIT_USER);

        dataSource.setAutoCommit(false);

        when(properties.getTaskDefCacheRefreshInterval()).thenReturn(Duration.ofSeconds(60));

        dataSource.setMaximumPoolSize(100);
        flywayMigrate(dataSource);

        return dataSource;
    }

    private void flywayMigrate(DataSource dataSource) {

        Flyway flyway = new Flyway();
        flyway.setLocations(Paths.get("db", "migration_oracle").toString());
        flyway.setDataSource(dataSource);
        flyway.migrate();
    }

    @Bean
    public OracleProperties oracleProperties() {
        return properties;
    }
}
