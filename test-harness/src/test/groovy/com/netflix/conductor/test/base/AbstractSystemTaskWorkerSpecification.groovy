/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.test.base

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.core.execution.tasks.SystemTaskWorker

/**
 * Restores test-harness coverage of the async system-task poll/execute path: SystemTaskWorker polls
 * the task queue and AsyncSystemTaskExecutor reserves and executes.
 *
 * The default harness config disables the worker ({@code conductor.system-task-workers.enabled=false}
 * in application-integrationtest.properties) and every existing spec hand-drives
 * {@code asyncSystemTaskExecutor.execute(...)} with a task id it looked up directly. That is
 * deterministic, but it bought determinism by hand-driving the executor: the poll/reserve/execute
 * path — where issues #1321 and #1322 (duplicate async system-task execution) live — is never
 * exercised. Specs extending this class cover it.
 *
 * Overriding the property here changes the merged @TestPropertySource set, so these specs get their
 * own Spring application context. That alone does NOT isolate them from the other specs: the Redis
 * Testcontainer in AbstractSpecification is static and every context shares the same integtest queue
 * namespace, so both contexts read and write the same physical queues. Isolation comes from the
 * setup()/cleanup() lifecycle below, which arms the worker only for the duration of a feature.
 */
// systemTaskWorkerThreadCount must be comfortably above 1: SystemTaskWorker shares a single
// ExecutionConfig (thread pool + semaphore) across every async system-task queue, so a task that
// blocks in start() holds a permit for its whole invocation. With only 2 permits, the remaining one
// is contended by all ~26 pollers and a queued message can sit undelivered for tens of seconds,
// which silently turns redelivery tests into false passes.
@TestPropertySource(properties = [
        "conductor.system-task-workers.enabled=true",
        "conductor.app.systemTaskWorkerThreadCount=10",
        "conductor.app.systemTaskMaxPollCount=10",
        "conductor.app.systemTaskWorkerPollInterval=50ms",
        "conductor.app.systemTaskQueuePopTimeout=100ms"
])
abstract class AbstractSystemTaskWorkerSpecification extends AbstractSpecification {

    @Autowired
    SystemTaskWorker systemTaskWorker

    /**
     * The Redis Testcontainer in AbstractSpecification is static and both contexts share the
     * integtest queue namespace, and Spring caches contexts without closing them. Left running,
     * this context's worker threads would keep popping messages that belong to workflows created
     * by the hand-driven specs. Arm the worker only for the duration of a feature.
     */
    def setup() {
        systemTaskWorker.start()
    }

    def cleanup() {
        systemTaskWorker.stop()
    }
}
