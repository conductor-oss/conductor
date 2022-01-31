/*
 * Copyright 2022 Netflix, Inc.
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

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/** This class contains utility functions for parsing/expanding JSON. */
@SuppressWarnings("unchecked")
@Component
public class JsonUtils {

    private final ObjectMapper objectMapper;

    public JsonUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Expands a JSON object into a java object
     *
     * @param input the object to be expanded
     * @return the expanded object containing java types like {@link Map} and {@link List}
     */
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

    private void expandList(List<Object> input) {
        for (Object value : input) {
            if (value instanceof String) {
                if (isJsonString(value.toString())) {
                    value = getJson(value.toString());
                }
            } else if (value instanceof Map) {
                expandMap((Map<String, Object>) value);
            } else if (value instanceof List) {
                expandList((List<Object>) value);
            }
        }
    }

    private void expandMap(Map<String, Object> input) {
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                if (isJsonString(value.toString())) {
                    entry.setValue(getJson(value.toString()));
                }
            } else if (value instanceof Map) {
                expandMap((Map<String, Object>) value);
            } else if (value instanceof List) {
                expandList((List<Object>) value);
            }
        }
    }

    /**
     * Used to obtain a JSONified object from a string
     *
     * @param jsonAsString the json object represented in string form
     * @return the JSONified object representation if the input is a valid json string if the input
     *     is not a valid json string, it will be returned as-is and no exception is thrown
     */
    private Object getJson(String jsonAsString) {
        try {
            return objectMapper.readValue(jsonAsString, Object.class);
        } catch (Exception e) {
            return jsonAsString;
        }
    }

    private boolean isJsonString(String jsonAsString) {
        jsonAsString = jsonAsString.trim();
        return jsonAsString.startsWith("{") || jsonAsString.startsWith("[");
    }
}
