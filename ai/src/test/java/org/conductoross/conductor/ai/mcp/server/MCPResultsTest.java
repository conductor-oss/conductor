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

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.run.Workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MCPResultsTest {

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();
    private final MCPResults results = new MCPResults(mapper, new MCPServerProperties());

    /** The result as the client receives it. */
    private JsonNode callResult(Workflow execution) {
        return mapper.valueToTree(results.callResult(execution));
    }

    private static Workflow ended(Workflow.WorkflowStatus status, String reason) {
        Workflow execution = new Workflow();
        execution.setWorkflowId("exec-1");
        execution.setStatus(status);
        execution.setReasonForIncompletion(reason);
        return execution;
    }

    private static String text(JsonNode result) {
        return result.get("content").get(0).get("text").asString();
    }

    @Test
    void failedExecution_sendsTheReasonAsWritten() {
        String reason = "Task 3f2a failed with status: FAILED and reason: 'card declined'";
        JsonNode result = callResult(ended(Workflow.WorkflowStatus.FAILED, reason));
        assertThat(result.get("isError").asBoolean()).isTrue();
        assertThat(text(result))
                .isEqualTo(
                        "The call ended with status FAILED: " + reason + ". Execution id: exec-1");
    }

    @Test
    void timedOutExecution_sendsTheReasonAsWritten() {
        JsonNode result =
                callResult(
                        ended(Workflow.WorkflowStatus.TIMED_OUT, "Workflow timed out after 60s"));
        assertThat(text(result))
                .isEqualTo(
                        "The call ended with status TIMED_OUT: Workflow timed out after 60s."
                                + " Execution id: exec-1");
    }

    @Test
    void completedExecution_outputIsTheStructuredContent() {
        Workflow execution = ended(Workflow.WorkflowStatus.COMPLETED, null);
        execution.setOutput(java.util.Map.of("orderId", "o-1"));
        JsonNode result = callResult(execution);
        assertThat(result.get("isError").asBoolean()).isFalse();
        assertThat(result.get("structuredContent").get("orderId").asString()).isEqualTo("o-1");
    }

    @Test
    void outputInExternalStorage_isAnErrorResult_notAnEmptyStructuredContent() {
        Workflow execution = ended(Workflow.WorkflowStatus.COMPLETED, null);
        execution.setOutput(java.util.Map.of());
        execution.setExternalOutputPayloadStoragePath("workflow/output/exec-1.json");
        JsonNode result = callResult(execution);
        assertThat(result.get("isError").asBoolean()).isTrue();
        assertThat(result.has("structuredContent")).isFalse();
        assertThat(text(result))
                .contains("external payload storage")
                .endsWith("Execution id: exec-1");
    }

    @Test
    void blankReason_isLeftOut() {
        JsonNode result = callResult(ended(Workflow.WorkflowStatus.FAILED, " "));
        assertThat(text(result))
                .isEqualTo("The call ended with status FAILED. Execution id: exec-1");
    }
}
