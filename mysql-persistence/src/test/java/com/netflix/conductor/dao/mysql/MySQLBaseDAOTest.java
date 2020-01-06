package com.netflix.conductor.dao.mysql;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.config.Configuration;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


@SuppressWarnings("Duplicates")
public class MySQLBaseDAOTest {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final DataSource dataSource;
    protected final TestConfiguration testConfiguration = new TestConfiguration();
    protected final ObjectMapper objectMapper = createObjectMapper();
    protected final EmbeddedDatabase DB = EmbeddedDatabase.INSTANCE;

    MySQLBaseDAOTest() {
        testConfiguration.setProperty("jdbc.url", "jdbc:mysql://localhost:33307/conductor?useSSL=false&useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC");
        testConfiguration.setProperty("jdbc.username", "root");
        testConfiguration.setProperty("jdbc.password", "");
        this.dataSource = getDataSource(testConfiguration);
    }

    private DataSource getDataSource(Configuration config) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getProperty("jdbc.url", "jdbc:mysql://localhost:33307/conductor"));
        dataSource.setUsername(config.getProperty("jdbc.username", "conductor"));
        dataSource.setPassword(config.getProperty("jdbc.password", "password"));
        dataSource.setAutoCommit(false);
        dataSource.setTransactionIsolation("TRANSACTION_READ_COMMITTED");

        // Prevent DB from getting exhausted during rapid testing
        dataSource.setMaximumPoolSize(8);

        if (!EmbeddedDatabase.hasBeenMigrated()) {
            synchronized (EmbeddedDatabase.class) {
                flywayMigrate(dataSource);
                EmbeddedDatabase.setHasBeenMigrated();
            }
        }

        return dataSource;
    }

    private synchronized static void flywayMigrate(DataSource dataSource) {
        if(EmbeddedDatabase.hasBeenMigrated()) {
            return;
        }

        synchronized (MySQLBaseDAOTest.class) {
            Flyway flyway = new Flyway();
            flyway.setDataSource(dataSource);
            flyway.setPlaceholderReplacement(false);
            flyway.migrate();
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new JsonMapperProvider().get();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        om.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        om.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return om;
    }

    protected void resetAllData() {
        logger.info("Resetting data for test");
        try (Connection connection = dataSource.getConnection()) {
            try(ResultSet rs = connection.prepareStatement("SHOW TABLES").executeQuery();
                    PreparedStatement keysOn = connection.prepareStatement("SET FOREIGN_KEY_CHECKS=1")) {
                try(PreparedStatement keysOff = connection.prepareStatement("SET FOREIGN_KEY_CHECKS=0")){
                    keysOff.execute();
                    while(rs.next()) {
                        String table = rs.getString(1);
                        try(PreparedStatement ps = connection.prepareStatement("TRUNCATE TABLE " + table)) {
                            ps.execute();
                        }
                    }
                } finally {
                    keysOn.execute();
                }
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
