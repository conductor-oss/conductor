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

import java.util.Base64;

import org.conductoross.conductor.dao.SkillPackageDAO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis {@link SkillPackageDAO}. Package bytes (Base64-encoded) live in a single {@code SKILL_PKG}
 * hash keyed by handle. Also serves {@code conductor.db.type=memory}.
 */
@Component
@Conditional(AnyRedisCondition.class)
@ConditionalOnProperty(name = "conductor.integrations.ai.enabled", havingValue = "true")
public class RedisSkillPackageDAO extends BaseDynoDAO implements SkillPackageDAO {

    private static final String SKILL_PKG = "SKILL_PKG";

    public RedisSkillPackageDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void put(String handle, byte[] data) {
        jedisProxy.hset(nsKey(SKILL_PKG), handle, Base64.getEncoder().encodeToString(data));
    }

    @Override
    public byte[] get(String handle) {
        String encoded = jedisProxy.hget(nsKey(SKILL_PKG), handle);
        return encoded == null ? null : Base64.getDecoder().decode(encoded);
    }

    @Override
    public boolean exists(String handle) {
        return Boolean.TRUE.equals(jedisProxy.hexists(nsKey(SKILL_PKG), handle));
    }

    @Override
    public void delete(String handle) {
        jedisProxy.hdel(nsKey(SKILL_PKG), handle);
    }
}
