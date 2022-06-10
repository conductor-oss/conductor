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
package com.netflix.conductor.core.execution.tasks;

import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT;
import static com.netflix.conductor.core.utils.DateTimeUtils.parseDate;
import static com.netflix.conductor.core.utils.DateTimeUtils.parseDuration;
import static com.netflix.conductor.model.TaskModel.Status.*;

@Component(TASK_TYPE_WAIT)
public class Wait extends WorkflowSystemTask {

    public static final String DURATION_INPUT = "duration";
    public static final String UNTIL_INPUT = "until";

    public Wait() {
        super(TASK_TYPE_WAIT);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {

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
                long seconds = (timeInMS - now) / 1000;
                task.setWaitTimeout(timeInMS);

            } catch (ParseException parseException) {
                task.setReasonForIncompletion(
                        "Invalid/Unsupported Wait Until format.  Provided: " + until);
                task.setStatus(FAILED_WITH_TERMINAL_ERROR);
            }
        }
        task.setStatus(IN_PROGRESS);
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        long timeOut = task.getWaitTimeout();
        if (timeOut == 0) {
            return false;
        }
        if (System.currentTimeMillis() > timeOut) {
            task.setStatus(COMPLETED);
            return true;
        }

        return false;
    }
}
