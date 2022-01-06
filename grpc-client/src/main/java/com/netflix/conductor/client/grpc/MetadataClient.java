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
package com.netflix.conductor.client.grpc;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.MetadataServicePb;

import com.google.common.base.Preconditions;

public class MetadataClient extends ClientBase {

    private final MetadataServiceGrpc.MetadataServiceBlockingStub stub;

    public MetadataClient(String address, int port) {
        super(address, port);
        this.stub = MetadataServiceGrpc.newBlockingStub(this.channel);
    }

    /**
     * Register a workflow definition with the server
     *
     * @param workflowDef the workflow definition
     */
    public void registerWorkflowDef(WorkflowDef workflowDef) {
        Preconditions.checkNotNull(workflowDef, "Worfklow definition cannot be null");
        stub.createWorkflow(
                MetadataServicePb.CreateWorkflowRequest.newBuilder()
                        .setWorkflow(protoMapper.toProto(workflowDef))
                        .build());
    }

    /**
     * Updates a list of existing workflow definitions
     *
     * @param workflowDefs List of workflow definitions to be updated
     */
    public void updateWorkflowDefs(List<WorkflowDef> workflowDefs) {
        Preconditions.checkNotNull(workflowDefs, "Workflow defs list cannot be null");
        stub.updateWorkflows(
                MetadataServicePb.UpdateWorkflowsRequest.newBuilder()
                        .addAllDefs(workflowDefs.stream().map(protoMapper::toProto)::iterator)
                        .build());
    }

    /**
     * Retrieve the workflow definition
     *
     * @param name the name of the workflow
     * @param version the version of the workflow def
     * @return Workflow definition for the given workflow and version
     */
    public WorkflowDef getWorkflowDef(String name, @Nullable Integer version) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "name cannot be blank");

        MetadataServicePb.GetWorkflowRequest.Builder request =
                MetadataServicePb.GetWorkflowRequest.newBuilder().setName(name);

        if (version != null) {
            request.setVersion(version);
        }

        return protoMapper.fromProto(stub.getWorkflow(request.build()).getWorkflow());
    }

    /**
     * Registers a list of task types with the conductor server
     *
     * @param taskDefs List of task types to be registered.
     */
    public void registerTaskDefs(List<TaskDef> taskDefs) {
        Preconditions.checkNotNull(taskDefs, "Task defs list cannot be null");
        stub.createTasks(
                MetadataServicePb.CreateTasksRequest.newBuilder()
                        .addAllDefs(taskDefs.stream().map(protoMapper::toProto)::iterator)
                        .build());
    }

    /**
     * Updates an existing task definition
     *
     * @param taskDef the task definition to be updated
     */
    public void updateTaskDef(TaskDef taskDef) {
        Preconditions.checkNotNull(taskDef, "Task definition cannot be null");
        stub.updateTask(
                MetadataServicePb.UpdateTaskRequest.newBuilder()
                        .setTask(protoMapper.toProto(taskDef))
                        .build());
    }

    /**
     * Retrieve the task definition of a given task type
     *
     * @param taskType type of task for which to retrieve the definition
     * @return Task Definition for the given task type
     */
    public TaskDef getTaskDef(String taskType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        return protoMapper.fromProto(
                stub.getTask(
                                MetadataServicePb.GetTaskRequest.newBuilder()
                                        .setTaskType(taskType)
                                        .build())
                        .getTask());
    }

    /**
     * Removes the task definition of a task type from the conductor server. Use with caution.
     *
     * @param taskType Task type to be unregistered.
     */
    public void unregisterTaskDef(String taskType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(taskType), "Task type cannot be blank");
        stub.deleteTask(
                MetadataServicePb.DeleteTaskRequest.newBuilder().setTaskType(taskType).build());
    }
}
