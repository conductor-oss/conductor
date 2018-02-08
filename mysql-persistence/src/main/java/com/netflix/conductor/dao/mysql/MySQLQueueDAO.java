package com.netflix.conductor.dao.mysql;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.lang.time.DateUtils;
import org.sql2o.Connection;
import org.sql2o.Sql2o;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.QueueDAO;

class MySQLQueueDAO extends MySQLBaseDAO implements QueueDAO {

	@Inject
	MySQLQueueDAO(ObjectMapper om, Sql2o sql2o) {
		super(om, sql2o);
	}

	@Override
	public void push(String queueName, String messageId, long offsetTimeInSecond) {
		withTransaction(tx -> pushMessage(tx, queueName, messageId, null, offsetTimeInSecond));
	}

	@Override
	public void push(String queueName, List<Message> messages) {
		withTransaction(tx ->
			messages.forEach(message ->
				pushMessage(tx, queueName, message.getId(), message.getPayload(), 0)
			)
		);
	}

	@Override
	public boolean pushIfNotExists(String queueName, String messageId, long offsetTimeInSecond) {
		return getWithTransaction(tx -> {
			if (!existsMessage(tx, queueName, messageId)) {
				pushMessage(tx, queueName, messageId, null, offsetTimeInSecond);
				return true;
			}
			return false;
		});
	}

	@Override
	public List<String> pop(String queueName, int count, int timeout) {
		long start = System.currentTimeMillis();
		List<String> foundsIds = peekMessages(queueName, count);

		while (foundsIds.size() < count && ((System.currentTimeMillis() - start) < timeout)) {
			Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
			foundsIds = peekMessages(queueName, count);
		}

		ImmutableList<String> messageIds = ImmutableList.copyOf(foundsIds);
		return getWithTransaction(tx -> popMessages(tx, queueName, messageIds));
	}

    @Override
    public List<Message> pollMessages(String queueName, int count, int timeout) {
        final long start = System.currentTimeMillis();
        return getWithTransaction(tx -> {
            List<String> peekedMessageIds = peekMessages(tx, queueName, count);
            while(peekedMessageIds.size() < count && ((System.currentTimeMillis() - start) < timeout)) {
                Uninterruptibles.sleepUninterruptibly(200, TimeUnit.MILLISECONDS);
                peekedMessageIds = peekMessages(queueName, count);
            }

            final List<String> msgIds = ImmutableList.copyOf(peekedMessageIds);
            List<Message> messages = readMessages(tx, queueName, msgIds);
            popMessages(tx, queueName, msgIds);

            return ImmutableList.copyOf(messages);
        });
    }

	@Override
	public void remove(String queueName, String messageId) {
		withTransaction(tx -> removeMessage(tx, queueName, messageId));
	}

	@Override
	public int getSize(String queueName) {
		String GET_QUEUE_SIZE = "SELECT COUNT(*) FROM queue_message WHERE queue_name = :queueName";
		return getWithTransaction(tx -> tx.createQuery(GET_QUEUE_SIZE).addParameter("queueName", queueName).executeScalar(Integer.class));
	}

	@Override
	public boolean ack(String queueName, String messageId) {
		return getWithTransaction(tx -> {
			if (existsMessage(tx, queueName, messageId)) {
				removeMessage(tx, queueName, messageId);
				return true;
			} else {
				return false;
			}
		});
	}

	@Override
	public boolean setUnackTimeout(String queueName, String messageId, long unackTimeout) {
		long updatedOffsetTimeInSecond = unackTimeout/1000;

		String UPDATE_UNACK_TIMEOUT =
			"UPDATE queue_message SET offset_time_seconds = :offsetSeconds, deliver_on = TIMESTAMPADD(SECOND,:offsetSeconds,created_on) \n" +
			"WHERE queue_name = :queueName AND message_id = :messageId";

		return getWithTransaction(tx ->
			tx.createQuery(UPDATE_UNACK_TIMEOUT)
				.addParameter("queueName", queueName)
				.addParameter("messageId", messageId)
				.addParameter("offsetSeconds", updatedOffsetTimeInSecond)
				.executeUpdate()
				.getResult()
		) == 1;
	}

