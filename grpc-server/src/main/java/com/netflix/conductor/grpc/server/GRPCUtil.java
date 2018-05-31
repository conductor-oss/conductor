package com.netflix.conductor.grpc.server;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.exception.ExceptionUtils;

public interface GRPCUtil {
    static void onError(StreamObserver<?> response, Throwable t) {
        response.onError(Status.INTERNAL
                .withDescription(t.getMessage())
                .augmentDescription(ExceptionUtils.getStackTrace(t))
                .withCause(t)
                .asException());
    }
}
