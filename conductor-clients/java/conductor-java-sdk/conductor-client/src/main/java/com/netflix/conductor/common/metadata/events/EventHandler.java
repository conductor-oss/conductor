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
package com.netflix.conductor.common.metadata.events;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import lombok.*;

/**
 * Defines an event handler
 */

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventHandler {

    /**
     * the name MUST be unique within a conductor instance
     */
    private String name;

    private String event;

    private String condition;

    private List<Action> actions = new LinkedList<>();

    private boolean active;

    private String evaluatorType;

    public static class Action {

        public enum Type {

            start_workflow, complete_task, fail_task, terminate_workflow, update_workflow_variables
        }

        private Type action;

        private StartWorkflow start_workflow;

        private TaskDetails complete_task;

        private TaskDetails fail_task;

        private boolean expandInlineJSON;

        private TerminateWorkflow terminate_workflow;

        private UpdateWorkflowVariables update_workflow_variables;

        /**
         * @return the action
         */
        public Type getAction() {
            return action;
        }

        /**
         * @param action the action to set
         */
        public void setAction(Type action) {
            this.action = action;
        }

        /**
         * @return the start_workflow
         */
        public StartWorkflow getStart_workflow() {
            return start_workflow;
        }

        /**
         * @param start_workflow the start_workflow to set
         */
        public void setStart_workflow(StartWorkflow start_workflow) {
            this.start_workflow = start_workflow;
        }

        /**
         * @return the complete_task
         */
        public TaskDetails getComplete_task() {
            return complete_task;
        }

        /**
         * @param complete_task the complete_task to set
         */
        public void setComplete_task(TaskDetails complete_task) {
            this.complete_task = complete_task;
        }

        /**
         * @return the fail_task
         */
        public TaskDetails getFail_task() {
            return fail_task;
        }

        /**
         * @param fail_task the fail_task to set
         */
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

        /**
         * @return true if the json strings within the payload should be expanded.
         */
        public boolean isExpandInlineJSON() {
            return expandInlineJSON;
        }

        /**
         * @return the terminate_workflow
         */
        public TerminateWorkflow getTerminate_workflow() {
            return terminate_workflow;
        }

        /**
         * @param terminate_workflow the terminate_workflow to set
         */
        public void setTerminate_workflow(TerminateWorkflow terminate_workflow) {
            this.terminate_workflow = terminate_workflow;
        }

        /**
         * @return the update_workflow_variables
         */
        public UpdateWorkflowVariables getUpdate_workflow_variables() {
            return update_workflow_variables;
        }

        /**
         * @param update_workflow_variables the update_workflow_variables to set
         */
        public void setUpdate_workflow_variables(UpdateWorkflowVariables update_workflow_variables) {
            this.update_workflow_variables = update_workflow_variables;
        }
    }

    public static class TaskDetails {

        private String workflowId;

        private String taskRefName;

        private Map<String, Object> output = new HashMap<>();

        private String taskId;

        /**
         * @return the workflowId
         */
        public String getWorkflowId() {
            return workflowId;
        }

        /**
         * @param workflowId the workflowId to set
         */
        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        /**
         * @return the taskRefName
         */
        public String getTaskRefName() {
            return taskRefName;
        }

        /**
         * @param taskRefName the taskRefName to set
         */
        public void setTaskRefName(String taskRefName) {
            this.taskRefName = taskRefName;
        }

        /**
         * @return the output
         */
        public Map<String, Object> getOutput() {
            return output;
        }

        /**
         * @param output the output to set
         */
        public void setOutput(Map<String, Object> output) {
            this.output = output;
        }

        /**
         * @return the taskId
         */
        public String getTaskId() {
            return taskId;
        }

        /**
         * @param taskId the taskId to set
         */
        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }
    }

    public static class StartWorkflow {

        private String name;

        private Integer version;

        private String correlationId;

        private Map<String, Object> input = new HashMap<>();

        private Map<String, String> taskToDomain;

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * @return the version
         */
        public Integer getVersion() {
            return version;
        }

        /**
         * @param version the version to set
         */
        public void setVersion(Integer version) {
            this.version = version;
        }

        /**
         * @return the correlationId
         */
        public String getCorrelationId() {
            return correlationId;
        }

        /**
         * @param correlationId the correlationId to set
         */
        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        /**
         * @return the input
         */
        public Map<String, Object> getInput() {
            return input;
        }

        /**
         * @param input the input to set
         */
        public void setInput(Map<String, Object> input) {
            this.input = input;
        }

        public Map<String, String> getTaskToDomain() {
            return taskToDomain;
        }

        public void setTaskToDomain(Map<String, String> taskToDomain) {
            this.taskToDomain = taskToDomain;
        }
    }

    public static class TerminateWorkflow {

        private String workflowId;

        private String terminationReason;

        /**
         * @return the workflowId
         */
        public String getWorkflowId() {
            return workflowId;
        }

        /**
         * @param workflowId the workflowId to set
         */
        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        /**
         * @return the reasonForTermination
         */
        public String getTerminationReason() {
            return terminationReason;
        }

        /**
         * @param terminationReason the reasonForTermination to set
         */
        public void setTerminationReason(String terminationReason) {
            this.terminationReason = terminationReason;
        }
    }

    public static class UpdateWorkflowVariables {

        private String workflowId;

        private Map<String, Object> variables;

        private Boolean appendArray;

        /**
         * @return the workflowId
         */
        public String getWorkflowId() {
            return workflowId;
        }

        /**
         * @param workflowId the workflowId to set
         */
        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        /**
         * @return the variables
         */
        public Map<String, Object> getVariables() {
            return variables;
        }

        /**
         * @param variables the variables to set
         */
        public void setVariables(Map<String, Object> variables) {
            this.variables = variables;
        }

        /**
         * @return appendArray
         */
        public Boolean isAppendArray() {
            return appendArray;
        }

        /**
         * @param appendArray the appendArray to set
         */
        public void setAppendArray(Boolean appendArray) {
            this.appendArray = appendArray;
        }
    }
}
