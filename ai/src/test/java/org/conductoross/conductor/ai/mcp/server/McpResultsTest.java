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

import static org.assertj.core.api.Assertions.assertThat;

class McpResultsTest {

    @Test
    void terminateTemplate_isRewritten() {
        assertThat(McpResults.clientReason("Workflow is FAILED by TERMINATE task: 3f1c-41d2-9a7e"))
                .isEqualTo("Stopped by a TERMINATE step with status FAILED");
        assertThat(McpResults.clientReason("Workflow is TERMINATED by TERMINATE task: abc"))
                .isEqualTo("Stopped by a TERMINATE step with status TERMINATED");
    }

    @Test
    void restartTemplate_isRewritten() {
        assertThat(McpResults.clientReason("Error when restarting the workflow"))
                .isEqualTo("Error when restarting the call");
    }

    @Test
    void otherReasons_keepTheirText_butNotTheWordWorkflow() {
        assertThat(McpResults.clientReason("card declined")).isEqualTo("card declined");
        assertThat(
                        McpResults.clientReason(
                                "Dynamic join definition is not followed by a join task.  Check"
                                        + " the workflow definition."))
                .isEqualTo(
                        "Dynamic join definition is not followed by a join task.  Check the call"
                                + " definition.");
        assertThat(McpResults.clientReason("Workflows failed; Sub-workflow x and subworkflow y"))
                .isEqualTo("Calls failed; Call x and call y");
    }

    @Test
    void wordsContainingWorkflow_andTheWordAgent_areLeftAlone() {
        assertThat(McpResults.clientReason("my_workflow_v2 failed for the travel agent"))
                .isEqualTo("my_workflow_v2 failed for the travel agent");
    }

    @Test
    void blankReason_isNull() {
        assertThat(McpResults.clientReason(null)).isNull();
        assertThat(McpResults.clientReason("  ")).isNull();
    }
}
