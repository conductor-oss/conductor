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
package com.netflix.conductor.sdk.workflow.def;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.tasks.Task;
import com.netflix.conductor.sdk.workflow.def.tasks.TaskRegistry;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import com.netflix.conductor.sdk.workflow.utils.InputOutputGetter;
import com.netflix.conductor.sdk.workflow.utils.ObjectMapperProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @param <T> Type of the workflow input
 */
public class ConductorWorkflow<T> {

    public static final InputOutputGetter input =
            new InputOutputGetter("workflow", InputOutputGetter.Field.input);

    public static final InputOutputGetter output =
            new InputOutputGetter("workflow", InputOutputGetter.Field.output);

    private String name;

    private String description;

    private int version;

    private String failureWorkflow;

    private String ownerEmail;

    private WorkflowDef.TimeoutPolicy timeoutPolicy;

    private Map<String, Object> workflowOutput;

    private long timeoutSeconds;

    private boolean restartable = true;

    private T defaultInput;

    private Map<String, Object> variables;

    private List<Task> tasks = new ArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private final WorkflowExecutor workflowExecutor;

    private String webhookUrl;

    private String webhookAuthToken;

    public ConductorWorkflow(WorkflowExecutor workflowExecutor) {
        this.workflowOutput = new HashMap<>();
        this.workflowExecutor = workflowExecutor;
        this.restartable = true;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setFailureWorkflow(String failureWorkflow) {
        this.failureWorkflow = failureWorkflow;
    }

    public void add(Task task) {
        this.tasks.add(task);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getVersion() {
        return version;
    }

    public String getFailureWorkflow() {
        return failureWorkflow;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public WorkflowDef.TimeoutPolicy getTimeoutPolicy() {
        return timeoutPolicy;
    }

    public void setTimeoutPolicy(WorkflowDef.TimeoutPolicy timeoutPolicy) {
        this.timeoutPolicy = timeoutPolicy;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isRestartable() {
        return restartable;
    }

    public void setRestartable(boolean restartable) {
        this.restartable = restartable;
    }

    public T getDefaultInput() {
        return defaultInput;
    }

    public void setDefaultInput(T defaultInput) {
        this.defaultInput = defaultInput;
    }

    public Map<String, Object> getWorkflowOutput() {
        return workflowOutput;
    }

    public void setWorkflowOutput(Map<String, Object> workflowOutput) {
        this.workflowOutput = workflowOutput;
    }

    public Object getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookAuthToken() {
        return webhookAuthToken;
    }

    public void setWebhookAuthToken(String webhookAuthToken) {
        this.webhookAuthToken = webhookAuthToken;
    }

    /**
     * Execute a dynamic workflow without creating a definition in metadata store.
     *
     * <p><br>
     * <b>Note</b>: Use this with caution - as this does not promote re-usability of the workflows
     *
     * @param input Workflow Input - The input object is converted a JSON doc as an input to the
     *     workflow
     * @return
     */
    public CompletableFuture<Workflow> executeDynamic(T input) {
        return workflowExecutor.executeWorkflow(this, input);
    }

    /**
     * Executes the workflow using registered metadata definitions
     *
     * @see #registerWorkflow()
     * @param input
     * @return
     */
    public CompletableFuture<Workflow> execute(T input) {
        return workflowExecutor.executeWorkflow(this.getName(), this.getVersion(), input);
    }

    /**
     * Registers a new workflow in the server.
     *
     * @return true if the workflow is successfully registered. False if the workflow cannot be
     *     registered and the workflow definition already exists on the server with given name +
     *     version The call will throw a runtime exception if any of the tasks are missing
     *     definitions on the server.
     */
    public boolean registerWorkflow() {
        return registerWorkflow(false, false);
    }

    /**
     * @param overwrite set to true if the workflow should be overwritten if the definition already
     *     exists with the given name and version. <font color=red>Use with caution</font>
     * @return true if success, false otherwise.
     */
    public boolean registerWorkflow(boolean overwrite) {
        return registerWorkflow(overwrite, false);
    }

    /**
     * @param overwrite set to true if the workflow should be overwritten if the definition already
     *     exists with the given name and version. <font color=red>Use with caution</font>
     * @param registerTasks if set to true, missing task definitions are registered with the default
     *     configuration.
     * @return true if success, false otherwise.
     */
    public boolean registerWorkflow(boolean overwrite, boolean registerTasks) {
        WorkflowDef workflowDef = toWorkflowDef();
        List<String> missing = getMissingTasks(workflowDef);
        if (!missing.isEmpty()) {
            if (!registerTasks) {
                throw new RuntimeException(
                        "Workflow cannot be registered.  The following tasks do not have definitions.  "
                                + "Please register these tasks before creating the workflow.  Missing Tasks = "
                                + missing);
            } else {
                String ownerEmail = this.ownerEmail;
                missing.stream().forEach(taskName -> registerTaskDef(taskName, ownerEmail));
            }
        }
        return workflowExecutor.registerWorkflow(workflowDef, overwrite);
    }

    /**
     * @return Convert to the WorkflowDef model used by the Metadata APIs
     */
    public WorkflowDef toWorkflowDef() {

        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setDescription(description);
        def.setVersion(version);
        def.setFailureWorkflow(failureWorkflow);
        def.setOwnerEmail(ownerEmail);
        def.setTimeoutPolicy(timeoutPolicy);
        def.setTimeoutSeconds(timeoutSeconds);
        def.setRestartable(restartable);
        def.setOutputParameters(workflowOutput);
        def.setVariables(variables);
        def.setInputTemplate(objectMapper.convertValue(defaultInput, Map.class));
        def.setWebhookUrl(webhookUrl);
        def.setWebhookAuthToken(webhookAuthToken);

        for (Task task : tasks) {
            def.getTasks().addAll(task.getWorkflowDefTasks());
        }
        return def;
    }

    /**
     * Generate ConductorWorkflow based on the workflow metadata definition
     *
     * @param def
     * @return
     */
    public static <T> ConductorWorkflow<T> fromWorkflowDef(WorkflowDef def) {
        ConductorWorkflow<T> workflow = new ConductorWorkflow<>(null);
        fromWorkflowDef(workflow, def);
        return workflow;
    }

    public ConductorWorkflow<T> from(String workflowName, Integer workflowVersion) {
        WorkflowDef def =
                workflowExecutor.getMetadataClient().getWorkflowDef(workflowName, workflowVersion);
        fromWorkflowDef(this, def);
        return this;
    }

    private static <T> void fromWorkflowDef(ConductorWorkflow<T> workflow, WorkflowDef def) {
        workflow.setName(def.getName());
        workflow.setVersion(def.getVersion());
        workflow.setFailureWorkflow(def.getFailureWorkflow());
        workflow.setRestartable(def.isRestartable());
        workflow.setVariables(def.getVariables());
        workflow.setDefaultInput((T) def.getInputTemplate());

        workflow.setWorkflowOutput(def.getOutputParameters());
        workflow.setOwnerEmail(def.getOwnerEmail());
        workflow.setDescription(def.getDescription());
        workflow.setTimeoutSeconds(def.getTimeoutSeconds());
        workflow.setTimeoutPolicy(def.getTimeoutPolicy());

        List<WorkflowTask> workflowTasks = def.getTasks();
        for (WorkflowTask workflowTask : workflowTasks) {
            Task task = TaskRegistry.getTask(workflowTask);
            workflow.tasks.add(task);
        }
    }

    private List<String> getMissingTasks(WorkflowDef workflowDef) {
        List<String> missing = new ArrayList<>();
        workflowDef.collectTasks().stream()
                .filter(workflowTask -> workflowTask.getType().equals(TaskType.TASK_TYPE_SIMPLE))
                .map(WorkflowTask::getName)
                .distinct()
                .parallel()
                .forEach(
                        taskName -> {
                            try {
                                TaskDef taskDef =
                                        workflowExecutor.getMetadataClient().getTaskDef(taskName);
                            } catch (ConductorClientException cce) {
                                if (cce.getStatus() == 404) {
                                    missing.add(taskName);
                                } else {
                                    throw cce;
                                }
                            }
                        });
        return missing;
    }

    private void registerTaskDef(String taskName, String ownerEmail) {
        TaskDef taskDef = new TaskDef();
        taskDef.setName(taskName);
        taskDef.setOwnerEmail(ownerEmail);
        workflowExecutor.getMetadataClient().registerTaskDefs(Arrays.asList(taskDef));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConductorWorkflow workflow = (ConductorWorkflow) o;
        return version == workflow.version && Objects.equals(name, workflow.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }

    @Override
    public String toString() {
        try {
            return objectMapper.writeValueAsString(toWorkflowDef());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
