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
package org.conductoross.conductor.ai.agentspan.runtime.credentials;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.model.WorkflowModel;

/**
 * SPI for injecting a delegated (per-user OAuth) access token into an AGENT task before it runs.
 *
 * <p>Enterprise provides the implementation; OSS leaves the bean absent so the injector is null
 * and no delegated-access logic runs. The implementation looks up the stored token for the user
 * who triggered this workflow execution and injects it as {@code credentials.bearerToken}.
 * If no token exists yet, it signals consent is required by failing the task.
 */
public interface DelegatedTokenInjector {

    /**
     * Called once per AGENT task execution, before parameter mapping.
     *
     * <p>If the task requests delegated access ({@code useCallerIdentity=true} and an
     * {@code integrationName} is set in {@code rawConfig}), the implementation must either inject
     * {@code bearerToken} into the task's credentials input, or set the task status to
     * {@link Task.Status#FAILED_WITH_TERMINAL_ERROR} with a reason that includes the consent URL.
     *
     * <p>If delegated access is not requested, this is a no-op.
     */
    void inject(Task task, WorkflowModel workflow);
}