	@Override
	public void flush(String queueName) {
		String FLUSH_QUEUE = "DELETE FROM queue_message WHERE queue_name = :queueName";
		withTransaction(tx -> tx.createQuery(FLUSH_QUEUE).addParameter("queueName", queueName).executeUpdate());
	}

	@Override
	public Map<String, Long> queuesDetail() {
		Map<String, Long> detail = Maps.newHashMap();

		String GET_QUEUES_DETAIL =
			"SELECT queue_name, (SELECT count(*) FROM queue_message WHERE popped = false AND queue_name = q.queue_name) AS size FROM queue q";

		withTransaction(tx -> tx.createQuery(GET_QUEUES_DETAIL).executeAndFetchTable().asList().forEach(row -> {
			String queueName = (String)row.get("queue_name");
			Number queueSize = (Number)row.get("size");
			detail.put(queueName, queueSize.longValue());
		}));

		return detail;
	}

	@Override
	public Map<String, Map<String, Map<String, Long>>> queuesDetailVerbose() {
		Map<String, Map<String, Map<String, Long>>> result = Maps.newHashMap();

		String GET_QUEUES_DETAIL_VERBOSE =
			"SELECT queue_name, \n" +
			"       (SELECT count(*) FROM queue_message WHERE popped = false AND queue_name = q.queue_name) AS size,\n" +
			"       (SELECT count(*) FROM queue_message WHERE popped = true AND queue_name = q.queue_name) AS uacked \n" +
			"FROM queue q";

		withTransaction(tx -> tx.createQuery(GET_QUEUES_DETAIL_VERBOSE).executeAndFetchTable().asList().forEach(row -> {
			String queueName = (String)row.get("queue_name");
			Number queueSize = (Number)row.get("size");
			Number queueUnacked = (Number)row.get("uacked");
			result.put(queueName, ImmutableMap.of(
					"a", ImmutableMap.of(   //sharding not implemented, returning only one shard with all the info
						"size", queueSize.longValue(),
						"uacked", queueUnacked.longValue()
					)
				)
			);
		}));

		return result;
	}

	@Override
	public void processUnacks(String queueName) {
		String PROCESS_UNACKS = "UPDATE queue_message SET popped = false WHERE CURRENT_TIMESTAMP > deliver_on AND popped = true";
		withTransaction(tx -> tx.createQuery(PROCESS_UNACKS).executeUpdate());
	}

	@Override
	public boolean setOffsetTime(String queueName, String messageId, long offsetTimeInSecond) {
		String SET_OFFSET_TIME =
				"UPDATE queue_message SET offset_time_seconds = :offsetSeconds, deliver_on = TIMESTAMPADD(SECOND,:offsetSeconds,created_on) \n" +
						"WHERE queue_name = :queueName AND message_id = :messageId";

		return getWithTransaction(tx ->
				tx.createQuery(SET_OFFSET_TIME)
						.addParameter("queueName", queueName)
						.addParameter("messageId", messageId)
						.addParameter("offsetSeconds", offsetTimeInSecond)
						.executeUpdate()
						.getResult()
		) == 1;
	}
	private boolean existsMessage(Connection connection, String queueName, String messageId) {
		String EXISTS_MESSAGE = "SELECT EXISTS(SELECT 1 FROM queue_message WHERE queue_name = :queueName AND message_id = :messageId)";
		return connection.createQuery(EXISTS_MESSAGE).addParameter("queueName", queueName).addParameter("messageId", messageId).executeScalar(Boolean.class);
	}

