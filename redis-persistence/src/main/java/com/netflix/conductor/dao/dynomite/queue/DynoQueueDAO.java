/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dao.dynomite.queue;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostBuilder;
import com.netflix.dyno.contrib.EurekaHostsSupplier;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.dyno.queues.DynoQueue;
import com.netflix.dyno.queues.Message;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.redis.RedisDynoQueue;
import com.netflix.dyno.queues.redis.RedisQueues;
import com.netflix.dyno.queues.shard.DynoShardSupplier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.commands.JedisCommands;

@Singleton
public class DynoQueueDAO implements QueueDAO {

    private static Logger logger = LoggerFactory.getLogger(DynoQueueDAO.class);

    private RedisQueues queues;

    private JedisCommands dynoClient;

    private JedisCommands dynoClientRead;

    private ShardSupplier ss;

    private String domain;

    private Configuration config;

    @Inject
    public DynoQueueDAO(RedisQueues queues) {
        this.queues = queues;
    }

    @Deprecated
    public DynoQueueDAO(DiscoveryClient dc, Configuration config) {

        logger.info("DynoQueueDAO::INIT");

        this.config = config;
        this.domain = config.getProperty("workflow.dyno.keyspace.domain", null);
        String cluster = config.getProperty("workflow.dynomite.cluster", null);
        final int readConnPort = config.getIntProperty("queues.dynomite.nonQuorum.port", 22122);

        EurekaHostsSupplier hostSupplier = new EurekaHostsSupplier(cluster, dc) {
            @Override
            public List<Host> getHosts() {
                List<Host> hosts = super.getHosts();
                List<Host> updatedHosts = new ArrayList<>(hosts.size());
                hosts.forEach(host -> updatedHosts.add(
                        new HostBuilder()
                                .setHostname(host.getHostName())
                                .setIpAddress(host.getIpAddress())
                                .setPort(readConnPort)
                                .setRack(host.getRack())
                                .setDatacenter(host.getDatacenter())
                                .setStatus(host.isUp() ? Host.Status.Up : Host.Status.Down)
                                .createHost()
                ));
                return updatedHosts;
            }
        };

        this.dynoClientRead = new DynoJedisClient.Builder()
            .withApplicationName(config.getAppId())
            .withDynomiteClusterName(cluster)
            .withHostSupplier(hostSupplier)
            .withConnectionPoolConsistency("DC_ONE")
            .build();

        DynoJedisClient dyno = new DynoJedisClient.Builder()
            .withApplicationName(config.getAppId())
            .withDynomiteClusterName(cluster)
            .withDiscoveryClient(dc)
            .build();

        this.dynoClient = dyno;

        String region = config.getRegion();
        String localDC = config.getAvailabilityZone();

        if (localDC == null) {
            throw new Error("Availability zone is not defined.  Ensure Configuration.getAvailabilityZone() returns a non-null and non-empty value.");
        }

        localDC = localDC.replaceAll(region, "");
        this.ss = new DynoShardSupplier(dyno.getConnPool().getConfiguration().getHostSupplier(), region, localDC);
        init();
    }

    @Deprecated
    public DynoQueueDAO(JedisCommands dynoClient, JedisCommands dynoClientRead, ShardSupplier ss, Configuration config) {
        this.dynoClient = dynoClient;
        this.dynoClientRead = dynoClient;
        this.ss = ss;
        this.config = config;
        init();
    }

    @Deprecated
    private void init() {

        String rootNamespace = config.getProperty("workflow.namespace.queue.prefix", null);
        String stack = config.getStack();
        String prefix = rootNamespace + "." + stack;
        if (domain != null) {
            prefix = prefix + "." + domain;
        }

        queues = new RedisQueues(dynoClient, dynoClientRead, prefix, ss, 60_000, 60_000);
        logger.info("DynoQueueDAO initialized with prefix " + prefix + "!");
    }

