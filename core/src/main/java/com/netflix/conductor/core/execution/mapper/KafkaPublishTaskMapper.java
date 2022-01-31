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
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

@Component
public class KafkaPublishTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(KafkaPublishTaskMapper.class);

    private final ParametersUtils parametersUtils;
    private final MetadataDAO metadataDAO;

    @Autowired
    public KafkaPublishTaskMapper(ParametersUtils parametersUtils, MetadataDAO metadataDAO) {
        this.parametersUtils = parametersUtils;
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.KAFKA_PUBLISH;
    }

    /**
     * This method maps a {@link WorkflowTask} of type {@link TaskType#KAFKA_PUBLISH} to a {@link
     * TaskModel} in a {@link TaskModel.Status#SCHEDULED} state
     *
     * @param taskMapperContext: A wrapper class containing the {@link WorkflowTask}, {@link
     *     WorkflowDef}, {@link WorkflowModel} and a string representation of the TaskId
     * @return a List with just one Kafka task
     * @throws TerminateWorkflowException In case if the task definition does not exist
     */
    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {

        LOGGER.debug("TaskMapperContext {} in KafkaPublishTaskMapper", taskMapperContext);

        WorkflowTask taskToSchedule = taskMapperContext.getTaskToSchedule();
        WorkflowModel workflowInstance = taskMapperContext.getWorkflowInstance();
        String taskId = taskMapperContext.getTaskId();
        int retryCount = taskMapperContext.getRetryCount();

        TaskDef taskDefinition =
                Optional.ofNullable(taskMapperContext.getTaskDefinition())
                        .orElseGet(() -> metadataDAO.getTaskDef(taskToSchedule.getName()));

        Map<String, Object> input =
                parametersUtils.getTaskInputV2(
                        taskToSchedule.getInputParameters(),
                        workflowInstance,
                        taskId,
                        taskDefinition);

        TaskModel kafkaPublishTask = new TaskModel();
        kafkaPublishTask.setTaskType(taskToSchedule.getType());
        kafkaPublishTask.setTaskDefName(taskToSchedule.getName());
        kafkaPublishTask.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        kafkaPublishTask.setWorkflowInstanceId(workflowInstance.getWorkflowId());
        kafkaPublishTask.setWorkflowType(workflowInstance.getWorkflowName());
        kafkaPublishTask.setCorrelationId(workflowInstance.getCorrelationId());
        kafkaPublishTask.setScheduledTime(System.currentTimeMillis());
        kafkaPublishTask.setTaskId(taskId);
        kafkaPublishTask.setInputData(input);
        kafkaPublishTask.setStatus(TaskModel.Status.SCHEDULED);
        kafkaPublishTask.setRetryCount(retryCount);
        kafkaPublishTask.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        kafkaPublishTask.setWorkflowTask(taskToSchedule);
        kafkaPublishTask.setWorkflowPriority(workflowInstance.getPriority());
        if (Objects.nonNull(taskDefinition)) {
            kafkaPublishTask.setExecutionNameSpace(taskDefinition.getExecutionNameSpace());
            kafkaPublishTask.setIsolationGroupId(taskDefinition.getIsolationGroupId());
            kafkaPublishTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
            kafkaPublishTask.setRateLimitFrequencyInSeconds(
                    taskDefinition.getRateLimitFrequencyInSeconds());
        }
        return Collections.singletonList(kafkaPublishTask);
    }
}
