/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.grpc.server.service;

public class HealthServiceImplTest {

    // SBMTODO: Move this Spring boot health check
    //    @Rule
    //    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    //
    //    @Rule
    //    public ExpectedException thrown = ExpectedException.none();
    //
    //    @Test
    //    public void healthServing() throws Exception {
    //        // Generate a unique in-process server name.
    //        String serverName = InProcessServerBuilder.generateName();
    //        HealthCheckAggregator hca = mock(HealthCheckAggregator.class);
    //        CompletableFuture<HealthCheckStatus> hcsf = mock(CompletableFuture.class);
    //        HealthCheckStatus hcs = mock(HealthCheckStatus.class);
    //        when(hcs.isHealthy()).thenReturn(true);
    //        when(hcsf.get()).thenReturn(hcs);
    //        when(hca.check()).thenReturn(hcsf);
    //        HealthServiceImpl healthyService = new HealthServiceImpl(hca);
    //
    //        addService(serverName, healthyService);
    //        HealthGrpc.HealthBlockingStub blockingStub = HealthGrpc.newBlockingStub(
    //                // Create a client channel and register for automatic graceful shutdown.
    //
    // grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
    //
    //
    //        HealthCheckResponse reply =
    // blockingStub.check(HealthCheckRequest.newBuilder().build());
    //
    //        assertEquals(HealthCheckResponse.ServingStatus.SERVING, reply.getStatus());
    //    }
    //
    //    @Test
    //    public void healthNotServing() throws Exception {
    //        // Generate a unique in-process server name.
    //        String serverName = InProcessServerBuilder.generateName();
    //        HealthCheckAggregator hca = mock(HealthCheckAggregator.class);
    //        CompletableFuture<HealthCheckStatus> hcsf = mock(CompletableFuture.class);
    //        HealthCheckStatus hcs = mock(HealthCheckStatus.class);
    //        when(hcs.isHealthy()).thenReturn(false);
    //        when(hcsf.get()).thenReturn(hcs);
    //        when(hca.check()).thenReturn(hcsf);
    //        HealthServiceImpl healthyService = new HealthServiceImpl(hca);
    //
    //        addService(serverName, healthyService);
    //        HealthGrpc.HealthBlockingStub blockingStub = HealthGrpc.newBlockingStub(
    //                // Create a client channel and register for automatic graceful shutdown.
    //
    // grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
    //
    //
    //        HealthCheckResponse reply =
    // blockingStub.check(HealthCheckRequest.newBuilder().build());
    //
    //        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, reply.getStatus());
    //    }
    //
    //    @Test
    //    public void healthException() throws Exception {
    //        // Generate a unique in-process server name.
    //        String serverName = InProcessServerBuilder.generateName();
    //        HealthCheckAggregator hca = mock(HealthCheckAggregator.class);
    //        CompletableFuture<HealthCheckStatus> hcsf = mock(CompletableFuture.class);
    //        when(hcsf.get()).thenThrow(InterruptedException.class);
    //        when(hca.check()).thenReturn(hcsf);
    //        HealthServiceImpl healthyService = new HealthServiceImpl(hca);
    //
    //        addService(serverName, healthyService);
    //        HealthGrpc.HealthBlockingStub blockingStub = HealthGrpc.newBlockingStub(
    //                // Create a client channel and register for automatic graceful shutdown.
    //
    // grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
    //
    //        thrown.expect(StatusRuntimeException.class);
    //        thrown.expect(hasProperty("status", is(Status.INTERNAL)));
    //        blockingStub.check(HealthCheckRequest.newBuilder().build());
    //
    //    }
    //
    //    private void addService(String name, BindableService service) throws Exception {
    //        // Create a server, add service, start, and register for automatic graceful shutdown.
    //        grpcCleanup.register(InProcessServerBuilder
    //                .forName(name).directExecutor().addService(service).build().start());
    //    }
}
