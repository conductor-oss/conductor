/*
 * Copyright 2020 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.ObjectMapperConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@ContextConfiguration(classes = {ObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class JsonUtilsTest {

    private JsonUtils jsonUtils;

    @Autowired
    private ObjectMapper objectMapper;

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
}
