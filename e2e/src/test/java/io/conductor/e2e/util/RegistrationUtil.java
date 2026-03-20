/*
 * Copyright 2024 Conductor Authors.
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
package io.conductor.e2e.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

public class RegistrationUtil {

    public static void registerWorkflowDef(
            String workflowName,
            String taskName1,
            String taskName2,
            MetadataClient metadataClient1) {
        TaskDef taskDef = new TaskDef(taskName1);
        taskDef.setRetryCount(0);
        taskDef.setOwnerEmail("test@conductor.io");
        TaskDef taskDef2 = new TaskDef(taskName2);
        taskDef2.setRetryCount(0);
        taskDef2.setOwnerEmail("test@conductor.io");

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName("inline_" + taskName1);
        inline.setName(taskName1);
        inline.setTaskDefinition(taskDef);
        inline.setWorkflowTaskType(TaskType.INLINE);
        inline.setInputParameters(Map.of("evaluatorType", "graaljs", "expression", "true;"));

        WorkflowTask simpleTask = new WorkflowTask();
        simpleTask.setTaskReferenceName(taskName2);
        simpleTask.setName(taskName2);
        simpleTask.setTaskDefinition(taskDef);
        simpleTask.setWorkflowTaskType(TaskType.SIMPLE);
        simpleTask.setInputParameters(Map.of("value", "${workflow.input.value}", "order", "123"));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setOwnerEmail("test@conductor.io");
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to monitor order state");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(inline, simpleTask));
        metadataClient1.updateWorkflowDefs(Arrays.asList(workflowDef));
        metadataClient1.registerTaskDefs(Arrays.asList(taskDef, taskDef2));
    }

    public static void registerWorkflowWithSubWorkflowDef(
            String workflowName,
            String subWorkflowName,
            String taskName,
            MetadataClient metadataClient) {
        TaskDef taskDef = new TaskDef(taskName);
        taskDef.setRetryCount(0);
        taskDef.setOwnerEmail("test@conductor.io");
        TaskDef taskDef2 = new TaskDef(subWorkflowName);
        taskDef2.setRetryCount(0);
        taskDef2.setOwnerEmail("test@conductor.io");

        WorkflowTask inline = new WorkflowTask();
        inline.setTaskReferenceName(taskName);
        inline.setName(taskName);
        inline.setTaskDefinition(taskDef);
        inline.setWorkflowTaskType(TaskType.SIMPLE);
        inline.setInputParameters(Map.of("evaluatorType", "graaljs", "expression", "true;"));

        WorkflowTask subworkflowTask = new WorkflowTask();
        subworkflowTask.setTaskReferenceName(subWorkflowName);
        subworkflowTask.setName(subWorkflowName);
        subworkflowTask.setTaskDefinition(taskDef2);
        subworkflowTask.setWorkflowTaskType(TaskType.SUB_WORKFLOW);
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName(subWorkflowName);
        subWorkflowParams.setVersion(1);
        subWorkflowParams.setPriority(12);
        subworkflowTask.setSubWorkflowParam(subWorkflowParams);
        subworkflowTask.setInputParameters(
                Map.of("subWorkflowName", subWorkflowName, "subWorkflowVersion", "1"));

        WorkflowDef subworkflowDef = new WorkflowDef();
        subworkflowDef.setName(subWorkflowName);
        subworkflowDef.setOwnerEmail("test@conductor.io");
        subworkflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        subworkflowDef.setDescription("Sub Workflow to test retry");
        subworkflowDef.setTimeoutSeconds(600);
        subworkflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        subworkflowDef.setTasks(Arrays.asList(inline));

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName(workflowName);
        workflowDef.setInputParameters(Arrays.asList("value", "inlineValue"));
        workflowDef.setDescription("Workflow to test retry");
        workflowDef.setTimeoutSeconds(600);
        workflowDef.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        workflowDef.setTasks(Arrays.asList(subworkflowTask));
        workflowDef.setOwnerEmail("test@conductor.io");
        metadataClient.updateWorkflowDefs(java.util.List.of(workflowDef));
        metadataClient.updateWorkflowDefs(java.util.List.of(subworkflowDef));
        metadataClient.registerTaskDefs(Arrays.asList(taskDef, taskDef2));
    }

    public static EventHandler registerAndGetEventHandler(
            String subscriberTopicName,
            EventClient eventClient,
            boolean startWorkflow,
            String integrationName,
            String handlerName) {
        // Convert JSON to EventHandler object
        EventHandler eventHandler = new EventHandler();
        eventHandler.setName(handlerName);
        eventHandler.setEvent("nats:" + integrationName + ":" + subscriberTopicName + ".*");
        eventHandler.setCondition("true");
        eventHandler.setActive(true);
        eventHandler.setEvaluatorType("javascript");
        // Note: tags field can be null - server handles it defensively

        EventHandler.Action action = new EventHandler.Action();
        if (startWorkflow) {
            // Start the workflow
            action.setAction(EventHandler.Action.Type.complete_task);
            EventHandler.TaskDetails taskDetails = new EventHandler.TaskDetails();
            taskDetails.setWorkflowId("${workflowInstanceId}");
            taskDetails.setTaskRefName("${taskReferenceName}");
            action.setComplete_task(taskDetails);
        } else {
            action.setAction(EventHandler.Action.Type.update_workflow_variables);
            EventHandler.UpdateWorkflowVariables updateWorkflowVariables =
                    new EventHandler.UpdateWorkflowVariables();
            // Update the workflow variables so that we assert on this array size.
            updateWorkflowVariables.setVariables(Map.of("updatedBy", "e2e"));
            updateWorkflowVariables.setWorkflowId("${workflowInstanceId}");
            updateWorkflowVariables.setAppendArray(true);
            action.setUpdate_workflow_variables(updateWorkflowVariables);
            eventHandler.setActions(List.of(action));
        }
        eventHandler.setActions(List.of(action));

        // Register Event Handler
        eventClient.registerEventHandler(eventHandler);
        return eventHandler;
    }
}
