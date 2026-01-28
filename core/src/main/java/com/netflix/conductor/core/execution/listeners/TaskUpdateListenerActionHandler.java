/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.core.execution.listeners;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.model.TaskModel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TaskUpdateListenerActionHandler implements TaskUpdateListener<String> {
    public static final String TASK_ID = "listenerTaskId";
    public static final String TYPE = "TASK";

    private final ExecutionDAO executionDAO;
    private final List<Action<TaskModel>> actions;

    public TaskUpdateListenerActionHandler(
            ExecutionDAO executionDAO, List<Action<TaskModel>> actions) {
        this.executionDAO = executionDAO;
        this.actions = actions;
    }

    @Override
    public String getType() {
        return "TASK";
    }

    @Override
    public String targetFromOptions(Object entry) {
        return (String) entry;
    }

    @Override
    public void onTaskUpdate(TaskModel task, String listeningTaskId) {
        TaskModel listenerTask = executionDAO.getTask(listeningTaskId);
        Action.Result actionsResult = Action.Result.UNMODIFIED;

        for (Action<TaskModel> action : actions) {
            actionsResult = action.onTaskUpdate(listenerTask, task);
        }

        if (actionsResult.targetUpdated()) {
            executionDAO.updateTask(listenerTask);
        }
    }
}
