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

import java.util.HashMap;
import java.util.Map;

public class TestWorkerConfig extends WorkerConfiguration {

    private Map<String, Integer> pollingIntervals = new HashMap<>();

    private Map<String, Integer> threadCounts = new HashMap<>();

    @Override
    public int getPollingInterval(String taskName) {
        return pollingIntervals.getOrDefault(taskName, 0);
    }

    public void setPollingInterval(String taskName, int interval) {
        pollingIntervals.put(taskName, interval);
    }

    public void setThreadCount(String taskName, int threadCount) {
        threadCounts.put(taskName, threadCount);
    }

    @Override
    public int getThreadCount(String taskName) {
        return threadCounts.getOrDefault(taskName, 0);
    }
}
