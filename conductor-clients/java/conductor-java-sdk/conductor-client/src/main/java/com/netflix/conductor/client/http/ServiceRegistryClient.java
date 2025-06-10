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
package com.netflix.conductor.client.http;

import java.util.List;

import org.apache.commons.lang3.Validate;

import com.netflix.conductor.client.config.ConductorClientConfiguration;
import com.netflix.conductor.client.config.DefaultConductorClientConfiguration;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.common.model.CircuitBreakerTransitionResponse;
import com.netflix.conductor.common.model.ProtoRegistryEntry;
import com.netflix.conductor.common.model.ServiceMethod;
import com.netflix.conductor.common.model.ServiceRegistry;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Client for the Service Registry API
 */
@Slf4j
public final class ServiceRegistryClient {

    private final ConductorClientConfiguration conductorClientConfiguration;
    private ConductorClient client;

    /**
     * Creates a default service registry client
     */
    public ServiceRegistryClient() {
        // client will be set once root uri is set
        this(null, new DefaultConductorClientConfiguration());
    }

    public ServiceRegistryClient(ConductorClient client) {
        this(client, new DefaultConductorClientConfiguration());
    }

    public ServiceRegistryClient(ConductorClient client, ConductorClientConfiguration config) {
        this.client = client;
        this.conductorClientConfiguration = config;
    }

    /**
     * Kept only for backwards compatibility
     *
     * @param rootUri basePath for the ApiClient
     */
    @Deprecated
    public void setRootURI(String rootUri) {
        if (client != null) {
            client.shutdown();
        }
        client = new ConductorClient(rootUri);
    }

    /**
     * Get all registered services
     *
     * @return List of registered services
     */
    public List<ServiceRegistry> getRegisteredServices() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/registry/service")
                .build();

