package com.netflix.conductor.grpc.server.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.protobuf.Empty;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.proto.TaskPb;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.TaskServicePb;
import com.netflix.conductor.proto.TaskResultPb;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.QueueDAO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class TaskServiceImpl extends TaskServiceGrpc.TaskServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final ProtoMapper protoMapper = ProtoMapper.INSTANCE;
    private static final GRPCHelper grpcHelper = new GRPCHelper(logger);

    private static final int MAX_TASK_COUNT = 100;
    private static final int POLL_TIMEOUT_MS = 100;
    private static final int MAX_POLL_TIMEOUT_MS = 5000;

    private final ExecutionService taskService;
    private final QueueDAO queues;

    @Inject
    public TaskServiceImpl(ExecutionService taskService, QueueDAO queues, Configuration config) {
        this.taskService = taskService;
        this.queues = queues;
    }

    @Override
    public void poll(TaskServicePb.PollRequest req, StreamObserver<TaskPb.Task> response) {
        try {
            List<Task> tasks = taskService.poll(req.getTaskType(), req.getWorkerId(), req.getDomain(), 1, POLL_TIMEOUT_MS);
            if (!tasks.isEmpty()) {
                TaskPb.Task t = protoMapper.toProto(tasks.get(0));
                response.onNext(t);
            }
            response.onCompleted();
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }
    }

    @Override
    public void batchPoll(TaskServicePb.BatchPollRequest req, StreamObserver<TaskPb.Task> response) {
        final int count = (req.getCount() == 0) ? 1 : req.getCount();
        final int timeout = (req.getTimeout() == 0) ? POLL_TIMEOUT_MS : req.getTimeout();

        if (timeout > MAX_POLL_TIMEOUT_MS) {
            response.onError(Status.INVALID_ARGUMENT
                    .withDescription("longpoll timeout cannot be longer than " + MAX_POLL_TIMEOUT_MS + "ms")
                    .asRuntimeException()
            );
            return;
        }

        try {
            List<Task> polledTasks = taskService.poll(req.getTaskType(), req.getWorkerId(), req.getDomain(), count, timeout);
            polledTasks.stream().map(protoMapper::toProto).forEach(response::onNext);
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }
    }

    @Override
    public void getTasksInProgress(TaskServicePb.TasksInProgressRequest req, StreamObserver<TaskServicePb.TasksInProgressResponse> response) {
        final int count = (req.getCount() != 0) ? req.getCount() : MAX_TASK_COUNT;

        try {
            response.onNext(
                    TaskServicePb.TasksInProgressResponse.newBuilder().addAllTasks(
                        taskService.getTasks(req.getTaskType(), req.getStartKey(), count).stream()
                            .map(protoMapper::toProto)::iterator
                    ).build()
            );
            response.onCompleted();
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }
    }

    @Override
    public void getPendingTaskForWorkflow(TaskServicePb.PendingTaskRequest req, StreamObserver<TaskPb.Task> response) {
        try {
            Task t = taskService.getPendingTaskForWorkflow(req.getTaskRefName(), req.getWorkflowId());
            response.onNext(protoMapper.toProto(t));
            response.onCompleted();
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }
    }

    @Override
    public void updateTask(TaskResultPb.TaskResult req, StreamObserver<TaskServicePb.TaskId> response) {
        try {
            TaskResult task = protoMapper.fromProto(req);
            taskService.updateTask(task);

            response.onNext(
                    TaskServicePb.TaskId.newBuilder()
                            .setTaskId(task.getTaskId()).build()
            );
            response.onCompleted();
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }
    }

    @Override
    public void ackTask(TaskServicePb.AckTaskRequest req, StreamObserver<TaskServicePb.AckTaskResponse> response) {
        try {
            boolean ack = taskService.ackTaskReceived(req.getTaskId());
            response.onNext(TaskServicePb.AckTaskResponse.newBuilder().setAck(ack).build());
            response.onCompleted();
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }
    }

    @Override
    public void addLog(TaskServicePb.AddLogRequest req, StreamObserver<Empty> response) {
        taskService.log(req.getTaskId(), req.getLog());
        response.onCompleted();
    }

    @Override
    public void getTaskLogs(TaskServicePb.TaskId req, StreamObserver<TaskServicePb.GetLogsResponse> response) {
        List<TaskExecLog> logs = taskService.getTaskLogs(req.getTaskId());
        response.onNext(TaskServicePb.GetLogsResponse.newBuilder()
                .addAllLogs(logs.stream().map(protoMapper::toProto)::iterator)
                .build()
        );
        response.onCompleted();
    }

    @Override
    public void getTask(TaskServicePb.TaskId req, StreamObserver<TaskPb.Task> response) {
        try {
            Task task = taskService.getTask(req.getTaskId());
            if (task == null) {
                response.onError(Status.NOT_FOUND
                        .withDescription("No such task found by id="+req.getTaskId())
                        .asRuntimeException()
                );
            } else {
                response.onNext(protoMapper.toProto(task));
                response.onCompleted();
            }
        } catch (Exception e) {
            grpcHelper.onError(response, e);
        }

    }

    @Override
    public void removeTaskFromQueue(TaskServicePb.RemoveTaskRequest req, StreamObserver<Empty> response) {
        taskService.removeTaskfromQueue(req.getTaskType(), req.getTaskId());
        response.onCompleted();
    }

    @Override
    public void getQueueSizesForTasks(TaskServicePb.QueueSizesRequest req, StreamObserver<TaskServicePb.QueueSizesResponse> response) {
        Map<String, Integer> sizes = taskService.getTaskQueueSizes(req.getTaskTypesList());
        response.onNext(
                TaskServicePb.QueueSizesResponse.newBuilder()
                        .putAllQueueForTask(sizes)
                        .build()
        );
        response.onCompleted();
    }

    @Override
    public void getQueueInfo(Empty req, StreamObserver<TaskServicePb.QueueInfoResponse> response) {
        Map<String, Long> queueInfo = queues.queuesDetail().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1, HashMap::new));

        response.onNext(
                TaskServicePb.QueueInfoResponse.newBuilder()
                        .putAllQueues(queueInfo)
                        .build()
        );
        response.onCompleted();
    }

    @Override
    public void getQueueAllInfo(Empty req, StreamObserver<TaskServicePb.QueueAllInfoResponse> response) {
        Map<String, Map<String, Map<String, Long>>> info = queues.queuesDetailVerbose();
        TaskServicePb.QueueAllInfoResponse.Builder queuesBuilder = TaskServicePb.QueueAllInfoResponse.newBuilder();

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
                queueInfoBuilder.putShards(shardName,
                        TaskServicePb.QueueAllInfoResponse.ShardInfo.newBuilder()
                                .setSize(shardInfo.get("size"))
                                .setUacked(shardInfo.get("uacked"))
                                .build()
                );
            }

            queuesBuilder.putQueues(queueName, queueInfoBuilder.build());
        }

        response.onNext(queuesBuilder.build());
        response.onCompleted();
    }
}
