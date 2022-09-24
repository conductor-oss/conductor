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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;

/**
 * A container class that holds a mapping of system task types {@link
 * com.netflix.conductor.common.metadata.tasks.TaskType} to {@link WorkflowSystemTask} instances.
 */
@Component
public class SystemTaskRegistry {

    public static final String ASYNC_SYSTEM_TASKS_QUALIFIER = "asyncSystemTasks";

    private final Map<String, WorkflowSystemTask> registry;
    private final Map<Class<? extends TaskMapper>, TaskMapper> taskMappers;

    public SystemTaskRegistry(Set<WorkflowSystemTask> tasks, Set<TaskMapper> taskMappers) {
        this.registry =
                tasks.stream()
                        .collect(
                                Collectors.toMap(
                                        WorkflowSystemTask::getTaskType, Function.identity()));
        this.taskMappers =
                taskMappers.stream()
                        .collect(Collectors.toMap(TaskMapper::getClass, Function.identity()));
    }

    public WorkflowSystemTask get(String taskType) {
        return Optional.ofNullable(registry.get(taskType))
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        taskType + " not found in " + getClass().getSimpleName()));
    }

    public TaskMapper getTaskMapper(String taskType) {
        if (TaskType.TASK_TYPE_SIMPLE.equals(taskType)) {
            return taskMappers.get(SimpleTaskMapper.class);
        }

        WorkflowSystemTask workflowSystemTask = get(taskType);
        Class<? extends TaskMapper> taskMapperType = workflowSystemTask.getTaskMapperType();
        Objects.requireNonNull(
                taskMapperType,
                workflowSystemTask.getClass()
                        + ".getTaskMapperType() for "
                        + taskType
                        + " should return a non-null type.");
        return Optional.ofNullable(this.taskMappers.get(taskMapperType))
                .orElseThrow(
                        () -> new IllegalStateException(taskMapperType + " bean is not found."));
    }

    public boolean isSystemTask(String taskType) {
        return registry.containsKey(taskType);
    }
}
