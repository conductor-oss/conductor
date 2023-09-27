/*
 * Copyright 2023 Netflix, Inc.
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
package com.netflix.conductor.core.execution.mapper;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.model.TaskModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.*;

@Component
public class NoopTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(NoopTaskMapper.class);

    @Override
    public String getTaskType() {
        return TaskType.NOOP.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        logger.debug("TaskMapperContext {} in NoopTaskMapper", taskMapperContext);

        TaskModel task = taskMapperContext.createTaskModel();
        task.setTaskType(TASK_TYPE_NOOP);
        task.setStartTime(System.currentTimeMillis());
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(task);
    }
}
