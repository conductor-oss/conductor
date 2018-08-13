/**
 * Copyright 2017 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.core.events;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.metadata.events.EventHandler.StartWorkflow;
import com.netflix.conductor.common.metadata.events.EventHandler.TaskDetails;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.JsonUtils;
import com.netflix.conductor.service.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Viren
 * Action Processor subscribes to the Event Actions queue and processes the actions (e.g. start workflow etc)
 * <p><b>Warning:</b> This is a work in progress and may be changed in future.  Not ready for production yet.
 */
@Singleton
public class ActionProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ActionProcessor.class);

    private final WorkflowExecutor executor;
    private final MetadataService metadataService;
    private final ParametersUtils parametersUtils = new ParametersUtils();
    private final JsonUtils jsonUtils = new JsonUtils();

    @Inject
    public ActionProcessor(WorkflowExecutor executor, MetadataService metadataService) {
        this.executor = executor;
        this.metadataService = metadataService;
    }

    public Map<String, Object> execute(Action action, Object payloadObject, String event, String messageId) {

        logger.debug("Executing action: {} for event: {} with messageId:{}", action.getAction(), event, messageId);

        Object jsonObject = payloadObject;
        if (action.isExpandInlineJSON()) {
            jsonObject = jsonUtils.expand(payloadObject);
        }

        switch (action.getAction()) {
            case start_workflow:
                return startWorkflow(action, jsonObject, event, messageId);
            case complete_task:
                return completeTask(action, jsonObject, action.getComplete_task(), Status.COMPLETED, event, messageId);
            case fail_task:
                return completeTask(action, jsonObject, action.getFail_task(), Status.FAILED, event, messageId);
            default:
                break;
        }
        throw new UnsupportedOperationException("Action not supported " + action.getAction() + " for event " + event);
    }

    @VisibleForTesting
    Map<String, Object> completeTask(Action action, Object payload, TaskDetails taskDetails, Status status, String event, String messageId) {

        Map<String, Object> input = new HashMap<>();
        input.put("workflowId", taskDetails.getWorkflowId());
        input.put("taskRefName", taskDetails.getTaskRefName());
        input.putAll(taskDetails.getOutput());

        Map<String, Object> replaced = parametersUtils.replace(input, payload);
        String workflowId = "" + replaced.get("workflowId");
        String taskRefName = "" + replaced.get("taskRefName");
        Workflow found = executor.getWorkflow(workflowId, true);
        if (found == null) {
            replaced.put("error", "No workflow found with ID: " + workflowId);
            return replaced;
        }
        Task task = found.getTaskByRefName(taskRefName);
        if (task == null) {
            replaced.put("error", "No task found with reference name: " + taskRefName + ", workflowId: " + workflowId);
            return replaced;
        }

        task.setStatus(status);
        task.setOutputData(replaced);
        task.getOutputData().put("conductor.event.messageId", messageId);
        task.getOutputData().put("conductor.event.name", event);

        try {
            executor.updateTask(new TaskResult(task));
        } catch (RuntimeException e) {
            logger.error("Error updating task: {} in workflow: {} in action: {} for event: {} for message: {}", taskDetails.getTaskRefName(), taskDetails.getWorkflowId(), action.getAction(), event, messageId, e);
            replaced.put("error", e.getMessage());
            throw e;
        }
        return replaced;
    }

    private Map<String, Object> startWorkflow(Action action, Object payload, String event, String messageId) {
        StartWorkflow params = action.getStart_workflow();
        Map<String, Object> output = new HashMap<>();
        try {
            WorkflowDef def = metadataService.getWorkflowDef(params.getName(), params.getVersion());
            Map<String, Object> inputParams = params.getInput();
            Map<String, Object> workflowInput = parametersUtils.replace(inputParams, payload);
            workflowInput.put("conductor.event.messageId", messageId);
            workflowInput.put("conductor.event.name", event);

            String id = executor.startWorkflow(def.getName(), def.getVersion(), params.getCorrelationId(), workflowInput, event);
            output.put("workflowId", id);
        } catch (RuntimeException e) {
            logger.error("Error starting workflow: {}, version: {}, for event: {} for message: {}", params.getName(), params.getVersion(), event, messageId, e);
            output.put("error", e.getMessage());
            throw e;
        }
        return output;
    }
}
