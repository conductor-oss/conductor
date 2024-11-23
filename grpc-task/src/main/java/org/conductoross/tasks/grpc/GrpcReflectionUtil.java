package org.conductoross.tasks.grpc;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import io.grpc.ManagedChannel;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GrpcReflectionUtil {

    private final ServerReflectionGrpc.ServerReflectionStub reflectionStub;
    private final Map<String, Descriptors.ServiceDescriptor> serviceCache = new HashMap<>();

    public GrpcReflectionUtil(ManagedChannel channel) {
        this.reflectionStub = ServerReflectionGrpc.newStub(channel);
    }

    public Descriptors.ServiceDescriptor getServiceDescriptor(String serviceName) {
        // Check cache
        if (serviceCache.containsKey(serviceName)) {
            return serviceCache.get(serviceName);
        }

        CountDownLatch latch = new CountDownLatch(1);
        final Descriptors.ServiceDescriptor[] resultHolder = new Descriptors.ServiceDescriptor[1];
        final Exception[] errorHolder = new Exception[1];

        StreamObserver<ServerReflectionRequest> requestObserver = reflectionStub.serverReflectionInfo(new StreamObserver<>() {
            @Override
            public void onNext(ServerReflectionResponse response) {
                if (response.hasFileDescriptorResponse()) {
                    try {
                        for (var descriptorProtoBytes : response.getFileDescriptorResponse().getFileDescriptorProtoList()) {
                            DescriptorProtos.FileDescriptorProto fileDescriptorProto =
                                    DescriptorProtos.FileDescriptorProto.parseFrom(descriptorProtoBytes);

                            Descriptors.FileDescriptor fileDescriptor =
                                    Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, new Descriptors.FileDescriptor[]{});

                            for (Descriptors.ServiceDescriptor serviceDescriptor : fileDescriptor.getServices()) {
                                if (serviceDescriptor.getFullName().equals(serviceName)) {
                                    serviceCache.put(serviceName, serviceDescriptor);
                                    resultHolder[0] = serviceDescriptor;
                                    latch.countDown();
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        errorHolder[0] = e;
                        latch.countDown();
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                errorHolder[0] = new RuntimeException("Error during reflection: " + t.getMessage(), t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
                .setFileContainingSymbol(serviceName)
                .build();

        requestObserver.onNext(request);
        requestObserver.onCompleted();

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Reflection request timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Reflection request interrupted", e);
        }

        if (errorHolder[0] != null) {
            throw new RuntimeException("Reflection error", errorHolder[0]);
        }

        if (resultHolder[0] == null) {
            throw new RuntimeException("Service descriptor not found for: " + serviceName);
        }

        return resultHolder[0];
    }
}
