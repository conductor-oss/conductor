/**
 * Copyright 2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.tasks.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EventTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(EventTaskMapper.class);

    private ParametersUtils parametersUtils;

    public EventTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {

        logger.debug("TaskMapperContext {} in EventTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        taskToSchedule.getInputParameters().put("sink", taskToSchedule.getSink());
        taskToSchedule.getInputParameters().put("asyncComplete", taskToSchedule.isAsyncComplete());
        Map<String, Object> eventTaskInput = parametersUtils.getTaskInputV2(taskToSchedule.getInputParameters(),
                workflowInstance, taskId, null);
        String sink = (String) eventTaskInput.get("sink");
        Boolean asynComplete = (Boolean) eventTaskInput.get("asyncComplete");

        Task eventTask = new Task();
        eventTask.setTaskType(Event.NAME);
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
        eventTask.setStatus(Task.Status.SCHEDULED);
        eventTask.setWorkflowPriority(workflowInstance.getPriority());
        eventTask.setWorkflowTask(taskToSchedule);

        return Collections.singletonList(eventTask);
    }
}
