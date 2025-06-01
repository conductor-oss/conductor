/*
 * Copyright 2025 Conductor Authors.
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
package io.orkes.conductor.client;

import com.netflix.conductor.client.http.ServiceRegistryClient;
import com.netflix.conductor.common.model.OrkesCircuitBreakerConfig;
import com.netflix.conductor.common.model.ServiceMethod;
import com.netflix.conductor.common.model.ServiceRegistry;
import io.orkes.conductor.client.util.ClientTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceRegistryClientTest {

    private static final String PROTO_FILENAME = "compiled.bin";
    private final ServiceRegistryClient client;
    private final String HTTP_SERVICE_NAME = "http-service";
    private final String GRPC_SERVICE_NAME = "grpc-service";

    public ServiceRegistryClientTest() {
        OrkesClients orkesClients = ClientTestUtil.getOrkesClients();
        this.client = orkesClients.getServiceRegistryClient();
    }

    @BeforeEach
    void setUp() {
        client.removeService(HTTP_SERVICE_NAME);
        client.removeService(GRPC_SERVICE_NAME);
    }

    @Test
    public void testHttpServiceRegistry() throws InterruptedException {
        ServiceRegistry serviceRegistry = new ServiceRegistry();
        serviceRegistry.setName(HTTP_SERVICE_NAME);
        serviceRegistry.setType(ServiceRegistry.Type.HTTP);
        serviceRegistry.setServiceURI("https://petstore.swagger.io/v2/swagger.json");
        client.addOrUpdateService(serviceRegistry);

        client.discover(HTTP_SERVICE_NAME, true);
        Thread.sleep(1000);
        List<ServiceRegistry> services = client.getRegisteredServices();
        ServiceRegistry actualService = services.stream()
                .filter(service -> service.getName().equals(HTTP_SERVICE_NAME))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No http service found with name: " + HTTP_SERVICE_NAME));

        assertEquals(actualService.getName(), HTTP_SERVICE_NAME);
        assertEquals(actualService.getType(), ServiceRegistry.Type.HTTP);
        assertEquals(actualService.getServiceURI(), "https://petstore.swagger.io/v2/swagger.json");
        assertTrue(actualService.getMethods().size() > 0);

        int size = actualService.getMethods().size();

        ServiceMethod method = new ServiceMethod();
        method.setOperationName("TestOperation");
        method.setMethodName("addBySdkTest");
        method.setMethodType("GET");
        method.setInputType("newHttpInputType");
        method.setOutputType("newHttpOutputType");

        client.addOrUpdateServiceMethod(HTTP_SERVICE_NAME, method);
        actualService = client.getService(HTTP_SERVICE_NAME);
        int actualSize = actualService.getMethods().size();
        assertEquals(size + 1, actualSize);

        OrkesCircuitBreakerConfig actualConfig = actualService.getConfig().getCircuitBreakerConfig();
        assertEquals(actualConfig.getFailureRateThreshold(), 50);
        assertEquals(actualConfig.getMinimumNumberOfCalls(), 100);
        assertEquals(actualConfig.getPermittedNumberOfCallsInHalfOpenState(), 100);
        assertEquals(actualConfig.getWaitDurationInOpenState(), 1000);
        assertEquals(actualConfig.getSlidingWindowSize(), 100);
        assertEquals(actualConfig.getSlowCallRateThreshold(), 50);
        assertEquals(actualConfig.getMaxWaitDurationInHalfOpenState(), 1);

        client.removeService(HTTP_SERVICE_NAME);
    }

    @Test
    void testGrpcService() throws IOException {
        ServiceRegistry serviceRegistry = new ServiceRegistry();
        serviceRegistry.setName(GRPC_SERVICE_NAME);
        serviceRegistry.setType(ServiceRegistry.Type.gRPC);
        serviceRegistry.setServiceURI("localhost:50051");

        client.addOrUpdateService(serviceRegistry);

        List<ServiceRegistry> services = client.getRegisteredServices();
        ServiceRegistry actualService = services.stream()
                .filter(service -> service.getName().equals(GRPC_SERVICE_NAME))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No service found with name: " + GRPC_SERVICE_NAME));

        assertEquals(actualService.getName(), GRPC_SERVICE_NAME);
        assertEquals(actualService.getType(), ServiceRegistry.Type.gRPC);
        assertEquals(actualService.getServiceURI(), "localhost:50051");
        assertEquals(actualService.getMethods().size(), 0);
        int size = actualService.getMethods().size();

        ServiceMethod method = new ServiceMethod();
        method.setOperationName("TestOperation");
        method.setMethodName("addBySdkTest");
        method.setMethodType("GET");
        method.setInputType("newHttpInputType");
        method.setOutputType("newHttpOutputType");

        client.addOrUpdateServiceMethod(GRPC_SERVICE_NAME, method);
        actualService = client.getService(GRPC_SERVICE_NAME);
        assertEquals(size + 1, actualService.getMethods().size());
        
        byte[] binaryData;
        try (InputStream inputStream = getClass().getResourceAsStream("/compiled.bin")) {
            binaryData = inputStream.readAllBytes();
        }

        client.setProtoData(GRPC_SERVICE_NAME, PROTO_FILENAME, binaryData);

        actualService = client.getService(GRPC_SERVICE_NAME);

        assertTrue(actualService.getMethods().size() > 0);

        OrkesCircuitBreakerConfig actualConfig = actualService.getConfig().getCircuitBreakerConfig();
        assertEquals(actualConfig.getFailureRateThreshold(), 50);
        assertEquals(actualConfig.getMinimumNumberOfCalls(), 100);
        assertEquals(actualConfig.getPermittedNumberOfCallsInHalfOpenState(), 100);
        assertEquals(actualConfig.getWaitDurationInOpenState(), 1000);
        assertEquals(actualConfig.getSlidingWindowSize(), 100);
        assertEquals(actualConfig.getSlowCallRateThreshold(), 50);
        assertEquals(actualConfig.getMaxWaitDurationInHalfOpenState(), 1);

        client.removeService(GRPC_SERVICE_NAME);
    }
}
