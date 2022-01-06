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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class JsonUtilsTest {

    private JsonUtils jsonUtils;

    @Autowired private ObjectMapper objectMapper;

    @Before
    public void setup() {
        jsonUtils = new JsonUtils(objectMapper);
    }

    @Test
    public void testArray() {
        List<Object> list = new LinkedList<>();
        Map<String, Object> map = new HashMap<>();
        map.put("externalId", "[{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}]");
        map.put("name", "conductor");
        map.put("version", 2);
        list.add(map);

        //noinspection unchecked
        map = (Map<String, Object>) list.get(0);
        assertTrue(map.get("externalId") instanceof String);

        int before = list.size();
        jsonUtils.expand(list);
        assertEquals(before, list.size());

        //noinspection unchecked
        map = (Map<String, Object>) list.get(0);
        assertTrue(map.get("externalId") instanceof ArrayList);
    }

    @Test
    public void testMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("externalId", "{\"taskRefName\":\"t001\",\"workflowId\":\"w002\"}");
        map.put("name", "conductor");
        map.put("version", 2);

        assertTrue(map.get("externalId") instanceof String);

        jsonUtils.expand(map);

        assertTrue(map.get("externalId") instanceof LinkedHashMap);
    }

    @Test
    public void testMultiLevelMap() {
        Map<String, Object> parentMap = new HashMap<>();
        parentMap.put("requestId", "abcde");
        parentMap.put("status", "PROCESSED");

        Map<String, Object> childMap = new HashMap<>();
        childMap.put("path", "test/path");
        childMap.put("type", "VIDEO");

        Map<String, Object> grandChildMap = new HashMap<>();
        grandChildMap.put("duration", "370");
        grandChildMap.put("passed", "true");

        childMap.put("metadata", grandChildMap);
        parentMap.put("asset", childMap);

        Object jsonObject = jsonUtils.expand(parentMap);
        assertNotNull(jsonObject);
    }

    // This test verifies that the types of the elements in the input are maintained upon expanding
    // the JSON object
    @Test
    public void testTypes() throws Exception {
        String map =
                "{\"requestId\":\"1375128656908832001\",\"workflowId\":\"fc147e1d-5408-4d41-b066-53cb2e551d0e\","
                        + "\"inner\":{\"num\":42,\"status\":\"READY\"}}";
        jsonUtils.expand(map);

        Object jsonObject = jsonUtils.expand(map);
        assertNotNull(jsonObject);
        assertTrue(jsonObject instanceof LinkedHashMap);
        assertTrue(((LinkedHashMap<?, ?>) jsonObject).get("requestId") instanceof String);
        assertTrue(((LinkedHashMap<?, ?>) jsonObject).get("workflowId") instanceof String);
        assertTrue(((LinkedHashMap<?, ?>) jsonObject).get("inner") instanceof LinkedHashMap);
        assertTrue(
                ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) jsonObject).get("inner")).get("num")
                        instanceof Integer);
        assertTrue(
                ((LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) jsonObject).get("inner"))
                                .get("status")
                        instanceof String);
    }
}
