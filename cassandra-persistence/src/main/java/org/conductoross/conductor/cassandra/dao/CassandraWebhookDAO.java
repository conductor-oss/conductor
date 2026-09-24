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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.dao.webhook.WebhookDAO;
import org.conductoross.conductor.service.webhook.WebhookMatcherComputer;
import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.springframework.lang.Nullable;

import com.netflix.conductor.cassandra.config.CassandraProperties;
import com.netflix.conductor.cassandra.dao.CassandraBaseDAO;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.dao.MetadataDAO;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Cassandra-backed {@link WebhookDAO}.
 *
 * <p>Tables (created on construction via {@link #ensureTables()}):
 *
 * <ul>
 *   <li>{@code webhook (bucket, webhook_id PK, json_data)} — single 'ALL' bucket so
 *       getAllWebhooks() is a single-partition scan. Anti-pattern for high cardinality, fine for
 *       admin-scale webhook configs.
 *   <li>{@code incoming_webhook_event (event_id PK, json_data)} — high cardinality, no listing.
 *   <li>{@code webhook_target_workflows (webhook_id PK, json_data)} — target workflow versions
 *       snapshot per webhook. Matchers themselves are recomputed from MetadataDAO on read.
 * </ul>
 */
@Slf4j
public class CassandraWebhookDAO extends CassandraBaseDAO implements WebhookDAO {

    private static final String TABLE_WEBHOOK = "webhook";
    private static final String TABLE_EVENT = "incoming_webhook_event";
    private static final String TABLE_TARGETS = "webhook_target_workflows";
    private static final String ALL_BUCKET = "ALL";

    private static final long DEFAULT_EVENT_TTL_SECONDS = 7L * 24 * 3600; // 7 days

    private final Session session;
    private final MetadataDAO metadataDAO;
    // objectMapper / toJson / readValue are package-private in CassandraBaseDAO; keep a local ref.
    private final ObjectMapper objectMapper;
    private final long incomingEventTtlSeconds;

    private final PreparedStatement insertWebhookStmt;
    private final PreparedStatement selectWebhookStmt;
    private final PreparedStatement selectAllWebhooksStmt;
    private final PreparedStatement deleteWebhookStmt;
    private final PreparedStatement insertEventStmt;
    private final PreparedStatement selectEventStmt;
    private final PreparedStatement deleteEventStmt;
    private final PreparedStatement insertTargetsStmt;
    private final PreparedStatement selectTargetsStmt;
    private final PreparedStatement deleteTargetsStmt;

    public CassandraWebhookDAO(
            Session session,
            ObjectMapper objectMapper,
            CassandraProperties properties,
            MetadataDAO metadataDAO) {
        this(session, objectMapper, properties, metadataDAO, DEFAULT_EVENT_TTL_SECONDS);
    }

    public CassandraWebhookDAO(
            Session session,
            ObjectMapper objectMapper,
            CassandraProperties properties,
            MetadataDAO metadataDAO,
            long incomingEventTtlSeconds) {
        super(session, objectMapper, properties);
        this.session = session;
        this.metadataDAO = metadataDAO;
        this.objectMapper = objectMapper;
        this.incomingEventTtlSeconds = incomingEventTtlSeconds;
        ensureTables();

        ConsistencyLevel readConsistency = properties.getReadConsistencyLevel();
        ConsistencyLevel writeConsistency = properties.getWriteConsistencyLevel();
        String webhookTable = properties.getKeyspace() + "." + TABLE_WEBHOOK;
        String eventTable = properties.getKeyspace() + "." + TABLE_EVENT;
        String targetsTable = properties.getKeyspace() + "." + TABLE_TARGETS;

        insertWebhookStmt =
                session.prepare(
                                "INSERT INTO "
                                        + webhookTable
                                        + " (bucket, webhook_id, json_data) VALUES (?, ?, ?)")
                        .setConsistencyLevel(writeConsistency);
        selectWebhookStmt =
                session.prepare(
                                "SELECT json_data FROM "
                                        + webhookTable
                                        + " WHERE bucket = ? AND webhook_id = ?")
                        .setConsistencyLevel(readConsistency);
        selectAllWebhooksStmt =
                session.prepare("SELECT json_data FROM " + webhookTable + " WHERE bucket = ?")
                        .setConsistencyLevel(readConsistency);
        deleteWebhookStmt =
                session.prepare(
                                "DELETE FROM "
                                        + webhookTable
                                        + " WHERE bucket = ? AND webhook_id = ?")
                        .setConsistencyLevel(writeConsistency);

        insertEventStmt =
                session.prepare(
                                "INSERT INTO "
                                        + eventTable
                                        + " (event_id, json_data) VALUES (?, ?)")
                        .setConsistencyLevel(writeConsistency);
        selectEventStmt =
                session.prepare("SELECT json_data FROM " + eventTable + " WHERE event_id = ?")
                        .setConsistencyLevel(readConsistency);
        deleteEventStmt =
                session.prepare("DELETE FROM " + eventTable + " WHERE event_id = ?")
                        .setConsistencyLevel(writeConsistency);

        insertTargetsStmt =
                session.prepare(
                                "INSERT INTO "
                                        + targetsTable
                                        + " (webhook_id, json_data) VALUES (?, ?)")
                        .setConsistencyLevel(writeConsistency);
        selectTargetsStmt =
                session.prepare("SELECT json_data FROM " + targetsTable + " WHERE webhook_id = ?")
                        .setConsistencyLevel(readConsistency);
        deleteTargetsStmt =
                session.prepare("DELETE FROM " + targetsTable + " WHERE webhook_id = ?")
                        .setConsistencyLevel(writeConsistency);
    }

    private void ensureTables() {
        String ks = properties.getKeyspace();
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + ks
                        + "."
                        + TABLE_WEBHOOK
                        + " (bucket text, webhook_id text, json_data text,"
                        + " PRIMARY KEY ((bucket), webhook_id))");
        // default_time_to_live makes Cassandra auto-expire rows after the configured
        // window — matches the cleanup-job behavior on the SQL backings. Note: CREATE
        // TABLE IF NOT EXISTS won't update an existing table's TTL; operators changing
        // the retention duration on an existing deployment need to ALTER TABLE manually.
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + ks
                        + "."
                        + TABLE_EVENT
                        + " (event_id text PRIMARY KEY, json_data text)"
                        + " WITH default_time_to_live = "
                        + incomingEventTtlSeconds);
        session.execute(
                "CREATE TABLE IF NOT EXISTS "
                        + ks
                        + "."
                        + TABLE_TARGETS
                        + " (webhook_id text PRIMARY KEY, json_data text)");
    }

    @Override
    public void createWebhook(String id, WebhookConfig webhookConfig) {
        session.execute(insertWebhookStmt.bind(ALL_BUCKET, id, toJson(webhookConfig)));
    }

    @Override
    public WebhookConfig getWebhook(String webhookId) {
        ResultSet rs = session.execute(selectWebhookStmt.bind(ALL_BUCKET, webhookId));
        Row row = rs.one();
        return row == null ? null : readValue(row.getString("json_data"), WebhookConfig.class);
    }

    @Override
    public List<WebhookConfig> getAllWebhooks() {
        ResultSet rs = session.execute(selectAllWebhooksStmt.bind(ALL_BUCKET));
        List<WebhookConfig> out = new ArrayList<>();
        for (Row row : rs) {
            out.add(readValue(row.getString("json_data"), WebhookConfig.class));
        }
        return out;
    }

    @Override
    public void removeWebhook(String id) {
        if (getWebhook(id) == null) {
            throw new NotFoundException("Webhook with id " + id + " not found");
        }
        session.execute(deleteWebhookStmt.bind(ALL_BUCKET, id));
    }

    @Override
    public void createIncomingWebhookEvent(String id, IncomingWebhookEvent event) {
        session.execute(insertEventStmt.bind(id, toJson(event)));
    }

    @Override
    public IncomingWebhookEvent getWebhookEvent(String messageId) {
        ResultSet rs = session.execute(selectEventStmt.bind(messageId));
        Row row = rs.one();
        return row == null
                ? null
                : readValue(row.getString("json_data"), IncomingWebhookEvent.class);
    }

    @Override
    public void removeWebhookEvent(String id) {
        session.execute(deleteEventStmt.bind(id));
    }

    @Override
    public Map<String, Map<String, Object>> getMatchers(String webhookId) {
        return WebhookMatcherComputer.compute(loadTargets(webhookId), metadataDAO);
    }

    @Override
    public void createMatchers(
            WebhookConfig webhookConfig,
            @Nullable Map<String, Integer> receiverWorkflowNamesToVersionsOverride) {
        Map<String, Integer> targets =
                receiverWorkflowNamesToVersionsOverride == null
                        ? Collections.emptyMap()
                        : receiverWorkflowNamesToVersionsOverride;
        session.execute(insertTargetsStmt.bind(webhookConfig.getId(), toJson(targets)));
    }

    @Override
    public void removeMatchers(String id) {
        session.execute(deleteTargetsStmt.bind(id));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> loadTargets(String webhookId) {
        ResultSet rs = session.execute(selectTargetsStmt.bind(webhookId));
        Row row = rs.one();
        if (row == null) {
            return Collections.emptyMap();
        }
        return readValue(row.getString("json_data"), Map.class);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T readValue(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
