/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.core.execution.tasks;

import java.util.List;

import org.springframework.stereotype.Component;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.service.TaskService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ServerTaskClient extends TaskClient {

    private final TaskService taskService;

    public ServerTaskClient(TaskService taskService) {
        log.info("ServerTaskClient::init");
        this.taskService = taskService;
    }

    @Override
    public Boolean ack(String taskId, String workerId) {
        taskService.ackTaskReceived(taskId, workerId);
        return true;
    }

    @Override
    public List<Task> batchPollTasksByTaskType(
            String taskType, String workerId, int count, int timeoutInMillisecond) {
        return taskService.batchPoll(taskType, workerId, null, count, timeoutInMillisecond);
    }

    @Override
    public List<Task> batchPollTasksInDomain(
            String taskType, String domain, String workerId, int count, int timeoutInMillisecond) {
        return taskService.batchPoll(taskType, workerId, domain, count, timeoutInMillisecond);
    }

    @Override
    public Task pollTask(String taskType, String workerId, String domain) {
        return taskService.poll(taskType, workerId, domain);
    }

    @Override
    public Task updateTaskV2(TaskResult taskResult) {
        TaskModel updatedTask = taskService.updateTask(taskResult);
        if (updatedTask == null) {
            return null;
        }
        String taskType = updatedTask.getTaskType();
        String domain = updatedTask.getDomain();
        return pollTask(taskType, taskResult.getWorkerId(), domain);
    }

    @Override
    public void updateTask(TaskResult taskResult) {
        taskService.updateTask(taskResult);
    }

    @Override
    public void logMessageForTask(String taskId, String logMessage) {
        taskService.log(taskId, logMessage);
    }
}
