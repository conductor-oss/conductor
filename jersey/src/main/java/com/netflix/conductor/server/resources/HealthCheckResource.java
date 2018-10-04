package com.netflix.conductor.server.resources;

import com.netflix.runtime.health.api.HealthCheckAggregator;
import com.netflix.runtime.health.api.HealthCheckStatus;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.swagger.annotations.Api;

@Api(value = "/health", produces = MediaType.APPLICATION_JSON, tags = "Health Check")
@Path("/health")
@Produces({MediaType.APPLICATION_JSON})
@Singleton
public class HealthCheckResource {
    private final HealthCheckAggregator healthCheck;

    @Inject
    public HealthCheckResource(HealthCheckAggregator healthCheck) {
        this.healthCheck = healthCheck;
    }

    @GET
    public HealthCheckStatus doCheck() throws Exception {
        return healthCheck.check().get();
    }
}