	private void pushMessage(Connection connection, String queueName, String messageId, String payload, long offsetTimeInSecond) {
		String PUSH_MESSAGE = "INSERT INTO queue_message (created_on, deliver_on, queue_name, message_id, offset_time_seconds, payload) VALUES (:createdOn, :deliverOn, :queueName, :messageId, :offsetSeconds, :payload)";
		String UPDATE_MESSAGE = "UPDATE queue_message SET payload = :payload WHERE queue_name = :queueName AND message_id = :messageId";

		createQueueIfNotExists(connection, queueName);

		Date now = DateUtils.truncate(new Date(), Calendar.SECOND);
		Date deliverTime = new Date(now.getTime() + (offsetTimeInSecond*1000));
		boolean exists = existsMessage(connection, queueName, messageId);

		if (!exists) {
			connection.createQuery(PUSH_MESSAGE)
					.addParameter("createdOn", now)
					.addParameter("deliverOn", deliverTime)
					.addParameter("queueName", queueName)
					.addParameter("messageId", messageId)
					.addParameter("offsetSeconds", offsetTimeInSecond)
					.addParameter("payload", payload).executeUpdate();
		} else {
			connection.createQuery(UPDATE_MESSAGE)
					.addParameter("queueName", queueName)
					.addParameter("messageId", messageId)
					.addParameter("payload", payload).executeUpdate();
		}
	}

	private void removeMessage(Connection connection, String queueName, String messageId) {
		String REMOVE_MESSAGE = "DELETE FROM queue_message WHERE queue_name = :queueName AND message_id = :messageId";
		connection.createQuery(REMOVE_MESSAGE).addParameter("queueName", queueName).addParameter("messageId", messageId).executeUpdate();
	}

    private List<String> peekMessages(Connection tx, String queueName, int count) {
        if (count < 1) return Collections.emptyList();
        String PEEK_MESSAGES = "SELECT message_id FROM queue_message WHERE queue_name = :queueName AND popped = false LIMIT :count";
        return tx.createQuery(PEEK_MESSAGES)
                 .addParameter("queueName", queueName)
                 .addParameter("count", count)
                 .executeScalarList(String.class);
    }

    private List<String> peekMessages(String queueName, int count) {
        return getWithTransaction(tx -> peekMessages(tx, queueName, count));
    }

	private List<String> popMessages(Connection connection, String queueName, List<String> messageIds) {
		if (messageIds.isEmpty()) return messageIds;

		String POP_MESSAGES = "UPDATE queue_message SET popped = true WHERE queue_name = :queueName AND message_id IN (%s) AND popped = false";
		String query = generateQueryWithParametersListPlaceholders(POP_MESSAGES, messageIds.size());

		int result = connection.createQuery(query).addParameter("queueName", queueName).withParams(messageIds.toArray()).executeUpdate().getResult();

		if (result != messageIds.size()) {
			String message = String.format("could not pop all messages for given ids: %s (%d messages were popped)", messageIds, result);
			throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, message);
		}

		return messageIds;
	}

    private List<Message> readMessages(Connection tx, String queueName, List<String> messageIds) {
        if (messageIds.isEmpty()) return Collections.emptyList();

        String READ_MESSAGES = "SELECT message_id, payload FROM queue_message WHERE queue_name = :queueName AND message_id IN (%s) AND popped = false";
        String query = generateQueryWithParametersListPlaceholders(READ_MESSAGES, messageIds.size());

        List<Message> messages = tx.createQuery(query)
                                   .addParameter("queueName", queueName)
                                   .addColumnMapping("message_id", "id")
                                   .withParams(messageIds.toArray())
                                   .executeAndFetch(Message.class);

        if (messages.size() != messageIds.size()) {
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, "could not read all messages for given ids: " + messageIds);
        }

        return messages;
    }

	private void createQueueIfNotExists(Connection connection, String queueName) {
		String EXISTS_QUEUE = "SELECT EXISTS(SELECT 1 FROM queue WHERE queue_name = :queueName)";
		boolean queueExists = connection.createQuery(EXISTS_QUEUE).addParameter("queueName", queueName).executeScalar(Boolean.class);

		if (!queueExists) {
			logger.info("creating queue {}", queueName);
			String CREATE_QUEUE = "INSERT INTO queue (queue_name) VALUES (:queueName)";
			connection.createQuery(CREATE_QUEUE).addParameter("queueName", queueName).executeUpdate();
		}
	}
}
