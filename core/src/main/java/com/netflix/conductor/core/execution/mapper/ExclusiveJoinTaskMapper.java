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
package com.netflix.conductor.core.execution.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;

@Component
public class ExclusiveJoinTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(ExclusiveJoinTaskMapper.class);

    @Override
    public String getTaskType() {
        return TaskType.EXCLUSIVE_JOIN.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in ExclusiveJoinTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();

        Map<String, Object> joinInput = new HashMap<>();
        joinInput.put("joinOn", workflowTask.getJoinOn());

        if (workflowTask.getDefaultExclusiveJoinTask() != null) {
            joinInput.put("defaultExclusiveJoinTask", workflowTask.getDefaultExclusiveJoinTask());
        }

        TaskModel joinTask = taskMapperContext.createTaskModel();
        joinTask.setTaskType(TaskType.TASK_TYPE_EXCLUSIVE_JOIN);
        joinTask.setTaskDefName(TaskType.TASK_TYPE_EXCLUSIVE_JOIN);
        joinTask.setStartTime(System.currentTimeMillis());
        joinTask.setInputData(joinInput);
        joinTask.setStatus(TaskModel.Status.IN_PROGRESS);

        return List.of(joinTask);
    }
}
