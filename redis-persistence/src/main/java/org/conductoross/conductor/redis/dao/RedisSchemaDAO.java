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
import java.util.Objects;
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
    public void save(SchemaDef schemaDef) {
        jedisProxy.hset(
                nsKey(SCHEMA_DEF, schemaDef.getName()),
                String.valueOf(schemaDef.getVersion()),
                toJson(schemaDef));
        jedisProxy.sadd(nsKey(SCHEMA_DEF_NAMES), schemaDef.getName());
    }

    @Override
    public SchemaDef findByNameAndVersion(String name, Integer version) {
        Objects.requireNonNull(version, "Schema version cannot be null");
        String json = jedisProxy.hget(nsKey(SCHEMA_DEF, name), String.valueOf(version));
        return json == null ? null : readValue(json, SchemaDef.class);
    }

    @Override
    public SchemaDef findLatestVersionByName(String name) {
        return versionsOf(name).stream()
                .map(json -> readValue(json, SchemaDef.class))
                .max(Comparator.comparingInt(SchemaDef::getVersion))
                .orElse(null);
    }

    @Override
    public List<SchemaDef> getAll() {
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
    public int deleteByNameAndVersion(String name, Integer version) {
        Objects.requireNonNull(version, "Schema version cannot be null");
        Long removed = jedisProxy.hdel(nsKey(SCHEMA_DEF, name), String.valueOf(version));
        // The name is only listable while some version of it survives.
        if (jedisProxy.hkeys(nsKey(SCHEMA_DEF, name)).isEmpty()) {
            jedisProxy.srem(nsKey(SCHEMA_DEF_NAMES), name);
        }
        return removed == null ? 0 : removed.intValue();
    }

    @Override
    public int deleteAllByName(String name) {
        // DEL reports keys removed, not fields, so the count comes from the hash's own length —
        // the same number the SQL backends' row count reports.
        Long fields = jedisProxy.hlen(nsKey(SCHEMA_DEF, name));
        jedisProxy.del(nsKey(SCHEMA_DEF, name));
        jedisProxy.srem(nsKey(SCHEMA_DEF_NAMES), name);
        return fields == null ? 0 : fields.intValue();
    }

    /**
     * One hash deleted per name. Redis has no multi-key delete that reports fields removed, so this
     * is not atomic across the batch: a failure part-way leaves the names already deleted gone.
     */
    @Override
    public int deleteAllByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (String name : names) {
            removed += deleteAllByName(name);
        }
        return removed;
    }

    @Override
    public List<SchemaDef> findAllVersionsByName(String name) {
        return versionsOf(name).stream()
                .map(json -> readValue(json, SchemaDef.class))
                .sorted(Comparator.comparingInt(SchemaDef::getVersion).reversed())
                .toList();
    }

    /**
     * Read from the version fields' names, so no payload is deserialized. The keyspace still has to
     * be walked hash by hash — there is no cheaper listing here, unlike the SQL backends where the
     * name and version are indexed columns.
     */
    @Override
    public List<SchemaDef> getAllShortenedSchemas() {
        List<SchemaDef> schemas = new ArrayList<>();
        for (String name : jedisProxy.smembers(nsKey(SCHEMA_DEF_NAMES))) {
            for (String version : jedisProxy.hkeys(nsKey(SCHEMA_DEF, name))) {
                schemas.add(nameAndVersion(name, Integer.parseInt(version)));
            }
        }
        schemas.sort(
                Comparator.comparing(SchemaDef::getName).thenComparingInt(SchemaDef::getVersion));
        return schemas;
    }

    /**
     * A name and a version and nothing else — no type and no document, so the result identifies a
     * registered schema but cannot be validated against.
     */
    private static SchemaDef nameAndVersion(String name, int version) {
        SchemaDef schema = new SchemaDef();
        schema.setName(name);
        schema.setVersion(version);
        return schema;
    }

    private List<String> versionsOf(String name) {
        Map<String, String> byVersion = jedisProxy.hgetAll(nsKey(SCHEMA_DEF, name));
        return new ArrayList<>(byVersion.values());
    }
}
