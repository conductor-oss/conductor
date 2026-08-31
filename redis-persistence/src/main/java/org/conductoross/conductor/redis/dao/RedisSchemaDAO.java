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
package org.conductoross.conductor.redis.dao;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dao.BaseDynoDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis {@link SchemaDAO}.
 *
 * <p>Mirrors the metadata DAO's workflow-definition layout: one hash per schema name whose fields
 * are the versions, plus a set of the names so every schema can be listed without scanning the
 * keyspace.
 */
@Component
@Conditional(AnyRedisCondition.class)
public class RedisSchemaDAO extends BaseDynoDAO implements SchemaDAO {

    private static final String SCHEMA_DEF = "SCHEMA_DEF";
    private static final String SCHEMA_DEF_NAMES = "SCHEMA_DEF_NAMES";

    public RedisSchemaDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void saveSchema(SchemaDef schemaDef) {
        jedisProxy.hset(
                nsKey(SCHEMA_DEF, schemaDef.getName()),
                String.valueOf(schemaDef.getVersion()),
                toJson(schemaDef));
        jedisProxy.sadd(nsKey(SCHEMA_DEF_NAMES), schemaDef.getName());
    }

    @Override
    public boolean createSchemaIfAbsent(SchemaDef schemaDef) {
        // HSETNX sets the field only when it is absent, so the server decides the race between
        // two writers allocating the same version rather than a read-then-write in this process.
        Long set =
                jedisProxy.hsetnx(
                        nsKey(SCHEMA_DEF, schemaDef.getName()),
                        String.valueOf(schemaDef.getVersion()),
                        toJson(schemaDef));
        if (set == null || set == 0L) {
            return false;
        }
        jedisProxy.sadd(nsKey(SCHEMA_DEF_NAMES), schemaDef.getName());
        return true;
    }

    @Override
    public Optional<SchemaDef> getSchema(String name, int version) {
        String json = jedisProxy.hget(nsKey(SCHEMA_DEF, name), String.valueOf(version));
        return json == null ? Optional.empty() : Optional.of(readValue(json, SchemaDef.class));
    }

    @Override
    public Optional<SchemaDef> getLatestSchema(String name) {
        return versionsOf(name).stream()
                .map(json -> readValue(json, SchemaDef.class))
                .max(Comparator.comparingInt(SchemaDef::getVersion));
    }

    @Override
    public List<SchemaDef> getAllSchemas() {
        Set<String> names = jedisProxy.smembers(nsKey(SCHEMA_DEF_NAMES));
        List<SchemaDef> schemas = new ArrayList<>();
        for (String name : names) {
            versionsOf(name).forEach(json -> schemas.add(readValue(json, SchemaDef.class)));
        }
        schemas.sort(
                Comparator.comparing(SchemaDef::getName).thenComparingInt(SchemaDef::getVersion));
        return schemas;
    }

    @Override
    public void deleteSchema(String name, int version) {
        jedisProxy.hdel(nsKey(SCHEMA_DEF, name), String.valueOf(version));
        // The name is only listable while some version of it survives.
        if (jedisProxy.hkeys(nsKey(SCHEMA_DEF, name)).isEmpty()) {
            jedisProxy.srem(nsKey(SCHEMA_DEF_NAMES), name);
        }
    }

    @Override
    public void deleteSchemaByName(String name) {
        jedisProxy.del(nsKey(SCHEMA_DEF, name));
        jedisProxy.srem(nsKey(SCHEMA_DEF_NAMES), name);
    }

    private List<String> versionsOf(String name) {
        Map<String, String> byVersion = jedisProxy.hgetAll(nsKey(SCHEMA_DEF, name));
        return new ArrayList<>(byVersion.values());
    }
}
