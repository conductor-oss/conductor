package com.netflix.conductor.client.grpc;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.TaskServicePb;
import com.netflix.conductor.proto.TaskPb;

public class TaskClient extends ClientBase {
    private TaskServiceGrpc.TaskServiceBlockingStub stub;

    public TaskClient(String address, int port) {
        super(address, port);
        this.stub = TaskServiceGrpc.newBlockingStub(this.channel);
    }

    public Task pollTask(String taskType, String workerId, String domain) {
        TaskPb.Task task = stub.poll(
                TaskServicePb.PollRequest.newBuilder()
                .setTaskType(taskType)
                .setWorkerId(workerId)
                .setDomain(domain)
                .build()
        );
        return protoMapper.fromProto(task);
    }
}
