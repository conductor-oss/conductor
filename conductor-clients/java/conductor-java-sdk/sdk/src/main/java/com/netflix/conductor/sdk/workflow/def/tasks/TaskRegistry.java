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
package com.netflix.conductor.sdk.workflow.def.tasks;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

public class TaskRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskRegistry.class);

    private static final Map<String, Class<? extends Task>> taskTypeMap = new HashMap<>();

    public static void register(String taskType, Class<? extends Task> taskImplementation) {
        taskTypeMap.put(taskType, taskImplementation);
    }

    public static Task<?> getTask(WorkflowTask workflowTask) {
        Class<? extends Task> clazz = taskTypeMap.get(workflowTask.getType());
        if (clazz == null) {
            throw new UnsupportedOperationException(
                    "No support to convert " + workflowTask.getType());
        }
        Task<?> task = null;
        try {
            task = clazz.getDeclaredConstructor(WorkflowTask.class).newInstance(workflowTask);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return task;
        }
        return task;
    }
}
