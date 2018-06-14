package com.netflix.conductor.client.grpc;

import com.netflix.conductor.grpc.ProtoMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public abstract class ClientBase {
    private static Logger logger = LoggerFactory.getLogger(ClientBase.class);
    protected static ProtoMapper protoMapper = ProtoMapper.INSTANCE;

    protected final ManagedChannel channel;

    public ClientBase(String address, int port) {
        this(ManagedChannelBuilder.forAddress(address, port).usePlaintext());
    }

    public ClientBase(ManagedChannelBuilder<?> builder) {
        channel = builder.build();
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

}
