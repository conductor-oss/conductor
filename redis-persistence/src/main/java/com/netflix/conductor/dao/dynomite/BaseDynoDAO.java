/*
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dyno.DynoProxy;
import com.netflix.conductor.metrics.Monitors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BaseDynoDAO {

    private static final String NAMESPACE_SEP = ".";
    private static final String DAO_NAME = "redis";

    protected DynoProxy dynoClient;

    protected ObjectMapper objectMapper;

    private String domain;

    private Configuration config;

    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected BaseDynoDAO(DynoProxy dynoClient, ObjectMapper objectMapper, Configuration config) {
        this.dynoClient = dynoClient;
        this.objectMapper = objectMapper;
        this.config = config;
        this.domain = config.getProperty("workflow.dyno.keyspace.domain", null);
    }

    String nsKey(String... nsValues) {
        String rootNamespace = config.getProperty("workflow.namespace.prefix", null);
        StringBuilder namespacedKey = new StringBuilder();
        if (StringUtils.isNotBlank(rootNamespace)) {
            namespacedKey.append(rootNamespace).append(NAMESPACE_SEP);
        }
        String stack = config.getStack();
        if (StringUtils.isNotBlank(stack)) {
            namespacedKey.append(stack).append(NAMESPACE_SEP);
        }
        if (StringUtils.isNotBlank(domain)) {
            namespacedKey.append(domain).append(NAMESPACE_SEP);
        }
        for (int i = 0; i < nsValues.length; i++) {
            namespacedKey.append(nsValues[i]).append(NAMESPACE_SEP);
        }
        return StringUtils.removeEnd(namespacedKey.toString(), NAMESPACE_SEP);
    }

    public DynoProxy getDyno() {
        return dynoClient;
    }

    String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    <T> T readValue(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void recordRedisDaoRequests(String action) {
        recordRedisDaoRequests(action, "n/a", "n/a");
    }

    void recordRedisDaoRequests(String action, String taskType, String workflowType) {
        Monitors.recordDaoRequests(DAO_NAME, action, taskType, workflowType);
    }

    void recordRedisDaoEventRequests(String action, String event) {
        Monitors.recordDaoEventRequests(DAO_NAME, action, event);
    }

    void recordRedisDaoPayloadSize(String action, int size, String taskType, String workflowType) {
        Monitors.recordDaoPayloadSize(DAO_NAME, action, StringUtils.defaultIfBlank(taskType,""), StringUtils.defaultIfBlank(workflowType,""), size);
    }
}
