/*
 * Copyright 2022 Orkes, Inc.
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
package io.orkes.conductor.client.model;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TaskDetails {

    private Map<String, Object> output = null;

    private String taskId = null;

    private String taskRefName = null;

    private String workflowId = null;

    public TaskDetails output(Map<String, Object> output) {
        this.output = output;
        return this;
    }

    public TaskDetails putOutputItem(String key, Object outputItem) {
        if (this.output == null) {
            this.output = new HashMap<>();
        }
        this.output.put(key, outputItem);
        return this;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public TaskDetails taskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public TaskDetails taskRefName(String taskRefName) {
        this.taskRefName = taskRefName;
        return this;
    }

    public String getTaskRefName() {
        return taskRefName;
    }

    public void setTaskRefName(String taskRefName) {
        this.taskRefName = taskRefName;
    }

    public TaskDetails workflowId(String workflowId) {
        this.workflowId = workflowId;
        return this;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

}
