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
package com.netflix.conductor.grpc.server.service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.MetadataServicePb;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.proto.TaskDefPb;
import com.netflix.conductor.proto.WorkflowDefPb;
import com.netflix.conductor.service.MetadataService;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

@Service("grpcMetadataService")
public class MetadataServiceImpl extends MetadataServiceGrpc.MetadataServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceImpl.class);
    private static final ProtoMapper PROTO_MAPPER = ProtoMapper.INSTANCE;
    private static final GRPCHelper GRPC_HELPER = new GRPCHelper(LOGGER);

    private final MetadataService service;

    public MetadataServiceImpl(MetadataService service) {
        this.service = service;
    }

    @Override
    public void createWorkflow(
            MetadataServicePb.CreateWorkflowRequest req,
            StreamObserver<MetadataServicePb.CreateWorkflowResponse> response) {
        WorkflowDef workflow = PROTO_MAPPER.fromProto(req.getWorkflow());
        service.registerWorkflowDef(workflow);
        response.onNext(MetadataServicePb.CreateWorkflowResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void updateWorkflows(
            MetadataServicePb.UpdateWorkflowsRequest req,
            StreamObserver<MetadataServicePb.UpdateWorkflowsResponse> response) {
        List<WorkflowDef> workflows =
                req.getDefsList().stream()
                        .map(PROTO_MAPPER::fromProto)
                        .collect(Collectors.toList());

        service.updateWorkflowDef(workflows);
        response.onNext(MetadataServicePb.UpdateWorkflowsResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void getWorkflow(
            MetadataServicePb.GetWorkflowRequest req,
            StreamObserver<MetadataServicePb.GetWorkflowResponse> response) {
        try {
            WorkflowDef workflowDef =
                    service.getWorkflowDef(req.getName(), GRPC_HELPER.optional(req.getVersion()));
            WorkflowDefPb.WorkflowDef workflow = PROTO_MAPPER.toProto(workflowDef);
            response.onNext(
                    MetadataServicePb.GetWorkflowResponse.newBuilder()
                            .setWorkflow(workflow)
                            .build());
            response.onCompleted();
        } catch (ApplicationException e) {
            // TODO replace this with gRPC exception interceptor.
            response.onError(
                    Status.NOT_FOUND
                            .withDescription("No such workflow found by name=" + req.getName())
                            .asRuntimeException());
        }
    }

    @Override
    public void createTasks(
            MetadataServicePb.CreateTasksRequest req,
            StreamObserver<MetadataServicePb.CreateTasksResponse> response) {
        service.registerTaskDef(
                req.getDefsList().stream()
                        .map(PROTO_MAPPER::fromProto)
                        .collect(Collectors.toList()));
        response.onNext(MetadataServicePb.CreateTasksResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void updateTask(
            MetadataServicePb.UpdateTaskRequest req,
            StreamObserver<MetadataServicePb.UpdateTaskResponse> response) {
        TaskDef task = PROTO_MAPPER.fromProto(req.getTask());
        service.updateTaskDef(task);
        response.onNext(MetadataServicePb.UpdateTaskResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void getTask(
            MetadataServicePb.GetTaskRequest req,
            StreamObserver<MetadataServicePb.GetTaskResponse> response) {
        TaskDef def = service.getTaskDef(req.getTaskType());
        if (def != null) {
            TaskDefPb.TaskDef task = PROTO_MAPPER.toProto(def);
            response.onNext(MetadataServicePb.GetTaskResponse.newBuilder().setTask(task).build());
            response.onCompleted();
        } else {
            response.onError(
                    Status.NOT_FOUND
                            .withDescription(
                                    "No such TaskDef found by taskType=" + req.getTaskType())
                            .asRuntimeException());
        }
    }

    @Override
    public void deleteTask(
            MetadataServicePb.DeleteTaskRequest req,
            StreamObserver<MetadataServicePb.DeleteTaskResponse> response) {
        service.unregisterTaskDef(req.getTaskType());
        response.onNext(MetadataServicePb.DeleteTaskResponse.getDefaultInstance());
        response.onCompleted();
    }
}
