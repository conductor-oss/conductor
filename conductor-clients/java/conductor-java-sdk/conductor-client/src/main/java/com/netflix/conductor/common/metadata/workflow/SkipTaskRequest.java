/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.common.metadata.workflow;

import java.util.Map;

import com.google.protobuf.Any;

public class SkipTaskRequest {

    private Map<String, Object> taskInput;

    private Map<String, Object> taskOutput;

    private Any taskInputMessage;

    private Any taskOutputMessage;

    public Map<String, Object> getTaskInput() {
        return taskInput;
    }

    public void setTaskInput(Map<String, Object> taskInput) {
        this.taskInput = taskInput;
    }

    public Map<String, Object> getTaskOutput() {
        return taskOutput;
    }

    public void setTaskOutput(Map<String, Object> taskOutput) {
        this.taskOutput = taskOutput;
    }

    public Any getTaskInputMessage() {
        return taskInputMessage;
    }

    public void setTaskInputMessage(Any taskInputMessage) {
        this.taskInputMessage = taskInputMessage;
    }

    public Any getTaskOutputMessage() {
        return taskOutputMessage;
    }

    public void setTaskOutputMessage(Any taskOutputMessage) {
        this.taskOutputMessage = taskOutputMessage;
    }

}