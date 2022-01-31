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
package com.netflix.conductor.postgres.dao;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.postgres.util.Query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Uninterruptibles;

public class PostgresQueueDAO extends PostgresBaseDAO implements QueueDAO {

    private static final Long UNACK_SCHEDULE_MS = 60_000L;

    public PostgresQueueDAO(ObjectMapper om, DataSource ds) {
        super(om, ds);

        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(
                        this::processAllUnacks,
                        UNACK_SCHEDULE_MS,
                        UNACK_SCHEDULE_MS,
                        TimeUnit.MILLISECONDS);
        logger.debug(PostgresQueueDAO.class.getName() + " is ready to serve");
    }

    @Override
    public void push(String queueName, String messageId, long offsetTimeInSecond) {
        push(queueName, messageId, 0, offsetTimeInSecond);
    }

    @Override
    public void push(String queueName, String messageId, int priority, long offsetTimeInSecond) {
        withTransaction(
                tx -> pushMessage(tx, queueName, messageId, null, priority, offsetTimeInSecond));
    }

    @Override
    public void push(String queueName, List<Message> messages) {
        withTransaction(
                tx ->
                        messages.forEach(
                                message ->
                                        pushMessage(
                                                tx,
                                                queueName,
                                                message.getId(),
                                                message.getPayload(),
                                                message.getPriority(),
                                                0)));
    }

    @Override
    public boolean pushIfNotExists(String queueName, String messageId, long offsetTimeInSecond) {
        return pushIfNotExists(queueName, messageId, 0, offsetTimeInSecond);
    }

    @Override
    public boolean pushIfNotExists(
            String queueName, String messageId, int priority, long offsetTimeInSecond) {
        return getWithRetriedTransactions(
                tx -> {
                    if (!existsMessage(tx, queueName, messageId)) {
                        pushMessage(tx, queueName, messageId, null, priority, offsetTimeInSecond);
                        return true;
                    }
                    return false;
                });
    }

