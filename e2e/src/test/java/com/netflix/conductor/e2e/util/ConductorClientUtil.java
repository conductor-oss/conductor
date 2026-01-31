/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.e2e.util;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConductorClientUtil {

    public static final String ENV_CONDUCTOR_SERVER_URL = "CONDUCTOR_SERVER_URL";
    private static final String DEFAULT_CONDUCTOR_URL = "http://localhost:8080/api";

    private static ConductorClient conductorClient;
    private static WorkflowClient workflowClient;
    private static TaskClient taskClient;
    private static MetadataClient metadataClient;

    public static synchronized ConductorClient getConductorClient() {
        if (conductorClient == null) {
            String serverUrl = getServerUrl();
            log.info("Creating ConductorClient with URL: {}", serverUrl);
            conductorClient =
                    ConductorClient.builder()
                            .basePath(serverUrl)
                            .connectTimeout(30000)
                            .readTimeout(30000)
                            .writeTimeout(30000)
                            .build();
        }
        return conductorClient;
    }

    public static synchronized WorkflowClient getWorkflowClient() {
        if (workflowClient == null) {
            workflowClient = new WorkflowClient(getConductorClient());
        }
        return workflowClient;
    }

    public static synchronized TaskClient getTaskClient() {
        if (taskClient == null) {
            taskClient = new TaskClient(getConductorClient());
        }
        return taskClient;
    }

    public static synchronized MetadataClient getMetadataClient() {
        if (metadataClient == null) {
            metadataClient = new MetadataClient(getConductorClient());
        }
        return metadataClient;
    }

    public static String getServerUrl() {
        String url = System.getenv(ENV_CONDUCTOR_SERVER_URL);
        return (url == null || url.isBlank()) ? DEFAULT_CONDUCTOR_URL : url;
    }
}
