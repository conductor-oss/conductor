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
package io.orkes.conductor.mq.dao;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.jedis.JedisCommands;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.orkes.conductor.mq.ConductorQueue;
import io.orkes.conductor.mq.QueueMessage;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.params.ZAddParams;

@Slf4j
public abstract class BaseRedisQueueDAO implements QueueDAO {

    private final String queueNamespace;

    private final String queueShard;

    private final Cache<String, ConductorQueue> queues;

    protected final RedisProperties redisProperties;

    protected final ConductorProperties conductorProperties;

    protected final ExecutorService queueMonitorExecutor;

    protected final JedisCommands jedisCommands;

    private final Set<String> queuesWithPayload =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public BaseRedisQueueDAO(
            JedisCommands jedisCommands,
            RedisProperties redisProperties,
            ConductorProperties conductorProperties) {
        this.jedisCommands = jedisCommands;
        this.redisProperties = redisProperties;
        this.conductorProperties = conductorProperties;

        // Stack is used for the backward compatibility with the DynoQueues
        this.queueNamespace =
                redisProperties.getQueueNamespacePrefix() + "." + conductorProperties.getStack();

        String az = redisProperties.getAvailabilityZone();
        this.queueShard = az.substring(az.length() - 1);
        this.queues =
                Caffeine.newBuilder()
                        .expireAfterAccess(
                                redisProperties.getQueueCacheExpireAfterAccessSeconds(),
                                TimeUnit.SECONDS)
                        .maximumSize(redisProperties.getQueueCacheMaxSize())
                        .build();
        this.queueMonitorExecutor =
                Executors.newFixedThreadPool(
                        10,
                        new BasicThreadFactory.Builder()
                                .namingPattern("queue-monitor-prefetch-%d")
                                .build());
    }

    private String getPayloadKey(String queueName) {
        return queueNamespace + ".QUEUE." + queueName + "." + queueShard + ".PAYLOAD";
    }

    protected abstract ConductorQueue getConductorQueue(
            String queueKey, ExecutorService executorService);

    private ConductorQueue get(String queueName) {
        // This scheme ensures full backward compatibility with existing DynoQueues as the drop in
        // replacement
        String queueKey = queueNamespace + ".QUEUE." + queueName + "." + queueShard;
        return queues.get(queueName, key -> getConductorQueue(queueKey, queueMonitorExecutor));
    }

    @Override
    public final void push(String queueName, String id, long offsetTimeInSecond) {
        QueueMessage message = new QueueMessage(id, "", offsetTimeInSecond * 1000);
        get(queueName).push(Arrays.asList(message));
    }

    @Override
    public final void push(String queueName, String id, int priority, long offsetTimeInSecond) {
        QueueMessage message = new QueueMessage(id, "", offsetTimeInSecond * 1000, priority);
        get(queueName).push(Arrays.asList(message));
    }

    @Override
    public final void push(String queueName, String id, int priority, Duration offsetTime) {
        QueueMessage message = new QueueMessage(id, "", offsetTime.toMillis(), priority);
        get(queueName).push(List.of(message));
    }

    @Override
    public final void push(String queueName, List<Message> messages) {
        List<QueueMessage> queueMessages = new ArrayList<>();
        for (Message message : messages) {
            queueMessages.add(
                    new QueueMessage(
                            message.getId(),
                            message.getPayload(),
                            message.getTimeout() * 1000L,
                            message.getPriority()));
            if (message.getPayload() != null && !message.getPayload().isEmpty()) {
                jedisCommands.hset(getPayloadKey(queueName), message.getId(), message.getPayload());
                queuesWithPayload.add(queueName);
            }
        }
        get(queueName).push(queueMessages);
    }

