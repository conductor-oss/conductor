package com.netflix.conductor.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class JsonUtils {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonUtils() {
    }

    private Object getJson(String jsonAsString) {
        try {
            return objectMapper.readValue(jsonAsString, Object.class);
        } catch (Exception e) {
            return jsonAsString;
        }
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    public Object expand(Object input) {
        if (input instanceof List) {
            expandList((List<Object>) input);
            return input;
        } else if (input instanceof Map) {
            expandMap((Map<String, Object>) input);
            return input;
        } else if (input instanceof String) {
            return getJson((String) input);
        } else {
            return input;
        }
    }

    @SuppressWarnings("unchecked")
    private void expandList(List<Object> input) {
        for (Object value : input) {
            if (value instanceof String) {
                value = getJson(value.toString());
            } else if (value instanceof Map) {
                expandMap((Map<String, Object>) value);
            } else if (value instanceof List) {
                expandList((List<Object>) value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void expandMap(Map<String, Object> input) {
        for (Map.Entry<String, Object> e : input.entrySet()) {
            Object value = e.getValue();
            if (value instanceof String) {
                value = getJson(value.toString());
                e.setValue(value);
            } else if (value instanceof Map) {
                expandMap((Map<String, Object>) value);
            } else if (value instanceof List) {
                expandList((List<Object>) value);
            }
        }
    }
}
