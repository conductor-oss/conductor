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
package com.netflix.conductor.tasks.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.Versions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component(JsonJqTransform.NAME)
public class JsonJqTransform extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonJqTransform.class);
    public static final String NAME = "JSON_JQ_TRANSFORM";
    private static final String QUERY_EXPRESSION_PARAMETER = "queryExpression";
    private static final String OUTPUT_RESULT = "result";
    private static final String OUTPUT_RESULT_LIST = "resultList";
    private static final String OUTPUT_ERROR = "error";
    private static final Version JQ_VERSION = Versions.JQ_1_6;

    /** Prefix jackson-jq uses on the wrapper exception that only echoes the query back. */
    private static final String QUERY_ECHO_PREFIX = "Cannot compile query";

    private static final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {};
    private final TypeReference<List<Object>> listType = new TypeReference<>() {};
    private final Scope rootScope;
    private final ObjectMapper objectMapper;
    private final LoadingCache<String, JsonQuery> queryCache = createQueryCache();

    public JsonJqTransform(ObjectMapper objectMapper) {
        super(NAME);
        this.objectMapper = objectMapper;
        this.rootScope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(JQ_VERSION, this.rootScope);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        final Map<String, Object> taskInput = task.getInputData();

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

            final List<JsonNode> result = new ArrayList<>();
            query.apply(childScope, input, result::add);

            task.setStatus(TaskModel.Status.COMPLETED);
            List<Object> extractedResults = extractBodies(result);
            if (extractedResults.isEmpty()) {
                task.addOutput(OUTPUT_RESULT, null);
            } else {
                task.addOutput(OUTPUT_RESULT, extractedResults.get(0));
            }
            task.addOutput(OUTPUT_RESULT_LIST, extractedResults);
        } catch (final Exception e) {
            LOGGER.error(
                    "Error executing task: {} in workflow: {}",
                    task.getTaskId(),
                    workflow.getWorkflowId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            final String message = extractFirstValidMessage(e);
            task.setReasonForIncompletion(message);
            task.addOutput(OUTPUT_ERROR, message);
        }
    }

    private LoadingCache<String, JsonQuery> createQueryCache() {
        final CacheLoader<String, JsonQuery> loader =
                expression -> JsonQuery.compile(expression, JQ_VERSION);
        return Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(1000)
                .build(loader);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        this.start(workflow, task, workflowExecutor);
        return true;
    }

    /**
     * Walks the exception chain and returns the first message that tells the workflow author
     * something they can act on.
     *
     * <p>jq failures arrive wrapped: the outer exception only restates the query, and the cause
     * carries the parser message with the line and column. Older jackson-jq marked the outer
     * message with "N/A"; 2.x prefixes it with "Cannot compile query". Both are skipped so the
     * specific message is what reaches the task output.
     */
    private String extractFirstValidMessage(final Exception e) {
        Throwable currentStack = e;
        final List<String> messages = new ArrayList<>();
        messages.add(currentStack.getMessage());
        while (currentStack.getCause() != null) {
            currentStack = currentStack.getCause();
            messages.add(currentStack.getMessage());
        }
        return messages.stream()
                .filter(
                        it ->
                                it != null
                                        && !it.contains("N/A")
                                        && !it.startsWith(QUERY_ECHO_PREFIX))
                .findFirst()
                .orElse("");
    }

    private List<Object> extractBodies(List<JsonNode> nodes) {
        List<Object> values = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            values.add(extractBody(node));
        }
        return values;
    }

    private Object extractBody(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isObject()) {
            return objectMapper.convertValue(node, mapType);
        } else if (node.isArray()) {
            return objectMapper.convertValue(node, listType);
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isNumber()) {
            if (node.isIntegralNumber()) {
                return node.asLong();
            } else {
                return node.asDouble();
            }
        } else {
            return node.asText();
        }
    }
}
