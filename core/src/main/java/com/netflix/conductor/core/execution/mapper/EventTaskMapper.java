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
package com.netflix.conductor.core.execution.mapper;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public EventTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.EVENT.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in EventTaskMapper", taskMapperContext);

        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        String taskId = taskMapperContext.getTaskId();

        workflowTask.getInputParameters().put("sink", workflowTask.getSink());
        workflowTask.getInputParameters().put("asyncComplete", workflowTask.isAsyncComplete());
        Map<String, Object> eventTaskInput =
                parametersUtils.getTaskInputV2(
                        workflowTask.getInputParameters(), workflowModel, taskId, null);
        String sink = (String) eventTaskInput.get("sink");
        Boolean asynComplete = (Boolean) eventTaskInput.get("asyncComplete");

        TaskModel eventTask = taskMapperContext.createTaskModel();
        eventTask.setTaskType(TASK_TYPE_EVENT);
        eventTask.setStatus(TaskModel.Status.SCHEDULED);

        eventTask.setInputData(eventTaskInput);
        eventTask.getInputData().put("sink", sink);
        eventTask.getInputData().put("asyncComplete", asynComplete);

        return List.of(eventTask);
    }
}
