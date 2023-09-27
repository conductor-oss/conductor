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

import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
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
import static com.netflix.conductor.core.execution.tasks.Wait.DURATION_INPUT;
import static com.netflix.conductor.core.execution.tasks.Wait.UNTIL_INPUT;
import static com.netflix.conductor.core.utils.DateTimeUtils.parseDate;
import static com.netflix.conductor.core.utils.DateTimeUtils.parseDuration;
import static com.netflix.conductor.model.TaskModel.Status.FAILED_WITH_TERMINAL_ERROR;

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
    public String getTaskType() {
        return TaskType.WAIT.name();
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {

        LOGGER.debug("TaskMapperContext {} in WaitTaskMapper", taskMapperContext);

        WorkflowModel workflowModel = taskMapperContext.getWorkflowModel();
        String taskId = taskMapperContext.getTaskId();

        Map<String, Object> waitTaskInput =
                parametersUtils.getTaskInputV2(
                        taskMapperContext.getWorkflowTask().getInputParameters(),
                        workflowModel,
                        taskId,
                        null);

        TaskModel waitTask = taskMapperContext.createTaskModel();
        waitTask.setTaskType(TASK_TYPE_WAIT);
        waitTask.setInputData(waitTaskInput);
        waitTask.setStartTime(System.currentTimeMillis());
        waitTask.setStatus(TaskModel.Status.IN_PROGRESS);
        setCallbackAfter(waitTask);
        return List.of(waitTask);
    }

    void setCallbackAfter(TaskModel task) {
        String duration =
                Optional.ofNullable(task.getInputData().get(DURATION_INPUT)).orElse("").toString();
        String until =
                Optional.ofNullable(task.getInputData().get(UNTIL_INPUT)).orElse("").toString();

        if (StringUtils.isNotBlank(duration) && StringUtils.isNotBlank(until)) {
            task.setReasonForIncompletion(
                    "Both 'duration' and 'until' specified. Please provide only one input");
            task.setStatus(FAILED_WITH_TERMINAL_ERROR);
            return;
        }

        if (StringUtils.isNotBlank(duration)) {

            Duration timeDuration = parseDuration(duration);
            long waitTimeout = System.currentTimeMillis() + (timeDuration.getSeconds() * 1000);
            task.setWaitTimeout(waitTimeout);
            long seconds = timeDuration.getSeconds();
            task.setCallbackAfterSeconds(seconds);

        } else if (StringUtils.isNotBlank(until)) {
            try {

                Date expiryDate = parseDate(until);
                long timeInMS = expiryDate.getTime();
                long now = System.currentTimeMillis();
                long seconds = ((timeInMS - now) / 1000);
                if (seconds < 0) {
                    seconds = 0;
                }
                task.setCallbackAfterSeconds(seconds);
                task.setWaitTimeout(timeInMS);

            } catch (ParseException parseException) {
                task.setReasonForIncompletion(
                        "Invalid/Unsupported Wait Until format.  Provided: " + until);
                task.setStatus(FAILED_WITH_TERMINAL_ERROR);
            }
        } else {
            // If there is no time duration specified then the WAIT task should wait forever
            task.setCallbackAfterSeconds(Integer.MAX_VALUE);
        }
    }
}
