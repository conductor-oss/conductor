package com.netflix.conductor.grpc.server.service;

import com.netflix.runtime.health.api.HealthCheckAggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;

public class HealthServiceImpl extends HealthGrpc.HealthImplBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthServiceImpl.class);
    private static final GRPCHelper GRPC_HELPER = new GRPCHelper(LOGGER);

    private final HealthCheckAggregator healthCheck;

    @Inject
    public HealthServiceImpl(HealthCheckAggregator healthCheck) {
        this.healthCheck = healthCheck;
    }

    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        try {
            if (healthCheck.check().get().isHealthy()) {
                responseObserver.onNext(
                        HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.SERVING).build()
                );
            } else {
                responseObserver.onNext(
                        HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.NOT_SERVING).build()
                );
            }
        } catch (Exception ex) {
            GRPC_HELPER.onError(responseObserver, ex);
        } finally {
            responseObserver.onCompleted();
        }
    }
}