    @Override
    public List<String> pop(String queueName, int count, int timeout) {
        return pollMessages(queueName, count, timeout).stream()
                .map(Message::getId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> pollMessages(String queueName, int count, int timeout) {
        if (timeout < 1) {
            List<Message> messages =
                    getWithTransactionWithOutErrorPropagation(
                            tx -> popMessages(tx, queueName, count, timeout));
            if (messages == null) {
                return new ArrayList<>();
            }
            return messages;
        }

        long start = System.currentTimeMillis();
        final List<Message> messages = new ArrayList<>();

        while (true) {
            List<Message> messagesSlice =
                    getWithTransactionWithOutErrorPropagation(
                            tx -> popMessages(tx, queueName, count - messages.size(), timeout));
            if (messagesSlice == null) {
                logger.warn(
                        "Unable to poll {} messages from {} due to tx conflict, only {} popped",
                        count,
                        queueName,
                        messages.size());
                // conflict could have happened, returned messages popped so far
                return messages;
            }

            messages.addAll(messagesSlice);
            if (messages.size() >= count || ((System.currentTimeMillis() - start) > timeout)) {
                return messages;
            }
            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void remove(String queueName, String messageId) {
        withTransaction(tx -> removeMessage(tx, queueName, messageId));
    }

    @Override
    public int getSize(String queueName) {
        final String GET_QUEUE_SIZE = "SELECT COUNT(*) FROM queue_message WHERE queue_name = ?";
        return queryWithTransaction(
                GET_QUEUE_SIZE, q -> ((Long) q.addParameter(queueName).executeCount()).intValue());
    }

    @Override
    public boolean ack(String queueName, String messageId) {
        return getWithRetriedTransactions(tx -> removeMessage(tx, queueName, messageId));
    }

    @Override
    public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
        long updatedOffsetTimeInSecond = unackTimeout / 1000;

        final String UPDATE_UNACK_TIMEOUT =
                "UPDATE queue_message SET offset_time_seconds = ?, deliver_on = (current_timestamp + (? ||' seconds')::interval) WHERE queue_name = ? AND message_id = ?";

        return queryWithTransaction(
                        UPDATE_UNACK_TIMEOUT,
                        q ->
                                q.addParameter(updatedOffsetTimeInSecond)
                                        .addParameter(updatedOffsetTimeInSecond)
                                        .addParameter(queueName)
                                        .addParameter(messageId)
                                        .executeUpdate())
                == 1;
    }

    @Override
    public void flush(String queueName) {
        final String FLUSH_QUEUE = "DELETE FROM queue_message WHERE queue_name = ?";
        executeWithTransaction(FLUSH_QUEUE, q -> q.addParameter(queueName).executeDelete());
    }

    @Override
    public Map<String, Long> queuesDetail() {
        final String GET_QUEUES_DETAIL =
                "SELECT queue_name, (SELECT count(*) FROM queue_message WHERE popped = false AND queue_name = q.queue_name) AS size FROM queue q FOR SHARE SKIP LOCKED";
        return queryWithTransaction(
                GET_QUEUES_DETAIL,
                q ->
                        q.executeAndFetch(
                                rs -> {
                                    Map<String, Long> detail = Maps.newHashMap();
                                    while (rs.next()) {
                                        String queueName = rs.getString("queue_name");
                                        Long size = rs.getLong("size");
                                        detail.put(queueName, size);
                                    }
                                    return detail;
                                }));
    }

    @Override
    public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
        // @formatter:off
        final String GET_QUEUES_DETAIL_VERBOSE =
                "SELECT queue_name, \n"
                        + "       (SELECT count(*) FROM queue_message WHERE popped = false AND queue_name = q.queue_name) AS size,\n"
                        + "       (SELECT count(*) FROM queue_message WHERE popped = true AND queue_name = q.queue_name) AS uacked \n"
                        + "FROM queue q FOR SHARE SKIP LOCKED";
        // @formatter:on

        return queryWithTransaction(
                GET_QUEUES_DETAIL_VERBOSE,
                q ->
                        q.executeAndFetch(
                                rs -> {
                                    Map<String, Map<String, Map<String, Long>>> result =
                                            Maps.newHashMap();
                                    while (rs.next()) {
                                        String queueName = rs.getString("queue_name");
                                        Long size = rs.getLong("size");
                                        Long queueUnacked = rs.getLong("uacked");
                                        result.put(
                                                queueName,
                                                ImmutableMap.of(
                                                        "a",
                                                        ImmutableMap
                                                                .of( // sharding not implemented,
                                                                        // returning only
                                                                        // one shard with all the
                                                                        // info
                                                                        "size",
                                                                        size,
                                                                        "uacked",
                                                                        queueUnacked)));
                                    }
                                    return result;
                                }));
    }

    /**
     * Un-pop all un-acknowledged messages for all queues.
     *
     * @since 1.11.6
     */
    public void processAllUnacks() {
        logger.trace("processAllUnacks started");

        getWithRetriedTransactions(
                tx -> {
                    String LOCK_TASKS =
                            "SELECT queue_name, message_id FROM queue_message WHERE popped = true AND (deliver_on + (60 ||' seconds')::interval)  <  current_timestamp limit 1000 FOR UPDATE SKIP LOCKED";

                    List<QueueMessage> messages =
                            query(
                                    tx,
                                    LOCK_TASKS,
                                    p ->
                                            p.executeAndFetch(
                                                    rs -> {
                                                        List<QueueMessage> results =
                                                                new ArrayList<QueueMessage>();
                                                        while (rs.next()) {
                                                            QueueMessage qm = new QueueMessage();
                                                            qm.queueName =
                                                                    rs.getString("queue_name");
                                                            qm.messageId =
                                                                    rs.getString("message_id");
                                                            results.add(qm);
                                                        }
                                                        return results;
                                                    }));

                    if (messages.size() == 0) {
                        return 0;
                    }

                    Map<String, List<String>> queueMessageMap = new HashMap<String, List<String>>();
                    for (QueueMessage qm : messages) {
                        if (!queueMessageMap.containsKey(qm.queueName)) {
                            queueMessageMap.put(qm.queueName, new ArrayList<String>());
                        }
                        queueMessageMap.get(qm.queueName).add(qm.messageId);
                    }

                    int totalUnacked = 0;
                    for (String queueName : queueMessageMap.keySet()) {
                        Integer unacked = 0;
                        ;
                        try {
                            final List<String> msgIds = queueMessageMap.get(queueName);
                            final String UPDATE_POPPED =
                                    String.format(
                                            "UPDATE queue_message SET popped = false WHERE queue_name = ? and message_id IN (%s)",
                                            Query.generateInBindings(msgIds.size()));

                            unacked =
                                    query(
                                            tx,
                                            UPDATE_POPPED,
                                            q ->
                                                    q.addParameter(queueName)
                                                            .addParameters(msgIds)
                                                            .executeUpdate());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        totalUnacked += unacked;
                        logger.debug("Unacked {} messages from all queues", unacked);
                    }

                    if (totalUnacked > 0) {
                        logger.debug("Unacked {} messages from all queues", totalUnacked);
                    }
                    return totalUnacked;
                });
    }

    @Override
    public void processUnacks(String queueName) {
        final String PROCESS_UNACKS =
                "UPDATE queue_message SET popped = false WHERE queue_name = ? AND popped = true AND (current_timestamp - (60 ||' seconds')::interval)  > deliver_on";
        executeWithTransaction(PROCESS_UNACKS, q -> q.addParameter(queueName).executeUpdate());
    }

    @Override
    public boolean resetOffsetTime(String queueName, String messageId) {
        long offsetTimeInSecond = 0; // Reset to 0
        final String SET_OFFSET_TIME =
                "UPDATE queue_message SET offset_time_seconds = ?, deliver_on = (current_timestamp + (? ||' seconds')::interval) \n"
                        + "WHERE queue_name = ? AND message_id = ?";

        return queryWithTransaction(
                SET_OFFSET_TIME,
                q ->
                        q.addParameter(offsetTimeInSecond)
                                        .addParameter(offsetTimeInSecond)
                                        .addParameter(queueName)
                                        .addParameter(messageId)
                                        .executeUpdate()
                                == 1);
    }

    private boolean existsMessage(Connection connection, String queueName, String messageId) {
        final String EXISTS_MESSAGE =
                "SELECT EXISTS(SELECT 1 FROM queue_message WHERE queue_name = ? AND message_id = ?) FOR SHARE";
        return query(
                connection,
                EXISTS_MESSAGE,
                q -> q.addParameter(queueName).addParameter(messageId).exists());
    }

    private void pushMessage(
            Connection connection,
            String queueName,
            String messageId,
            String payload,
            Integer priority,
            long offsetTimeInSecond) {

        createQueueIfNotExists(connection, queueName);

        String UPDATE_MESSAGE =
                "UPDATE queue_message SET payload=?, deliver_on=(current_timestamp + (? ||' seconds')::interval) WHERE queue_name = ? AND message_id = ?";
        int rowsUpdated =
                query(
                        connection,
                        UPDATE_MESSAGE,
                        q ->
                                q.addParameter(payload)
                                        .addParameter(offsetTimeInSecond)
                                        .addParameter(queueName)
                                        .addParameter(messageId)
                                        .executeUpdate());

        if (rowsUpdated == 0) {
            String PUSH_MESSAGE =
                    "INSERT INTO queue_message (deliver_on, queue_name, message_id, priority, offset_time_seconds, payload) VALUES ((current_timestamp + (? ||' seconds')::interval), ?,?,?,?,?) ON CONFLICT (queue_name,message_id) DO UPDATE SET payload=excluded.payload, deliver_on=excluded.deliver_on";
            execute(
                    connection,
                    PUSH_MESSAGE,
                    q ->
                            q.addParameter(offsetTimeInSecond)
                                    .addParameter(queueName)
                                    .addParameter(messageId)
                                    .addParameter(priority)
                                    .addParameter(offsetTimeInSecond)
                                    .addParameter(payload)
                                    .executeUpdate());
        }
    }

    private boolean removeMessage(Connection connection, String queueName, String messageId) {
        final String REMOVE_MESSAGE =
                "DELETE FROM queue_message WHERE queue_name = ? AND message_id = ?";
        return query(
                connection,
                REMOVE_MESSAGE,
                q -> q.addParameter(queueName).addParameter(messageId).executeDelete());
    }

    private List<Message> peekMessages(Connection connection, String queueName, int count) {
        if (count < 1) {
            return Collections.emptyList();
        }

        final String PEEK_MESSAGES =
                "SELECT message_id, priority, payload FROM queue_message WHERE queue_name = ? AND popped = false AND deliver_on <= (current_timestamp + (1000 ||' microseconds')::interval) ORDER BY priority DESC, deliver_on, created_on LIMIT ? FOR UPDATE SKIP LOCKED";

        return query(
                connection,
                PEEK_MESSAGES,
                p ->
                        p.addParameter(queueName)
                                .addParameter(count)
                                .executeAndFetch(
                                        rs -> {
                                            List<Message> results = new ArrayList<>();
                                            while (rs.next()) {
                                                Message m = new Message();
                                                m.setId(rs.getString("message_id"));
                                                m.setPriority(rs.getInt("priority"));
                                                m.setPayload(rs.getString("payload"));
                                                results.add(m);
                                            }
                                            return results;
                                        }));
    }

    private List<Message> popMessages(
            Connection connection, String queueName, int count, int timeout) {
        List<Message> messages = peekMessages(connection, queueName, count);

        if (messages.isEmpty()) {
            return messages;
        }

        List<Message> poppedMessages = new ArrayList<>();
        for (Message message : messages) {
            final String POP_MESSAGE =
                    "UPDATE queue_message SET popped = true WHERE queue_name = ? AND message_id = ? AND popped = false";
            int result =
                    query(
                            connection,
                            POP_MESSAGE,
                            q ->
                                    q.addParameter(queueName)
                                            .addParameter(message.getId())
                                            .executeUpdate());

            if (result == 1) {
                poppedMessages.add(message);
            }
        }
        return poppedMessages;
    }

    @Override
    public boolean containsMessage(String queueName, String messageId) {
        return getWithRetriedTransactions(tx -> existsMessage(tx, queueName, messageId));
    }

    private void createQueueIfNotExists(Connection connection, String queueName) {
        logger.trace("Creating new queue '{}'", queueName);
        final String EXISTS_QUEUE =
                "SELECT EXISTS(SELECT 1 FROM queue WHERE queue_name = ?) FOR SHARE";
        boolean exists = query(connection, EXISTS_QUEUE, q -> q.addParameter(queueName).exists());
        if (!exists) {
            final String CREATE_QUEUE =
                    "INSERT INTO queue (queue_name) VALUES (?) ON CONFLICT (queue_name) DO NOTHING";
            execute(connection, CREATE_QUEUE, q -> q.addParameter(queueName).executeUpdate());
        }
    }

    private class QueueMessage {
        public String queueName;
        public String messageId;
    }
}
