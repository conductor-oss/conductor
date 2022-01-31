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
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;

/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link
 * TaskType#WAIT} to a {@link TaskModel} of type {@link Wait} with {@link
 * TaskModel.Status#IN_PROGRESS}
 */
@Component
public class WaitTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(WaitTaskMapper.class);

    private final ParametersUtils parametersUtils;

    public WaitTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.WAIT;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in WaitTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> waitTaskInput =
                parametersUtils.getTaskInputV2(
                        taskMapperContext.getTaskToSchedule().getInputParameters(),
                        workflowInstance,
                        taskId,
                        null);

        TaskModel waitTask = new TaskModel();
        waitTask.setTaskType(TASK_TYPE_WAIT);
        waitTask.setTaskDefName(taskMapperContext.getTaskToSchedule().getName());
        waitTask.setReferenceTaskName(taskMapperContext.getTaskToSchedule().getTaskReferenceName());
        waitTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        waitTask.setWorkflowType(workflowInstance.getWorkflowName());
        waitTask.setCorrelationId(workflowInstance.getCorrelationId());
        waitTask.setScheduledTime(System.currentTimeMillis());
        waitTask.setInputData(waitTaskInput);
        waitTask.setTaskId(taskId);
        waitTask.setStatus(TaskModel.Status.IN_PROGRESS);
        waitTask.setWorkflowTask(taskToSchedule);
        waitTask.setWorkflowPriority(workflowInstance.getPriority());
        return Collections.singletonList(waitTask);
    }
}
