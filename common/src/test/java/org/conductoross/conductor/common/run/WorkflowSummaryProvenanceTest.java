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
package org.conductoross.conductor.common.run;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowSummaryProvenanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    private static final List<String> COPIED_FIELDS =
            List.of(
                    "domainId",
                    "originDomainId",
                    "rootWorkflowId",
                    "initiatingPrincipal",
                    "actingPrincipal",
                    "originSettings",
                    "admission",
                    "historyExpiresAt",
                    "triggeringWorkflowId",
                    "detached");

    @Test
    void summaryCopiesProvenanceFromWorkflow() {
        Workflow workflow = provenancedWorkflow();

        WorkflowSummary summary = new WorkflowSummary(workflow);

        assertThat(summary)
                .usingRecursiveComparison()
                .comparingOnlyFields(COPIED_FIELDS.toArray(String[]::new))
                .isEqualTo(workflow);
    }

    @Test
    void summaryDoesNotCopyRestrictedDetails() {
        Workflow workflow = provenancedWorkflow();
        workflow.setRestrictedDetails(true);

        WorkflowSummary summary = new WorkflowSummary(workflow);

        assertThat(summary.getRestrictedDetails()).isNull();
    }

    @Test
    void provenanceSurvivesJsonRoundTrip() throws Exception {
        WorkflowSummary summary = new WorkflowSummary(provenancedWorkflow());
        summary.setRestrictedDetails(true);

        WorkflowSummary read =
                MAPPER.readValue(MAPPER.writeValueAsString(summary), WorkflowSummary.class);

        assertThat(read)
                .usingRecursiveComparison()
                .comparingOnlyFields(COPIED_FIELDS.toArray(String[]::new))
                .isEqualTo(summary);
        assertThat(read.getRestrictedDetails()).isTrue();
    }

    @Test
    void unsetProvenanceFieldsAreOmitted() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("w1");
        workflow.setWorkflowDefinition(workflowDef());

        // Writes nulls by default, so omission comes from the model's own annotations.
        ObjectMapper includeNulls =
                MAPPER.copy().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
        JsonNode json = includeNulls.valueToTree(new WorkflowSummary(workflow));

        assertThat(json.has("correlationId")).isTrue();
        for (String field : COPIED_FIELDS) {
            assertThat(json.has(field)).as(field).isFalse();
        }
        assertThat(json.has("restrictedDetails")).isFalse();
    }

    @Test
    void existingPayloadDeserializesUnchanged() throws Exception {
        String legacy =
                "{\"workflowType\":\"checkout\",\"version\":1,\"workflowId\":\"w1\","
                        + "\"status\":\"COMPLETED\",\"createdBy\":\"user@example.com\"}";

        WorkflowSummary read = MAPPER.readValue(legacy, WorkflowSummary.class);

        assertThat(read.getWorkflowId()).isEqualTo("w1");
        assertThat(read.getCreatedBy()).isEqualTo("user@example.com");
        assertThat(read.getDomainId()).isNull();
        assertThat(read.getAdmission()).isNull();
    }

    private static Workflow provenancedWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("w1");
        workflow.setWorkflowDefinition(workflowDef());
        workflow.setDomainId("pay1");
        workflow.setOriginDomainId("ord1");
        workflow.setRootWorkflowId("root");
        workflow.setInitiatingPrincipal(
                new ExecutionPrincipal(ExecutionPrincipal.Type.USER, "alice"));
        workflow.setActingPrincipal(
                new ExecutionPrincipal(ExecutionPrincipal.Type.APPLICATION, "domain:pay1"));
        OriginSettings settings = new OriginSettings();
        settings.setPriority(80);
        settings.setBudgetFullBehavior(OriginSettings.BudgetFullBehavior.REJECT);
        workflow.setOriginSettings(settings);
        Admission admission = new Admission();
        admission.setState(Admission.State.ADMITTED);
        admission.setAcceptedAt(1L);
        admission.setAdmittedAt(2L);
        workflow.setAdmission(admission);
        workflow.setHistoryExpiresAt(3L);
        workflow.setTriggeringWorkflowId("trigger");
        workflow.setDetached(true);
        return workflow;
    }

    private static WorkflowDef workflowDef() {
        WorkflowDef def = new WorkflowDef();
        def.setName("checkout");
        def.setVersion(1);
        return def;
    }
}
