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

public class SkipTaskRequest {

    private Map<String, Object> taskInput;

    private Map<String, Object> taskOutput;

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

}
