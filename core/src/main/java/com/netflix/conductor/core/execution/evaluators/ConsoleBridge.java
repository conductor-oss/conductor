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
package com.netflix.conductor.core.execution.evaluators;

import java.util.ArrayList;
import java.util.List;

import com.netflix.conductor.common.metadata.tasks.TaskExecLog;

public class ConsoleBridge {
    private final List<TaskExecLog> logEntries = new ArrayList<>();

    private final String taskId;

    public ConsoleBridge(String taskId) {
        this.taskId = taskId;
    }

    public void error(Object message) {
        log("[Error]", message);
    }

    public void info(Object message) {
        log("[Info]", message);
    }

    public void log(Object message) {
        log("[Log]", message);
    }

    private void log(String level, Object message) {
        String logEntry = String.format("%s \"%s\"", level, message);
        var entry = new TaskExecLog(logEntry);
        entry.setTaskId(taskId);
        logEntries.add(entry);
    }

    public List<TaskExecLog> logs() {
        return logEntries;
    }
}
