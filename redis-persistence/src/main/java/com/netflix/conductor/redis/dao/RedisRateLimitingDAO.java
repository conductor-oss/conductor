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

import java.util.Optional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.dao.RateLimitingDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Conditional(AnyRedisCondition.class)
public class RedisRateLimitingDAO extends BaseDynoDAO implements RateLimitingDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRateLimitingDAO.class);

    private static final String TASK_RATE_LIMIT_BUCKET = "TASK_RATE_LIMIT_BUCKET";

    public RedisRateLimitingDAO(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
    }

    /**
     * This method evaluates if the {@link TaskDef} is rate limited or not based on {@link
     * TaskModel#getRateLimitPerFrequency()} and {@link TaskModel#getRateLimitFrequencyInSeconds()}
     * if not checks the {@link TaskModel} is rate limited or not based on {@link
     * TaskModel#getRateLimitPerFrequency()} and {@link TaskModel#getRateLimitFrequencyInSeconds()}
     *
     * <p>The rate limiting is implemented using the Redis constructs of sorted set and TTL of each
     * element in the rate limited bucket.
     *
     * <ul>
     *   <li>All the entries that are in the not in the frequency bucket are cleaned up by
     *       leveraging {@link JedisProxy#zremrangeByScore(String, String, String)}, this is done to
     *       make the next step of evaluation efficient
     *   <li>A current count(tasks executed within the frequency) is calculated based on the current
     *       time and the beginning of the rate limit frequency time(which is current time - {@link
     *       TaskModel#getRateLimitFrequencyInSeconds()} in millis), this is achieved by using
     *       {@link JedisProxy#zcount(String, double, double)}
     *   <li>Once the count is calculated then a evaluation is made to determine if it is within the
     *       bounds of {@link TaskModel#getRateLimitPerFrequency()}, if so the count is increased
     *       and an expiry TTL is added to the entry
     * </ul>
     *
     * @param task: which needs to be evaluated whether it is rateLimited or not
     * @return true: If the {@link TaskModel} is rateLimited false: If the {@link TaskModel} is not
     *     rateLimited
     */
    @Override
    public boolean exceedsRateLimitPerFrequency(TaskModel task, TaskDef taskDef) {
        // Check if the TaskDefinition is not null then pick the definition values or else pick from
        // the Task
        ImmutablePair<Integer, Integer> rateLimitPair =
                Optional.ofNullable(taskDef)
                        .map(
                                definition ->
                                        new ImmutablePair<>(
                                                definition.getRateLimitPerFrequency(),
                                                definition.getRateLimitFrequencyInSeconds()))
                        .orElse(
                                new ImmutablePair<>(
                                        task.getRateLimitPerFrequency(),
                                        task.getRateLimitFrequencyInSeconds()));

        int rateLimitPerFrequency = rateLimitPair.getLeft();
        int rateLimitFrequencyInSeconds = rateLimitPair.getRight();
        if (rateLimitPerFrequency <= 0 || rateLimitFrequencyInSeconds <= 0) {
            LOGGER.debug(
                    "Rate limit not applied to the Task: {}  either rateLimitPerFrequency: {} or rateLimitFrequencyInSeconds: {} is 0 or less",
                    task,
                    rateLimitPerFrequency,
                    rateLimitFrequencyInSeconds);
            return false;
        } else {
            LOGGER.debug(
                    "Evaluating rate limiting for TaskId: {} with TaskDefinition of: {} with rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {}",
                    task.getTaskId(),
                    task.getTaskDefName(),
                    rateLimitPerFrequency,
                    rateLimitFrequencyInSeconds);
            long currentTimeEpochMillis = System.currentTimeMillis();
            long currentTimeEpochMinusRateLimitBucket =
                    currentTimeEpochMillis - (rateLimitFrequencyInSeconds * 1000L);
            String key = nsKey(TASK_RATE_LIMIT_BUCKET, task.getTaskDefName());
            jedisProxy.zremrangeByScore(
                    key, "-inf", String.valueOf(currentTimeEpochMinusRateLimitBucket));
            int currentBucketCount =
                    Math.toIntExact(
                            jedisProxy.zcount(
                                    key,
                                    currentTimeEpochMinusRateLimitBucket,
                                    currentTimeEpochMillis));
            if (currentBucketCount < rateLimitPerFrequency) {
                jedisProxy.zadd(
                        key, currentTimeEpochMillis, String.valueOf(currentTimeEpochMillis));
                jedisProxy.expire(key, rateLimitFrequencyInSeconds);
                LOGGER.info(
                        "TaskId: {} with TaskDefinition of: {} has rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} within the rate limit with current count {}",
                        task.getTaskId(),
                        task.getTaskDefName(),
                        rateLimitPerFrequency,
                        rateLimitFrequencyInSeconds,
                        ++currentBucketCount);
                Monitors.recordTaskRateLimited(task.getTaskDefName(), rateLimitPerFrequency);
                return false;
            } else {
                LOGGER.info(
                        "TaskId: {} with TaskDefinition of: {} has rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} is out of bounds of rate limit with current count {}",
                        task.getTaskId(),
                        task.getTaskDefName(),
                        rateLimitPerFrequency,
                        rateLimitFrequencyInSeconds,
                        currentBucketCount);
                return true;
            }
        }
    }
}
