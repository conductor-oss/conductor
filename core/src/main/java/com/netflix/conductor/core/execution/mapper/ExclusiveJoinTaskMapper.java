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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

@Component
public class ExclusiveJoinTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(ExclusiveJoinTaskMapper.class);

    @Override
    public TaskType getTaskType() {
        return TaskType.EXCLUSIVE_JOIN;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in ExclusiveJoinTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> joinInput = new HashMap<>();
        joinInput.put("joinOn", taskToSchedule.getJoinOn());

        if (taskToSchedule.getDefaultExclusiveJoinTask() != null) {
            joinInput.put("defaultExclusiveJoinTask", taskToSchedule.getDefaultExclusiveJoinTask());
        }

        TaskModel joinTask = new TaskModel();
        joinTask.setTaskType(TaskType.TASK_TYPE_EXCLUSIVE_JOIN);
        joinTask.setTaskDefName(TaskType.TASK_TYPE_EXCLUSIVE_JOIN);
        joinTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        joinTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        joinTask.setCorrelationId(workflowInstance.getCorrelationId());
        joinTask.setWorkflowType(workflowInstance.getWorkflowName());
        joinTask.setScheduledTime(System.currentTimeMillis());
        joinTask.setStartTime(System.currentTimeMillis());
        joinTask.setInputData(joinInput);
        joinTask.setTaskId(taskId);
        joinTask.setStatus(TaskModel.Status.IN_PROGRESS);
        joinTask.setWorkflowPriority(workflowInstance.getPriority());
        joinTask.setWorkflowTask(taskToSchedule);

        return Collections.singletonList(joinTask);
    }
}
