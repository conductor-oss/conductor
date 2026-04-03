/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.common.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

public class StringTemplate {

    private static final Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");

    private static final Pattern patternWithQuotes = Pattern.compile("\"\\$\\{(.*?)\\}\"");

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fString(Map<String, Object> input, Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (value instanceof String) {
                value = fString(value.toString(), data);
            } else if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                List<Object> replacedList = new ArrayList<>();
                for (Object o : list) {
                    String replacedValue = fString(o.toString(), data);
                    replacedList.add(replacedValue);
                }
                value = replacedList;
            } else if (value instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) value;
                value = fString(map, data);
            }
            result.put(key, value);
        }
        return result;
    }

    public static String fString2(String s, Map<String, Object> data) {
        Matcher matcher = pattern.matcher(s);

        while (matcher.find()) {
            Object value = data.get(matcher.group(1));
            if (value != null) {
                s = s.replace(matcher.group(0), value.toString());
            }
        }

        return s;
    }

    public static String fString(String s, Map<String, Object> data) {
        Matcher matcher = pattern.matcher(s);

        while (matcher.find()) {
            Object value = data.get(matcher.group(1));
            if (value == null) {
                continue;
            }
            String valueString = null;
            if (value instanceof String || value instanceof Number) {
                valueString = value.toString();
            } else {
                try {
                    valueString = objectMapper.writeValueAsString(value);
                } catch (Exception ignored) {
                    valueString = value.toString();
                }
            }
            s = s.replace(matcher.group(0), valueString);
        }

        return s;
    }

    public static String removeQuotes(String s) {
        Matcher matcher = patternWithQuotes.matcher(s);

        while (matcher.find()) {
            String value = matcher.group(0);
            String replaced = value.substring(1, value.length() - 1);
            s = s.replace(matcher.group(0), replaced);
        }

        return s;
    }

    public static Set<String> fStringParams(String s) {
        Matcher matcher = pattern.matcher(s);
        Set<String> variables = new HashSet<>();
        while (matcher.find()) {
            String group = matcher.group(1);
            if (!StringUtils.isBlank(group)) {
                variables.add(group);
            }
        }

        return variables;
    }
}
