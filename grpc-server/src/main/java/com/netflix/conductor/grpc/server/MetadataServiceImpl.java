package com.netflix.conductor.grpc.server;

import com.google.protobuf.Empty;
import com.netflix.conductor.common.annotations.ProtoMessage;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.MetadataServicePb;
import com.netflix.conductor.proto.TaskDefPb;
import com.netflix.conductor.proto.WorkflowDefPb;
import com.netflix.conductor.service.MetadataService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class MetadataServiceImpl extends MetadataServiceGrpc.MetadataServiceImplBase {
    private MetadataService service;

    @Inject
    public MetadataServiceImpl(MetadataService service) {
        this.service = service;
    }

    @Override
    public void createWorkflow(WorkflowDefPb.WorkflowDef req, StreamObserver<Empty> response) {
        try {
            service.registerWorkflowDef(ProtoMapper.fromProto(req));
            response.onCompleted();
        } catch (Exception e) {
            GRPCUtil.onError(response, e);
        }
    }

    @Override
    public void updateWorkflows(MetadataServicePb.UpdateWorkflowsRequest req, StreamObserver<Empty> response) {
        List<WorkflowDef> workflows = new ArrayList<>();
        for (WorkflowDefPb.WorkflowDef def : req.getDefsList()) {
            workflows.add(ProtoMapper.fromProto(def));
        }

        try {
            service.updateWorkflowDef(workflows);
            response.onCompleted();
        } catch (Exception e) {
            GRPCUtil.onError(response, e);
        }
    }

    @Override
    public void getWorkflow(MetadataServicePb.GetWorkflowRequest req, StreamObserver<WorkflowDefPb.WorkflowDef> response) {
        // TODO: req.getVersion optional
        WorkflowDef def = service.getWorkflowDef(req.getName(), req.getVersion());
        if (def != null) {
            response.onNext(ProtoMapper.toProto(def));
            response.onCompleted();
        } else {
            response.onError(Status.NOT_FOUND
                    .withDescription("No such workflow found by name="+req.getName())
                    .asRuntimeException()
            );
        }
    }

    @Override
    public void getAllWorkflows(Empty _request, StreamObserver<WorkflowDefPb.WorkflowDef> response) {
        for (WorkflowDef def : service.getWorkflowDefs()) {
            response.onNext(ProtoMapper.toProto(def));
        }
        response.onCompleted();
    }

    @Override
    public void createTasks(MetadataServicePb.CreateTasksRequest req, StreamObserver<Empty> response) {
        List<TaskDef> allTasks = new ArrayList<>();
        for (TaskDefPb.TaskDef task : req.getDefsList()) {
            allTasks.add(ProtoMapper.fromProto(task));
        }
        service.registerTaskDef(allTasks);
        response.onCompleted();
    }

    @Override
    public void updateTask(TaskDefPb.TaskDef req, StreamObserver<Empty> response) {
        service.updateTaskDef(ProtoMapper.fromProto(req));
        response.onCompleted();
    }

    @Override
    public void getAllTasks(Empty _request, StreamObserver<TaskDefPb.TaskDef> response) {
        for (TaskDef def : service.getTaskDefs()) {
            response.onNext(ProtoMapper.toProto(def));
        }
        response.onCompleted();
    }

    @Override
    public void getTask(MetadataServicePb.GetTaskRequest req, StreamObserver<TaskDefPb.TaskDef> response) {
        TaskDef def = service.getTaskDef(req.getTaskType());
        if (def != null) {
            response.onNext(ProtoMapper.toProto(def));
            response.onCompleted();
        } else {
            response.onError(Status.NOT_FOUND
                    .withDescription("No such TaskDef found by taskType="+req.getTaskType())
                    .asRuntimeException()
            );
        }
    }

    @Override
    public void deleteTask(MetadataServicePb.GetTaskRequest req, StreamObserver<Empty> response) {
        service.unregisterTaskDef(req.getTaskType());
        response.onCompleted();
    }
}
