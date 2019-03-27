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
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.tasks.Wait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * An implementation of {@link TaskMapper} to map a {@link WorkflowTask} of type {@link TaskType#WAIT}
 * to a {@link Task} of type {@link Wait} with {@link Task.Status#IN_PROGRESS}
 */
public class WaitTaskMapper implements TaskMapper {

    public static final Logger logger = LoggerFactory.getLogger(WaitTaskMapper.class);

    private ParametersUtils parametersUtils;

    public WaitTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {

        logger.debug("TaskMapperContext {} in WaitTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> waitTaskInput = parametersUtils.getTaskInputV2(taskMapperContext.getTaskToSchedule().getInputParameters(),
                workflowInstance, taskId, null);

        Task waitTask = new Task();
        waitTask.setTaskType(Wait.NAME);
        waitTask.setTaskDefName(taskMapperContext.getTaskToSchedule().getName());
        waitTask.setReferenceTaskName(taskMapperContext.getTaskToSchedule().getTaskReferenceName());
        waitTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        waitTask.setWorkflowType(workflowInstance.getWorkflowName());
        waitTask.setCorrelationId(workflowInstance.getCorrelationId());
        waitTask.setScheduledTime(System.currentTimeMillis());
        waitTask.setInputData(waitTaskInput);
        waitTask.setTaskId(taskId);
        waitTask.setStatus(Task.Status.IN_PROGRESS);
        waitTask.setWorkflowTask(taskToSchedule);
        waitTask.setWorkflowPriority(workflowInstance.getPriority());
        return Collections.singletonList(waitTask);
    }
}
