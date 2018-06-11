package com.netflix.conductor.grpc.server;

import com.google.protobuf.Empty;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.grpc.MetadataServiceGrpc;
import com.netflix.conductor.grpc.MetadataServicePb;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.proto.TaskDefPb;
import com.netflix.conductor.proto.WorkflowDefPb;
import com.netflix.conductor.service.MetadataService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class MetadataServiceImpl extends MetadataServiceGrpc.MetadataServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MetadataServiceImpl.class);
    private static final ProtoMapper protoMapper = ProtoMapper.INSTANCE;
    private static final GRPCHelper grpcHelper = new GRPCHelper(logger);

    private final MetadataService service;

    @Inject
    public MetadataServiceImpl(MetadataService service) {
        this.service = service;
    }

    @Override
    public void createWorkflow(WorkflowDefPb.WorkflowDef req, StreamObserver<Empty> response) {
        try {
            service.registerWorkflowDef(protoMapper.fromProto(req));
            response.onCompleted();
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }
    }

    @Override
    public void updateWorkflows(MetadataServicePb.UpdateWorkflowsRequest req, StreamObserver<Empty> response) {
        List<WorkflowDef> workflows = req.getDefsList().stream()
                .map(protoMapper::fromProto).collect(Collectors.toList());

        try {
            service.updateWorkflowDef(workflows);
            response.onCompleted();
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }
    }

    @Override
    public void getWorkflow(MetadataServicePb.GetWorkflowRequest req, StreamObserver<WorkflowDefPb.WorkflowDef> response) {
        // TODO: req.getVersion optional
        WorkflowDef def = service.getWorkflowDef(req.getName(), req.getVersion());
        if (def != null) {
            response.onNext(protoMapper.toProto(def));
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
        service.getWorkflowDefs().stream().map(protoMapper::toProto).forEach(response::onNext);
        response.onCompleted();
    }

    @Override
    public void createTasks(MetadataServicePb.CreateTasksRequest req, StreamObserver<Empty> response) {
        service.registerTaskDef(
                req.getDefsList().stream().map(protoMapper::fromProto).collect(Collectors.toList())
        );
        response.onCompleted();
    }

    @Override
    public void updateTask(TaskDefPb.TaskDef req, StreamObserver<Empty> response) {
        service.updateTaskDef(protoMapper.fromProto(req));
        response.onCompleted();
    }

    @Override
    public void getAllTasks(Empty _request, StreamObserver<TaskDefPb.TaskDef> response) {
        service.getTaskDefs().stream().map(protoMapper::toProto).forEach(response::onNext);
        response.onCompleted();
    }

    @Override
    public void getTask(MetadataServicePb.GetTaskRequest req, StreamObserver<TaskDefPb.TaskDef> response) {
        TaskDef def = service.getTaskDef(req.getTaskType());
        if (def != null) {
            response.onNext(protoMapper.toProto(def));
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
