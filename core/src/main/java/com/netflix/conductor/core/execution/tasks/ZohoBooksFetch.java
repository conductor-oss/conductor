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

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.conductoross.conductor.common.integrations.zoho.ZohoBooksConnection;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksFetchRequest;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksFetchResponse;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksIntegrationException;
import org.conductoross.conductor.common.integrations.zoho.ZohoBooksIntegrationService;
import org.conductoross.conductor.dao.ZohoBooksConnectionDAO;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_ZOHO_BOOKS_FETCH;

/**
 * Fetches bill-related GRN and POD records from Zoho Books for downstream reconciliation workflows.
 */
@Component(TASK_TYPE_ZOHO_BOOKS_FETCH)
public class ZohoBooksFetch extends WorkflowSystemTask {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Object>> OBJECT_LIST_TYPE = new TypeReference<>() {};

    public static final String INPUT_CONNECTION_ID = "connectionId";
    public static final String INPUT_WORKFLOW_CONNECTION_ID = "zohoBooksConnectionId";
    public static final String INPUT_BILL_NUMBER = "billNumber";
    public static final String INPUT_BILL_NUMBERS = "billNumbers";
    public static final String INPUT_INVOICE_NUMBER = "invoiceNumber";
    public static final String INPUT_INVOICE_NUMBERS = "invoiceNumbers";
    public static final String OUTPUT_CONNECTION_ID = "connectionId";
    public static final String OUTPUT_BILL_NUMBER = "billNumber";
    public static final String OUTPUT_BILL_ID = "billId";
    public static final String OUTPUT_BILL_NUMBERS = "billNumbers";
    public static final String OUTPUT_BILL_IDS = "billIds";
    public static final String OUTPUT_BILL = "bill";
    public static final String OUTPUT_BILLS = "bills";
    public static final String OUTPUT_INVOICE_NUMBER = "invoiceNumber";
    public static final String OUTPUT_INVOICE_ID = "invoiceId";
    public static final String OUTPUT_INVOICE_NUMBERS = "invoiceNumbers";
    public static final String OUTPUT_INVOICE_IDS = "invoiceIds";
    public static final String OUTPUT_INVOICE = "invoice";
    public static final String OUTPUT_INVOICES = "invoices";
    public static final String OUTPUT_GRN_LIST = "grnList";
    public static final String OUTPUT_POD_LIST = "podList";
    public static final String OUTPUT_ERROR = "error";

    private final ZohoBooksIntegrationService zohoBooksIntegrationService;
    private final ZohoBooksConnectionDAO zohoBooksConnectionDAO;

    public ZohoBooksFetch(
            ZohoBooksIntegrationService zohoBooksIntegrationService,
            ZohoBooksConnectionDAO zohoBooksConnectionDAO) {
        super(TASK_TYPE_ZOHO_BOOKS_FETCH);
        this.zohoBooksIntegrationService = zohoBooksIntegrationService;
        this.zohoBooksConnectionDAO = zohoBooksConnectionDAO;
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        try {
            ZohoBooksFetchRequest request = toRequest(workflow, task.getInputData());
            ZohoBooksConnection connection = resolveConnection(request.getConnectionId());
            ZohoBooksFetchResponse response =
                    zohoBooksIntegrationService.fetchInvoiceDocuments(connection, request);
            task.addOutput(OUTPUT_CONNECTION_ID, response.getConnectionId());
            task.addOutput(OUTPUT_BILL_NUMBER, response.getBillNumber());
            task.addOutput(OUTPUT_BILL_ID, response.getBillId());
            task.addOutput(OUTPUT_BILL_NUMBERS, response.getBillNumbers());
            task.addOutput(OUTPUT_BILL_IDS, response.getBillIds());
            task.addOutput(OUTPUT_BILL, response.getBill());
            task.addOutput(OUTPUT_BILLS, response.getBills());
            task.addOutput(OUTPUT_INVOICE_NUMBER, response.getInvoiceNumber());
            task.addOutput(OUTPUT_INVOICE_ID, response.getInvoiceId());
            task.addOutput(OUTPUT_INVOICE_NUMBERS, response.getInvoiceNumbers());
            task.addOutput(OUTPUT_INVOICE_IDS, response.getInvoiceIds());
            task.addOutput(OUTPUT_INVOICE, response.getInvoice());
            task.addOutput(OUTPUT_INVOICES, response.getInvoices());
            task.addOutput(OUTPUT_GRN_LIST, response.getGrnList());
            task.addOutput(OUTPUT_POD_LIST, response.getPodList());
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (IllegalArgumentException e) {
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            task.setReasonForIncompletion(e.getMessage());
            task.addOutput(OUTPUT_ERROR, e.getMessage());
        } catch (ZohoBooksIntegrationException e) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            task.addOutput(OUTPUT_ERROR, e.getMessage());
        } catch (Exception e) {
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(e.getMessage());
            task.addOutput(OUTPUT_ERROR, e.getMessage());
        }
        return true;
    }

