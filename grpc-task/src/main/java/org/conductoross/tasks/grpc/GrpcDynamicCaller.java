package org.conductoross.tasks.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class GrpcDynamicCaller {

    private final ManagedChannel channel;

    public GrpcDynamicCaller(ManagedChannel channel) {
        this.channel = channel;
    }

    public DynamicMessage callUnaryMethod(
            String fullMethodName,
            Descriptors.Descriptor inputDescriptor,
            Descriptors.Descriptor outputDescriptor,
            DynamicMessage requestMessage
    ) {
        // Build the gRPC MethodDescriptor dynamically
        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethodDescriptor =
                MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(fullMethodName)
                        .setRequestMarshaller(new DynamicMessageMarshaller(inputDescriptor))
                        .setResponseMarshaller(new DynamicMessageMarshaller(outputDescriptor))
                        .build();

        // Make the call
        return ClientCalls.blockingUnaryCall(channel, grpcMethodDescriptor, io.grpc.CallOptions.DEFAULT, requestMessage);
    }

    public Stream<DynamicMessage> callResponseStreamMethod(
            String fullMethodName,
            Descriptors.Descriptor inputDescriptor,
            Descriptors.Descriptor outputDescriptor,
            DynamicMessage requestMessage
    ) {
        // Build the gRPC MethodDescriptor dynamically
        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethodDescriptor =
                MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                        .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                        .setFullMethodName(fullMethodName)
                        .setRequestMarshaller(new DynamicMessageMarshaller(inputDescriptor))
                        .setResponseMarshaller(new DynamicMessageMarshaller(outputDescriptor))
                        .build();

        // Make the call
        var stream = ClientCalls.blockingServerStreamingCall(channel, grpcMethodDescriptor, io.grpc.CallOptions.DEFAULT, requestMessage);
        return toStream(stream);
    }

    public DynamicMessage callUnaryMethod(
            Descriptors.ServiceDescriptor serviceDescriptor,
            String methodName,
            DynamicMessage requestMessage
    ) {
        // Find the method descriptor
        Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(methodName);
        if (methodDescriptor == null) {
            throw new IllegalArgumentException("Method " + methodName + " not found in service " + serviceDescriptor.getFullName());
        }

        // Build the gRPC method descriptor
        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethodDescriptor =
                MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(
                                MethodDescriptor.generateFullMethodName(serviceDescriptor.getFullName(), methodName))
                        .setRequestMarshaller(new DynamicMessageMarshaller(methodDescriptor.getInputType()))
                        .setResponseMarshaller(new DynamicMessageMarshaller(methodDescriptor.getOutputType()))
                        .build();

        // Make the call
        return ClientCalls.blockingUnaryCall(channel, grpcMethodDescriptor, io.grpc.CallOptions.DEFAULT, requestMessage);
    }

    public static DynamicMessage jsonToProto(String json, Descriptors.Descriptor descriptor) throws InvalidProtocolBufferException {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
        JsonFormat.parser().merge(json, builder);
        return builder.build();
    }

    private static <T> Stream<T> toStream(Iterator<T> iterator) {
        return StreamSupport.stream(
                ((Iterable<T>) () -> iterator).spliterator(),
                false
        );
    }






}
