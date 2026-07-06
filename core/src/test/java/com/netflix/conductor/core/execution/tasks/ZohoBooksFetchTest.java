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
package com.netflix.conductor.core.execution.tasks;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.integrations.zoho.ZohoBooksConnection;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksFetchRequest;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksFetchResponse;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksIntegrationService;
import org.conductoross.conductor.core.dao.InMemoryZohoBooksConnectionDAO;
import org.junit.Test;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ZohoBooksFetchTest {

    @Test
    public void executeCompletesWithGrnAndPodLists() {
        ZohoBooksFetchResponse response = new ZohoBooksFetchResponse();
        response.setConnectionId("zoho-prod");
        response.setInvoiceNumbers(List.of("INV-1001", "INV-1002"));
        response.setInvoiceIds(List.of("123456789", "987654321"));
        response.setBills(
                List.of(
                        Map.of("bill_id", "123456789", "bill_number", "INV-1001"),
                        Map.of("bill_id", "987654321", "bill_number", "INV-1002")));
        response.setGrnList(List.of(Map.of("document_name", "GRN-1001.pdf")));
        response.setPodList(List.of(Map.of("document_name", "POD-1001.pdf")));

        RecordingZohoBooksIntegrationService service =
                new RecordingZohoBooksIntegrationService(response);
        InMemoryZohoBooksConnectionDAO dao = daoWithConnection("zoho-prod");
        ZohoBooksFetch zohoBooksFetch = new ZohoBooksFetch(service, dao);

        TaskModel task =
                taskWithInput(
                        Map.of(
                                ZohoBooksFetch.INPUT_CONNECTION_ID,
                                "zoho-prod",
                                ZohoBooksFetch.INPUT_INVOICE_NUMBERS,
                                List.of("INV-1001", "INV-1002")));

        boolean progressed =
                zohoBooksFetch.execute(new WorkflowModel(), task, mock(WorkflowExecutor.class));

        assertTrue(progressed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("zoho-prod", service.lastConnection.getConnectionId());
        assertEquals(List.of("INV-1001", "INV-1002"), service.lastRequest.getInvoiceNumbers());
        assertEquals("zoho-prod", task.getOutputData().get(ZohoBooksFetch.OUTPUT_CONNECTION_ID));
        assertEquals(
                List.of("INV-1001", "INV-1002"),
                task.getOutputData().get(ZohoBooksFetch.OUTPUT_INVOICE_NUMBERS));
        assertEquals(
                List.of("123456789", "987654321"),
                task.getOutputData().get(ZohoBooksFetch.OUTPUT_INVOICE_IDS));
        assertSame(response.getBills(), task.getOutputData().get(ZohoBooksFetch.OUTPUT_BILLS));
        assertSame(
                response.getInvoices(), task.getOutputData().get(ZohoBooksFetch.OUTPUT_INVOICES));
        assertSame(response.getGrnList(), task.getOutputData().get(ZohoBooksFetch.OUTPUT_GRN_LIST));
        assertSame(response.getPodList(), task.getOutputData().get(ZohoBooksFetch.OUTPUT_POD_LIST));
    }

    @Test
    public void executeUsesWorkflowConnectionInputWhenTaskConnectionIsMissing() {
        ZohoBooksFetchResponse response = new ZohoBooksFetchResponse();
        response.setConnectionId("zoho-workflow");
        response.setInvoiceNumber("INV-2002");
        response.setInvoiceId("987654321");
        response.setBill(Map.of("bill_id", "987654321", "bill_number", "INV-2002"));

        RecordingZohoBooksIntegrationService service =
                new RecordingZohoBooksIntegrationService(response);
        InMemoryZohoBooksConnectionDAO dao = daoWithConnection("zoho-workflow");
        ZohoBooksFetch zohoBooksFetch = new ZohoBooksFetch(service, dao);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of(ZohoBooksFetch.INPUT_WORKFLOW_CONNECTION_ID, "zoho-workflow"));
        TaskModel task = taskWithInput(Map.of(ZohoBooksFetch.INPUT_INVOICE_NUMBER, "INV-2002"));

        zohoBooksFetch.execute(workflow, task, mock(WorkflowExecutor.class));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("zoho-workflow", service.lastConnection.getConnectionId());
        assertSame(response.getBill(), task.getOutputData().get(ZohoBooksFetch.OUTPUT_BILL));
        assertSame(response.getInvoice(), task.getOutputData().get(ZohoBooksFetch.OUTPUT_INVOICE));
    }

    private InMemoryZohoBooksConnectionDAO daoWithConnection(String connectionId) {
        InMemoryZohoBooksConnectionDAO dao = new InMemoryZohoBooksConnectionDAO();
        dao.saveConnection(
                new ZohoBooksConnection(
                        connectionId, "client-id", "client-secret", "organization-id"));
        return dao;
    }

    private TaskModel taskWithInput(Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setInputData(input);
        return task;
    }

    private static class RecordingZohoBooksIntegrationService extends ZohoBooksIntegrationService {

        private final ZohoBooksFetchResponse response;
        private ZohoBooksConnection lastConnection;
        private ZohoBooksFetchRequest lastRequest;

        private RecordingZohoBooksIntegrationService(ZohoBooksFetchResponse response) {
            this.response = response;
        }

        @Override
        public ZohoBooksFetchResponse fetchInvoiceDocuments(
                ZohoBooksConnection connection, ZohoBooksFetchRequest request) {
            this.lastConnection = connection;
            this.lastRequest = request;
            return response;
        }
    }
}
