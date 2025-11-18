/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.contribs.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.TaskSummary;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonFilter("SecretRemovalFilter")
public class TaskNotification extends TaskSummary {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStatusPublisher.class);

    public String workflowTaskType;

    /**
     * Following attributes doesnt exist in TaskSummary so add it here. Not adding in TaskSummary as
     * it belongs to conductor-common
     */
    private String referenceTaskName;

    private int retryCount;

    private String taskDescription;

    private ObjectMapper objectMapper = new ObjectMapper();

    public String getReferenceTaskName() {
        return referenceTaskName;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public TaskNotification(Task task) {
        super(task);

        referenceTaskName = task.getReferenceTaskName();
        retryCount = task.getRetryCount();
        taskDescription = task.getWorkflowTask().getDescription();

        workflowTaskType = task.getWorkflowTask().getType();

        boolean isFusionMetaPresent = task.getInputData().containsKey("_ioMeta");
        if (!isFusionMetaPresent) {
            return;
        }
    }

    String toJsonString() {
        String jsonString;
        SimpleBeanPropertyFilter theFilter =
                SimpleBeanPropertyFilter.serializeAllExcept("input", "output");
        FilterProvider provider =
                new SimpleFilterProvider().addFilter("SecretRemovalFilter", theFilter);
        try {
            jsonString = objectMapper.writer(provider).writeValueAsString(this);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to convert Task: {} to String. Exception: {}", this, e);
            throw new RuntimeException(e);
        }
        return jsonString;
    }

    /*
     * https://github.com/Netflix/conductor/pull/2128
     * To enable Workflow/Task Summary Input/Output JSON Serialization, use the following:
     * conductor.app.summary-input-output-json-serialization.enabled=true
     */
    String toJsonStringWithInputOutput() {
        String jsonString;
        try {
            SimpleBeanPropertyFilter emptyFilter = SimpleBeanPropertyFilter.serializeAllExcept();
            FilterProvider provider =
                    new SimpleFilterProvider().addFilter("SecretRemovalFilter", emptyFilter);

            jsonString = objectMapper.writer(provider).writeValueAsString(this);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to convert Task: {} to String. Exception: {}", this, e);
            throw new RuntimeException(e);
        }
        return jsonString;
    }
}
