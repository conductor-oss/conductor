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
package org.conductoross.conductor.ai.model;

import org.conductoross.conductor.ai.a2a.model.A2ATask;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of {@code CANCEL_AGENT} for remote A2A and native Conductor agent executions. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class A2ACancelResult {

    /** Native Conductor agent execution id that was canceled. */
    private String executionId;

    /** Whether the native Conductor agent cancellation request was accepted. */
    private Boolean canceled;

    /** Remote A2A task returned by the cancellation operation. */
    private A2ATask task;
}
