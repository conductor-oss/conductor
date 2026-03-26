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
package io.conductor.e2e.util;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;

public class ApiUtil {

    // The conductor-client SDK appends paths like /metadata/workflow to the basePath,
    // so we must point basePath to the API root (/api), not the server root.
    private static final String SERVER_HOST =
            System.getenv().getOrDefault("SERVER_ROOT_URI", "http://localhost:8000");

    public static final String SERVER_ROOT_URI =
            SERVER_HOST.endsWith("/api") ? SERVER_HOST : SERVER_HOST + "/api";

    public static final ConductorClient CLIENT =
            ConductorClient.builder()
                    .basePath(SERVER_ROOT_URI)
                    .readTimeout(
                            30_000) // 30 seconds to support synchronous workflow execution endpoint
                    .build();

    public static final WorkflowClient WORKFLOW_CLIENT = new WorkflowClient(CLIENT);
    public static final TaskClient TASK_CLIENT = new TaskClient(CLIENT);
    public static final MetadataClient METADATA_CLIENT = new MetadataClient(CLIENT);
    public static final EventClient EVENT_CLIENT = new EventClient(CLIENT);
}
