package com.netflix.conductor.core.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

public class TestParametersUtils {
    private ParametersUtils parametersUtils;
    private JsonUtils jsonUtils;
    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        parametersUtils = new ParametersUtils();
        jsonUtils = new JsonUtils();
        objectMapper = new JsonMapperProvider().get();
    }

    @Test
    public void testReplace() throws Exception {
        Map<String, Object> map = new HashMap<>();
		map.put("name", "conductor");
		map.put("version", 2);
		map.put("externalId", "{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}");

		Map<String, Object> input = new HashMap<>();
		input.put("k1", "${$.externalId}");
		input.put("k4", "${name}");
		input.put("k5", "${version}");

		Object jsonObj = objectMapper.readValue(objectMapper.writeValueAsString(map), Object.class);

		Map<String, Object> replaced = parametersUtils.replace(input, jsonObj);
		assertNotNull(replaced);
		System.out.println("testNoExpand(): " + replaced);

		assertEquals("{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}", replaced.get("k1"));
		assertEquals("conductor", replaced.get("k4"));
		assertEquals(2, replaced.get("k5"));
    }

    @Test
    public void testReplaceWithArrayExpand() {
        List<Object> list = new LinkedList<>();
        Map<String, Object> map = new HashMap<>();
		map.put("externalId", "[{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}]");
		map.put("name", "conductor");
		map.put("version", 2);
		list.add(map);
        jsonUtils.expand(list);

        Map<String, Object> input = new HashMap<>();
		input.put("k1", "${$..externalId}");
		input.put("k2", "${$[0].externalId[0].taskRefName}");
		input.put("k3", "${__json_externalId.taskRefName}");
		input.put("k4", "${$[0].name}");
		input.put("k5", "${$[0].version}");

		Map<String, Object> replaced = parametersUtils.replace(input, list);
		assertNotNull(replaced);
		assertEquals(replaced.get("k2"), "t001");
		assertNull(replaced.get("k3"));
		assertEquals(replaced.get("k4"), "conductor");
		assertEquals(replaced.get("k5"), 2);
    }

    @Test
    public void testReplaceWithMapExpand() {
        Map<String, Object> map = new HashMap<>();
		map.put("externalId", "{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}");
		map.put("name", "conductor");
		map.put("version", 2);
		jsonUtils.expand(map);

        Map<String, Object> input = new HashMap<>();
		input.put("k1", "${$.externalId}");
		input.put("k2", "${externalId.taskRefName}");
		input.put("k4", "${name}");
		input.put("k5", "${version}");

		Map<String, Object> replaced = parametersUtils.replace(input, map);
		assertNotNull(replaced);
		assertEquals("t001", replaced.get("k2"));
		assertNull(replaced.get("k3"));
		assertEquals("conductor", replaced.get("k4"));
		assertEquals(2, replaced.get("k5"));
    }

	@Test
	public void testReplaceConcurrent() throws ExecutionException, InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(2);

    	AtomicReference<String> generatedId = new AtomicReference<>("test-0");
		Map<String, Object> input = new HashMap<>();
		Map<String, Object> payload = new HashMap<>();
    	payload.put("event", "conductor:TEST_EVENT");
    	payload.put("someId", generatedId);
		input.put("payload", payload);
		input.put("name", "conductor");
		input.put("version", 2);

		Map<String, Object> inputParams = new HashMap<>();
		inputParams.put("k1", "${payload.someId}");
		inputParams.put("k2", "${name}");

		CompletableFuture.runAsync(() -> {
			for (int i = 0; i < 10000; i++) {
				generatedId.set("test-" + i);
				payload.put("someId", generatedId.get());
				Object jsonObj = null;
				try {
					jsonObj = objectMapper.readValue(objectMapper.writeValueAsString(input), Object.class);
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					return;
				}
				Map<String, Object> replaced = parametersUtils.replace(inputParams, jsonObj);
				assertNotNull(replaced);
				assertEquals(generatedId.get(), replaced.get("k1"));
				assertEquals("conductor", replaced.get("k2"));
				assertNull(replaced.get("k3"));
			}
		}, executorService).get();

		executorService.shutdown();
	}

	// Tests ParametersUtils with Map and List input values, and verifies input map is not mutated by ParametersUtils.
	@Test
	public void testReplaceInputWithMapAndList() throws Exception {
		Map<String, Object> map = new HashMap<>();
		map.put("name", "conductor");
		map.put("version", 2);
		map.put("externalId", "{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}");

		Map<String, Object> input = new HashMap<>();
		input.put("k1", "${$.externalId}");
		input.put("k2", "${name}");
		input.put("k3", "${version}");

		Map<String, String> mapValue = new HashMap<>();
		mapValue.put("name", "${name}");
		mapValue.put("version", "${version}");
		input.put("map", mapValue);

		List<String> listValue = new ArrayList<>();
		listValue.add("${name}");
		listValue.add("${version}");
		input.put("list", listValue);

		Object jsonObj = objectMapper.readValue(objectMapper.writeValueAsString(map), Object.class);

		Map<String, Object> replaced = parametersUtils.replace(input, jsonObj);
		assertNotNull(replaced);

		// Verify that values are replaced correctly.
		assertEquals("{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}", replaced.get("k1"));
		assertEquals("conductor", replaced.get("k2"));
		assertEquals(2, replaced.get("k3"));

		Map replacedMap = (Map) replaced.get("map");
		assertEquals("conductor", replacedMap.get("name"));
		assertEquals(2, replacedMap.get("version"));

		List replacedList = (List) replaced.get("list");
		assertEquals(2, replacedList.size());
		assertEquals("conductor", replacedList.get(0));
		assertEquals(2, replacedList.get(1));


		// Verify that input map is not mutated
		assertEquals("${$.externalId}", input.get("k1"));
		assertEquals("${name}", input.get("k2"));
		assertEquals("${version}", input.get("k3"));

		Map inputMap = (Map) input.get("map");
		assertEquals("${name}", inputMap.get("name"));
		assertEquals("${version}", inputMap.get("version"));

		List inputList = (List) input.get("list");
		assertEquals(2, inputList.size());
		assertEquals("${name}", inputList.get(0));
		assertEquals("${version}", inputList.get(1));
	}
}
