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
package org.conductoross.conductor.ai.mcp.server;

import com.netflix.conductor.common.run.Workflow;

/**
 * Reads and stops executions. Tool calls of both kinds run as workflows (an agent compiles to one),
 * so a task id is a workflow id and needs no state of its own.
 */
public interface McpExecutions {

    /**
     * The execution with this id, without tasks.
     *
     * @throws com.netflix.conductor.core.exception.NotFoundException for an unknown id
     */
    Workflow get(String executionId);

    /** Terminates a running or paused execution. */
    void terminate(String executionId, String reason);
}
