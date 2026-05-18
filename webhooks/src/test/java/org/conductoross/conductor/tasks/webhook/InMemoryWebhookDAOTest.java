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
package org.conductoross.conductor.tasks.webhook;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.common.webhook.model.WebhookConfig;
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
    public void matchersStoredAndRetrievedByWebhookId() {
        Map<String, Map<String, Object>> index =
                Map.of("order_workflow;1;waitForPayment", Map.of("$['type']", "payment.completed"));
        dao.createMatchers("w1", index);
        assertEquals(index, dao.getMatchers("w1"));
    }

    @Test
    public void getMatchersReturnsEmptyMapForUnknownWebhook() {
        assertTrue(dao.getMatchers("does-not-exist").isEmpty());
    }

    @Test
    public void createMatchersOverwritesPreviousIndex() {
        dao.createMatchers("w1", Map.of("key-1", Map.of("a", "1")));
        dao.createMatchers("w1", Map.of("key-2", Map.of("b", "2")));
        assertEquals(Map.of("key-2", Map.of("b", "2")), dao.getMatchers("w1"));
    }

    @Test
    public void removeMatchersDropsTheIndex() {
        dao.createMatchers("w1", Map.of("k", Map.of("a", "1")));
        dao.removeMatchers("w1");
        assertTrue(dao.getMatchers("w1").isEmpty());
    }

    @Test
    public void incomingEventRoundTripsById() {
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setId("e1");
        event.setWebhookId("w1");
        event.setBody("{\"foo\":\"bar\"}");
        event.setTimestamp(1234567890L);
        dao.createIncomingWebhookEvent("e1", event);
        assertEquals(event, dao.getIncomingWebhookEvent("e1"));
    }

    @Test
    public void getIncomingEventReturnsNullForUnknownId() {
        assertNull(dao.getIncomingWebhookEvent("does-not-exist"));
    }

    @Test
    public void removeIncomingEventDropsTheRecord() {
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setId("e1");
        dao.createIncomingWebhookEvent("e1", event);
        dao.removeIncomingWebhookEvent("e1");
        assertNull(dao.getIncomingWebhookEvent("e1"));
    }

    @Test
    public void configMatchersAndEventsAreIsolatedNamespaces() {
        // Same id used across all three storages — must not collide.
        dao.createWebhook("shared-id", newConfig("shared-id", "config"));
        dao.createMatchers("shared-id", Map.of("k", Map.of("a", "1")));
        IncomingWebhookEvent event = new IncomingWebhookEvent();
        event.setId("shared-id");
        dao.createIncomingWebhookEvent("shared-id", event);

        dao.removeWebhook("shared-id");
        assertNull(dao.getWebhook("shared-id"));
        assertEquals(Map.of("k", Map.of("a", "1")), dao.getMatchers("shared-id"));
        assertEquals(event, dao.getIncomingWebhookEvent("shared-id"));
    }
}
