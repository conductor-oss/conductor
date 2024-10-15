package com.netflix.conductor.postgres.config;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.Assert.assertTrue;

@ContextConfiguration(
        classes = {
                TestObjectMapperConfiguration.class,
                PostgresConfiguration.class,
                FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@TestPropertySource(
        properties = {
                "conductor.app.asyncIndexingEnabled=false",
                "conductor.elasticsearch.version=0",
                "conductor.indexing.type=postgres",
                "conductor.postgres.applyDataMigrations=false",
                "spring.flyway.clean-disabled=false"
        })
@SpringBootTest
public class PostgresConfigurationDataMigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    ResourcePatternResolver resourcePatternResolver;

    // clean the database between tests.
    @Before
    public void before() {
        flyway.migrate();
    }

    @Test
    public void dataMigrationIsNotAppliedWhenDisabled() throws Exception {
        var files = resourcePatternResolver.getResources("classpath:db/migration_postgres_data/*");
        Arrays.stream(flyway.info().applied()).forEach(migrationInfo ->
            assertTrue("Data migration wrongly applied: " + migrationInfo.getScript(),
                    Arrays.stream(files)
                    .map(Resource::getFilename)
                    .filter(Objects::nonNull)
                    .noneMatch(fileName -> fileName.contains(migrationInfo.getScript()))));
    }
}
