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
package org.conductoross.conductor.ai.models;

import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Input for the {@code CALL_AGENT} task: send a message to a remote A2A agent and await the result.
 *
 * <p>The message body is taken from the first of {@link #message}, {@link #parts}, {@link #text},
 * or the inherited {@code prompt}. To continue an existing conversation/task (e.g. resume after the
 * agent asked for input), pass {@link #contextId} and {@link #taskId} from a prior call's output.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class A2ACallRequest extends LLMWorkerInput {

    /** The remote agent's JSON-RPC endpoint URL (the {@code url} from its Agent Card). */
    private String agentUrl;

    /** Convenience: a single text part to send. */
    private String text;

    /** Explicit message parts (advanced) — each a raw A2A {@code Part} object. */
    private List<Map<String, Object>> parts;

    /** Full A2A message override (advanced); takes precedence over {@link #parts}/{@link #text}. */
    private Map<String, Object> message;

    /** Context id to continue a related conversation/session. */
    private String contextId;

    /** Task id to continue/resume an existing remote task (e.g. provide requested input). */
    private String taskId;

    /** HTTP headers for the request (e.g. {@code Authorization: Bearer ...}). */
    private Map<String, String> headers;

    /** How much message history to request back in the returned task. */
    private Integer historyLength;

    /** Poll interval (seconds) while the remote task is running. Defaults to 5. */
    private Integer pollIntervalSeconds;

    /**
     * Absolute deadline (seconds) for the remote task to reach a terminal state. Past this, the
     * Conductor task fails terminally rather than waiting forever. Defaults to 86400 (24h).
     */
    private Integer maxDurationSeconds;

    /**
     * Max consecutive transient poll failures (agent unreachable) before failing terminally. Guards
     * against polling a dead agent forever. Defaults to 30.
     */
    private Integer maxPollFailures;

    /** Use SSE streaming ({@code message/stream}) instead of send+poll. */
    private boolean streaming;

    /**
     * Use push-notification (webhook) mode for long-running tasks. The agent's webhook completes
     * the task quickly; a slow backstop poll (see {@link #pushBackstopPollSeconds}) still
     * guarantees the task completes even if the webhook is never delivered.
     */
    private boolean pushNotification;

    /** Backstop poll interval (seconds) in push mode. Defaults to 300 (5 min). */
    private Integer pushBackstopPollSeconds;

    /** Extra metadata to pass on the {@code message/send} call. */
    private Map<String, Object> metadata;
}
