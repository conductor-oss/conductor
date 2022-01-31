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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_EVENT;

@Component
public class EventTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(EventTaskMapper.class);

    private final ParametersUtils parametersUtils;

    @Autowired
    public EventTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.EVENT;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in EventTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        taskToSchedule.getInputParameters().put("sink", taskToSchedule.getSink());
        taskToSchedule.getInputParameters().put("asyncComplete", taskToSchedule.isAsyncComplete());
        Map<String, Object> eventTaskInput =
                parametersUtils.getTaskInputV2(
                        taskToSchedule.getInputParameters(), workflowInstance, taskId, null);
        String sink = (String) eventTaskInput.get("sink");
        Boolean asynComplete = (Boolean) eventTaskInput.get("asyncComplete");

        TaskModel eventTask = new TaskModel();
        eventTask.setTaskType(TASK_TYPE_EVENT);
        eventTask.setTaskDefName(taskToSchedule.getName());
        eventTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        eventTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        eventTask.setWorkflowType(workflowInstance.getWorkflowName());
        eventTask.setCorrelationId(workflowInstance.getCorrelationId());
        eventTask.setScheduledTime(System.currentTimeMillis());
        eventTask.setInputData(eventTaskInput);
        eventTask.getInputData().put("sink", sink);
        eventTask.getInputData().put("asyncComplete", asynComplete);
        eventTask.setTaskId(taskId);
        eventTask.setStatus(TaskModel.Status.SCHEDULED);
        eventTask.setWorkflowPriority(workflowInstance.getPriority());
        eventTask.setWorkflowTask(taskToSchedule);

        return Collections.singletonList(eventTask);
    }
}
