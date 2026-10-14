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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowProvenanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapperProvider().getObjectMapper();

    private static final List<String> PROVENANCE_FIELDS =
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
                    "detached",
                    "restrictedDetails");

    @Test
    void provenanceFieldsSurviveJsonRoundTrip() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("order-run-42");
        workflow.setWorkflowDefinition(workflowDef());
        workflow.setDomainId("ord1");
        workflow.setOriginDomainId("ord1");
        workflow.setRootWorkflowId("order-run-1");
        workflow.setInitiatingPrincipal(
                new ExecutionPrincipal(ExecutionPrincipal.Type.APPLICATION, "checkout-service"));
        workflow.setActingPrincipal(
                new ExecutionPrincipal(ExecutionPrincipal.Type.APPLICATION, "domain:pay1"));
        OriginSettings settings = new OriginSettings();
        settings.setPriority(80);
        settings.setBudgetFullBehavior(OriginSettings.BudgetFullBehavior.QUEUE);
        settings.setMaxQueuedRoots(1000);
        settings.setMaxQueueWaitSeconds(3600);
        settings.setRetentionDays(7);
        workflow.setOriginSettings(settings);
        Admission admission = new Admission();
        admission.setState(Admission.State.QUEUED);
        admission.setAcceptedAt(1791072000000L);
        admission.setReason("WORKFLOW_START_BUDGET_FULL");
        workflow.setAdmission(admission);
        workflow.setHistoryExpiresAt(1791676800000L);
        workflow.setTriggeringWorkflowId("order-run-7");
        workflow.setDetached(true);
        workflow.setRestrictedDetails(true);

        String json = MAPPER.writeValueAsString(workflow);
        Workflow read = MAPPER.readValue(json, Workflow.class);

        assertThat(read.getDomainId()).isEqualTo("ord1");
        assertThat(read.getOriginDomainId()).isEqualTo("ord1");
        assertThat(read.getRootWorkflowId()).isEqualTo("order-run-1");
        assertThat(read.getInitiatingPrincipal().getType())
                .isEqualTo(ExecutionPrincipal.Type.APPLICATION);
        assertThat(read.getInitiatingPrincipal().getId()).isEqualTo("checkout-service");
        assertThat(read.getActingPrincipal().getId()).isEqualTo("domain:pay1");
        assertThat(read.getOriginSettings().getPriority()).isEqualTo(80);
        assertThat(read.getOriginSettings().getBudgetFullBehavior())
                .isEqualTo(OriginSettings.BudgetFullBehavior.QUEUE);
        assertThat(read.getOriginSettings().getMaxQueuedRoots()).isEqualTo(1000);
        assertThat(read.getOriginSettings().getMaxQueueWaitSeconds()).isEqualTo(3600);
        assertThat(read.getOriginSettings().getRetentionDays()).isEqualTo(7);
        assertThat(read.getAdmission().getState()).isEqualTo(Admission.State.QUEUED);
        assertThat(read.getAdmission().getAcceptedAt()).isEqualTo(1791072000000L);
        assertThat(read.getAdmission().getAdmittedAt()).isNull();
        assertThat(read.getAdmission().getReason()).isEqualTo("WORKFLOW_START_BUDGET_FULL");
        assertThat(read.getHistoryExpiresAt()).isEqualTo(1791676800000L);
        assertThat(read.getTriggeringWorkflowId()).isEqualTo("order-run-7");
        assertThat(read.getDetached()).isTrue();
        assertThat(read.getRestrictedDetails()).isTrue();
    }

    @Test
    void unsetProvenanceFieldsAreOmitted() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId("w1");
        workflow.setWorkflowDefinition(workflowDef());

        JsonNode json = includeNullsMapper().valueToTree(workflow);

        assertThat(json.has("workflowId")).isTrue();
        assertThat(json.has("correlationId")).isTrue();
        for (String field : PROVENANCE_FIELDS) {
            assertThat(json.has(field)).as(field).isFalse();
        }
    }

    @Test
    void unsetNestedFieldsAreOmitted() {
        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef());
        workflow.setInitiatingPrincipal(new ExecutionPrincipal());
        workflow.setOriginSettings(new OriginSettings());
        workflow.setAdmission(new Admission());

        JsonNode json = includeNullsMapper().valueToTree(workflow);

        assertThat(json.get("initiatingPrincipal").size()).isZero();
        assertThat(json.get("originSettings").size()).isZero();
        assertThat(json.get("admission").size()).isZero();
    }

    @Test
    void existingPayloadDeserializesUnchanged() throws Exception {
        String legacy =
                "{\"workflowId\":\"w1\",\"status\":\"RUNNING\",\"priority\":5,"
                        + "\"createdBy\":\"user@example.com\",\"input\":{\"a\":1}}";

        Workflow read = MAPPER.readValue(legacy, Workflow.class);

        assertThat(read.getWorkflowId()).isEqualTo("w1");
        assertThat(read.getStatus()).isEqualTo(Workflow.WorkflowStatus.RUNNING);
        assertThat(read.getPriority()).isEqualTo(5);
        assertThat(read.getCreatedBy()).isEqualTo("user@example.com");
        assertThat(read.getDomainId()).isNull();
        assertThat(read.getInitiatingPrincipal()).isNull();
        assertThat(read.getOriginSettings()).isNull();
        assertThat(read.getAdmission()).isNull();
        assertThat(read.getDetached()).isNull();
        assertThat(read.getRestrictedDetails()).isNull();
    }

    @Test
    void copyCarriesProvenance() {
        Workflow workflow = new Workflow();
        workflow.setDomainId("ord1");
        workflow.setOriginDomainId("pay1");
        workflow.setRootWorkflowId("root");
        workflow.setInitiatingPrincipal(
                new ExecutionPrincipal(ExecutionPrincipal.Type.USER, "alice"));
        workflow.setActingPrincipal(
                new ExecutionPrincipal(ExecutionPrincipal.Type.APPLICATION, "app"));
        workflow.setOriginSettings(new OriginSettings());
        workflow.setAdmission(new Admission());
        workflow.setHistoryExpiresAt(1L);
        workflow.setTriggeringWorkflowId("trigger");
        workflow.setDetached(false);
        workflow.setRestrictedDetails(false);

        Workflow copy = workflow.copy();

        assertThat(copy)
                .usingRecursiveComparison()
                .comparingOnlyFields(PROVENANCE_FIELDS.toArray(String[]::new))
                .isEqualTo(workflow);
    }

    // Writes nulls by default, so omission comes from the models' own annotations.
    private static ObjectMapper includeNullsMapper() {
        return MAPPER.copy().setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
    }

    private static WorkflowDef workflowDef() {
        WorkflowDef def = new WorkflowDef();
        def.setName("checkout");
        def.setVersion(1);
        return def;
    }
}
