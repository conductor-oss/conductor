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

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.a2a.model.A2AMessage;
import org.conductoross.conductor.ai.a2a.model.A2ATask;
import org.conductoross.conductor.ai.a2a.model.Artifact;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output of {@code AGENT} for both remote A2A and native Conductor agents.
 *
 * <p>Task lifecycle state such as callback delay and completion status belongs to {@code
 * TaskContext}; this class describes only the output data persisted on the task.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class A2ACallResult {

    /** Normalized A2A task state for either a remote or native Conductor agent. */
    private String state;

    /** A2A task id; the native Conductor branch uses its child execution id. */
    private String taskId;

    /** A2A conversation context id; the native branch uses sessionId when supplied. */
    private String contextId;

    /** Artifacts produced by either agent type. */
    private List<Artifact> artifacts;

    /** Latest or final text produced by either agent type. */
    private String text;

    /** Direct reply or current status message returned by either agent type. */
    private A2AMessage agentMessage;

    /** Complete A2A task representation for either agent type. */
    private A2ATask task;

    /** Token used to authenticate a remote A2A push notification callback. */
    private String pushToken;

    /** Epoch time when the remote A2A call started. */
    private Long a2aStartedAt;

    /** Number of consecutive transient failures while polling the remote A2A task. */
    private Integer a2aPollFailures;

    /** Native Conductor agent execution id. */
    private String executionId;

    /** Name of the native Conductor agent. */
    private String agentName;

    /** Whether a native Conductor agent is waiting for a tool or human response. */
    private Boolean waiting;

    /** Tool or human request currently blocking a native Conductor agent. */
    private Map<String, Object> pendingTool;

    /** Structured final output of a native Conductor agent. */
    private Map<String, Object> output;

    /** Epoch time when the native Conductor agent execution started. */
    private Long agentStartTime;

    /** Epoch time when the native Conductor agent execution reached a terminal state. */
    private Long agentEndTime;

    /** Number of consecutive transient failures while polling the Conductor agent. */
    private Integer agentPollFailures;
}
