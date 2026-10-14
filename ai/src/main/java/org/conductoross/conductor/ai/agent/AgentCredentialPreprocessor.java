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
package org.conductoross.conductor.ai.agent;

import java.util.Map;

/**
 * SPI for resolving agent credentials before a task reaches the agent client.
 *
 * <p>Enterprise/agentspan provides an implementation that resolves {@code
 * rawConfig.integrationName} references against the integration store and merges the result into
 * the credentials map. OSS leaves the bean absent; clients receive credentials exactly as supplied
 * by the workflow.
 *
 * <p>Implementations must be idempotent: if credentials are already fully resolved (no
 * integrationName or already-populated keys), the input map is returned unchanged.
 */
public interface AgentCredentialPreprocessor {

    /**
     * Returns a credentials map suitable for passing to the agent client. May return the same map
     * instance if no resolution is needed.
     */
    Map<String, String> resolve(Map<String, String> credentials, Map<String, Object> rawConfig);
}
