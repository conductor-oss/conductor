/*
 * Copyright 2021 Netflix, Inc.
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
package com.netflix.conductor.core.utils;

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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
@SuppressWarnings("rawtypes")
public class ParametersUtilsTest {

    private ParametersUtils parametersUtils;
    private JsonUtils jsonUtils;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void setup() {
        parametersUtils = new ParametersUtils(objectMapper);
        jsonUtils = new JsonUtils(objectMapper);
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

        CompletableFuture.runAsync(
                        () -> {
                            for (int i = 0; i < 10000; i++) {
                                generatedId.set("test-" + i);
                                payload.put("someId", generatedId.get());
                                Object jsonObj = null;
                                try {
                                    jsonObj =
                                            objectMapper.readValue(
                                                    objectMapper.writeValueAsString(input),
                                                    Object.class);
                                } catch (JsonProcessingException e) {
                                    e.printStackTrace();
                                    return;
                                }
                                Map<String, Object> replaced =
                                        parametersUtils.replace(inputParams, jsonObj);
                                assertNotNull(replaced);
                                assertEquals(generatedId.get(), replaced.get("k1"));
                                assertEquals("conductor", replaced.get("k2"));
                                assertNull(replaced.get("k3"));
                            }
                        },
                        executorService)
                .get();

        executorService.shutdown();
    }

    // Tests ParametersUtils with Map and List input values, and verifies input map is not mutated
    // by ParametersUtils.
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
        input.put("k4", "${}");
        input.put("k5", "${    }");

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
        assertEquals("", replaced.get("k4"));
        assertEquals("", replaced.get("k5"));

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

    @Test
    public void testNestedPathExpressions() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "conductor");
        map.put("index", 1);
        map.put("mapValue", "a");
        map.put("recordIds", List.of(1, 2, 3));
        map.put("map", Map.of("a", List.of(1, 2, 3), "b", List.of(2, 4, 5), "c", List.of(3, 7, 8)));

        Map<String, Object> input = new HashMap<>();
        input.put("k1", "${recordIds[${index}]}");
        input.put("k2", "${map.${mapValue}[${index}]}");
        input.put("k3", "${map.b[${map.${mapValue}[${index}]}]}");

        Object jsonObj = objectMapper.readValue(objectMapper.writeValueAsString(map), Object.class);

        Map<String, Object> replaced = parametersUtils.replace(input, jsonObj);
        assertNotNull(replaced);

        assertEquals(2, replaced.get("k1"));
        assertEquals(2, replaced.get("k2"));
        assertEquals(5, replaced.get("k3"));
    }

    @Test
    public void testReplaceWithEscapedTags() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("someString", "conductor");
        map.put("someNumber", 2);

        Map<String, Object> input = new HashMap<>();
        input.put(
                "k1",
                "${$.someString} $${$.someNumber}${$.someNumber} ${$.someNumber}$${$.someString}");
        input.put("k2", "$${$.someString}afterText");
        input.put("k3", "beforeText$${$.someString}");
        input.put("k4", "$${$.someString} afterText");
        input.put("k5", "beforeText $${$.someString}");

        Map<String, String> mapValue = new HashMap<>();
        mapValue.put("a", "${someString}");
        mapValue.put("b", "${someNumber}");
        mapValue.put("c", "$${someString} ${someNumber}");
        input.put("map", mapValue);

        List<String> listValue = new ArrayList<>();
        listValue.add("${someString}");
        listValue.add("${someNumber}");
        listValue.add("${someString} $${someNumber}");
        input.put("list", listValue);

        Object jsonObj = objectMapper.readValue(objectMapper.writeValueAsString(map), Object.class);

        Map<String, Object> replaced = parametersUtils.replace(input, jsonObj);
        assertNotNull(replaced);

        // Verify that values are replaced correctly.
        assertEquals("conductor ${$.someNumber}2 2${$.someString}", replaced.get("k1"));
        assertEquals("${$.someString}afterText", replaced.get("k2"));
        assertEquals("beforeText${$.someString}", replaced.get("k3"));
        assertEquals("${$.someString} afterText", replaced.get("k4"));
        assertEquals("beforeText ${$.someString}", replaced.get("k5"));

        Map replacedMap = (Map) replaced.get("map");
        assertEquals("conductor", replacedMap.get("a"));
        assertEquals(2, replacedMap.get("b"));
        assertEquals("${someString} 2", replacedMap.get("c"));

        List replacedList = (List) replaced.get("list");
        assertEquals(3, replacedList.size());
        assertEquals("conductor", replacedList.get(0));
        assertEquals(2, replacedList.get(1));
        assertEquals("conductor ${someNumber}", replacedList.get(2));

        // Verify that input map is not mutated
        Map inputMap = (Map) input.get("map");
        assertEquals("${someString}", inputMap.get("a"));
        assertEquals("${someNumber}", inputMap.get("b"));
        assertEquals("$${someString} ${someNumber}", inputMap.get("c"));

        // Verify that input list is not mutated
        List inputList = (List) input.get("list");
        assertEquals(3, inputList.size());
        assertEquals("${someString}", inputList.get(0));
        assertEquals("${someNumber}", inputList.get(1));
        assertEquals("${someString} $${someNumber}", inputList.get(2));
    }

    @Test
    public void getWorkflowInputHandlesNullInputTemplate() {
        WorkflowDef workflowDef = new WorkflowDef();
        Map<String, Object> inputParams = Map.of("key", "value");
        Map<String, Object> workflowInput =
                parametersUtils.getWorkflowInput(workflowDef, inputParams);
        assertEquals("value", workflowInput.get("key"));
    }

    @Test
    public void getWorkflowInputFillsInTemplatedFields() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setInputTemplate(Map.of("other_key", "other_value"));
        Map<String, Object> inputParams = new HashMap<>(Map.of("key", "value"));
        Map<String, Object> workflowInput =
                parametersUtils.getWorkflowInput(workflowDef, inputParams);
        assertEquals("value", workflowInput.get("key"));
        assertEquals("other_value", workflowInput.get("other_key"));
    }

    @Test
    public void getWorkflowInputPreservesExistingFieldsIfPopulated() {
        WorkflowDef workflowDef = new WorkflowDef();
        String keyName = "key";
        workflowDef.setInputTemplate(Map.of(keyName, "templated_value"));
        Map<String, Object> inputParams = new HashMap<>(Map.of(keyName, "supplied_value"));
        Map<String, Object> workflowInput =
                parametersUtils.getWorkflowInput(workflowDef, inputParams);
        assertEquals("supplied_value", workflowInput.get(keyName));
    }
}
