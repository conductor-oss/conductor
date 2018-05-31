package com.netflix.conductor.grpc.server;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.PrintWriter;
import java.io.StringWriter;

public class GRPCUtil {
    private GRPCUtil() {}

    private static String stacktraceToString(Throwable e) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        e.printStackTrace(printWriter);
        return stringWriter.toString();
    }

    public static void onError(StreamObserver<?> response, Throwable t) {
        response.onError(Status.INTERNAL
                .withDescription(t.getMessage())
                .augmentDescription(stacktraceToString(t))
                .withCause(t)
                .asException());
    }
}
