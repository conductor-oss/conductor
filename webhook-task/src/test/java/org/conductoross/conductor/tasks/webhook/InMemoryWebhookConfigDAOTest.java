/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryWebhookConfigDAOTest {

    private InMemoryWebhookConfigDAO dao;

    @BeforeEach
    void setUp() {
        dao = new InMemoryWebhookConfigDAO();
    }

    private WebhookConfig buildConfig(String id, String name) {
        WebhookConfig c = new WebhookConfig();
        c.setId(id);
        c.setName(name);
        c.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        c.setReceiverWorkflowNamesToVersions(Map.of("my_workflow", 1));
        return c;
    }

    @Test
    void save_and_get() {
        WebhookConfig config = buildConfig("id1", "webhook1");
        dao.save("id1", config);
        WebhookConfig retrieved = dao.get("id1");
        assertNotNull(retrieved);
        assertEquals("webhook1", retrieved.getName());
    }

    @Test
    void get_nonexistent_returns_null() {
        assertNull(dao.get("does-not-exist"));
    }

    @Test
    void save_overwrites_existing() {
        dao.save("id1", buildConfig("id1", "original"));
        dao.save("id1", buildConfig("id1", "updated"));
        assertEquals("updated", dao.get("id1").getName());
    }

    @Test
    void remove_deletes_entry() {
        dao.save("id1", buildConfig("id1", "webhook1"));
        dao.remove("id1");
        assertNull(dao.get("id1"));
    }

    @Test
    void remove_nonexistent_is_noop() {
        assertDoesNotThrow(() -> dao.remove("does-not-exist"));
    }

    @Test
    void getAll_empty() {
        assertTrue(dao.getAll().isEmpty());
    }

    @Test
    void getAll_returns_all_stored() {
        dao.save("id1", buildConfig("id1", "webhook1"));
        dao.save("id2", buildConfig("id2", "webhook2"));
        List<WebhookConfig> all = dao.getAll();
        assertEquals(2, all.size());
    }

    @Test
    void getAll_does_not_include_removed() {
        dao.save("id1", buildConfig("id1", "webhook1"));
        dao.save("id2", buildConfig("id2", "webhook2"));
        dao.remove("id1");
        List<WebhookConfig> all = dao.getAll();
        assertEquals(1, all.size());
        assertEquals("webhook2", all.get(0).getName());
    }
}
