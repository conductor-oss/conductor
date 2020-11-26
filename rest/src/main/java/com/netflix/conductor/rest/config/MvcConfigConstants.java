package com.netflix.conductor.rest.config;

public interface MvcConfigConstants {

    String API_PREFIX = "/api/";

    String ADMIN = API_PREFIX + "admin";
    String EVENT = API_PREFIX + "event";
    String METADATA = API_PREFIX + "metadata";
    String TASKS = API_PREFIX + "tasks";
    String WORKFLOW_BULK = API_PREFIX + "workflow/bulk";
    String WORKFLOW = API_PREFIX + "workflow";
}
