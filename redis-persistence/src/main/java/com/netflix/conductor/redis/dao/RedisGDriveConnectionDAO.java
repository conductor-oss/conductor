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
package com.netflix.conductor.redis.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.integrations.gdrive.GDriveConnection;
import org.conductoross.conductor.dao.GDriveConnectionDAO;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Primary
@Conditional(AnyRedisCondition.class)
public class RedisGDriveConnectionDAO extends BaseDynoDAO implements GDriveConnectionDAO {

    private static final String GDRIVE_CONNECTION = "GDRIVE_CONNECTION";

    public RedisGDriveConnectionDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void saveConnection(GDriveConnection connection) {
        GDriveConnection existing = getConnection(connection.getConnectionId());
        GDriveConnection stored = new GDriveConnection();
        long now = Instant.now().toEpochMilli();
        stored.setConnectionId(connection.getConnectionId());
        stored.setAccountName(connection.getAccountName());
        stored.setOauthTokenJson(connection.getOauthTokenJson());
        stored.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        stored.setUpdatedAt(now);
        jedisProxy.hset(nsKey(GDRIVE_CONNECTION), connection.getConnectionId(), toJson(stored));
    }

    @Override
    public GDriveConnection getConnection(String connectionId) {
        String json = jedisProxy.hget(nsKey(GDRIVE_CONNECTION), connectionId);
        if (json == null) {
            return null;
        }
        return readValue(json, GDriveConnection.class);
    }

    @Override
    public List<GDriveConnection> getAllConnections() {
        Map<String, String> values = jedisProxy.hgetAll(nsKey(GDRIVE_CONNECTION));
        List<GDriveConnection> connections = new ArrayList<>();
        for (String json : values.values()) {
            connections.add(readValue(json, GDriveConnection.class));
        }
        return connections;
    }

    @Override
    public void deleteConnection(String connectionId) {
        jedisProxy.hdel(nsKey(GDRIVE_CONNECTION), connectionId);
    }
}
