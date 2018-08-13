package com.netflix.conductor.core.utils;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
}
