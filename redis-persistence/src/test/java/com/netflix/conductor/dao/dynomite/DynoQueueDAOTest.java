/**
 * Copyright 2016 Netflix, Inc.
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.conductor.dao.redis.JedisMock;
import com.netflix.dyno.queues.ShardSupplier;

import redis.clients.jedis.JedisCommands;

/**
 * 
 * @author Viren
 *
 */
public class DynoQueueDAOTest {

	private QueueDAO dao;

	private static ObjectMapper om = new ObjectMapper();

	static {
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		om.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
		om.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
		om.setSerializationInclusion(Include.NON_NULL);
		om.setSerializationInclusion(Include.NON_EMPTY);
	}

	@Before
	public void init() throws Exception {
		JedisCommands jedisMock = new JedisMock();
		dao = new DynoQueueDAO(jedisMock, jedisMock, new ShardSupplier() {

			@Override
			public Set<String> getQueueShards() {
				return Arrays.asList("a").stream().collect(Collectors.toSet());
			}

			@Override
			public String getCurrentShard() {
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
