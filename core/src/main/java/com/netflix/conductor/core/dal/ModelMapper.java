/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.core.dal;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage.Operation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage.PayloadType;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

@Component
public class ModelMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelMapper.class);

    private final ExternalPayloadStorageUtils externalPayloadStorageUtils;

    public ModelMapper(ExternalPayloadStorageUtils externalPayloadStorageUtils) {
        this.externalPayloadStorageUtils = externalPayloadStorageUtils;
    }

    /**
     * Fetch the fully formed workflow domain object with complete payloads
     *
     * @param workflowModel the workflow domain object from the datastore
     * @return the workflow domain object {@link WorkflowModel} with payloads from external storage
     */
    public WorkflowModel getFullCopy(WorkflowModel workflowModel) {
        populateWorkflowAndTaskPayloadData(workflowModel);
        return workflowModel;
    }

    /**
     * Get a lean copy of the workflow domain object with large payloads externalized
     *
     * @param workflowModel the fully formed workflow domain object
     * @return the workflow domain object {@link WorkflowModel} with large payloads externalized
     */
    public WorkflowModel getLeanCopy(WorkflowModel workflowModel) {
        WorkflowModel leanWorkflowModel = workflowModel.copy();
        externalizeWorkflowData(leanWorkflowModel);
        workflowModel.getTasks().forEach(this::getLeanCopy);
        return leanWorkflowModel;
    }

    /**
     * Map the workflow domain object to the workflow DTO
     *
     * @param workflowModel the workflow domain object {@link WorkflowModel}
     * @return the workflow DTO {@link Workflow}
     */
    public Workflow getWorkflow(WorkflowModel workflowModel) {
        externalizeWorkflowData(workflowModel);

        Workflow workflow = new Workflow();
        BeanUtils.copyProperties(workflowModel, workflow);
        workflow.setStatus(Workflow.WorkflowStatus.valueOf(workflowModel.getStatus().name()));
        workflow.setTasks(
                workflowModel.getTasks().stream().map(this::getTask).collect(Collectors.toList()));

        return workflow;
    }

    /**
     * Fetch the fully formed task domain object with complete payloads
     *
     * @param taskModel the task domain object from the datastore
     * @return the task domain object {@link TaskModel} with payloads from external storage
     */
    public TaskModel getFullCopy(TaskModel taskModel) {
        populateTaskData(taskModel);
        return taskModel;
    }

    /**
     * Get a lean copy of the task domain object with large payloads externalized
     *
     * @param taskModel the fully formed task domain object
     * @return the task domain object {@link TaskModel} with large payloads externalized
     */
    public TaskModel getLeanCopy(TaskModel taskModel) {
        TaskModel leanTaskModel = taskModel.copy();
        externalizeTaskData(leanTaskModel);
        return leanTaskModel;
    }

    /**
     * Map the task domain object to the task DTO
     *
     * @param taskModel the task domain object {@link TaskModel}
     * @return the task DTO {@link Task}
     */
    public Task getTask(TaskModel taskModel) {
        externalizeTaskData(taskModel);

        Task task = new Task();
        BeanUtils.copyProperties(taskModel, task);
        task.setStatus(Task.Status.valueOf(taskModel.getStatus().name()));
        return task;
    }

    public Task.Status mapToTaskStatus(TaskModel.Status status) {
        return Task.Status.valueOf(status.name());
    }

    /**
     * Populates the workflow input data and the tasks input/output data if stored in external
     * payload storage.
     *
     * @param workflowModel the workflowModel for which the payload data needs to be populated from
     *     external storage (if applicable)
     */
    private void populateWorkflowAndTaskPayloadData(WorkflowModel workflowModel) {
        if (StringUtils.isNotBlank(workflowModel.getExternalInputPayloadStoragePath())) {
            Map<String, Object> workflowInputParams =
                    externalPayloadStorageUtils.downloadPayload(
                            workflowModel.getExternalInputPayloadStoragePath());
            Monitors.recordExternalPayloadStorageUsage(
                    workflowModel.getWorkflowName(),
                    Operation.READ.toString(),
                    PayloadType.WORKFLOW_INPUT.toString());
            workflowModel.setInput(workflowInputParams);
            workflowModel.setExternalInputPayloadStoragePath(null);
        }

        if (StringUtils.isNotBlank(workflowModel.getExternalOutputPayloadStoragePath())) {
            Map<String, Object> workflowOutputParams =
                    externalPayloadStorageUtils.downloadPayload(
                            workflowModel.getExternalOutputPayloadStoragePath());
            Monitors.recordExternalPayloadStorageUsage(
                    workflowModel.getWorkflowName(),
                    Operation.READ.toString(),
                    PayloadType.WORKFLOW_OUTPUT.toString());
            workflowModel.setOutput(workflowOutputParams);
            workflowModel.setExternalOutputPayloadStoragePath(null);
        }

        workflowModel.getTasks().forEach(this::populateTaskData);
    }

    private void populateTaskData(TaskModel taskModel) {
        if (StringUtils.isNotBlank(taskModel.getExternalOutputPayloadStoragePath())) {
            taskModel.setOutputData(
                    externalPayloadStorageUtils.downloadPayload(
                            taskModel.getExternalOutputPayloadStoragePath()));
            Monitors.recordExternalPayloadStorageUsage(
                    taskModel.getTaskDefName(),
                    Operation.READ.toString(),
                    PayloadType.TASK_OUTPUT.toString());
            taskModel.setExternalOutputPayloadStoragePath(null);
        }

        if (StringUtils.isNotBlank(taskModel.getExternalInputPayloadStoragePath())) {
            taskModel.setInputData(
                    externalPayloadStorageUtils.downloadPayload(
                            taskModel.getExternalInputPayloadStoragePath()));
            Monitors.recordExternalPayloadStorageUsage(
                    taskModel.getTaskDefName(),
                    Operation.READ.toString(),
                    PayloadType.TASK_INPUT.toString());
            taskModel.setExternalInputPayloadStoragePath(null);
        }
    }

    private void externalizeTaskData(TaskModel taskModel) {
        externalPayloadStorageUtils.verifyAndUpload(taskModel, PayloadType.TASK_INPUT);
        externalPayloadStorageUtils.verifyAndUpload(taskModel, PayloadType.TASK_OUTPUT);
    }

    private void externalizeWorkflowData(WorkflowModel workflowModel) {
        externalPayloadStorageUtils.verifyAndUpload(workflowModel, PayloadType.WORKFLOW_INPUT);
        externalPayloadStorageUtils.verifyAndUpload(workflowModel, PayloadType.WORKFLOW_OUTPUT);
    }
}
