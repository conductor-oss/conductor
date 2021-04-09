/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_LAMBDA;

/**
 * @author x-ultra
 */
@Component
public class LambdaTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(LambdaTaskMapper.class);
    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    public LambdaTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.LAMBDA;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in LambdaTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        TaskDef taskDefinition = Optional.ofNullable(taskMapperContext.getTaskDefinition())
            .orElseGet(() -> Optional.ofNullable(metadataDAO.getTaskDef(taskToSchedule.getName()))
                .orElse(null));

        Map<String, Object> taskInput = parametersUtils
            .getTaskInputV2(taskMapperContext.getTaskToSchedule().getInputParameters(), workflowInstance, taskId,
                taskDefinition);

        Task lambdaTask = new Task();
        lambdaTask.setTaskType(TASK_TYPE_LAMBDA);
        lambdaTask.setTaskDefName(taskMapperContext.getTaskToSchedule().getName());
        lambdaTask.setReferenceTaskName(taskMapperContext.getTaskToSchedule().getTaskReferenceName());
        lambdaTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        lambdaTask.setWorkflowType(workflowInstance.getWorkflowName());
        lambdaTask.setCorrelationId(workflowInstance.getCorrelationId());
        lambdaTask.setStartTime(System.currentTimeMillis());
        lambdaTask.setScheduledTime(System.currentTimeMillis());
        lambdaTask.setInputData(taskInput);
        lambdaTask.setTaskId(taskId);
        lambdaTask.setStatus(Task.Status.IN_PROGRESS);
        lambdaTask.setWorkflowTask(taskToSchedule);
        lambdaTask.setWorkflowPriority(workflowInstance.getPriority());

        return Collections.singletonList(lambdaTask);
    }
}
