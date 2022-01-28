/*
 * Copyright 2021 Netflix, Inc.
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

import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.common.metadata.tasks.TaskType;

@ProtoMessage
public class DynamicForkJoinTask {

    @ProtoField(id = 1)
    private String taskName;

    @ProtoField(id = 2)
    private String workflowName;

    @ProtoField(id = 3)
    private String referenceName;

    @ProtoField(id = 4)
    private Map<String, Object> input = new HashMap<>();

    @ProtoField(id = 5)
    private String type = TaskType.SIMPLE.name();

    public DynamicForkJoinTask() {}

    public DynamicForkJoinTask(
            String taskName, String workflowName, String referenceName, Map<String, Object> input) {
        super();
        this.taskName = taskName;
        this.workflowName = workflowName;
        this.referenceName = referenceName;
        this.input = input;
    }

    public DynamicForkJoinTask(
            String taskName,
            String workflowName,
            String referenceName,
            String type,
            Map<String, Object> input) {
        super();
        this.taskName = taskName;
        this.workflowName = workflowName;
        this.referenceName = referenceName;
        this.input = input;
        this.type = type;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
