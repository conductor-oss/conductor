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
package com.netflix.conductor.common.metadata.agent;

import org.conductoross.conductor.common.metadata.agent.AgentStatusResponse;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStatusResponseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void preservesAgentControllerBooleanWireNames() {
        AgentStatusResponse response =
                AgentStatusResponse.builder()
                        .executionId("exec-1")
                        .status("RUNNING")
                        .complete(false)
                        .running(true)
                        .waiting(false)
                        .startTime(1000L)
                        .endTime(2000L)
                        .build();

        JsonNode json = MAPPER.valueToTree(response);

        assertThat(json.get("isComplete").booleanValue()).isFalse();
        assertThat(json.get("isRunning").booleanValue()).isTrue();
        assertThat(json.get("isWaiting").booleanValue()).isFalse();
        assertThat(json.has("complete")).isFalse();
        assertThat(json.has("running")).isFalse();
        assertThat(json.has("waiting")).isFalse();
        assertThat(json.get("startTime").longValue()).isEqualTo(1000L);
        assertThat(json.get("endTime").longValue()).isEqualTo(2000L);
    }
}
