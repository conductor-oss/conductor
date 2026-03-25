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

import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.junit.jupiter.api.Assertions.*;

class WebhookConfigServiceTest {

    private InMemoryWebhookConfigDAO dao;
    private WebhookConfigService service;

    @BeforeEach
    void setUp() {
        dao = new InMemoryWebhookConfigDAO();
        service = new WebhookConfigService(dao);
    }

    private WebhookConfig minimalConfig(String name) {
        WebhookConfig c = new WebhookConfig();
        c.setName(name);
        c.setReceiverWorkflowNamesToVersions(Map.of("order_workflow", 1));
        return c;
    }

    // -------------------------------------------------------------------------
    // createWebhook
    // -------------------------------------------------------------------------

    @Test
    void createWebhook_assigns_uuid_when_no_id() {
        WebhookConfig config = minimalConfig("no-id");
        service.createWebhook(config);
        assertNotNull(config.getId());
        assertFalse(config.getId().isBlank());
    }

    @Test
    void createWebhook_uses_provided_id() {
        WebhookConfig config = minimalConfig("explicit-id");
        config.setId("my-explicit-id");
        service.createWebhook(config);
        assertNotNull(dao.get("my-explicit-id"));
    }

    @Test
    void createWebhook_throws_conflict_if_id_already_exists() {
        WebhookConfig first = minimalConfig("first");
        first.setId("dup-id");
        service.createWebhook(first);

        WebhookConfig second = minimalConfig("second");
        second.setId("dup-id");
        // Duplicate ID is a conflict (409), not a bad request (400)
        assertThrows(ConflictException.class, () -> service.createWebhook(second));
    }

    @Test
    void createWebhook_persists_to_dao() {
        WebhookConfig config = minimalConfig("my-webhook");
        service.createWebhook(config);
        assertNotNull(dao.get(config.getId()));
    }

    // -------------------------------------------------------------------------
    // updateWebhook
    // -------------------------------------------------------------------------

    @Test
    void updateWebhook_persists_changes() {
        WebhookConfig config = minimalConfig("original");
        service.createWebhook(config);

        WebhookConfig updated = minimalConfig("renamed");
        updated.setId(config.getId());
        service.updateWebhook(updated);

        assertEquals("renamed", dao.get(config.getId()).getName());
    }

    @Test
    void updateWebhook_throws_if_not_found() {
        WebhookConfig config = minimalConfig("ghost");
        config.setId("nonexistent-id");
        assertThrows(NotFoundException.class, () -> service.updateWebhook(config));
    }

    @Test
    void updateWebhook_preserves_secret_when_masked() {
        WebhookConfig config = minimalConfig("with-secret");
        config.setSecretValue("real-secret");
        service.createWebhook(config);

        WebhookConfig update = minimalConfig("with-secret");
        update.setId(config.getId());
        update.setSecretValue(WebhookConfigService.SECRET_MASK);
        service.updateWebhook(update);

        assertEquals("real-secret", dao.get(config.getId()).getSecretValue());
    }

    @Test
    void updateWebhook_replaces_secret_when_new_value_supplied() {
        WebhookConfig config = minimalConfig("secret-change");
        config.setSecretValue("old-secret");
        service.createWebhook(config);

        WebhookConfig update = minimalConfig("secret-change");
        update.setId(config.getId());
        update.setSecretValue("new-secret");
        service.updateWebhook(update);

        assertEquals("new-secret", dao.get(config.getId()).getSecretValue());
    }

    @Test
    void updateWebhook_preserves_urlVerified() {
        WebhookConfig config = minimalConfig("verified");
        config.setUrlVerified(true);
        service.createWebhook(config);

        WebhookConfig update = minimalConfig("verified");
        update.setId(config.getId());
        update.setUrlVerified(false); // client tries to clear it
        service.updateWebhook(update);

        assertTrue(dao.get(config.getId()).isUrlVerified());
    }

    // -------------------------------------------------------------------------
    // deleteWebhook
    // -------------------------------------------------------------------------

    @Test
    void deleteWebhook_removes_entry() {
        WebhookConfig config = minimalConfig("to-delete");
        service.createWebhook(config);
        service.deleteWebhook(config.getId());
        assertNull(dao.get(config.getId()));
    }

    @Test
    void deleteWebhook_throws_if_not_found() {
        assertThrows(NotFoundException.class, () -> service.deleteWebhook("ghost-id"));
    }

    // -------------------------------------------------------------------------
    // getWebhook
    // -------------------------------------------------------------------------

    @Test
    void getWebhook_returns_config() {
        WebhookConfig config = minimalConfig("get-me");
        service.createWebhook(config);
        WebhookConfig retrieved = service.getWebhook(config.getId());
        assertNotNull(retrieved);
        assertEquals("get-me", retrieved.getName());
    }

    @Test
    void getWebhook_returns_null_for_unknown_id() {
        assertNull(service.getWebhook("unknown"));
    }

    // -------------------------------------------------------------------------
    // getAllWebhooks
    // -------------------------------------------------------------------------

    @Test
    void getAllWebhooks_masks_secrets() {
        WebhookConfig config = minimalConfig("masked");
        config.setSecretValue("top-secret");
        service.createWebhook(config);

        List<WebhookConfig> all = service.getAllWebhooks();
        assertEquals(1, all.size());
        assertEquals(WebhookConfigService.SECRET_MASK, all.get(0).getSecretValue());
    }

    @Test
    void getAllWebhooks_returns_all() {
        service.createWebhook(minimalConfig("a"));
        service.createWebhook(minimalConfig("b"));
        assertEquals(2, service.getAllWebhooks().size());
    }

    @Test
    void getAllWebhooks_does_not_corrupt_stored_secret() {
        // Regression: getAllWebhooks() used to mutate the stored object directly.
        // After calling it, getWebhook() must still return the real secret.
        WebhookConfig config = minimalConfig("secret-guard");
        config.setSecretValue("real-secret");
        service.createWebhook(config);

        // First call masks the returned copy
        List<WebhookConfig> listed = service.getAllWebhooks();
        assertEquals(WebhookConfigService.SECRET_MASK, listed.get(0).getSecretValue());

        // The stored object must still hold the real secret
        WebhookConfig stored = service.getWebhook(config.getId());
        assertEquals("real-secret", stored.getSecretValue());

        // A second list call must also return the mask (not the real value, not null)
        List<WebhookConfig> listed2 = service.getAllWebhooks();
        assertEquals(WebhookConfigService.SECRET_MASK, listed2.get(0).getSecretValue());
    }
}