    @Override
    public final boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond) {
        if (get(queueName).exists(id)) {
            return false;
        }
        push(queueName, id, offsetTimeInSecond);
        return true;
    }

    @Override
    public final boolean pushIfNotExists(
            String queueName, String id, int priority, long offsetTimeInSecond) {
        if (get(queueName).exists(id)) {
            return false;
        }
        push(queueName, id, priority, offsetTimeInSecond);
        return true;
    }

    @Override
    public final List<String> pop(String queueName, int count, int timeout) {
        List<QueueMessage> messages = get(queueName).pop(count, timeout, TimeUnit.MILLISECONDS);
        return messages.stream().map(msg -> msg.getId()).collect(Collectors.toList());
    }

    @Override
    public final List<Message> pollMessages(String queueName, int count, int timeout) {
        List<QueueMessage> queueMessages =
                get(queueName).pop(count, timeout, TimeUnit.MILLISECONDS);
        boolean hasPayloads = queuesWithPayload.contains(queueName);
        if (!hasPayloads) {
            return queueMessages.stream()
                    .map(
                            msg ->
                                    new Message(
                                            msg.getId(),
                                            msg.getPayload(),
                                            msg.getId(),
                                            msg.getPriority()))
                    .collect(Collectors.toList());
        }
        String payloadKey = getPayloadKey(queueName);
        return queueMessages.stream()
                .map(
                        msg -> {
                            String payload = jedisCommands.hget(payloadKey, msg.getId());
                            return new Message(
                                    msg.getId(),
                                    payload != null ? payload : msg.getPayload(),
                                    msg.getId(),
                                    msg.getPriority());
                        })
                .collect(Collectors.toList());
    }

    @Override
    public final void remove(String queueName, String messageId) {
        get(queueName).remove(messageId);
        if (queuesWithPayload.contains(queueName)) {
            jedisCommands.hdel(getPayloadKey(queueName), messageId);
        }
    }

    @Override
    public final int getSize(String queueName) {
        return (int) get(queueName).size();
    }

    @Override
    public final boolean ack(String queueName, String messageId) {
        get(queueName).ack(messageId);
        if (queuesWithPayload.contains(queueName)) {
            jedisCommands.hdel(getPayloadKey(queueName), messageId);
        }
        return true;
    }

    @Override
    public final boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
        return get(queueName).setUnacktimeout(messageId, unackTimeout);
    }

    @Override
    public final boolean setUnackTimeoutIfShorter(
            String queueName, String messageId, long unackTimeout) {
        // ZADD XX LT: update score only if the new value is less (sooner delivery time).
        // This is atomic in Redis and avoids pushing the evaluation further out than whatever
        // shorter timeout another in-flight task already established.
        double score = System.currentTimeMillis() + unackTimeout;
        String queueKey = queueNamespace + ".QUEUE." + queueName + "." + queueShard;
        ZAddParams params = ZAddParams.zAddParams().xx().lt().ch();
        Long modified = jedisCommands.zadd(queueKey, score, messageId, params);
        return modified != null && modified > 0;
    }

    @Override
    public final void flush(String queueName) {
        get(queueName).flush();
        if (queuesWithPayload.remove(queueName)) {
            jedisCommands.del(getPayloadKey(queueName));
        }
    }

    @Override
    public Map<String, Long> queuesDetail() {
        Map<String, Long> sizes = new HashMap<>();
        for (Map.Entry<String, ConductorQueue> entry : queues.asMap().entrySet()) {
            sizes.put(entry.getKey(), entry.getValue().size());
        }
        return sizes;
    }

    @Override
    public final Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
        Map<String, Map<String, Map<String, Long>>> queueDetails = new HashMap<>();
        for (ConductorQueue conductorRedisQueue : queues.asMap().values()) {
            Map<String, Map<String, Long>> verbose = new HashMap<>();

            Map<String, Long> sizes = new HashMap<>();
            sizes.put("size", conductorRedisQueue.size());
            sizes.put("uacked", 0L); // we do not keep a separate queue
            verbose.put(conductorRedisQueue.getShardName(), sizes);
            queueDetails.put(conductorRedisQueue.getName(), verbose);
        }
        return queueDetails;
    }

    @Override
    public final boolean resetOffsetTime(String queueName, String id) {
        return get(queueName).setUnacktimeout(id, 0);
    }

    @Override
    public final boolean containsMessage(String queueName, String messageId) {
        return get(queueName).exists(messageId);
    }

    public boolean postpone(
            String queueName, String messageId, int priority, long postponeDurationInSeconds) {
        push(queueName, messageId, priority, postponeDurationInSeconds);
        return true;
    }
}
