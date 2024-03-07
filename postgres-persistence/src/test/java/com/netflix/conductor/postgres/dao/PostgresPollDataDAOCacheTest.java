/*
 * Copyright 2023 Conductor Authors.
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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.postgres.config.PostgresConfiguration;
import com.netflix.conductor.postgres.util.Query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

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
            "conductor.postgres.pollDataFlushInterval=200",
            "conductor.postgres.pollDataCacheValidityPeriod=100",
            "spring.flyway.clean-disabled=false"
        })
@SpringBootTest
public class PostgresPollDataDAOCacheTest {

    @Autowired private PollDataDAO pollDataDAO;

    @Autowired private ObjectMapper objectMapper;

    @Qualifier("dataSource")
    @Autowired
    private DataSource dataSource;

    @Autowired Flyway flyway;

    // clean the database between tests.
    @Before
    public void before() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            conn.prepareStatement("truncate table poll_data").executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private List<Map<String, Object>> queryDb(String query) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            try (Query q = new Query(objectMapper, c, query)) {
                return q.executeAndFetchMap();
            }
        }
    }

    private void waitForCacheFlush() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long lastDiff =
                System.currentTimeMillis() - ((PostgresPollDataDAO) pollDataDAO).getLastFlushTime();

        if (lastDiff == 0) {
            return;
        }

        while (true) {
            long currentDiff =
                    System.currentTimeMillis()
                            - ((PostgresPollDataDAO) pollDataDAO).getLastFlushTime();

            if (currentDiff < lastDiff || System.currentTimeMillis() - startTime > 1000) {
                return;
            }

            lastDiff = currentDiff;

            Thread.sleep(1);
        }
    }

    @Test
    public void cacheFlushTest()
            throws SQLException, JsonProcessingException, InterruptedException {
        waitForCacheFlush();
        pollDataDAO.updateLastPollData("dummy-task", "dummy-domain", "dummy-worker-id");

        List<Map<String, Object>> records =
                queryDb("SELECT * FROM poll_data WHERE queue_name = 'dummy-task'");

        assertEquals("Poll data records returned", 0, records.size());

        waitForCacheFlush();

        records = queryDb("SELECT * FROM poll_data WHERE queue_name = 'dummy-task'");
        assertEquals("Poll data records returned", 1, records.size());
        assertEquals("Wrong domain set", "dummy-domain", records.get(0).get("domain"));

        JsonNode jsonData = objectMapper.readTree(records.get(0).get("json_data").toString());
        assertEquals(
                "Poll data is incorrect", "dummy-worker-id", jsonData.get("workerId").asText());
    }

    @Test
    public void getCachedPollDataByDomainTest() throws InterruptedException, SQLException {
        waitForCacheFlush();
        pollDataDAO.updateLastPollData("dummy-task2", "dummy-domain2", "dummy-worker-id2");

        PollData pollData = pollDataDAO.getPollData("dummy-task2", "dummy-domain2");
        assertNotNull("pollData is null", pollData);
        assertEquals("dummy-worker-id2", pollData.getWorkerId());

        List<Map<String, Object>> records =
                queryDb("SELECT * FROM poll_data WHERE queue_name = 'dummy-task2'");

        assertEquals("Poll data records returned", 0, records.size());
    }
}
