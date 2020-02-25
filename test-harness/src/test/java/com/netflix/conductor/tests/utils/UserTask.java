/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.tests.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Viren
 *
 */
public class UserTask extends WorkflowSystemTask {

	public static final String NAME = "USER_TASK";

	private ObjectMapper objectMapper = new JsonMapperProvider().get();

	private static final TypeReference<Map<String, Map<String, List<Object>>>> mapStringListObjects =
		new TypeReference<Map<String, Map<String, List<Object>>>>() {};

	public UserTask() {
		super(NAME);
	}

	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor executor) {
		Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

		Map<String, Map<String, List<Object>>> map = objectMapper.convertValue(task.getInputData(), mapStringListObjects);
		Map<String, Object> output = new HashMap<>();
		Map<String, List<Object>> defaultLargeInput = new HashMap<>();
		defaultLargeInput.put("TEST_SAMPLE", Collections.singletonList("testDefault"));
		output.put("size", map.getOrDefault("largeInput", defaultLargeInput).get("TEST_SAMPLE").size());
		task.setOutputData(output);
		task.setStatus(Status.COMPLETED);
	}

	@Override
	public boolean isAsync() {
		return true;
	}
}
