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
package com.netflix.conductor.client.metrics;

import com.netflix.conductor.client.automator.events.PollCompleted;
import com.netflix.conductor.client.automator.events.PollFailure;
import com.netflix.conductor.client.automator.events.PollStarted;
import com.netflix.conductor.client.automator.events.TaskExecutionCompleted;
import com.netflix.conductor.client.automator.events.TaskExecutionFailure;
import com.netflix.conductor.client.automator.events.TaskExecutionStarted;

public interface MetricsCollector {

    void consume(PollFailure e);

    void consume(PollCompleted e);

    void consume(PollStarted e);

    void consume(TaskExecutionStarted e);

    void consume(TaskExecutionCompleted e);

    void consume(TaskExecutionFailure e);
}
