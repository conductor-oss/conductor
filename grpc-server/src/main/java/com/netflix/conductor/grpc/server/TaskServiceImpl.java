package com.netflix.conductor.grpc.server;

import java.util.List;

import com.google.protobuf.Empty;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.proto.TaskPb;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.TaskServicePb;
import com.netflix.conductor.proto.TaskResultPb;
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

    private static final int MAX_TASK_COUNT = 100;
    private static final int POLL_TIMEOUT_MS = 100;

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
            GRPCUtil.onError(response, e);
        }
    }

    @Override
    public StreamObserver<TaskServicePb.StreamingPollRequest> pollStream(StreamObserver<TaskPb.Task> observer) {
        final ServerCallStreamObserver<TaskPb.Task> responseObserver =
                (ServerCallStreamObserver<TaskPb.Task>) observer;

        return new StreamObserver<TaskServicePb.StreamingPollRequest>() {
            @Override
            public void onNext(TaskServicePb.StreamingPollRequest req) {
                try {
                    for (TaskResultPb.TaskResult result : req.getCompletedList()) {
                        TaskResult task = protoMapper.fromProto(result);
                        taskService.updateTask(task);
                    }

                    List<Task> newTasks = taskService.poll(
                            req.getTaskType(), req.getWorkerId(), req.getDomain(),
                            req.getCapacity(), POLL_TIMEOUT_MS);

                    for (Task task : newTasks) {
                        responseObserver.onNext(protoMapper.toProto(task));
                    }
                } catch (Exception e) {
                    GRPCUtil.onError(observer, e);
                }
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void getTasksInProgress(TaskServicePb.TasksInProgressRequest req, StreamObserver<TaskServicePb.TasksInProgressResponse> response) {
        final int count = (req.getCount() != 0) ? req.getCount() : MAX_TASK_COUNT;

        try {
            List<Task> tasks = taskService.getTasks(req.getTaskType(), req.getStartKey(), count);
            TaskServicePb.TasksInProgressResponse.Builder builder =
                    TaskServicePb.TasksInProgressResponse.newBuilder();

            for (Task t : tasks) {
                builder.addTasks(protoMapper.toProto(t));
            }

            response.onNext(builder.build());
            response.onCompleted();
        } catch (Exception e) {
            GRPCUtil.onError(response, e);
        }
    }

    @Override
    public void getPendingTaskForWorkflow(TaskServicePb.PendingTaskRequest req, StreamObserver<TaskPb.Task> response) {
        try {
            Task t = taskService.getPendingTaskForWorkflow(req.getTaskRefName(), req.getWorkflowId());
            response.onNext(protoMapper.toProto(t));
            response.onCompleted();
        } catch (Exception e) {
            GRPCUtil.onError(response, e);
        }
    }

    @Override
    public void updateTask(TaskResultPb.TaskResult req, StreamObserver<TaskServicePb.TaskUpdateResponse> response) {
        try {
            TaskResult task = protoMapper.fromProto(req);
            taskService.updateTask(task);

            TaskServicePb.TaskUpdateResponse resp = TaskServicePb.TaskUpdateResponse
                    .newBuilder()
                    .setTaskId(task.getTaskId())
                    .build();
            response.onNext(resp);
            response.onCompleted();
        } catch (Exception e) {
            GRPCUtil.onError(response, e);
        }
    }

    @Override
    public void ackTask(TaskServicePb.AckTaskRequest req, StreamObserver<TaskServicePb.AckTaskResponse> response) {
        try {
            boolean ack = taskService.ackTaskReceived(req.getTaskId());
            TaskServicePb.AckTaskResponse resp = TaskServicePb.AckTaskResponse
                    .newBuilder().setAck(ack).build();
            response.onNext(resp);
            response.onCompleted();
        } catch (Exception e) {
            GRPCUtil.onError(response, e);
        }
    }

    @Override
    public void addLog(TaskServicePb.AddLogRequest req, StreamObserver<Empty> response) {
        taskService.log(req.getTaskId(), req.getLog());
        response.onCompleted();
    }

    @Override
    public void getLogs(TaskServicePb.TaskId req, StreamObserver<TaskServicePb.GetLogsResponse> response) {
        List<TaskExecLog> logs = taskService.getTaskLogs(req.getTaskId());
        TaskServicePb.GetLogsResponse.Builder builder = TaskServicePb.GetLogsResponse.newBuilder();

        for (TaskExecLog l : logs) {
            builder.addLogs(protoMapper.toProto(l));
        }

        response.onNext(builder.build());
        response.onCompleted();
    }
}
