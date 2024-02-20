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
import java.util.*;
import java.util.stream.Collectors;

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
            "conductor.postgres.pollDataFlushInterval=0",
            "conductor.postgres.pollDataCacheValidityPeriod=0",
            "spring.flyway.clean-disabled=false"
        })
@SpringBootTest
public class PostgresPollDataDAONoCacheTest {

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

    @Test
    public void updateLastPollDataTest() throws SQLException, JsonProcessingException {
        pollDataDAO.updateLastPollData("dummy-task", "dummy-domain", "dummy-worker-id");

        List<Map<String, Object>> records =
                queryDb("SELECT * FROM poll_data WHERE queue_name = 'dummy-task'");

        assertEquals("More than one poll data records returned", 1, records.size());
        assertEquals("Wrong domain set", "dummy-domain", records.get(0).get("domain"));

        JsonNode jsonData = objectMapper.readTree(records.get(0).get("json_data").toString());
        assertEquals(
                "Poll data is incorrect", "dummy-worker-id", jsonData.get("workerId").asText());
    }

    @Test
    public void updateLastPollDataNullDomainTest() throws SQLException, JsonProcessingException {
        pollDataDAO.updateLastPollData("dummy-task", null, "dummy-worker-id");

        List<Map<String, Object>> records =
                queryDb("SELECT * FROM poll_data WHERE queue_name = 'dummy-task'");

        assertEquals("More than one poll data records returned", 1, records.size());
        assertEquals("Wrong domain set", "DEFAULT", records.get(0).get("domain"));

        JsonNode jsonData = objectMapper.readTree(records.get(0).get("json_data").toString());
        assertEquals(
                "Poll data is incorrect", "dummy-worker-id", jsonData.get("workerId").asText());
    }

    @Test
    public void getPollDataByDomainTest() {
        pollDataDAO.updateLastPollData("dummy-task", "dummy-domain", "dummy-worker-id");

        PollData pollData = pollDataDAO.getPollData("dummy-task", "dummy-domain");
        assertEquals("dummy-task", pollData.getQueueName());
        assertEquals("dummy-domain", pollData.getDomain());
        assertEquals("dummy-worker-id", pollData.getWorkerId());
    }

    @Test
    public void getPollDataByNullDomainTest() {
        pollDataDAO.updateLastPollData("dummy-task", null, "dummy-worker-id");

        PollData pollData = pollDataDAO.getPollData("dummy-task", null);
        assertEquals("dummy-task", pollData.getQueueName());
        assertNull(pollData.getDomain());
        assertEquals("dummy-worker-id", pollData.getWorkerId());
    }

    @Test
    public void getPollDataByTaskTest() {
        pollDataDAO.updateLastPollData("dummy-task1", "domain1", "dummy-worker-id1");
        pollDataDAO.updateLastPollData("dummy-task1", "domain2", "dummy-worker-id2");
        pollDataDAO.updateLastPollData("dummy-task1", null, "dummy-worker-id3");
        pollDataDAO.updateLastPollData("dummy-task2", "domain2", "dummy-worker-id4");

        List<PollData> pollData = pollDataDAO.getPollData("dummy-task1");
        assertEquals("Wrong number of records returned", 3, pollData.size());

        List<String> queueNames =
                pollData.stream().map(x -> x.getQueueName()).collect(Collectors.toList());
        assertEquals(3, Collections.frequency(queueNames, "dummy-task1"));

        List<String> domains =
                pollData.stream().map(x -> x.getDomain()).collect(Collectors.toList());
        assertTrue(domains.contains("domain1"));
        assertTrue(domains.contains("domain2"));
        assertTrue(domains.contains(null));

        List<String> workerIds =
                pollData.stream().map(x -> x.getWorkerId()).collect(Collectors.toList());
        assertTrue(workerIds.contains("dummy-worker-id1"));
        assertTrue(workerIds.contains("dummy-worker-id2"));
        assertTrue(workerIds.contains("dummy-worker-id3"));
    }

    @Test
    public void getAllPollDataTest() {
        pollDataDAO.updateLastPollData("dummy-task1", "domain1", "dummy-worker-id1");
        pollDataDAO.updateLastPollData("dummy-task1", "domain2", "dummy-worker-id2");
        pollDataDAO.updateLastPollData("dummy-task1", null, "dummy-worker-id3");
        pollDataDAO.updateLastPollData("dummy-task2", "domain2", "dummy-worker-id4");

        List<PollData> pollData = pollDataDAO.getAllPollData();
        assertEquals("Wrong number of records returned", 4, pollData.size());

        List<String> queueNames =
                pollData.stream().map(x -> x.getQueueName()).collect(Collectors.toList());
        assertEquals(3, Collections.frequency(queueNames, "dummy-task1"));
        assertEquals(1, Collections.frequency(queueNames, "dummy-task2"));

        List<String> domains =
                pollData.stream().map(x -> x.getDomain()).collect(Collectors.toList());
        assertEquals(1, Collections.frequency(domains, "domain1"));
        assertEquals(2, Collections.frequency(domains, "domain2"));
        assertEquals(1, Collections.frequency(domains, null));

        List<String> workerIds =
                pollData.stream().map(x -> x.getWorkerId()).collect(Collectors.toList());
        assertTrue(workerIds.contains("dummy-worker-id1"));
        assertTrue(workerIds.contains("dummy-worker-id2"));
        assertTrue(workerIds.contains("dummy-worker-id3"));
        assertTrue(workerIds.contains("dummy-worker-id4"));
    }
}
