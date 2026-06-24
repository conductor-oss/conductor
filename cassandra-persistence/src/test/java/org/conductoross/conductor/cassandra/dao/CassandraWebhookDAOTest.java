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
package org.conductoross.conductor.cassandra.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.CassandraContainer;

import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.MetadataDAO;

import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class CassandraWebhookDAOTest {

    private static final String KEYSPACE = "conductor_test";

    @ClassRule
    public static final CassandraContainer<?> cassandra =
            new CassandraContainer<>("cassandra:3.11.2");

    private static Session session;
    private static ObjectMapper objectMapper;
    private CassandraWebhookDAO dao;
    private MetadataDAO metadataDAO;

    @BeforeClass
    public static void setUpOnce() {
        session = cassandra.getCluster().newSession();
        session.execute(
                "CREATE KEYSPACE IF NOT EXISTS "
                        + KEYSPACE
                        + " WITH replication = {'class':'SimpleStrategy','replication_factor':1}");
        objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    @Before
    public void setUp() {
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".webhook");
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".incoming_webhook_event");
        session.execute("DROP TABLE IF EXISTS " + KEYSPACE + ".webhook_target_workflows");
        CassandraProperties props = new CassandraProperties();
        props.setKeyspace(KEYSPACE);
        metadataDAO = Mockito.mock(MetadataDAO.class);
        dao = new CassandraWebhookDAO(session, objectMapper, props, metadataDAO);
    }

    @AfterClass
    public static void tearDown() {
        if (session != null) session.close();
    }

    @Test
    public void crud_webhookConfig_roundtrip() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        config.setName("my-hook");

        dao.createWebhook("hook-1", config);

        WebhookConfig loaded = dao.getWebhook("hook-1");
        assertNotNull(loaded);
        assertEquals("hook-1", loaded.getId());
        assertEquals(1, dao.getAllWebhooks().size());

        dao.removeWebhook("hook-1");
        assertNull(dao.getWebhook("hook-1"));
        assertTrue(dao.getAllWebhooks().isEmpty());
    }

    @Test
    public void createWebhook_upsertsExistingId() {
        WebhookConfig v1 = new WebhookConfig();
        v1.setId("hook-1");
        v1.setName("original");
        dao.createWebhook("hook-1", v1);

        WebhookConfig v2 = new WebhookConfig();
        v2.setId("hook-1");
        v2.setName("updated");
        dao.createWebhook("hook-1", v2);

        assertEquals("updated", dao.getWebhook("hook-1").getName());
    }

    @Test
    public void removeWebhook_unknownId_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> dao.removeWebhook("missing"));
    }

    @Test
    public void crud_incomingWebhookEvent_roundtrip() {
        IncomingWebhookEvent event =
                IncomingWebhookEvent.builder()
                        .id("ev-1")
                        .webhookId("hook-1")
                        .body("{\"event\":\"push\"}")
                        .timeStamp(123L)
                        .build();
        dao.createIncomingWebhookEvent("ev-1", event);
        assertEquals("hook-1", dao.getWebhookEvent("ev-1").getWebhookId());
        dao.removeWebhookEvent("ev-1");
        assertNull(dao.getWebhookEvent("ev-1"));
    }

    @Test
    public void getMatchers_emptyOrNullTargets_returnsEmpty() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);

        dao.createMatchers(config, Map.of());
        assertTrue(dao.getMatchers("hook-1").isEmpty());

        dao.createMatchers(config, null);
        assertTrue(dao.getMatchers("hook-1").isEmpty());
    }

    @Test
    public void getMatchers_recomputesFromMetadataDAO_onEachCall_noStaleCache() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);
        dao.createMatchers(config, Map.of("wf-a", 1));

        WorkflowTask t1 = new WorkflowTask();
        t1.setType("WAIT_FOR_WEBHOOK");
        t1.setTaskReferenceName("wait_ref");
        t1.setInputParameters(Map.of("matches", Map.of("event", "push")));
        WorkflowDef def1 = new WorkflowDef();
        def1.setName("wf-a");
        def1.setVersion(1);
        def1.setTasks(List.of(t1));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def1));

        assertEquals(Map.of("event", "push"), dao.getMatchers("hook-1").get("wf-a;1;wait_ref"));

        WorkflowTask t2 = new WorkflowTask();
        t2.setType("WAIT_FOR_WEBHOOK");
        t2.setTaskReferenceName("wait_ref");
        t2.setInputParameters(Map.of("matches", Map.of("event", "merge")));
        WorkflowDef def2 = new WorkflowDef();
        def2.setName("wf-a");
        def2.setVersion(1);
        def2.setTasks(List.of(t2));
        when(metadataDAO.getWorkflowDef("wf-a", 1)).thenReturn(Optional.of(def2));

        assertEquals(Map.of("event", "merge"), dao.getMatchers("hook-1").get("wf-a;1;wait_ref"));
    }

    @Test
    public void removeMatchers_drops() {
        WebhookConfig config = new WebhookConfig();
        config.setId("hook-1");
        dao.createWebhook("hook-1", config);
        dao.createMatchers(config, Map.of("wf-a", 1));

        dao.removeMatchers("hook-1");
        assertTrue(dao.getMatchers("hook-1").isEmpty());
    }
}