    private ZohoBooksFetchRequest toRequest(WorkflowModel workflow, Map<String, Object> input) {
        String connectionId = stringInput(input, INPUT_CONNECTION_ID);
        if (isBlank(connectionId)) {
            connectionId = workflowInput(workflow, INPUT_WORKFLOW_CONNECTION_ID);
        }
        ZohoBooksFetchRequest request = new ZohoBooksFetchRequest();
        request.setConnectionId(connectionId);
        request.setBillNumber(
                firstNonBlank(
                        stringInput(input, INPUT_BILL_NUMBER),
                        stringInput(input, INPUT_INVOICE_NUMBER)));
        List<String> billNumbers = stringListInput(input.get(INPUT_BILL_NUMBERS));
        request.setBillNumbers(
                billNumbers.isEmpty()
                        ? stringListInput(input.get(INPUT_INVOICE_NUMBERS))
                        : billNumbers);
        request.setInvoiceNumber(stringInput(input, INPUT_INVOICE_NUMBER));
        request.setInvoiceNumbers(stringListInput(input.get(INPUT_INVOICE_NUMBERS)));
        return request;
    }

    private ZohoBooksConnection resolveConnection(String connectionId) {
        if (isBlank(connectionId)) {
            return latestConnection();
        }

        String normalizedConnectionId = connectionId.trim();
        ZohoBooksConnection connection =
                zohoBooksConnectionDAO.getConnection(normalizedConnectionId);
        if (connection == null) {
            throw new IllegalArgumentException(
                    "No Zoho Books connection found for connectionId " + normalizedConnectionId);
        }
        return connection;
    }

    private ZohoBooksConnection latestConnection() {
        ZohoBooksConnection connection =
                zohoBooksConnectionDAO.getAllConnections().stream()
                        .filter(Objects::nonNull)
                        .max(
                                Comparator.comparingLong(
                                                (ZohoBooksConnection item) ->
                                                        timestamp(item.getUpdatedAt()))
                                        .thenComparingLong(item -> timestamp(item.getCreatedAt()))
                                        .thenComparing(ZohoBooksConnection::getConnectionId))
                        .orElse(null);
        if (connection == null) {
            throw new IllegalArgumentException(
                    "connectionId input is required or create a Zoho Books connection in the UI");
        }
        return connection;
    }

    private long timestamp(Long value) {
        return value == null ? 0L : value;
    }

    private String stringInput(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.startsWith("${") && text.endsWith("}") ? null : text;
    }

    private String workflowInput(WorkflowModel workflow, String key) {
        if (workflow == null || workflow.getInput() == null) {
            return null;
        }
        return stringInput(workflow.getInput(), key);
    }

    private String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred;
    }

    private List<String> stringListInput(Object value) {
        if (value instanceof List<?>) {
            return normalizeStringList((List<?>) value);
        }
        if (value instanceof String && !isBlank((String) value)) {
            String text = ((String) value).trim();
            if (text.startsWith("${") && text.endsWith("}")) {
                return Collections.emptyList();
            }
            if (text.startsWith("[") && text.endsWith("]")) {
                try {
                    return normalizeStringList(OBJECT_MAPPER.readValue(text, OBJECT_LIST_TYPE));
                } catch (Exception ignored) {
                    // Fall back to delimiter parsing so invalid JSON still gets a clear
                    // invoice-number value.
                }
            }
            return Arrays.stream(text.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(item -> !isBlank(item))
                    .filter(item -> !(item.startsWith("${") && item.endsWith("}")))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<String> normalizeStringList(List<?> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(text -> !isBlank(text))
                .filter(text -> !(text.startsWith("${") && text.endsWith("}")))
                .collect(Collectors.toList());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