        ConductorClientResponse<List<ServiceRegistry>> resp = client.execute(request,
                new TypeReference<List<ServiceRegistry>>() {
                });
        return resp.getData();
    }

    /**
     * Get a specific service by name
     *
     * @param name The name of the service to retrieve
     * @return ServiceRegistry object for the specified service
     */
    public ServiceRegistry getService(String name) {
        Validate.notBlank(name, "Service name cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/registry/service/{name}")
                .addPathParam("name", name)
                .build();

        ConductorClientResponse<ServiceRegistry> resp = client.execute(request,
                new TypeReference<ServiceRegistry>() {
                });
        return resp.getData();
    }

    /**
     * Add or update a service registry entry
     *
     * @param serviceRegistry The service registry to add or update
     */
    public void addOrUpdateService(ServiceRegistry serviceRegistry) {
        Validate.notNull(serviceRegistry, "Service registry cannot be null");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/registry/service")
                .body(serviceRegistry)
                .build();

        client.execute(request, new TypeReference<Void>() {
        });
    }

    /**
     * Remove a service from the registry
     *
     * @param name The name of the service to remove
     */
    public void removeService(String name) {
        Validate.notBlank(name, "Service name cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/registry/service/{name}")
                .addPathParam("name", name)
                .build();

        client.execute(request, new TypeReference<Void>() {
        });
    }

    /**
     * Open the circuit breaker for a service
     *
     * @param name The name of the service
     * @return Circuit breaker transition response
     */
    public CircuitBreakerTransitionResponse openCircuitBreaker(String name) {
        Validate.notBlank(name, "Service name cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/registry/service/{name}/circuit-breaker/open")
                .addPathParam("name", name)
                .build();

        ConductorClientResponse<CircuitBreakerTransitionResponse> resp = client.execute(request,
                new TypeReference<CircuitBreakerTransitionResponse>() {
                });
        return resp.getData();
    }

    /**
     * Close the circuit breaker for a service
     *
     * @param name The name of the service
     * @return Circuit breaker transition response
     */
    public CircuitBreakerTransitionResponse closeCircuitBreaker(String name) {
        Validate.notBlank(name, "Service name cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/registry/service/{name}/circuit-breaker/close")
                .addPathParam("name", name)
                .build();

        ConductorClientResponse<CircuitBreakerTransitionResponse> resp = client.execute(request,
                new TypeReference<CircuitBreakerTransitionResponse>() {
                });
        return resp.getData();
    }

    /**
     * Get the circuit breaker status for a service
     *
     * @param name The name of the service
     * @return Circuit breaker transition response with status
     */
    public CircuitBreakerTransitionResponse getCircuitBreakerStatus(String name) {
        Validate.notBlank(name, "Service name cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/registry/service/{name}/circuit-breaker/status")
                .addPathParam("name", name)
                .build();

        ConductorClientResponse<CircuitBreakerTransitionResponse> resp = client.execute(request,
                new TypeReference<CircuitBreakerTransitionResponse>() {
                });
        return resp.getData();
    }

    /**
     * Add or update a service method
     *
     * @param registryName The name of the registry
     * @param method       The service method to add or update
     */
    public void addOrUpdateServiceMethod(String registryName, ServiceMethod method) {
        Validate.notBlank(registryName, "Registry name cannot be blank");
        Validate.notNull(method, "Service method cannot be null");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/registry/service/{registryName}/methods")
                .addPathParam("registryName", registryName)
                .body(method)
                .build();

        client.execute(request, new TypeReference<Void>() {
        });
    }

    /**
     * Remove a method from a service
     *
     * @param registryName The name of the registry
     * @param serviceName  The name of the service
     * @param method       The method name
     * @param methodType   The method type
     */
    public void removeMethod(String registryName, String serviceName, String method, String methodType) {
        Validate.notBlank(registryName, "Registry name cannot be blank");
        Validate.notBlank(serviceName, "Service name cannot be blank");
        Validate.notBlank(method, "Method name cannot be blank");
        Validate.notBlank(methodType, "Method type cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/registry/service/{registryName}/methods")
                .addPathParam("registryName", registryName)
                .addQueryParam("serviceName", serviceName)
                .addQueryParam("method", method)
                .addQueryParam("methodType", methodType)
                .build();

        client.execute(request, new TypeReference<Void>() {
        });
    }

    /**
     * Get proto data for a service
     *
     * @param registryName The name of the registry
     * @param filename     The proto filename
     * @return The proto data as byte array
     */
    public byte[] getProtoData(String registryName, String filename) {
        Validate.notBlank(registryName, "Registry name cannot be blank");
        Validate.notBlank(filename, "Filename cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/registry/service/protos/{registryName}/{filename}")
                .addPathParam("registryName", registryName)
                .addPathParam("filename", filename)
                .build();

        ConductorClientResponse<byte[]> resp = client.execute(request,
                new TypeReference<byte[]>() {
                });
        return resp.getData();
    }

    /**
     * Set proto data for a service
     *
     * @param registryName The name of the registry
     * @param filename     The proto filename
     * @param data         The proto data as byte array
     */
    public void setProtoData(String registryName, String filename, byte[] data) {
        Validate.notBlank(registryName, "Registry name cannot be blank");
        Validate.notBlank(filename, "Filename cannot be blank");
        Validate.notNull(data, "Proto data cannot be null");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/registry/service/protos/{registryName}/{filename}")
                .addPathParam("registryName", registryName)
                .addPathParam("filename", filename)
                .addHeaderParam("Content-Type", "application/octet-stream")
                .body(data)
                .build();

        client.execute(request, new TypeReference<Void>() {
        });
    }

    /**
     * Delete a proto file
     *
     * @param registryName The name of the registry
     * @param filename     The proto filename to delete
     */
    public void deleteProto(String registryName, String filename) {
        Validate.notBlank(registryName, "Registry name cannot be blank");
        Validate.notBlank(filename, "Filename cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/registry/service/protos/{registryName}/{filename}")
                .addPathParam("registryName", registryName)
                .addPathParam("filename", filename)
                .build();

        client.execute(request, new TypeReference<Void>() {
        });
    }

    /**
     * Get all protos for a registry
     *
     * @param registryName The name of the registry
     * @return List of proto registry entries
     */
    public List<ProtoRegistryEntry> getAllProtos(String registryName) {
        Validate.notBlank(registryName, "Registry name cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/registry/service/protos/{registryName}")
                .addPathParam("registryName", registryName)
                .build();

        ConductorClientResponse<List<ProtoRegistryEntry>> resp = client.execute(request,
                new TypeReference<List<ProtoRegistryEntry>>() {
                });
        return resp.getData();
    }

    /**
     * Discover methods for a service
     *
     * @param name   The name of the service
     * @param create Whether to create discovered methods
     * @return List of discovered service methods
     */
    public List<ServiceMethod> discover(String name, boolean create) {
        Validate.notBlank(name, "Service name cannot be blank");

        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/registry/service/{name}/discover")
                .addPathParam("name", name)
                .addQueryParam("create", String.valueOf(create))
                .build();

        ConductorClientResponse<List<ServiceMethod>> resp = client.execute(request,
                new TypeReference<List<ServiceMethod>>() {
                });
        return resp.getData();
    }
}
