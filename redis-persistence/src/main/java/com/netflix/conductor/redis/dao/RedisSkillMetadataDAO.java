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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.conductoross.conductor.dao.SkillMetadataDAO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis {@link SkillMetadataDAO}. Per owner, versions live in a {@code SKILL_META} hash keyed by
 * {@code name<SEP>version}; the latest-version pointer lives in a {@code SKILL_LATEST} hash keyed
 * by {@code name}. Also serves {@code conductor.db.type=memory}.
 */
@Component
@Conditional(AnyRedisCondition.class)
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class RedisSkillMetadataDAO extends BaseDynoDAO implements SkillMetadataDAO {

    private static final String SKILL_META = "SKILL_META";
    private static final String SKILL_LATEST = "SKILL_LATEST";
    private static final String SEP = "\u0000";

    public RedisSkillMetadataDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    private static String field(String name, String version) {
        return name + SEP + version;
    }

    @Override
    public void save(
            String ownerId,
            String name,
            String version,
            boolean makeLatest,
            String detailJson,
            Long createdAt,
            Long updatedAt) {
        jedisProxy.hset(nsKey(SKILL_META, ownerId), field(name, version), detailJson);
        if (makeLatest) {
            jedisProxy.hset(nsKey(SKILL_LATEST, ownerId), name, version);
        }
    }

    @Override
    public Optional<String> find(String ownerId, String name, String version) {
        return Optional.ofNullable(
                jedisProxy.hget(nsKey(SKILL_META, ownerId), field(name, version)));
    }

    @Override
    public Optional<String> latestVersion(String ownerId, String name) {
        return Optional.ofNullable(jedisProxy.hget(nsKey(SKILL_LATEST, ownerId), name));
    }

    @Override
    public List<String> listVersions(String ownerId, String name) {
        String prefix = name + SEP;
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> entry :
                jedisProxy.hgetAll(nsKey(SKILL_META, ownerId)).entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                out.add(entry.getValue());
            }
        }
        return out;
    }

    @Override
    public List<String> list(String ownerId, boolean allVersions) {
        Map<String, String> all = jedisProxy.hgetAll(nsKey(SKILL_META, ownerId));
        if (allVersions) {
            return new ArrayList<>(all.values());
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> latest :
                jedisProxy.hgetAll(nsKey(SKILL_LATEST, ownerId)).entrySet()) {
            String detail = all.get(field(latest.getKey(), latest.getValue()));
            if (detail != null) {
                out.add(detail);
            }
        }
        return out;
    }

    @Override
    public void delete(String ownerId, String name, String version) {
        jedisProxy.hdel(nsKey(SKILL_META, ownerId), field(name, version));
        String latest = jedisProxy.hget(nsKey(SKILL_LATEST, ownerId), name);
        if (version.equals(latest)) {
            recomputeLatest(ownerId, name);
        }
    }

    /** Re-point the latest version for a skill to its newest remaining version (by updatedAt). */
    private void recomputeLatest(String ownerId, String name) {
        String prefix = name + SEP;
        String newestVersion = null;
        long newestUpdatedAt = Long.MIN_VALUE;
        for (Map.Entry<String, String> entry :
                jedisProxy.hgetAll(nsKey(SKILL_META, ownerId)).entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            String entryVersion = entry.getKey().substring(prefix.length());
            long updatedAt = readUpdatedAt(entry.getValue());
            if (newestVersion == null || updatedAt >= newestUpdatedAt) {
                newestVersion = entryVersion;
                newestUpdatedAt = updatedAt;
            }
        }
        if (newestVersion != null) {
            jedisProxy.hset(nsKey(SKILL_LATEST, ownerId), name, newestVersion);
        } else {
            jedisProxy.hdel(nsKey(SKILL_LATEST, ownerId), name);
        }
    }

    private long readUpdatedAt(String detailJson) {
        try {
            JsonNode node = objectMapper.readTree(detailJson).path("updatedAt");
            return node.isNumber() ? node.asLong() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
