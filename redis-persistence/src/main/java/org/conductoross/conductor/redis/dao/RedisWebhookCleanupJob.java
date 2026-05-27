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

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.conductoross.conductor.webhook.model.IncomingWebhookEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.redis.config.AnyRedisCondition;
import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dao.BaseDynoDAO;
import com.netflix.conductor.redis.jedis.JedisProxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodic walker that deletes expired entries from the {@code WEBHOOK_EVENT} hash.
 *
 * <p>Per-field TTL on a Redis hash isn't broadly available (HEXPIRE landed in Redis 7.4 and isn't
 * yet ubiquitous), so we do an HGETALL + parse + HDEL pass on the cron. Cheap at low cardinality;
 * if your incoming-event volume is high, prefer migrating to a per-key storage shape with TTL.
 *
 * <p>Same property surface as the SQL cleanup jobs:
 *
 * <ul>
 *   <li>{@code conductor.webhooks.cleanup.cron} (default hourly)
 *   <li>{@code conductor.webhooks.cleanup.retention-duration} (default 7 days)
 *   <li>{@code conductor.webhooks.cleanup.enabled} (default true)
 * </ul>
 */
@Component
@Conditional(AnyRedisCondition.class)
@ConditionalOnProperty(
        name = "conductor.webhooks.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Slf4j
public class RedisWebhookCleanupJob extends BaseDynoDAO {

    private static final String WEBHOOK_EVENT = "WEBHOOK_EVENT";
    private static final String CLEANUP_LEASE = "WEBHOOK_CLEANUP_LEASE";

    private Duration retentionDuration;
    private Duration leaseTtl = Duration.ofMinutes(5);
    private final String holderId =
            ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID();

    public RedisWebhookCleanupJob(
            JedisProxy jedisProxy,
            ObjectMapper objectMapper,
            ConductorProperties conductorProperties,
            RedisProperties properties,
            @Value("${conductor.webhooks.cleanup.retention-duration:PT168H}")
                    Duration retentionDuration) {
        super(jedisProxy, objectMapper, conductorProperties, properties);
        this.retentionDuration = retentionDuration;
    }

    void setRetentionDuration(Duration retentionDuration) {
        this.retentionDuration = retentionDuration;
    }

    void setLeaseTtl(Duration leaseTtl) {
        this.leaseTtl = leaseTtl;
    }

    /**
     * Atomically claim the cleanup lease via {@code SET key value NX PX ttl}. Returns true iff
     * no live lease exists. The key auto-expires so a crashed lease holder doesn't block other
     * instances beyond {@link #leaseTtl}.
     */
    private boolean tryAcquireLease() {
        try {
            String leaseKey = nsKey(CLEANUP_LEASE);
            String acquired =
                    jedisProxy.setWithExpiryInMilliIfNotExists(
                            leaseKey, holderId, leaseTtl.toMillis());
            return "OK".equals(acquired);
        } catch (Exception e) {
            log.warn("webhook cleanup lease acquisition failed", e);
            return false;
        }
    }

    @Scheduled(cron = "${conductor.webhooks.cleanup.cron:0 0 * * * *}")
    public void run() {
        if (!tryAcquireLease()) {
            log.debug("webhook event cleanup: lease held elsewhere, skipping tick");
            return;
        }
        String key = nsKey(WEBHOOK_EVENT);
        Map<String, String> all = jedisProxy.hgetAll(key);
        if (all == null || all.isEmpty()) {
            return;
        }
        long threshold = System.currentTimeMillis() - retentionDuration.toMillis();
        List<String> toDelete = new ArrayList<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            try {
                IncomingWebhookEvent event =
                        objectMapper.readValue(entry.getValue(), IncomingWebhookEvent.class);
                if (event.getTimeStamp() > 0 && event.getTimeStamp() < threshold) {
                    toDelete.add(entry.getKey());
                }
            } catch (Exception e) {
                log.warn(
                        "unparseable webhook event {} during cleanup; leaving in place",
                        entry.getKey(),
                        e);
            }
        }
        if (toDelete.isEmpty()) {
            return;
        }
        jedisProxy.hdel(key, toDelete.toArray(new String[0]));
        log.info(
                "webhook event cleanup: deleted {} entries older than {}",
                toDelete.size(),
                retentionDuration);
    }
}
