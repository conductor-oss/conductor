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
package org.conductoross.conductor.common.spi;

import java.util.Map;

/**
 * SPI for injecting a delegated (per-user OAuth) access token into an AGENT task before it runs.
 *
 * <p>Enterprise provides the implementation; OSS leaves the bean absent so the injector is null and
 * no delegated-access logic runs.
 *
 * <p>The implementation looks up the stored token for {@code workflowCreatedBy} and either injects
 * {@code credentials.bearerToken} directly into {@code inputData}, or returns a failure reason that
 * the caller should set as the task's {@code reasonForIncompletion}.
 */
public interface DelegatedTokenInjector {

    /**
     * Attempt to inject a delegated token for this task execution.
     *
     * @param inputData mutable task input map — implementation may inject {@code
     *     credentials.bearerToken} here
     * @param workflowCreatedBy the userId from {@code workflow.getCreatedBy()}
     * @return {@code null} on success (either injected or not applicable); a non-null reason string
     *     (e.g. {@code "CONSENT_REQUIRED:<integrationName>"}) when the task should fail
     */
    String resolveToken(Map<String, Object> inputData, String workflowCreatedBy);
}
