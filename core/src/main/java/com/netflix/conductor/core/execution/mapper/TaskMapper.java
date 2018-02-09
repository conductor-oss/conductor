package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;

import java.util.List;

public interface TaskMapper {

    List<Task> getMappedTasks(TaskMapperContext taskMapperContext);
}
