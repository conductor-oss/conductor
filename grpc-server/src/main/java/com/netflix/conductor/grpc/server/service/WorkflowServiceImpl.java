package com.netflix.conductor.grpc.server.service;

import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.grpc.SearchPb;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServicePb;
import com.netflix.conductor.proto.RerunWorkflowRequestPb;
import com.netflix.conductor.proto.StartWorkflowRequestPb;
import com.netflix.conductor.proto.WorkflowPb;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WorkflowServiceImpl extends WorkflowServiceGrpc.WorkflowServiceImplBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final ProtoMapper PROTO_MAPPER = ProtoMapper.INSTANCE;
    private static final GRPCHelper GRPC_HELPER = new GRPCHelper(LOGGER);

    private final WorkflowExecutor executor;
    private final ExecutionService service;
    private final MetadataService metadata;
    private final int maxSearchSize;

    @Inject
    public WorkflowServiceImpl(WorkflowExecutor executor, ExecutionService service, MetadataService metadata, Configuration config) {
        this.executor = executor;
        this.service = service;
        this.metadata = metadata;
        this.maxSearchSize = config.getIntProperty("workflow.max.search.size", 5_000);
    }

    @Override
    public void startWorkflow(StartWorkflowRequestPb.StartWorkflowRequest pbRequest, StreamObserver<WorkflowServicePb.StartWorkflowResponse> response) {
        // TODO: better handling of optional 'version'
        final StartWorkflowRequest request = PROTO_MAPPER.fromProto(pbRequest);

        try {
            // TODO When moving to Java 9: Use ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction)
            String id;
            if (request.getWorkflowDef() == null) {
                id = executor.startWorkflow(
                        request.getName(),
                        GRPC_HELPER.optional(request.getVersion()),
                        request.getCorrelationId(),
                        request.getInput(),
                        request.getExternalInputPayloadStoragePath(),
                        null,
                        request.getTaskToDomain());
            } else {
                id = executor.startWorkflow(
                        request.getWorkflowDef(),
                        request.getInput(),
                        request.getExternalInputPayloadStoragePath(),
                        request.getCorrelationId(),
                        null,
                        request.getTaskToDomain());
            }
            response.onNext(WorkflowServicePb.StartWorkflowResponse.newBuilder()
                    .setWorkflowId(id)
                    .build()
            );
            response.onCompleted();
        } catch (ApplicationException ae) {
            if (ae.getCode().equals(ApplicationException.Code.NOT_FOUND)) {
                response.onError(Status.NOT_FOUND
                        .withDescription("No such workflow found by name="+request.getName())
                        .asRuntimeException()
                );
            } else {
                GRPC_HELPER.onError(response, ae);
            }
        }
    }

    @Override
    public void getWorkflows(WorkflowServicePb.GetWorkflowsRequest req, StreamObserver<WorkflowServicePb.GetWorkflowsResponse> response) {
        final String name = req.getName();
        final boolean includeClosed = req.getIncludeClosed();
        final boolean includeTasks = req.getIncludeTasks();

        WorkflowServicePb.GetWorkflowsResponse.Builder builder = WorkflowServicePb.GetWorkflowsResponse.newBuilder();

        for (String correlationId : req.getCorrelationIdList()) {
            List<Workflow> workflows = service.getWorkflowInstances(name, correlationId, includeClosed, includeTasks);
            builder.putWorkflowsById(correlationId,
                    WorkflowServicePb.GetWorkflowsResponse.Workflows.newBuilder()
                            .addAllWorkflows(workflows.stream().map(PROTO_MAPPER::toProto)::iterator)
                            .build()
            );
        }

        response.onNext(builder.build());
        response.onCompleted();
    }

    @Override
    public void getWorkflowStatus(WorkflowServicePb.GetWorkflowStatusRequest req, StreamObserver<WorkflowPb.Workflow> response) {
        try {
            Workflow workflow = service.getExecutionStatus(req.getWorkflowId(), req.getIncludeTasks());
            response.onNext(PROTO_MAPPER.toProto(workflow));
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void removeWorkflow(WorkflowServicePb.RemoveWorkflowRequest req, StreamObserver<WorkflowServicePb.RemoveWorkflowResponse> response) {
        try {
            service.removeWorkflow(req.getWorkflodId(), req.getArchiveWorkflow());
            response.onNext(WorkflowServicePb.RemoveWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void getRunningWorkflows(WorkflowServicePb.GetRunningWorkflowsRequest req, StreamObserver<WorkflowServicePb.GetRunningWorkflowsResponse> response) {
        try {
            List<String> workflowIds;

            if (req.getStartTime() != 0 && req.getEndTime() != 0) {
                workflowIds = executor.getWorkflows(req.getName(), req.getVersion(), req.getStartTime(), req.getEndTime());
            } else {
                workflowIds = executor.getRunningWorkflowIds(req.getName());
            }

            response.onNext(
                WorkflowServicePb.GetRunningWorkflowsResponse.newBuilder()
                    .addAllWorkflowIds(workflowIds)
                    .build()
            );
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void decideWorkflow(WorkflowServicePb.DecideWorkflowRequest req, StreamObserver<WorkflowServicePb.DecideWorkflowResponse> response) {
        try {
            executor.decide(req.getWorkflowId());
            response.onNext(WorkflowServicePb.DecideWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void pauseWorkflow(WorkflowServicePb.PauseWorkflowRequest req, StreamObserver<WorkflowServicePb.PauseWorkflowResponse> response) {
        try {
            executor.pauseWorkflow(req.getWorkflowId());
            response.onNext(WorkflowServicePb.PauseWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void resumeWorkflow(WorkflowServicePb.ResumeWorkflowRequest req, StreamObserver<WorkflowServicePb.ResumeWorkflowResponse> response) {
        try {
            executor.resumeWorkflow(req.getWorkflowId());
            response.onNext(WorkflowServicePb.ResumeWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void skipTaskFromWorkflow(WorkflowServicePb.SkipTaskRequest req, StreamObserver<WorkflowServicePb.SkipTaskResponse> response) {
        try {
            SkipTaskRequest skipTask = PROTO_MAPPER.fromProto(req.getRequest());
            executor.skipTaskFromWorkflow(req.getWorkflowId(), req.getTaskReferenceName(), skipTask);
            response.onNext(WorkflowServicePb.SkipTaskResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void rerunWorkflow(RerunWorkflowRequestPb.RerunWorkflowRequest req, StreamObserver<WorkflowServicePb.RerunWorkflowResponse> response) {
        try {
            String id = executor.rerun(PROTO_MAPPER.fromProto(req));
            response.onNext(WorkflowServicePb.RerunWorkflowResponse.newBuilder()
                    .setWorkflowId(id)
                    .build()
            );
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void restartWorkflow(WorkflowServicePb.RestartWorkflowRequest req, StreamObserver<WorkflowServicePb.RestartWorkflowResponse> response) {
        try {
            executor.rewind(req.getWorkflowId());
            response.onNext(WorkflowServicePb.RestartWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void retryWorkflow(WorkflowServicePb.RetryWorkflowRequest req, StreamObserver<WorkflowServicePb.RetryWorkflowResponse> response) {
        try {
            executor.retry(req.getWorkflowId());
            response.onNext(WorkflowServicePb.RetryWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void resetWorkflowCallbacks(WorkflowServicePb.ResetWorkflowCallbacksRequest req, StreamObserver<WorkflowServicePb.ResetWorkflowCallbacksResponse> response) {
        try {
            executor.resetCallbacksForInProgressTasks(req.getWorkflowId());
            response.onNext(WorkflowServicePb.ResetWorkflowCallbacksResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void terminateWorkflow(WorkflowServicePb.TerminateWorkflowRequest req, StreamObserver<WorkflowServicePb.TerminateWorkflowResponse> response) {
        try {
            executor.terminateWorkflow(req.getWorkflowId(), req.getReason());
            response.onNext(WorkflowServicePb.TerminateWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    private void doSearch(boolean searchByTask, SearchPb.Request req, StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> response) {
        final int start = req.getStart();
        final int size = GRPC_HELPER.optionalOr(req.getSize(), maxSearchSize);
        final List<String> sort = convertSort(req.getSort());
        final String freeText = GRPC_HELPER.optionalOr(req.getFreeText(), "*");
        final String query = req.getQuery();

        if (size > maxSearchSize) {
            response.onError(
                    Status.INVALID_ARGUMENT
                    .withDescription("Cannot return more than "+maxSearchSize+" results")
                    .asRuntimeException()
            );
            return;
        }

        SearchResult<WorkflowSummary> search;
        if (searchByTask) {
            search = service.searchWorkflowByTasks(query, freeText, start, size, sort);
        } else {
            search = service.search(query, freeText, start, size, sort);
        }

        response.onNext(
            WorkflowServicePb.WorkflowSummarySearchResult.newBuilder()
                .setTotalHits(search.getTotalHits())
                .addAllResults(
                    search.getResults().stream().map(PROTO_MAPPER::toProto)::iterator
                ).build()
        );
        response.onCompleted();
    }

    private List<String> convertSort(String sortStr) {
        List<String> list = new ArrayList<String>();
        if(sortStr != null && sortStr.length() != 0){
            list = Arrays.asList(sortStr.split("\\|"));
        }
        return list;
    }

    @Override
    public void search(SearchPb.Request request, StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> responseObserver) {
        doSearch(false, request, responseObserver);
    }

    @Override
    public void searchByTasks(SearchPb.Request request, StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> responseObserver) {
        doSearch(true, request, responseObserver);
    }
}
