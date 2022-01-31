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
package com.netflix.conductor.contribs.tasks.json;

import java.util.ArrayList;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.exception.JsonQueryException;

@Component(JsonJqTransform.NAME)
public class JsonJqTransform extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonJqTransform.class);
    public static final String NAME = "JSON_JQ_TRANSFORM";
    private static final String QUERY_EXPRESSION_PARAMETER = "queryExpression";
    private static final String OUTPUT_RESULT = "result";
    private static final String OUTPUT_RESULT_LIST = "resultList";
    private static final String OUTPUT_ERROR = "error";
    private final Scope rootScope;
    private final ObjectMapper objectMapper;
    private final LoadingCache<String, JsonQuery> queryCache = createQueryCache();

    @Autowired
    public JsonJqTransform(ObjectMapper objectMapper) {
        super(NAME);
        this.objectMapper = objectMapper;
        this.rootScope = Scope.newEmptyScope();
        this.rootScope.loadFunctions(Scope.class.getClassLoader());
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        final Map<String, Object> taskInput = task.getInputData();
        final Map<String, Object> taskOutput = task.getOutputData();

        final String queryExpression = (String) taskInput.get(QUERY_EXPRESSION_PARAMETER);

        if (queryExpression == null) {
            task.setReasonForIncompletion(
                    "Missing '" + QUERY_EXPRESSION_PARAMETER + "' in input parameters");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        try {
            final JsonNode input = objectMapper.valueToTree(taskInput);
            final JsonQuery query = queryCache.get(queryExpression);

            final Scope childScope = Scope.newChildScope(rootScope);

            final List<JsonNode> result = query.apply(childScope, input);

            task.setStatus(TaskModel.Status.COMPLETED);
            if (result == null) {
                taskOutput.put(OUTPUT_RESULT, null);
                taskOutput.put(OUTPUT_RESULT_LIST, null);
            } else if (result.isEmpty()) {
                taskOutput.put(OUTPUT_RESULT, null);
                taskOutput.put(OUTPUT_RESULT_LIST, result);
            } else {
                taskOutput.put(OUTPUT_RESULT, result.get(0));
                taskOutput.put(OUTPUT_RESULT_LIST, result);
            }
        } catch (final Exception e) {
            LOGGER.error(
                    "Error executing task: {} in workflow: {}",
                    task.getTaskId(),
                    workflow.getWorkflowId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            final String message = extractFirstValidMessage(e);
            task.setReasonForIncompletion(message);
            taskOutput.put(OUTPUT_ERROR, message);
        }
    }

    private LoadingCache<String, JsonQuery> createQueryCache() {
        final CacheLoader<String, JsonQuery> loader =
                new CacheLoader<String, JsonQuery>() {
                    @Override
                    public JsonQuery load(String query) throws JsonQueryException {
                        return JsonQuery.compile(query);
                    }
                };
        return CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(1000)
                .build(loader);
    }

    private String extractFirstValidMessage(final Exception e) {
        Throwable currentStack = e;
        final List<String> messages = new ArrayList<>();
        messages.add(currentStack.getMessage());
        while (currentStack.getCause() != null) {
            currentStack = currentStack.getCause();
            messages.add(currentStack.getMessage());
        }
        return messages.stream().filter(it -> !it.contains("N/A")).findFirst().orElse("");
    }
}
