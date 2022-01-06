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
package com.netflix.conductor.rest.config;

public interface RequestMappingConstants {

    String API_PREFIX = "/api/";

    String ADMIN = API_PREFIX + "admin";
    String EVENT = API_PREFIX + "event";
    String METADATA = API_PREFIX + "metadata";
    String QUEUE = API_PREFIX + "queue";
    String TASKS = API_PREFIX + "tasks";
    String WORKFLOW_BULK = API_PREFIX + "workflow/bulk";
    String WORKFLOW = API_PREFIX + "workflow";
}
