/*
 * Copyright 2022 Netflix, Inc.
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

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

@Component
@Conditional(AnyRedisCondition.class)
public class RedisPollDataDAO extends BaseDynoDAO implements PollDataDAO {

    private static final String POLL_DATA = "POLL_DATA";

    public RedisPollDataDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    @Override
    public void updateLastPollData(String taskDefName, String domain, String workerId) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");
        PollData pollData = new PollData(taskDefName, domain, workerId, System.currentTimeMillis());

        String key = nsKey(POLL_DATA, pollData.getQueueName());
        String field = (domain == null) ? "DEFAULT" : domain;

        String payload = toJson(pollData);
        recordRedisDaoRequests("updatePollData");
        recordRedisDaoPayloadSize("updatePollData", payload.length(), "n/a", "n/a");
        jedisProxy.hset(key, field, payload);
    }

    @Override
    public PollData getPollData(String taskDefName, String domain) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");

        String key = nsKey(POLL_DATA, taskDefName);
        String field = (domain == null) ? "DEFAULT" : domain;

        String pollDataJsonString = jedisProxy.hget(key, field);
        recordRedisDaoRequests("getPollData");
        recordRedisDaoPayloadSize(
                "getPollData", StringUtils.length(pollDataJsonString), "n/a", "n/a");

        PollData pollData = null;
        if (StringUtils.isNotBlank(pollDataJsonString)) {
            pollData = readValue(pollDataJsonString, PollData.class);
        }
        return pollData;
    }

    @Override
    public List<PollData> getPollData(String taskDefName) {
        Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");

        String key = nsKey(POLL_DATA, taskDefName);

        Map<String, String> pMapdata = jedisProxy.hgetAll(key);
        List<PollData> pollData = new ArrayList<>();
        if (pMapdata != null) {
            pMapdata.values()
                    .forEach(
                            pollDataJsonString -> {
                                pollData.add(readValue(pollDataJsonString, PollData.class));
                                recordRedisDaoRequests("getPollData");
                                recordRedisDaoPayloadSize(
                                        "getPollData", pollDataJsonString.length(), "n/a", "n/a");
                            });
        }
        return pollData;
    }
}
