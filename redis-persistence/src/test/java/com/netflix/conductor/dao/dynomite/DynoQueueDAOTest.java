/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.dynomite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.conductor.dao.redis.JedisMock;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.queues.ShardSupplier;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import redis.clients.jedis.commands.JedisCommands;

/**
 * 
 * @author Viren
 *
 */
public class DynoQueueDAOTest {

	private QueueDAO queueDAO;

	@Before
	public void init() {
		JedisCommands jedisMock = new JedisMock();
		queueDAO = new DynoQueueDAO(jedisMock, jedisMock, new ShardSupplier() {

			@Override
			public Set<String> getQueueShards() {
				return new HashSet<>(Collections.singletonList("a"));
			}

			@Override
			public String getCurrentShard() {
				return "a";
			}

			@Override
			public String getShardForHost(Host host) {
				return "a";
			}
		}, new TestConfiguration());
	}

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void test() {
		String queueName = "TestQueue";
		long offsetTimeInSecond = 0;
		
		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			queueDAO.push(queueName, messageId, offsetTimeInSecond);
		}
		int size = queueDAO.getSize(queueName);
		assertEquals(10, size);
		Map<String, Long> details = queueDAO.queuesDetail();
		assertEquals(1, details.size());
		assertEquals(10L, details.get(queueName).longValue());
		
		
		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			queueDAO.pushIfNotExists(queueName, messageId, offsetTimeInSecond);
		}
		
		List<String> popped = queueDAO.pop(queueName, 10, 100);
		assertNotNull(popped);
		assertEquals(10, popped.size());
		
		Map<String, Map<String, Map<String, Long>>> verbose = queueDAO.queuesDetailVerbose();
		assertEquals(1, verbose.size());
		long shardSize = verbose.get(queueName).get("a").get("size");
		long unackedSize = verbose.get(queueName).get("a").get("uacked");
		assertEquals(0, shardSize);
		assertEquals(10, unackedSize);
		
		popped.forEach(messageId -> queueDAO.ack(queueName, messageId));
		
		verbose = queueDAO.queuesDetailVerbose();
		assertEquals(1, verbose.size());
		shardSize = verbose.get(queueName).get("a").get("size");
		unackedSize = verbose.get(queueName).get("a").get("uacked");
		assertEquals(0, shardSize);
		assertEquals(0, unackedSize);
		
		popped = queueDAO.pop(queueName, 10, 100);
		assertNotNull(popped);
		assertEquals(0, popped.size());
		
		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			queueDAO.pushIfNotExists(queueName, messageId, offsetTimeInSecond);
		}
		size = queueDAO.getSize(queueName);
		assertEquals(10, size);
		
		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			queueDAO.remove(queueName, messageId);
		}
		
		size = queueDAO.getSize(queueName);
		assertEquals(0, size);
		
		for(int i = 0; i < 10; i++) {
			String messageId = "msg" + i;
			queueDAO.pushIfNotExists(queueName, messageId, offsetTimeInSecond);
		}
		queueDAO.flush(queueName);
		size = queueDAO.getSize(queueName);
		assertEquals(0, size);
	}
}
