/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.common.metadata.events;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.netflix.conductor.annotations.protogen.ProtoEnum;
import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;

import com.google.protobuf.Any;
import io.swagger.v3.oas.annotations.Hidden;

/** Defines an event handler */
@ProtoMessage
public class EventHandler {

    @ProtoField(id = 1)
    @NotEmpty(message = "Missing event handler name")
    private String name;

    @ProtoField(id = 2)
    @NotEmpty(message = "Missing event location")
    private String event;

    @ProtoField(id = 3)
    private String condition;

    @ProtoField(id = 4)
    @NotNull
    @NotEmpty(message = "No actions specified. Please specify at-least one action")
    private List<@Valid Action> actions = new LinkedList<>();

    @ProtoField(id = 5)
    private boolean active;

    @ProtoField(id = 6)
    private String evaluatorType;

    public EventHandler() {}

    /** @return the name MUST be unique within a conductor instance */
    public String getName() {
        return name;
    }

    /** @param name the name to set */
    public void setName(String name) {
        this.name = name;
    }

    /** @return the event */
    public String getEvent() {
        return event;
    }

    /** @param event the event to set */
    public void setEvent(String event) {
        this.event = event;
    }

    /** @return the condition */
    public String getCondition() {
        return condition;
    }

    /** @param condition the condition to set */
    public void setCondition(String condition) {
        this.condition = condition;
    }

    /** @return the actions */
    public List<Action> getActions() {
        return actions;
    }

    /** @param actions the actions to set */
    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    /** @return the active */
    public boolean isActive() {
        return active;
    }

    /** @param active if set to false, the event handler is deactivated */
    public void setActive(boolean active) {
        this.active = active;
    }

    /** @return the evaluator type */
    public String getEvaluatorType() {
        return evaluatorType;
    }

    /** @param evaluatorType the evaluatorType to set */
    public void setEvaluatorType(String evaluatorType) {
        this.evaluatorType = evaluatorType;
    }

    @ProtoMessage
    public static class Action {

        @ProtoEnum
        public enum Type {
            start_workflow,
            complete_task,
            fail_task
        }

        @ProtoField(id = 1)
        private Type action;

        @ProtoField(id = 2)
        private StartWorkflow start_workflow;

        @ProtoField(id = 3)
        private TaskDetails complete_task;

        @ProtoField(id = 4)
        private TaskDetails fail_task;

        @ProtoField(id = 5)
        private boolean expandInlineJSON;

        /** @return the action */
        public Type getAction() {
            return action;
        }

        /** @param action the action to set */
        public void setAction(Type action) {
            this.action = action;
        }

        /** @return the start_workflow */
        public StartWorkflow getStart_workflow() {
            return start_workflow;
        }

        /** @param start_workflow the start_workflow to set */
        public void setStart_workflow(StartWorkflow start_workflow) {
            this.start_workflow = start_workflow;
        }

        /** @return the complete_task */
        public TaskDetails getComplete_task() {
            return complete_task;
        }

        /** @param complete_task the complete_task to set */
        public void setComplete_task(TaskDetails complete_task) {
            this.complete_task = complete_task;
        }

        /** @return the fail_task */
        public TaskDetails getFail_task() {
            return fail_task;
        }

        /** @param fail_task the fail_task to set */
        public void setFail_task(TaskDetails fail_task) {
            this.fail_task = fail_task;
        }

        /**
         * @param expandInlineJSON when set to true, the in-lined JSON strings are expanded to a
         *     full json document
         */
        public void setExpandInlineJSON(boolean expandInlineJSON) {
            this.expandInlineJSON = expandInlineJSON;
        }

        /** @return true if the json strings within the payload should be expanded. */
        public boolean isExpandInlineJSON() {
            return expandInlineJSON;
        }
    }

    @ProtoMessage
    public static class TaskDetails {

        @ProtoField(id = 1)
        private String workflowId;

        @ProtoField(id = 2)
        private String taskRefName;

        @ProtoField(id = 3)
        private Map<String, Object> output = new HashMap<>();

        @ProtoField(id = 4)
        @Hidden
        private Any outputMessage;

        @ProtoField(id = 5)
        private String taskId;

        /** @return the workflowId */
        public String getWorkflowId() {
            return workflowId;
        }

        /** @param workflowId the workflowId to set */
        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        /** @return the taskRefName */
        public String getTaskRefName() {
            return taskRefName;
        }

        /** @param taskRefName the taskRefName to set */
        public void setTaskRefName(String taskRefName) {
            this.taskRefName = taskRefName;
        }

        /** @return the output */
        public Map<String, Object> getOutput() {
            return output;
        }

        /** @param output the output to set */
        public void setOutput(Map<String, Object> output) {
            this.output = output;
        }

        public Any getOutputMessage() {
            return outputMessage;
        }

        public void setOutputMessage(Any outputMessage) {
            this.outputMessage = outputMessage;
        }

        /** @return the taskId */
        public String getTaskId() {
            return taskId;
        }

        /** @param taskId the taskId to set */
        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }
    }

    @ProtoMessage
    public static class StartWorkflow {

        @ProtoField(id = 1)
        private String name;

        @ProtoField(id = 2)
        private Integer version;

        @ProtoField(id = 3)
        private String correlationId;

        @ProtoField(id = 4)
        private Map<String, Object> input = new HashMap<>();

        @ProtoField(id = 5)
        @Hidden
        private Any inputMessage;

        @ProtoField(id = 6)
        private Map<String, String> taskToDomain;

        /** @return the name */
        public String getName() {
            return name;
        }

        /** @param name the name to set */
        public void setName(String name) {
            this.name = name;
        }

        /** @return the version */
        public Integer getVersion() {
            return version;
        }

        /** @param version the version to set */
        public void setVersion(Integer version) {
            this.version = version;
        }

        /** @return the correlationId */
        public String getCorrelationId() {
            return correlationId;
        }

        /** @param correlationId the correlationId to set */
        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        /** @return the input */
        public Map<String, Object> getInput() {
            return input;
        }

        /** @param input the input to set */
        public void setInput(Map<String, Object> input) {
            this.input = input;
        }

        public Any getInputMessage() {
            return inputMessage;
        }

        public void setInputMessage(Any inputMessage) {
            this.inputMessage = inputMessage;
        }

        public Map<String, String> getTaskToDomain() {
            return taskToDomain;
        }

        public void setTaskToDomain(Map<String, String> taskToDomain) {
            this.taskToDomain = taskToDomain;
        }
    }
}
