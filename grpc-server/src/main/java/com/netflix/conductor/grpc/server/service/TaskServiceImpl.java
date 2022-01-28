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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.grpc.SearchPb;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.TaskServicePb;
import com.netflix.conductor.proto.TaskPb;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.TaskService;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

@Service("grpcTaskService")
public class TaskServiceImpl extends TaskServiceGrpc.TaskServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final ProtoMapper PROTO_MAPPER = ProtoMapper.INSTANCE;
    private static final GRPCHelper GRPC_HELPER = new GRPCHelper(LOGGER);

    private static final int POLL_TIMEOUT_MS = 100;
    private static final int MAX_POLL_TIMEOUT_MS = 5000;

    private final TaskService taskService;
    private final int maxSearchSize;
    private final ExecutionService executionService;

    public TaskServiceImpl(
            ExecutionService executionService,
            TaskService taskService,
            @Value("${workflow.max.search.size:5000}") int maxSearchSize) {
        this.executionService = executionService;
        this.taskService = taskService;
        this.maxSearchSize = maxSearchSize;
    }

    @Override
    public void poll(
            TaskServicePb.PollRequest req, StreamObserver<TaskServicePb.PollResponse> response) {
        try {
            List<Task> tasks =
                    executionService.poll(
                            req.getTaskType(),
                            req.getWorkerId(),
                            GRPC_HELPER.optional(req.getDomain()),
                            1,
                            POLL_TIMEOUT_MS);
            if (!tasks.isEmpty()) {
                TaskPb.Task t = PROTO_MAPPER.toProto(tasks.get(0));
                response.onNext(TaskServicePb.PollResponse.newBuilder().setTask(t).build());
            }
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void batchPoll(
            TaskServicePb.BatchPollRequest req, StreamObserver<TaskPb.Task> response) {
        final int count = GRPC_HELPER.optionalOr(req.getCount(), 1);
        final int timeout = GRPC_HELPER.optionalOr(req.getTimeout(), POLL_TIMEOUT_MS);

        if (timeout > MAX_POLL_TIMEOUT_MS) {
            response.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(
                                    "longpoll timeout cannot be longer than "
                                            + MAX_POLL_TIMEOUT_MS
                                            + "ms")
                            .asRuntimeException());
            return;
        }

        try {
            List<Task> polledTasks =
                    taskService.batchPoll(
                            req.getTaskType(),
                            req.getWorkerId(),
                            GRPC_HELPER.optional(req.getDomain()),
                            count,
                            timeout);
            LOGGER.info("polled tasks: " + polledTasks);
            polledTasks.stream().map(PROTO_MAPPER::toProto).forEach(response::onNext);
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void updateTask(
            TaskServicePb.UpdateTaskRequest req,
            StreamObserver<TaskServicePb.UpdateTaskResponse> response) {
        try {
            TaskResult task = PROTO_MAPPER.fromProto(req.getResult());
            taskService.updateTask(task);

            response.onNext(
                    TaskServicePb.UpdateTaskResponse.newBuilder()
                            .setTaskId(task.getTaskId())
                            .build());
            response.onCompleted();
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void addLog(
            TaskServicePb.AddLogRequest req,
            StreamObserver<TaskServicePb.AddLogResponse> response) {
        taskService.log(req.getTaskId(), req.getLog());
        response.onNext(TaskServicePb.AddLogResponse.getDefaultInstance());
        response.onCompleted();
    }

    @Override
    public void getTaskLogs(
            TaskServicePb.GetTaskLogsRequest req,
            StreamObserver<TaskServicePb.GetTaskLogsResponse> response) {
        List<TaskExecLog> logs = taskService.getTaskLogs(req.getTaskId());
        response.onNext(
                TaskServicePb.GetTaskLogsResponse.newBuilder()
                        .addAllLogs(logs.stream().map(PROTO_MAPPER::toProto)::iterator)
                        .build());
        response.onCompleted();
    }

    @Override
    public void getTask(
            TaskServicePb.GetTaskRequest req,
            StreamObserver<TaskServicePb.GetTaskResponse> response) {
        try {
            Task task = taskService.getTask(req.getTaskId());
            if (task == null) {
                response.onError(
                        Status.NOT_FOUND
                                .withDescription("No such task found by id=" + req.getTaskId())
                                .asRuntimeException());
            } else {
                response.onNext(
                        TaskServicePb.GetTaskResponse.newBuilder()
                                .setTask(PROTO_MAPPER.toProto(task))
                                .build());
                response.onCompleted();
            }
        } catch (Exception e) {
            GRPC_HELPER.onError(response, e);
        }
    }

    @Override
    public void getQueueSizesForTasks(
            TaskServicePb.QueueSizesRequest req,
            StreamObserver<TaskServicePb.QueueSizesResponse> response) {
        Map<String, Integer> sizes = taskService.getTaskQueueSizes(req.getTaskTypesList());
        response.onNext(
                TaskServicePb.QueueSizesResponse.newBuilder().putAllQueueForTask(sizes).build());
        response.onCompleted();
    }

    @Override
    public void getQueueInfo(
            TaskServicePb.QueueInfoRequest req,
            StreamObserver<TaskServicePb.QueueInfoResponse> response) {
        Map<String, Long> queueInfo = taskService.getAllQueueDetails();

        response.onNext(
                TaskServicePb.QueueInfoResponse.newBuilder().putAllQueues(queueInfo).build());
        response.onCompleted();
    }

    @Override
    public void getQueueAllInfo(
            TaskServicePb.QueueAllInfoRequest req,
            StreamObserver<TaskServicePb.QueueAllInfoResponse> response) {
        Map<String, Map<String, Map<String, Long>>> info = taskService.allVerbose();
        TaskServicePb.QueueAllInfoResponse.Builder queuesBuilder =
                TaskServicePb.QueueAllInfoResponse.newBuilder();

        for (Map.Entry<String, Map<String, Map<String, Long>>> queue : info.entrySet()) {
            final String queueName = queue.getKey();
            final Map<String, Map<String, Long>> queueShards = queue.getValue();

            TaskServicePb.QueueAllInfoResponse.QueueInfo.Builder queueInfoBuilder =
                    TaskServicePb.QueueAllInfoResponse.QueueInfo.newBuilder();

            for (Map.Entry<String, Map<String, Long>> shard : queueShards.entrySet()) {
                final String shardName = shard.getKey();
                final Map<String, Long> shardInfo = shard.getValue();

                // FIXME: make shardInfo an actual type
                // shardInfo is an immutable map with predefined keys, so we can always
                // access 'size' and 'uacked'. It would be better if shardInfo
                // were actually a POJO.
                queueInfoBuilder.putShards(
                        shardName,
                        TaskServicePb.QueueAllInfoResponse.ShardInfo.newBuilder()
                                .setSize(shardInfo.get("size"))
                                .setUacked(shardInfo.get("uacked"))
                                .build());
            }

            queuesBuilder.putQueues(queueName, queueInfoBuilder.build());
        }

        response.onNext(queuesBuilder.build());
        response.onCompleted();
    }

    @Override
    public void search(
            SearchPb.Request req, StreamObserver<TaskServicePb.TaskSummarySearchResult> response) {
        final int start = req.getStart();
        final int size = GRPC_HELPER.optionalOr(req.getSize(), maxSearchSize);
        final String sort = req.getSort();
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
        SearchResult<TaskSummary> searchResult =
                taskService.search(start, size, sort, freeText, query);
        response.onNext(
                TaskServicePb.TaskSummarySearchResult.newBuilder()
                        .setTotalHits(searchResult.getTotalHits())
                        .addAllResults(
                                searchResult.getResults().stream().map(PROTO_MAPPER::toProto)
                                        ::iterator)
                        .build());
        response.onCompleted();
    }

    @Override
    public void searchV2(
            SearchPb.Request req, StreamObserver<TaskServicePb.TaskSearchResult> response) {
        final int start = req.getStart();
        final int size = GRPC_HELPER.optionalOr(req.getSize(), maxSearchSize);
        final String sort = req.getSort();
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

        SearchResult<Task> searchResult = taskService.searchV2(start, size, sort, freeText, query);
        response.onNext(
                TaskServicePb.TaskSearchResult.newBuilder()
                        .setTotalHits(searchResult.getTotalHits())
                        .addAllResults(
                                searchResult.getResults().stream().map(PROTO_MAPPER::toProto)
                                        ::iterator)
                        .build());
        response.onCompleted();
    }
}
