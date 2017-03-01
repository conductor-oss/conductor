/**
 * Copyright 2017 Netflix, Inc.
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
/**
 * 
 */
package com.netflix.conductor.core.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.core.execution.ParametersUtils;

/**
 * @author Viren
 *
 */
public class TestActionProcessor {

	@Test
	public void testAray() throws Exception {
		ActionProcessor ap = new ActionProcessor(null, null);
		ParametersUtils pu = new ParametersUtils();

		List<Object> list = new LinkedList<>();
		Map<String, Object> map = new HashMap<>();
		map.put("externalId", "[{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}]");
		map.put("name", "conductor");
		map.put("version", 2);
		list.add(map);
		
		int before = list.size();
		ap.expand(list);
		assertEquals(before, list.size());
		
		
		Map<String, Object> input = new HashMap<>();
		input.put("k1", "${$..externalId}");
		input.put("k2", "${$[0].externalId[0].taskRefName}");
		input.put("k3", "${__json_externalId.taskRefName}");
		input.put("k4", "${$[0].name}");
		input.put("k5", "${$[0].version}");
		
		Map<String, Object> replaced = pu.replace(input, list);
		assertNotNull(replaced);
		System.out.println(replaced);

		assertEquals(replaced.get("k2"), "t001");
		assertNull(replaced.get("k3"));
		assertEquals(replaced.get("k4"), "conductor");
		assertEquals(replaced.get("k5"), 2);
	}
	
	@Test
	public void testMap() throws Exception {
		ActionProcessor ap = new ActionProcessor(null, null);
		ParametersUtils pu = new ParametersUtils();

		
		Map<String, Object> map = new HashMap<>();
		map.put("externalId", "{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}");
		map.put("name", "conductor");
		map.put("version", 2);

		ap.expand(map);
		
		
		Map<String, Object> input = new HashMap<>();
		input.put("k1", "${$.externalId}");
		input.put("k2", "${externalId.taskRefName}");
		input.put("k4", "${name}");
		input.put("k5", "${version}");
		
		//Map<String, Object> replaced = pu.replace(input, new ObjectMapper().writeValueAsString(map));
		Map<String, Object> replaced = pu.replace(input, map);
		assertNotNull(replaced);
		System.out.println("testMap(): " + replaced);

		assertEquals("t001", replaced.get("k2"));
		assertNull(replaced.get("k3"));
		assertEquals("conductor", replaced.get("k4"));
		assertEquals(2, replaced.get("k5"));
	}
	
	@Test
	public void testNoExpand() throws Exception {
		ParametersUtils pu = new ParametersUtils();

		
		Map<String, Object> map = new HashMap<>();
		map.put("name", "conductor");
		map.put("version", 2);
		map.put("externalId", "{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}");
		
		Map<String, Object> input = new HashMap<>();
		input.put("k1", "${$.externalId}");
		input.put("k4", "${name}");
		input.put("k5", "${version}");
		
		ObjectMapper om = new ObjectMapper();
		Object jsonObj = om.readValue(om.writeValueAsString(map), Object.class);
		
		Map<String, Object> replaced = pu.replace(input, jsonObj);
		assertNotNull(replaced);
		System.out.println("testNoExpand(): " + replaced);

		assertEquals("{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}", replaced.get("k1"));
		assertEquals("conductor", replaced.get("k4"));
		assertEquals(2, replaced.get("k5"));
	}
	
}
