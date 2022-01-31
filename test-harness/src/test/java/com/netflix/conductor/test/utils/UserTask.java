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
package com.netflix.conductor.test.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;

@Component(UserTask.NAME)
public class UserTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserTask.class);

    public static final String NAME = "USER_TASK";

    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Map<String, List<Object>>>>
            mapStringListObjects = new TypeReference<>() {};

    @Autowired
    public UserTask(ObjectMapper objectMapper) {
        super(NAME);
        this.objectMapper = objectMapper;
        LOGGER.info("Initialized system task - {}", getClass().getCanonicalName());
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        if (task.getWorkflowTask().isAsyncComplete()) {
            task.setStatus(TaskModel.Status.IN_PROGRESS);
        } else {
            Map<String, Map<String, List<Object>>> map =
                    objectMapper.convertValue(task.getInputData(), mapStringListObjects);
            Map<String, Object> output = new HashMap<>();
            Map<String, List<Object>> defaultLargeInput = new HashMap<>();
            defaultLargeInput.put("TEST_SAMPLE", Collections.singletonList("testDefault"));
            output.put(
                    "size",
                    map.getOrDefault("largeInput", defaultLargeInput).get("TEST_SAMPLE").size());
            task.setOutputData(output);
            task.setStatus(TaskModel.Status.COMPLETED);
        }
    }

    @Override
    public boolean isAsync() {
        return true;
    }
}
