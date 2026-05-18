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
package org.conductoross.conductor.dao.memory.webhook;

import java.util.List;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InMemoryWebhookDAOTest {

    private InMemoryWebhookDAO dao;

    @Before
    public void setUp() {
        dao = new InMemoryWebhookDAO();
    }

    private WebhookConfig newConfig(String id, String name) {
        WebhookConfig config = new WebhookConfig();
        config.setId(id);
        config.setName(name);
        config.setVerifier(WebhookConfig.Verifier.HEADER_BASED);
        return config;
    }

    @Test
    public void getWebhookReturnsNullWhenUnknown() {
        assertNull(dao.getWebhook("does-not-exist"));
    }

    @Test
    public void createThenGetRoundTrips() {
        WebhookConfig config = newConfig("w1", "stripe-payments");
        dao.createWebhook("w1", config);
        assertEquals(config, dao.getWebhook("w1"));
    }

    @Test
    public void createOverwritesExisting() {
        dao.createWebhook("w1", newConfig("w1", "original"));
        WebhookConfig replacement = newConfig("w1", "replacement");
        dao.createWebhook("w1", replacement);
        assertEquals("replacement", dao.getWebhook("w1").getName());
    }

    @Test
    public void getAllWebhooksReturnsAllRegistered() {
        dao.createWebhook("w1", newConfig("w1", "a"));
        dao.createWebhook("w2", newConfig("w2", "b"));
        dao.createWebhook("w3", newConfig("w3", "c"));
        List<WebhookConfig> all = dao.getAllWebhooks();
        assertEquals(3, all.size());
    }

    @Test
    public void getAllWebhooksReturnsEmptyListWhenNoneRegistered() {
        assertTrue(dao.getAllWebhooks().isEmpty());
    }

    @Test
    public void removeWebhookDropsTheConfig() {
        dao.createWebhook("w1", newConfig("w1", "a"));
        dao.removeWebhook("w1");
        assertNull(dao.getWebhook("w1"));
    }

    @Test
    public void removeWebhookOfUnknownIdIsNoOp() {
        dao.removeWebhook("does-not-exist");
        assertTrue(dao.getAllWebhooks().isEmpty());
    }

    @Test
    public void incomingEventRoundTripsById() {
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setId("e1");
        event.setWebhookId("w1");
        event.setBody("{\"foo\":\"bar\"}");
        event.setTimeStamp(1234567890L);
        dao.createIncomingWebhookEvent("e1", event);
        assertEquals(event, dao.getWebhookEvent("e1"));
    }

    @Test
    public void getIncomingEventReturnsNullForUnknownId() {
        assertNull(dao.getWebhookEvent("does-not-exist"));
    }

    @Test
    public void removeIncomingEventDropsTheRecord() {
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setId("e1");
        dao.createIncomingWebhookEvent("e1", event);
        dao.removeWebhookEvent("e1");
        assertNull(dao.getWebhookEvent("e1"));
    }

    @Test
    public void getWebhookReturnsStoredReferenceCallersMustNotMutate() {
        // Documents the mutation contract: in-memory impl returns a live reference; callers must
        // not mutate. To change a stored config, fetch + construct new + write back.
        dao.createWebhook("w1", newConfig("w1", "original"));
        WebhookConfig fetched = dao.getWebhook("w1");
        fetched.setName("mutated-by-caller");
        assertEquals(
                "in-memory impl returns the stored reference; this test pins the contract so a "
                        + "future switch to defensive-copy semantics is a deliberate decision",
                "mutated-by-caller",
                dao.getWebhook("w1").getName());
    }

    @Test
    public void getAllWebhooksReturnsFreshListButLiveElements() {
        dao.createWebhook("w1", newConfig("w1", "a"));
        List<WebhookConfig> first = dao.getAllWebhooks();
        first.clear(); // mutating the returned list must not affect storage
        assertEquals(1, dao.getAllWebhooks().size());
        // ...but elements are live references; same contract as getWebhook
        dao.getAllWebhooks().get(0).setName("mutated-by-caller");
        assertEquals("mutated-by-caller", dao.getWebhook("w1").getName());
    }

    @Test
    public void configAndEventsAreIsolatedNamespaces() {
        // Same id used across both storages — must not collide.
        dao.createWebhook("shared-id", newConfig("shared-id", "config"));
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setId("shared-id");
        dao.createIncomingWebhookEvent("shared-id", event);

        dao.removeWebhook("shared-id");
        assertNull(dao.getWebhook("shared-id"));
        assertEquals(event, dao.getWebhookEvent("shared-id"));
    }
}
