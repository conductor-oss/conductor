/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.core.execution.mapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.SystemTaskType;

public class ExclusiveJoinTaskMapper implements TaskMapper {

	public static final Logger logger = LoggerFactory.getLogger(ExclusiveJoinTaskMapper.class);

	@Override
	public List<Task> getMappedTasks(TaskMapperContext taskMapperContext) {

		logger.debug("TaskMapperContext {} in ExclusiveJoinTaskMapper", taskMapperContext);

		WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
		Workflow workflowInstance = taskMapperContext.getWorkflowInstance();
		String taskId = taskMapperContext.getTaskId();

		Map<String, Object> joinInput = new HashMap<>();
		joinInput.put("joinOn", taskToSchedule.getJoinOn());

		if (taskToSchedule.getDefaultExclusiveJoinTask() != null) {
			joinInput.put("defaultExclusiveJoinTask", taskToSchedule.getDefaultExclusiveJoinTask());
		}

		Task joinTask = new Task();
		joinTask.setTaskType(SystemTaskType.EXCLUSIVE_JOIN.name());
		joinTask.setTaskDefName(SystemTaskType.EXCLUSIVE_JOIN.name());
		joinTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
		joinTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
		joinTask.setCorrelationId(workflowInstance.getCorrelationId());
		joinTask.setWorkflowType(workflowInstance.getWorkflowName());
		joinTask.setScheduledTime(System.currentTimeMillis());
		joinTask.setStartTime(System.currentTimeMillis());
		joinTask.setInputData(joinInput);
		joinTask.setTaskId(taskId);
		joinTask.setStatus(Task.Status.IN_PROGRESS);
		joinTask.setWorkflowPriority(workflowInstance.getPriority());
		joinTask.setWorkflowTask(taskToSchedule);

		return Collections.singletonList(joinTask);
	}
}
