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
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
public class JsonJqTransform extends WorkflowSystemTask {
    private static final Logger logger = LoggerFactory.getLogger(JsonJqTransform.class);
    private static final String NAME = "JSON_JQ_TRANSFORM";
    private static final String QUERY_EXPRESSION_PARAMETER = "queryExpression";
    private static final String OUTPUT_RESULT = "result";
    private static final String OUTPUT_RESULT_LIST = "resultList";
    private static final String OUTPUT_ERROR = "error";

    private final ObjectMapper objectMapper;
    private final LoadingCache<String, JsonQuery> queryCache = createQueryCache();

    @Inject
    public JsonJqTransform(ObjectMapper objectMapper) {
        super(NAME);
        this.objectMapper = objectMapper;
    }

    @Override
    public void start(Workflow workflow, Task task, WorkflowExecutor executor) {
        final Map<String, Object> taskInput = task.getInputData();
        final Map<String, Object> taskOutput = task.getOutputData();

        final String queryExpression = (String) taskInput.get(QUERY_EXPRESSION_PARAMETER);

        if (queryExpression == null) {
            task.setReasonForIncompletion("Missing '" + QUERY_EXPRESSION_PARAMETER + "' in input parameters");
            task.setStatus(Task.Status.FAILED);
            return;
        }

        try {
            final JsonNode input = objectMapper.valueToTree(taskInput);
            final JsonQuery query = queryCache.get(queryExpression);
            final List<JsonNode> result = query.apply(input);

            task.setStatus(Task.Status.COMPLETED);
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
            logger.error("Error executing task: {} in workflow: {}", task.getTaskId(), workflow.getWorkflowId(), e);
            task.setStatus(Task.Status.FAILED);
            final String message = extractFirstValidMessage(e);
            task.setReasonForIncompletion(message);
            taskOutput.put(OUTPUT_ERROR, message);
        }
    }

    private LoadingCache<String, JsonQuery> createQueryCache() {
        final CacheLoader<String, JsonQuery> loader = new CacheLoader<String, JsonQuery>() {
            @Override
            public JsonQuery load(@Nonnull String query) throws JsonQueryException {
                return JsonQuery.compile(query);
            }
        };
        return CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000).build(loader);
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