    @Override
    public void push(String queueName, String id, long offsetTimeInSecond) {
        push(queueName, id, -1, offsetTimeInSecond);
    }

    @Override
    public void push(String queueName, String id, int priority, long offsetTimeInSecond) {
        Message msg = new Message(id, null);
        msg.setTimeout(offsetTimeInSecond, TimeUnit.SECONDS);
        if (priority >= 0 && priority <= 99) {
            msg.setPriority(priority);
        }
        queues.get(queueName).push(Collections.singletonList(msg));
    }

    @Override
    public void push(String queueName, List<com.netflix.conductor.core.events.queue.Message> messages) {
        List<Message> msgs = messages.stream()
				.map(msg -> {
				    Message m = new Message(msg.getId(), msg.getPayload());
				    if (msg.getPriority() > 0) {
                        m.setPriority(msg.getPriority());
                    }
				    return m;
				})
				.collect(Collectors.toList());
        queues.get(queueName).push(msgs);
    }

    @Override
    public boolean pushIfNotExists(String queueName, String id, long offsetTimeInSecond) {
        return pushIfNotExists(queueName, id, -1, offsetTimeInSecond);
    }

    @Override
    public boolean pushIfNotExists(String queueName, String id, int priority, long offsetTimeInSecond) {
        DynoQueue queue = queues.get(queueName);
        if (queue.get(id) != null) {
            return false;
        }
        Message msg = new Message(id, null);
        if (priority >= 0 && priority <= 99) {
            msg.setPriority(priority);
        }
        msg.setTimeout(offsetTimeInSecond, TimeUnit.SECONDS);
        queue.push(Collections.singletonList(msg));
        return true;
    }

    @Override
    public List<String> pop(String queueName, int count, int timeout) {
        List<Message> msg = queues.get(queueName).pop(count, timeout, TimeUnit.MILLISECONDS);
        return msg.stream()
				.map(Message::getId)
				.collect(Collectors.toList());
    }

    @Override
    public List<com.netflix.conductor.core.events.queue.Message> pollMessages(String queueName, int count, int timeout) {
        List<Message> msgs = queues.get(queueName).pop(count, timeout, TimeUnit.MILLISECONDS);
        return msgs.stream()
				.map(msg -> new com.netflix.conductor.core.events.queue.Message(msg.getId(), msg.getPayload(), null, msg.getPriority()))
				.collect(Collectors.toList());
    }

    @Override
    public void remove(String queueName, String messageId) {
        queues.get(queueName).remove(messageId);
    }

    @Override
    public int getSize(String queueName) {
        return (int) queues.get(queueName).size();
    }

    @Override
    public boolean ack(String queueName, String messageId) {
        return queues.get(queueName).ack(messageId);

    }

    @Override
    public boolean setUnackTimeout(String queueName, String messageId, long timeout) {
        return queues.get(queueName).setUnackTimeout(messageId, timeout);
    }

    @Override
    public void flush(String queueName) {
        DynoQueue queue = queues.get(queueName);
        if (queue != null) {
            queue.clear();
        }
    }

    @Override
    public Map<String, Long> queuesDetail() {
        Map<String, Long> map = queues.queues().stream().collect(Collectors.toMap(queue -> queue.getName(), q -> q.size()));
        return map;
    }

    @Override
    public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
        return queues.queues().stream()
                .collect(Collectors.toMap(DynoQueue::getName, DynoQueue::shardSizes));
    }

    public void processUnacks(String queueName) {
        ((RedisDynoQueue) queues.get(queueName)).processUnacks();
    }

    @Override
    public boolean resetOffsetTime(String queueName, String id) {
        DynoQueue queue = queues.get(queueName);
        return queue.setTimeout(id, 0);

    }

    @Override
    public boolean containsMessage(String queueName, String messageId) {
        DynoQueue queue = queues.get(queueName);
        Message message = queue.get(messageId);
        return Objects.nonNull(message);
    }
}
