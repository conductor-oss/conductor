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
package org.conductoross.conductor.core.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Configuration
@ConfigurationProperties("conductor.app.sweeper")
@Getter
@Setter
@ToString
public class SweeperProperties {
    private int sweepBatchSize = 2;
    private int queuePopTimeout = 100;

    /**
     * Grace period (ms) during which a system task whose execution has started (persisted
     * startTime) is not repaired even if its queue message is missing. The message is acked before
     * the task executes, so a missing message plus a recent startTime means "in flight", not
     * "orphaned"; repushing it would double-execute the task. Size above the worst-case system
     * task execution time. 0 disables the grace (legacy behavior).
     */
    private long startedTaskRepairGraceMillis = 60_000;
}
