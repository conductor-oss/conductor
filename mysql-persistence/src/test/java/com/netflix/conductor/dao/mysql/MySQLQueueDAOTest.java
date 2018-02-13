package com.netflix.conductor.dao.mysql;

import com.google.common.collect.ImmutableList;

import com.netflix.conductor.core.events.queue.Message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sql2o.Connection;

public class MySQLQueueDAOTest extends MySQLBaseDAOTest {

	private MySQLQueueDAO dao;

	@Before
	public void setup() throws Exception {
		dao = new MySQLQueueDAO(objectMapper, testSql2o);
		resetAllData();
	}

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void test() {
		String queueName = "TestQueue";
		long offsetTimeInSecond = 0;

		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			dao.push(queueName, messageId, offsetTimeInSecond);
		}
		int size = dao.getSize(queueName);
		assertEquals(10, size);
		Map<String, Long> details = dao.queuesDetail();
		assertEquals(1, details.size());
		assertEquals(10L, details.get(queueName).longValue());


		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			dao.pushIfNotExists(queueName, messageId, offsetTimeInSecond);
		}

		List<String> popped = dao.pop(queueName, 10, 100);
		assertNotNull(popped);
		assertEquals(10, popped.size());

		Map<String, Map<String, Map<String, Long>>> verbose = dao.queuesDetailVerbose();
		assertEquals(1, verbose.size());
		long shardSize = verbose.get(queueName).get("a").get("size");
		long unackedSize = verbose.get(queueName).get("a").get("uacked");
		assertEquals(0, shardSize);
		assertEquals(10, unackedSize);

		popped.forEach(messageId -> dao.ack(queueName, messageId));

		verbose = dao.queuesDetailVerbose();
		assertEquals(1, verbose.size());
		shardSize = verbose.get(queueName).get("a").get("size");
		unackedSize = verbose.get(queueName).get("a").get("uacked");
		assertEquals(0, shardSize);
		assertEquals(0, unackedSize);

		popped = dao.pop(queueName, 10, 100);
		assertNotNull(popped);
		assertEquals(0, popped.size());

		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			dao.pushIfNotExists(queueName, messageId, offsetTimeInSecond);
		}
		size = dao.getSize(queueName);
		assertEquals(10, size);

		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			dao.remove(queueName, messageId);
		}

		size = dao.getSize(queueName);
		assertEquals(0, size);

		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			dao.pushIfNotExists(queueName, messageId, offsetTimeInSecond);
		}
		dao.flush(queueName);
		size = dao.getSize(queueName);
		assertEquals(0, size);

	}

    /**
     * Test fix for https://github.com/Netflix/conductor/issues/399
     * @since 1.8.2-rc5
     */
    @Test
    public void pollMessagesTest() {
        final List<Message> messages = new ArrayList<>();
        final String queueName = "issue399_testQueue";
        final int totalSize = 10;

        for(int i = 0; i < totalSize; i++) {
            String payload = "{\"id\": " + i + ", \"msg\":\"test " + i + "\"}";
            messages.add(new Message("testmsg-" + i, payload, ""));
        }

        // Populate the queue with our test message batch
        dao.push(queueName, ImmutableList.copyOf(messages));


        // Assert that all messages were persisted and no extras are in there
        assertEquals("Queue size mismatch", totalSize, dao.getSize(queueName));

        final int firstPollSize = 3;
        List<Message> firstPoll = dao.pollMessages(queueName, firstPollSize, 100);
        assertNotNull("First poll was null", firstPoll);
        assertFalse("First poll was empty", firstPoll.isEmpty());
        assertEquals("First poll size mismatch", firstPollSize, firstPoll.size());

        for(int i = 0; i < firstPollSize; i++) {
            assertEquals(messages.get(i).getId(), firstPoll.get(i).getId());
            assertEquals(messages.get(i).getPayload(), firstPoll.get(i).getPayload());
        }

        final int secondPollSize = 4;
        List<Message> secondPoll = dao.pollMessages(queueName, secondPollSize, 100);
        assertNotNull("Second poll was null", secondPoll);
        assertFalse("Second poll was empty", secondPoll.isEmpty());
        assertEquals("Second poll size mismatch", secondPollSize, secondPoll.size());

        for(int i = 0; i < secondPollSize; i++) {
            assertEquals(messages.get(i + firstPollSize).getId(), secondPoll.get(i).getId());
            assertEquals(messages.get(i + firstPollSize).getPayload(), secondPoll.get(i).getPayload());
        }

        // Assert that the total queue size hasn't changed
        assertEquals("Total queue size should have remained the same", totalSize, dao.getSize(queueName));

        // Assert that our un-popped messages match our expected size
        final int expectedSize = totalSize - firstPollSize - secondPollSize;
        try(Connection c = testSql2o.open()) {
            String UNPOPPED = "SELECT COUNT(*) FROM queue_message WHERE queue_name = :queueName AND popped = false";
            int count = c.createQuery(UNPOPPED)
                         .addParameter("queueName", queueName)
                         .executeScalar(Integer.class);

            assertEquals("Remaining queue size mismatch", expectedSize, count);
        }
    }
}
