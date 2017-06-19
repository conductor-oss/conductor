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

import java.util.List;

import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.exception.JsonQueryException;

@Singleton
public class JsonTransform extends WorkflowSystemTask {

	private static final Logger logger = LoggerFactory.getLogger(JsonTransform.class);
	
	private static final String INPUT_JSON_PARAMETER = "inputJSON";
	private static final String QUERY_EXPRESSION_PARAMETER = "queryExpression";
	
	private ObjectMapper om = new ObjectMapper();
	
	public JsonTransform() {
		super("JSON_TRANSFORM");
	}
	
	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		Object inputData = task.getInputData().get(INPUT_JSON_PARAMETER);
		String queryExpression = (String) task.getInputData().get(QUERY_EXPRESSION_PARAMETER);
		
		if(inputData == null) {
			task.setReasonForIncompletion("Missing '" + INPUT_JSON_PARAMETER + "' in input parameters");
			task.setStatus(Task.Status.FAILED);
			return;
		}

		if(queryExpression == null) {
			task.setReasonForIncompletion("Missing '" + QUERY_EXPRESSION_PARAMETER + "' in input parameters");
			task.setStatus(Task.Status.FAILED);
			return;
		}

		try {
			JsonNode inputJson = om.valueToTree(inputData);
			Object result = applyTransformation(inputJson, queryExpression);
			task.setStatus(Task.Status.COMPLETED);
			task.getOutputData().put("result", result);
			
		} catch(Exception e) {
			logger.error(e.getMessage(), e);
			task.setStatus(Task.Status.FAILED);
			task.setReasonForIncompletion(e.getMessage());
			task.getOutputData().put("error", e.getCause().getMessage());
		}
	}

	private static Object applyTransformation(JsonNode jsonNode, String queryExpression) throws JsonQueryException {
		JsonQuery jsonQuery = JsonQuery.compile(queryExpression);
		List<JsonNode> result = jsonQuery.apply(jsonNode);
		return result.size() == 1 ? result.get(0) : result;
	}
}
