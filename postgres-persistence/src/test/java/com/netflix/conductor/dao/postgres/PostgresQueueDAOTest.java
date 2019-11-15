/*
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
package com.netflix.conductor.dao.postgres;

import com.google.common.collect.ImmutableList;
import com.netflix.conductor.core.events.queue.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("Duplicates")
public class PostgresQueueDAOTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(PostgresQueueDAOTest.class);

	private PostgresDAOTestUtil testUtil;
	private PostgresQueueDAO dao;

    @Rule
    public TestName name = new TestName();

    @Rule
    public ExpectedException expected = ExpectedException.none();

	@Before
	public void setup() throws Exception {
        testUtil = new PostgresDAOTestUtil(name.getMethodName().toLowerCase());
		dao = new PostgresQueueDAO(testUtil.getObjectMapper(), testUtil.getDataSource());
	}

	@After
    public void teardown() throws Exception {
        testUtil.resetAllData();
        testUtil.getDataSource().close();
    }

	@Test
	public void complexQueueTest() {
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
			Message m = new Message("testmsg-" + i, payload, "");
			if (i % 2 == 0) {
				// Set priority on message with pair id
				m.setPriority(99-i);
			}
			messages.add(m);
		}

		// Populate the queue with our test message batch
		dao.push(queueName, ImmutableList.copyOf(messages));


		// Assert that all messages were persisted and no extras are in there
		assertEquals("Queue size mismatch", totalSize, dao.getSize(queueName));

		final int firstPollSize = 3;
		List<Message> firstPoll = dao.pollMessages(queueName, firstPollSize, 10_000);
		assertNotNull("First poll was null", firstPoll);
		assertFalse("First poll was empty", firstPoll.isEmpty());
		assertEquals("First poll size mismatch", firstPollSize, firstPoll.size());

		final int secondPollSize = 4;
		List<Message> secondPoll = dao.pollMessages(queueName, secondPollSize, 10_000);
		assertNotNull("Second poll was null", secondPoll);
		assertFalse("Second poll was empty", secondPoll.isEmpty());
		assertEquals("Second poll size mismatch", secondPollSize, secondPoll.size());

		// Assert that the total queue size hasn't changed
		assertEquals("Total queue size should have remained the same", totalSize, dao.getSize(queueName));

		// Assert that our un-popped messages match our expected size
		final long expectedSize = totalSize - firstPollSize - secondPollSize;
		try(Connection c = testUtil.getDataSource().getConnection()) {
			String UNPOPPED = "SELECT COUNT(*) FROM queue_message WHERE queue_name = ? AND popped = false";
			try(Query q = new Query(testUtil.getObjectMapper(), c, UNPOPPED)) {
				long count = q.addParameter(queueName).executeCount();
				assertEquals("Remaining queue size mismatch", expectedSize, count);
			}
		} catch (Exception ex) {
			fail(ex.getMessage());
		}
	}

	/**
	 * Test fix for https://github.com/Netflix/conductor/issues/448
	 * @since 1.8.2-rc5
	 */
	@Test
	public void pollDeferredMessagesTest() throws InterruptedException {
		final List<Message> messages = new ArrayList<>();
		final String queueName = "issue448_testQueue";
		final int totalSize = 10;

		for(int i = 0; i < totalSize; i++) {
			int offset = 0;
			if(i < 5){ offset = 0; }
			else if(i == 6 || i == 7){
				// Purposefully skipping id:5 to test out of order deliveries
				// Set id:6 and id:7 for a 2s delay to be picked up in the second polling batch
				offset = 5;
			} else {
				// Set all other queue messages to have enough of a delay that they won't accidentally
				// be picked up.
				offset = 10_000 + i;
			}

			String payload = "{\"id\": " + i + ",\"offset_time_seconds\":" + offset + "}";
			Message m = new Message("testmsg-" + i, payload, "");
			messages.add(m);
			dao.push(queueName, "testmsg-" + i, offset);
		}

		// Assert that all messages were persisted and no extras are in there
		assertEquals("Queue size mismatch", totalSize, dao.getSize(queueName));

		final int firstPollSize = 4;
		List<Message> firstPoll = dao.pollMessages(queueName, firstPollSize, 100);
		assertNotNull("First poll was null", firstPoll);
		assertFalse("First poll was empty", firstPoll.isEmpty());
		assertEquals("First poll size mismatch", firstPollSize, firstPoll.size());

		List<String> firstPollMessageIds = messages.stream().map(Message::getId).collect(Collectors.toList()).subList(0, firstPollSize + 1);

		for(int i = 0; i < firstPollSize; i++) {
			String actual = firstPoll.get(i).getId();
			assertTrue("Unexpected Id: " + actual, firstPollMessageIds.contains(actual));
		}

		final int secondPollSize = 3;

		// Sleep a bit to get the next batch of messages
		LOGGER.debug("Sleeping for second poll...");
		Thread.sleep(5_000);

		// Poll for many more messages than expected
		List<Message> secondPoll = dao.pollMessages(queueName, secondPollSize + 10, 100);
		assertNotNull("Second poll was null", secondPoll);
		assertFalse("Second poll was empty", secondPoll.isEmpty());
		assertEquals("Second poll size mismatch", secondPollSize, secondPoll.size());

		List<String> expectedIds = Arrays.asList("testmsg-4","testmsg-6","testmsg-7");
		for(int i = 0; i < secondPollSize; i++) {
			String actual = secondPoll.get(i).getId();
			assertTrue("Unexpected Id: " + actual, expectedIds.contains(actual));
		}

		// Assert that the total queue size hasn't changed
		assertEquals("Total queue size should have remained the same", totalSize, dao.getSize(queueName));

		// Assert that our un-popped messages match our expected size
		final long expectedSize = totalSize - firstPollSize - secondPollSize;
		try(Connection c = testUtil.getDataSource().getConnection()) {
			String UNPOPPED = "SELECT COUNT(*) FROM queue_message WHERE queue_name = ? AND popped = false";
			try(Query q = new Query(testUtil.getObjectMapper(), c, UNPOPPED)) {
				long count = q.addParameter(queueName).executeCount();
				assertEquals("Remaining queue size mismatch", expectedSize, count);
			}
		} catch (Exception ex) {
			fail(ex.getMessage());
		}
	}

	@Test
	public void processUnacksTest() {
		final String queueName = "process_unacks_test";
		// Count of messages in the queue(s)
		final int count = 10;
		// Number of messages to process acks for
		final int unackedCount = 4;
		// A secondary queue to make sure we don't accidentally process other queues
		final String otherQueueName = "process_unacks_test_other_queue";

		// Create testing queue with some messages (but not all) that will be popped/acked.
		for(int i = 0; i < count; i++) {
			int offset = 0;
			if(i >= unackedCount){ offset = 1_000_000; }

			dao.push(queueName, "unack-" + i, offset);
		}

		// Create a second queue to make sure that unacks don't occur for it
		for(int i = 0; i < count; i++) {
			dao.push(otherQueueName, "other-" + i, 0);
		}

		// Poll for first batch of messages (should be equal to unackedCount)
		List<Message> polled = dao.pollMessages(queueName, 100, 10_000);
		assertNotNull(polled);
		assertFalse(polled.isEmpty());
		assertEquals(unackedCount, polled.size());

		// Poll messages from the other queue so we know they don't get unacked later
		dao.pollMessages(otherQueueName, 100, 10_000);

		// Ack one of the polled messages
		assertTrue(dao.ack(queueName, "unack-1"));

		// Should have one less un-acked popped message in the queue
		Long uacked = dao.queuesDetailVerbose().get(queueName).get("a").get("uacked");
		assertNotNull(uacked);
		assertEquals(uacked.longValue(), unackedCount - 1);


		// Process unacks
		dao.processUnacks(queueName);

		// Check uacks for both queues after processing
		Map<String, Map<String, Map<String, Long>>> details = dao.queuesDetailVerbose();
		uacked = details.get(queueName).get("a").get("uacked");
		assertNotNull(uacked);
		assertEquals("There should be no unacked messages", uacked.longValue(), 0);

		Long otherUacked = details.get(otherQueueName).get("a").get("uacked");
		assertNotNull(otherUacked);
		assertEquals("Other queue should have unacked messages", otherUacked.longValue(), count);

		Long size = dao.queuesDetail().get(queueName);
		assertNotNull(size);
		assertEquals(size.longValue(), count - 1);
	}
}
