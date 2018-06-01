package com.netflix.conductor.grpc.server;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;

public class GRPCHelper {
    private final Logger logger;

    public GRPCHelper(Logger log) {
        this.logger = log;
    }

    void onError(StreamObserver<?> response, Throwable t) {
        logger.error("error during GRPC request", t);
        response.onError(Status.INTERNAL
                .withDescription(t.getMessage())
                .augmentDescription(ExceptionUtils.getStackTrace(t))
                .withCause(t)
                .asException());
    }
}
