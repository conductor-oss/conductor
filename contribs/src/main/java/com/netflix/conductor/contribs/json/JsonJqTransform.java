/**
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.conductor.contribs.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
public class JsonJqTransform extends WorkflowSystemTask {

	private static final Logger logger = LoggerFactory.getLogger(JsonJqTransform.class);
	private static final String NAME = "JSON_JQ_TRANSFORM";
	private static final String QUERY_EXPRESSION_PARAMETER = "queryExpression";
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final LoadingCache<String, JsonQuery> queryCache = createQueryCache();

	public JsonJqTransform() {
		super(NAME);
	}
	
	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor executor) {
		Map<String, Object> taskInput = task.getInputData();
		Map<String, Object> taskOutput = task.getOutputData();

		String queryExpression = (String) taskInput.get(QUERY_EXPRESSION_PARAMETER);
		
		if(queryExpression == null) {
			task.setReasonForIncompletion("Missing '" + QUERY_EXPRESSION_PARAMETER + "' in input parameters");
			task.setStatus(Task.Status.FAILED);
			return;
		}
		
		try {
			JsonNode input = objectMapper.valueToTree(taskInput);
			JsonQuery query = queryCache.get(queryExpression);
			List<JsonNode> result = query.apply(input);
			
			task.setStatus(Task.Status.COMPLETED);
			if (result == null) {
				taskOutput.put("result", null);
				taskOutput.put("resultList", null);
			} else if (result.isEmpty()) {
				taskOutput.put("result", null);
				taskOutput.put("resultList", result);
			} else {
				taskOutput.put("result", result.get(0));
				taskOutput.put("resultList", result);
			}
		} catch(Exception e) {
			logger.error(e.getMessage(), e);
			task.setStatus(Task.Status.FAILED);
			task.setReasonForIncompletion(e.getMessage());
			taskOutput.put("error", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
		}
	}

	private LoadingCache<String, JsonQuery> createQueryCache() {
		CacheLoader<String, JsonQuery> loader = new CacheLoader<String, JsonQuery>() {
			@Override
			public JsonQuery load(@Nonnull String query) throws JsonQueryException {
				return JsonQuery.compile(query);
			}
		};
		return CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000).build(loader);
	}
}
