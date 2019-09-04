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
import com.netflix.conductor.core.execution.tasks.Lambda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author x-ultra
 */
public class LambdaTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(LambdaTaskMapper.class);
    private ParametersUtils parametersUtils;

    public LambdaTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }


    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {

        logger.debug("TaskMapperContext {} in LambdaTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> taskInput = parametersUtils.getTaskInputV2(taskMapperContext.getTaskToSchedule().getInputParameters(), workflowInstance, taskId, null);

        Task task = new Task();
        task.setTaskType(Lambda.TASK_NAME);
        task.setTaskDefName(taskMapperContext.getTaskToSchedule().getName());
        task.setReferenceTaskName(taskMapperContext.getTaskToSchedule().getTaskReferenceName());
        task.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        task.setWorkflowType(workflowInstance.getWorkflowName());
        task.setCorrelationId(workflowInstance.getCorrelationId());
        task.setScheduledTime(System.currentTimeMillis());
        task.setInputData(taskInput);
        task.setTaskId(taskId);
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setWorkflowTask(taskToSchedule);
        task.setWorkflowPriority(workflowInstance.getPriority());

        return Arrays.asList(task);
    }
}
