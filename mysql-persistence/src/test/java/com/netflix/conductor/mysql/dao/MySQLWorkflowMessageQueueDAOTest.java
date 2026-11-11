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
package com.netflix.conductor.mysql.dao;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.config.WorkflowMessageQueueProperties;
import com.netflix.conductor.core.dao.JdbcWorkflowMessageQueueDAO;
import com.netflix.conductor.mysql.config.MySQLConfiguration;

import tools.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The durable workflow message queue on MySQL: claims, the claim fence, and delivery order. */
@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            MySQLConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
public class MySQLWorkflowMessageQueueDAOTest {

    @Qualifier("dataSource")
    @Autowired
    private DataSource dataSource;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private Flyway flyway;

    private JdbcWorkflowMessageQueueDAO dao;

    @Before
    public void before() {
        flyway.migrate();
        WorkflowMessageQueueProperties props = new WorkflowMessageQueueProperties();
        props.setMaxQueueSize(10);
        dao = new JdbcWorkflowMessageQueueDAO(dataSource, objectMapper, props, true);
    }

    private static WorkflowMessage message(String id, String wf) {
        WorkflowMessage m = new WorkflowMessage();
        m.setId(id);
        m.setWorkflowId(wf);
        m.setPayload(Map.of("kind", "signal", "name", "deposit"));
        m.setReceivedAt("2026-11-08T00:00:00Z");
        return m;
    }

    private static List<String> ids(List<WorkflowMessage> messages) {
        return messages.stream().map(WorkflowMessage::getId).toList();
    }

    @Test
    public void popReturnsMessagesOnceInOrder() {
        String wf = "my-pop-" + java.util.UUID.randomUUID();
        dao.push(wf, message("a", wf));
        dao.push(wf, message("b", wf));
        assertEquals(List.of("a"), ids(dao.pop(wf, 1)));
        assertEquals(List.of("b"), ids(dao.pop(wf, 5)));
        assertTrue(dao.pop(wf, 5).isEmpty());
    }

    @Test
    public void claimsAreRepeatableFencedAndAcknowledged() {
        String wf = "my-claims-" + java.util.UUID.randomUUID();
        dao.push(wf, message("a", wf));
        dao.push(wf, message("b", wf));
        assertEquals(List.of("a", "b"), ids(dao.claim(wf, 5, "signal__1", 1)));
        assertEquals(List.of("a", "b"), ids(dao.claim(wf, 5, "signal__1", 1))); // repeatable
        assertTrue(dao.claim(wf, 5, "signal__2", 2).isEmpty()); // acknowledges a and b
        dao.push(wf, message("close", wf));
        assertTrue(dao.claim(wf, 5, "signal__1", 1).isEmpty()); // a stale pull takes nothing new
        assertEquals(List.of("close"), ids(dao.claim(wf, 5, "signal__2", 2)));
        dao.delete(wf);
        assertEquals(0, dao.size(wf));
    }

    @Test
    public void concurrentClaimsBySamePullGetTheSameMessages() throws Exception {
        String wf = "my-concurrent-" + java.util.UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            dao.push(wf, message("m" + i, wf));
        }
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<List<String>>> runs = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            runs.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return ids(dao.claim(wf, 5, "signal__1", 1));
                            }));
        }
        go.countDown();
        java.util.Set<List<String>> distinct = new java.util.HashSet<>();
        for (java.util.concurrent.Future<List<String>> r : runs) {
            distinct.add(r.get());
        }
        pool.shutdown();
        assertEquals(java.util.Set.of(List.of("m0", "m1", "m2", "m3", "m4")), distinct);
    }
}
