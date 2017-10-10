package com.netflix.conductor.dao.mysql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
}
