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
package org.conductoross.conductor.ai.agentspan.runtime;

/** Shared OCG wire-contract names used by AgentSpan compilation and runtime services. */
public final class OcgConstants {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    public static final String JSON_MEDIA_TYPE = "application/json";
    public static final String AGENT_RUN_ENDPOINT = "/api/v1/memories/agent-run";
    public static final String FEEDBACK_ENDPOINT = AGENT_RUN_ENDPOINT + "/feedback";
    public static final String AGENT_RUNS_ENDPOINT = "/api/v1/agent-runs";
    public static final String MCP_ENDPOINT = "/mcp/";
    public static final String MEMORY_ENDPOINT_SUFFIX = "/memory";

    public static final String AGENT = "agent";
    public static final String USER = "user";
    public static final String SESSION_ID = "session_id";
    public static final String EXECUTION_ID = "execution_id";
    public static final String INPUT = "input";
    public static final String RESULT = "result";
    public static final String EVENTS = "events";
    public static final String RATING = "rating";
    public static final String REASON = "reason";
    public static final String VISIBILITY = "visibility";
    public static final String OUTCOME = "outcome";
    public static final String STARTED_AT = "started_at";
    public static final String ENDED_AT = "ended_at";
    public static final String SUBMITTED_AT = "submitted_at";
    public static final String DESCRIPTION = "description";
    public static final String DETAIL = "detail";
    public static final String OUTPUT = "output";
    public static final String PROMPT = "prompt";
    public static final String REPOSITORY = "repo";
    public static final String BRANCH = "branch";
    public static final String CURRENT_WORKING_DIRECTORY = "cwd";

    public static final String AGENT_PREFIX = "agent:";
    public static final String USER_PREFIX = "user:";
    public static final String VISIBILITY_PRIVATE = "private";
    public static final String VISIBILITY_PUBLIC = "public";

    public static final String SEARCH_MEMORIES_METHOD = "cg_search_memories";
    public static final String DEFAULT_AGENT = "agentspan";
    public static final String REDACTED_VALUE = "[REDACTED]";
    public static final String SOURCE_EXECUTION_ID = "sourceExecutionId";
    public static final String AGENT_DEFINITION_METADATA = "agentDef";
    public static final String LONG_TERM_MEMORY = "longTermMemory";

    private OcgConstants() {}
}
