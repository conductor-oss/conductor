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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.exception.ApplicationException.Code;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.grpc.SearchPb;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServicePb;
import com.netflix.conductor.proto.RerunWorkflowRequestPb;
import com.netflix.conductor.proto.StartWorkflowRequestPb;
import com.netflix.conductor.proto.WorkflowPb;
import com.netflix.conductor.service.WorkflowService;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

@Service("grpcWorkflowService")
public class WorkflowServiceImpl extends WorkflowServiceGrpc.WorkflowServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final ProtoMapper PROTO_MAPPER = ProtoMapper.INSTANCE;
    private static final GRPCHelper GRPC_HELPER = new GRPCHelper(LOGGER);

    private final WorkflowService workflowService;
    private final int maxSearchSize;

    public WorkflowServiceImpl(
            WorkflowService workflowService,
            @Value("${workflow.max.search.size:5000}") int maxSearchSize) {
        this.workflowService = workflowService;
        this.maxSearchSize = maxSearchSize;
    }

    @Override
    public void startWorkflow(
            StartWorkflowRequestPb.StartWorkflowRequest pbRequest,
            StreamObserver<WorkflowServicePb.StartWorkflowResponse> response) {

        // TODO: better handling of optional 'version'
        final StartWorkflowRequest request = PROTO_MAPPER.fromProto(pbRequest);
        try {
            String id =
                    workflowService.startWorkflow(
                            pbRequest.getName(),
                            GRPC_HELPER.optional(request.getVersion()),
                            request.getCorrelationId(),
                            request.getPriority(),
                            request.getInput(),
                            request.getExternalInputPayloadStoragePath(),
                            request.getTaskToDomain(),
                            request.getWorkflowDef());

            response.onNext(
                    WorkflowServicePb.StartWorkflowResponse.newBuilder().setWorkflowId(id).build());
            response.onCompleted();
        } catch (ApplicationException ae) {
            if (ae.getCode() == Code.NOT_FOUND) {
                response.onError(
                        Status.NOT_FOUND
                                .withDescription(
                                        "No such workflow found by name=" + request.getName())
                                .asRuntimeException());
            } else {
                GRPC_HELPER.onError(response, ae);
            }
        }
    }

    @Override
    public void getWorkflows(
            WorkflowServicePb.GetWorkflowsRequest req,
            StreamObserver<WorkflowServicePb.GetWorkflowsResponse> response) {
        final String name = req.getName();
        final boolean includeClosed = req.getIncludeClosed();
        final boolean includeTasks = req.getIncludeTasks();

        WorkflowServicePb.GetWorkflowsResponse.Builder builder =
                WorkflowServicePb.GetWorkflowsResponse.newBuilder();

        for (String correlationId : req.getCorrelationIdList()) {
            List<Workflow> workflows =
                    workflowService.getWorkflows(name, correlationId, includeClosed, includeTasks);
            builder.putWorkflowsById(
                    correlationId,
                    WorkflowServicePb.GetWorkflowsResponse.Workflows.newBuilder()
                            .addAllWorkflows(
                                    workflows.stream().map(PROTO_MAPPER::toProto)::iterator)
                            .build());
        }

        response.onNext(builder.build());
        response.onCompleted();
    }

    @Override
    public void getWorkflowStatus(
            WorkflowServicePb.GetWorkflowStatusRequest req,
            StreamObserver<WorkflowPb.Workflow> response) {
        try {
            Workflow workflow =
                    workflowService.getExecutionStatus(req.getWorkflowId(), req.getIncludeTasks());
            response.onNext(PROTO_MAPPER.toProto(workflow));
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void removeWorkflow(
            WorkflowServicePb.RemoveWorkflowRequest req,
            StreamObserver<WorkflowServicePb.RemoveWorkflowResponse> response) {
        try {
            workflowService.deleteWorkflow(req.getWorkflodId(), req.getArchiveWorkflow());
            response.onNext(WorkflowServicePb.RemoveWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void getRunningWorkflows(
            WorkflowServicePb.GetRunningWorkflowsRequest req,
            StreamObserver<WorkflowServicePb.GetRunningWorkflowsResponse> response) {
        try {
            List<String> workflowIds =
                    workflowService.getRunningWorkflows(
                            req.getName(), req.getVersion(), req.getStartTime(), req.getEndTime());

            response.onNext(
                    WorkflowServicePb.GetRunningWorkflowsResponse.newBuilder()
                            .addAllWorkflowIds(workflowIds)
                            .build());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void decideWorkflow(
            WorkflowServicePb.DecideWorkflowRequest req,
            StreamObserver<WorkflowServicePb.DecideWorkflowResponse> response) {
        try {
            workflowService.decideWorkflow(req.getWorkflowId());
            response.onNext(WorkflowServicePb.DecideWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void pauseWorkflow(
            WorkflowServicePb.PauseWorkflowRequest req,
            StreamObserver<WorkflowServicePb.PauseWorkflowResponse> response) {
        try {
            workflowService.pauseWorkflow(req.getWorkflowId());
            response.onNext(WorkflowServicePb.PauseWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void resumeWorkflow(
            WorkflowServicePb.ResumeWorkflowRequest req,
            StreamObserver<WorkflowServicePb.ResumeWorkflowResponse> response) {
        try {
            workflowService.resumeWorkflow(req.getWorkflowId());
            response.onNext(WorkflowServicePb.ResumeWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void skipTaskFromWorkflow(
            WorkflowServicePb.SkipTaskRequest req,
            StreamObserver<WorkflowServicePb.SkipTaskResponse> response) {
        try {
            SkipTaskRequest skipTask = PROTO_MAPPER.fromProto(req.getRequest());

            workflowService.skipTaskFromWorkflow(
                    req.getWorkflowId(), req.getTaskReferenceName(), skipTask);
            response.onNext(WorkflowServicePb.SkipTaskResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void rerunWorkflow(
            RerunWorkflowRequestPb.RerunWorkflowRequest req,
            StreamObserver<WorkflowServicePb.RerunWorkflowResponse> response) {
        try {
            String id =
                    workflowService.rerunWorkflow(
                            req.getReRunFromWorkflowId(), PROTO_MAPPER.fromProto(req));
            response.onNext(
                    WorkflowServicePb.RerunWorkflowResponse.newBuilder().setWorkflowId(id).build());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void restartWorkflow(
            WorkflowServicePb.RestartWorkflowRequest req,
            StreamObserver<WorkflowServicePb.RestartWorkflowResponse> response) {
        try {
            workflowService.restartWorkflow(req.getWorkflowId(), req.getUseLatestDefinitions());
            response.onNext(WorkflowServicePb.RestartWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void retryWorkflow(
            WorkflowServicePb.RetryWorkflowRequest req,
            StreamObserver<WorkflowServicePb.RetryWorkflowResponse> response) {
        try {
            workflowService.retryWorkflow(req.getWorkflowId(), req.getResumeSubworkflowTasks());
            response.onNext(WorkflowServicePb.RetryWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void resetWorkflowCallbacks(
            WorkflowServicePb.ResetWorkflowCallbacksRequest req,
            StreamObserver<WorkflowServicePb.ResetWorkflowCallbacksResponse> response) {
        try {
            workflowService.resetWorkflow(req.getWorkflowId());
            response.onNext(WorkflowServicePb.ResetWorkflowCallbacksResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void terminateWorkflow(
            WorkflowServicePb.TerminateWorkflowRequest req,
            StreamObserver<WorkflowServicePb.TerminateWorkflowResponse> response) {
        try {
            workflowService.terminateWorkflow(req.getWorkflowId(), req.getReason());
            response.onNext(WorkflowServicePb.TerminateWorkflowResponse.getDefaultInstance());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    private void doSearch(
            boolean searchByTask,
            SearchPb.Request req,
            StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> response) {
        final int start = req.getStart();
        final int size = GRPC_HELPER.optionalOr(req.getSize(), maxSearchSize);
        final List<String> sort = convertSort(req.getSort());
        final String freeText = GRPC_HELPER.optionalOr(req.getFreeText(), "*");
        final String query = req.getQuery();

        if (size > maxSearchSize) {
            response.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(
                                    "Cannot return more than " + maxSearchSize + " results")
                            .asRuntimeException());
            return;
        }

        SearchResult<WorkflowSummary> search;
        if (searchByTask) {
            search = workflowService.searchWorkflowsByTasks(start, size, sort, freeText, query);
        } else {
            search = workflowService.searchWorkflows(start, size, sort, freeText, query);
        }

        response.onNext(
                WorkflowServicePb.WorkflowSummarySearchResult.newBuilder()
                        .setTotalHits(search.getTotalHits())
                        .addAllResults(
                                search.getResults().stream().map(PROTO_MAPPER::toProto)::iterator)
                        .build());
        response.onCompleted();
    }

    private void doSearchV2(
            boolean searchByTask,
            SearchPb.Request req,
            StreamObserver<WorkflowServicePb.WorkflowSearchResult> response) {
        final int start = req.getStart();
        final int size = GRPC_HELPER.optionalOr(req.getSize(), maxSearchSize);
        final List<String> sort = convertSort(req.getSort());
        final String freeText = GRPC_HELPER.optionalOr(req.getFreeText(), "*");
        final String query = req.getQuery();

        if (size > maxSearchSize) {
            response.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(
                                    "Cannot return more than " + maxSearchSize + " results")
                            .asRuntimeException());
            return;
        }

        SearchResult<Workflow> search;
        if (searchByTask) {
            search = workflowService.searchWorkflowsByTasksV2(start, size, sort, freeText, query);
        } else {
            search = workflowService.searchWorkflowsV2(start, size, sort, freeText, query);
        }

        response.onNext(
                WorkflowServicePb.WorkflowSearchResult.newBuilder()
                        .setTotalHits(search.getTotalHits())
                        .addAllResults(
                                search.getResults().stream().map(PROTO_MAPPER::toProto)::iterator)
                        .build());
        response.onCompleted();
    }

    private List<String> convertSort(String sortStr) {
        List<String> list = new ArrayList<>();
        if (sortStr != null && sortStr.length() != 0) {
            list = Arrays.asList(sortStr.split("\\|"));
        }
        return list;
    }

    @Override
    public void search(
            SearchPb.Request request,
            StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> responseObserver) {
        doSearch(false, request, responseObserver);
    }

    @Override
    public void searchByTasks(
            SearchPb.Request request,
            StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> responseObserver) {
        doSearch(true, request, responseObserver);
    }

    @Override
    public void searchV2(
            SearchPb.Request request,
            StreamObserver<WorkflowServicePb.WorkflowSearchResult> responseObserver) {
        doSearchV2(false, request, responseObserver);
    }

    @Override
    public void searchByTasksV2(
            SearchPb.Request request,
            StreamObserver<WorkflowServicePb.WorkflowSearchResult> responseObserver) {
        doSearchV2(true, request, responseObserver);
    }
}
