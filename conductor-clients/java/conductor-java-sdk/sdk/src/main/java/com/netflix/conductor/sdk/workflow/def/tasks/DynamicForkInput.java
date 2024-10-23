/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.sdk.workflow.def.tasks;

import java.util.List;
import java.util.Map;

public class DynamicForkInput {

    /** List of tasks to execute in parallel */
    private List<Task<?>> tasks;

    /**
     * Input to the tasks. Key is the reference name of the task and value is an Object that is sent
     * as input to the task
     */
    private Map<String, Object> inputs;

    public DynamicForkInput(List<Task<?>> tasks, Map<String, Object> inputs) {
        this.tasks = tasks;
        this.inputs = inputs;
    }

    public DynamicForkInput() {}

    public List<Task<?>> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task<?>> tasks) {
        this.tasks = tasks;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }
}
