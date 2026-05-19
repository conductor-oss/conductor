/*
 * Copyright 2022 Orkes, Inc.
 * <p>
 * Licensed under the Orkes Enterprise License (the "License"); you may not use this file except in compliance with
 * the License.
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.tasks.webhook;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.model.TaskModel;

import lombok.extern.slf4j.Slf4j;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WAIT_FOR_WEBHOOK;

@Component
@Slf4j
public class WebhookTaskMapper implements TaskMapper {

    @Override
    public String getTaskType() {
        return WAIT_FOR_WEBHOOK;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {

        log.debug("TaskMapperContext {} in WebhookTaskMapper", taskMapperContext);

        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        int retryCount = taskMapperContext.getRetryCount();

        List<TaskModel> tasksToBeScheduled = new LinkedList<>();
        TaskModel webhookTask = taskMapperContext.createTaskModel();
        webhookTask.setTaskType(WAIT_FOR_WEBHOOK);
        webhookTask.setTaskDefName(WAIT_FOR_WEBHOOK);
        long epochMillis = System.currentTimeMillis();
        webhookTask.setStartTime(epochMillis);
        webhookTask.setEndTime(epochMillis);
        webhookTask.setInputData(taskInput);
        webhookTask.setRetryCount(retryCount);
        webhookTask.setStatus(TaskModel.Status.IN_PROGRESS);

        tasksToBeScheduled.add(webhookTask);
        return tasksToBeScheduled;
    }
}
