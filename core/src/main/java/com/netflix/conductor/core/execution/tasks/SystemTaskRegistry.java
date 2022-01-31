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
package com.netflix.conductor.core.execution.tasks;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * A container class that holds a mapping of system task types {@link
 * com.netflix.conductor.common.metadata.tasks.TaskType} to {@link WorkflowSystemTask} instances.
 */
@Component
public class SystemTaskRegistry {

    public static final String ASYNC_SYSTEM_TASKS_QUALIFIER = "asyncSystemTasks";

    private final Map<String, WorkflowSystemTask> registry;

    public SystemTaskRegistry(Set<WorkflowSystemTask> tasks) {
        this.registry =
                tasks.stream()
                        .collect(
                                Collectors.toMap(
                                        WorkflowSystemTask::getTaskType, Function.identity()));
    }

    public WorkflowSystemTask get(String taskType) {
        return Optional.ofNullable(registry.get(taskType))
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        taskType + "not found in " + getClass().getSimpleName()));
    }

    public boolean isSystemTask(String taskType) {
        return registry.containsKey(taskType);
    }
}
