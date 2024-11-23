package org.conductoross.tasks.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.conductoross.tasks.grpc.GrpcDynamicCaller.jsonToProto;

public class GrpcDynamicCallerTest {

    public static void main(String[] args) throws Exception {

        // Use https://github.com/conductor-oss/grpcbin to bring up the server
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();

        try {
            var classLoader = GrpcDynamicCallerTest.class.getClassLoader();
            String file = classLoader.getResource("./compiled_protos.desc").getFile();
            Path filePath = Paths.get(classLoader.getResource("input.json").toURI());
            String jsonString = Files.readString(filePath);

            DynamicServiceDescriptorBuilder builder = new DynamicServiceDescriptorBuilder();
            builder.loadDescriptorSet(file);

            Descriptors.Descriptor messageType = builder.getMessageType( "complex.ComplexMessage");
            DynamicMessage requestMessage = jsonToProto(jsonString, messageType);

            GrpcDynamicCaller dynamicCaller = new GrpcDynamicCaller(channel);

            DynamicMessage response = dynamicCaller.callUnaryMethod("helloworld.HelloWorldService/ComplexRequest",
                    messageType, messageType, requestMessage);

            String json = JsonFormat.printer().print(response);
            System.out.println("Response(2): " + json);


            Stream<DynamicMessage> responseStream = dynamicCaller.callResponseStreamMethod("helloworld.HelloWorldService/ComplexRequestStream",
                    messageType, messageType, requestMessage);
            List<DynamicMessage> responses = responseStream.toList();
            for (DynamicMessage res : responses) {
                json = JsonFormat.printer().print(res);
                System.out.println("Response(XX): " + json);
            }

        } finally {
            channel.shutdown();
        }
    }




}
