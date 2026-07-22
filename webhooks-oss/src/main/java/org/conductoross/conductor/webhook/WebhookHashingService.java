/*
 * Copyright 2022 Conductor Authors.
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
package org.conductoross.conductor.webhook;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

@Service
@Slf4j
public class WebhookHashingService {

    public static final String WEBHOOK_DELIMITER = ";";

    private static final ObjectMapper om = new ObjectMapperProvider().getObjectMapper();

    public String computeJsonHash(
            StringBuilder hash,
            Map<String, Object> matches,
            String body,
            Map<String, Object> parameters) {
        Map<String, Object> map;
        try {
            Object obj = om.readValue(body, Object.class);
            if (obj instanceof Map) {
                map = (Map<String, Object>) obj;
            } else {
                map = new HashMap<>();
                map.put("request", obj);
            }
        } catch (JsonProcessingException e) {
            map = new HashMap<>();
        }
        map.putAll(parameters);

        DocumentContext jsonContext = JsonPath.parse(map);

        TreeSet<String> pathsToCheck = new TreeSet<>(matches.keySet());
        for (String path : pathsToCheck) {
            Object value = extract(jsonContext, path);
            if (value == null) {
                return null;
            }
            if (value instanceof JSONArray) {
                JSONArray valueArray = ((JSONArray) value);
                if (valueArray.isEmpty()) {
                    return null;
                }
                value = valueArray.get(0).toString();
            }

            String expectedValue = Objects.toString(matches.get(path));
            if (expectedValue.startsWith("$")
                    || expectedValue.trim().equalsIgnoreCase(value.toString().trim())) {
                hash.append(WEBHOOK_DELIMITER).append(value);
            } else {
                return null;
            }
        }

        return hash.toString();
    }

    private Object extract(DocumentContext jsonContext, String path) {
        try {
            return jsonContext.read(path);
        } catch (Exception e) {
            log.warn("Exception reading path {} - {}", path, e.getMessage(), e);
            return null;
        }
    }
}
