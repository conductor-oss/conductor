/*
 * Copyright 2023 Netflix, Inc.
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
package com.netflix.conductor.sdk.workflow.executor.task;

public class WorkerConfiguration {

    private int defaultPollingInterval = 0;

    public WorkerConfiguration(int defaultPollingInterval) {
        this.defaultPollingInterval = defaultPollingInterval;
    }

    public WorkerConfiguration() {}

    public int getPollingInterval(String taskName) {
        return defaultPollingInterval;
    }

    public int getThreadCount(String taskName) {
        return 0;
    }

    public String getDomain(String taskName) {
        return null;
    }
}
