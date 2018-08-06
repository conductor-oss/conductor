package com.netflix.conductor.grpc.server.service;

import com.netflix.runtime.health.api.HealthCheckAggregator;
import com.netflix.runtime.health.api.HealthCheckStatus;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.CompletableFuture;

import io.grpc.BindableService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HealthServiceImplTest {

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void healthServing() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();
        HealthCheckAggregator hca = mock(HealthCheckAggregator.class);
        CompletableFuture<HealthCheckStatus> hcsf = mock(CompletableFuture.class);
        HealthCheckStatus hcs = mock(HealthCheckStatus.class);
        when(hcs.isHealthy()).thenReturn(true);
        when(hcsf.get()).thenReturn(hcs);
        when(hca.check()).thenReturn(hcsf);
        HealthServiceImpl healthyService = new HealthServiceImpl(hca);

        addService(serverName, healthyService);
        HealthGrpc.HealthBlockingStub blockingStub = HealthGrpc.newBlockingStub(
                // Create a client channel and register for automatic graceful shutdown.
                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));


        HealthCheckResponse reply = blockingStub.check(HealthCheckRequest.newBuilder().build());

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, reply.getStatus());
    }

    @Test
    public void healthNotServing() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();
        HealthCheckAggregator hca = mock(HealthCheckAggregator.class);
        CompletableFuture<HealthCheckStatus> hcsf = mock(CompletableFuture.class);
        HealthCheckStatus hcs = mock(HealthCheckStatus.class);
        when(hcs.isHealthy()).thenReturn(false);
        when(hcsf.get()).thenReturn(hcs);
        when(hca.check()).thenReturn(hcsf);
        HealthServiceImpl healthyService = new HealthServiceImpl(hca);

        addService(serverName, healthyService);
        HealthGrpc.HealthBlockingStub blockingStub = HealthGrpc.newBlockingStub(
                // Create a client channel and register for automatic graceful shutdown.
                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));


        HealthCheckResponse reply = blockingStub.check(HealthCheckRequest.newBuilder().build());

        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, reply.getStatus());
    }

    @Test
    public void healthException() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();
        HealthCheckAggregator hca = mock(HealthCheckAggregator.class);
        CompletableFuture<HealthCheckStatus> hcsf = mock(CompletableFuture.class);
        when(hcsf.get()).thenThrow(InterruptedException.class);
        when(hca.check()).thenReturn(hcsf);
        HealthServiceImpl healthyService = new HealthServiceImpl(hca);

        addService(serverName, healthyService);
        HealthGrpc.HealthBlockingStub blockingStub = HealthGrpc.newBlockingStub(
                // Create a client channel and register for automatic graceful shutdown.
                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

        thrown.expect(StatusRuntimeException.class);
        thrown.expect(hasProperty("status", is(Status.INTERNAL)));
        blockingStub.check(HealthCheckRequest.newBuilder().build());

    }

    private void addService(String name, BindableService service) throws Exception {
        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder
                .forName(name).directExecutor().addService(service).build().start());
    }
}
