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
package org.conductoross.conductor.ai.testing;

import org.apache.commons.lang3.StringUtils;

/** Explicit identity supplied by the caller; logical stream names must not contain runtime IDs. */
public record LlmCallContext(String workflowId, String taskId, int retryAttempt, String stream) {
    public LlmCallContext {
        if (StringUtils.isAnyBlank(workflowId, taskId, stream) || retryAttempt < 0) {
            throw new IllegalArgumentException(
                    "LLM fixture calls require workflow, task, attempt, and stream identity");
        }
    }
}
