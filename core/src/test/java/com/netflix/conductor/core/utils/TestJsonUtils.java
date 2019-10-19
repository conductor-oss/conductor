package com.netflix.conductor.core.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class TestJsonUtils {

    private JsonUtils jsonUtils;

    @Before
    public void setup() {
        jsonUtils = new JsonUtils();
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
