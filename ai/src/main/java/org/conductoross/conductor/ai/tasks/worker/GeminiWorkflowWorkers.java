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
package org.conductoross.conductor.ai.tasks.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.conductoross.conductor.ai.http.AIHttpClients;
import org.conductoross.conductor.ai.providers.gemini.api.GeminiApi;
import org.conductoross.conductor.common.integrations.gdrive.GDriveConnection;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationException;
import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gemini.GeminiConnection;
import org.conductoross.conductor.common.integrations.gemini.GeminiIntegrationException;
import org.conductoross.conductor.common.integrations.gemini.GeminiIntegrationService;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.conductoross.conductor.dao.GDriveConnectionDAO;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
@Component
public class GeminiWorkflowWorkers implements AnnotatedSystemTaskWorker {

    public static final String GEMINI_LLM_TASK = "GEMINI_LLM";
    public static final String RECONCILE_TASK = "GRN_POD_RECONCILE";

    private static final Set<String> CLASSIFY_PROMPTS = Set.of("attachment_classify");
    private static final Set<String> PROMPT_NAME_PLACEHOLDERS = Set.of("select j2", "enterj2 name");
    private static final Set<String> PROMPT_PLACEHOLDERS =
            Set.of("paste your prompt here", "enter your prompt");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final GeminiIntegrationService geminiIntegrationService;
    private final GDriveIntegrationService gDriveIntegrationService;
    private final GDriveConnectionDAO gDriveConnectionDAO;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public GeminiWorkflowWorkers(
            GeminiIntegrationService geminiIntegrationService,
            GDriveIntegrationService gDriveIntegrationService,
            GDriveConnectionDAO gDriveConnectionDAO) {
        this.geminiIntegrationService = geminiIntegrationService;
        this.gDriveIntegrationService = gDriveIntegrationService;
        this.gDriveConnectionDAO = gDriveConnectionDAO;
        this.objectMapper = new ObjectMapperProvider().getObjectMapper();
        this.httpClient = AIHttpClients.defaultClient();
    }

    @WorkerTask(GEMINI_LLM_TASK)
    public Map<String, Object> runGemini(Map<String, Object> input) {
        String connectionId = stringValue(input.get("connectionId"));
        String requestedModel = stringValue(input.get("model"));
        GeminiConnection connection =
                geminiIntegrationService.resolveTaskConnection(
                        connectionId, stringValue(input.get("apiKey")), requestedModel);
        String promptName =
                firstNonBlank(
                        promptNameValue(input.get("promptname")),
                        promptNameValue(input.get("promptName")),
                        connection.getPromptName());
        String prompt = promptValue(input.get("prompt"));
        Map<String, Object> promptVariables = mapValue(input.get("promptVariables"));
        if (prompt.isBlank() && promptName.isBlank()) {
            throw new GeminiIntegrationException("GEMINI_LLM requires promptname or prompt input");
        }
        if (prompt.isBlank()) {
            prompt = geminiIntegrationService.loadPrompt(promptName, promptVariables);
        } else {
            prompt = geminiIntegrationService.renderPrompt(prompt, promptVariables);
        }
        String model = firstNonBlank(requestedModel, connection.getModel());
        boolean jsonOutput = booleanValue(input.get("jsonOutput"), true);

        List<Map<String, Object>> documents = documentsFromInput(input);
        if (documents.isEmpty()) {
            documents = List.of(new LinkedHashMap<>());
        }

        GeminiApi api = geminiApi(input, connection);
        List<Map<String, Object>> responses = new ArrayList<>();
        List<Map<String, Object>> classifiedDocuments = new ArrayList<>();
        List<Map<String, Object>> grnDocuments = new ArrayList<>();
        List<Map<String, Object>> podDocuments = new ArrayList<>();
        List<Map<String, Object>> records = new ArrayList<>();

        for (Map<String, Object> document : documents) {
            GeminiDocumentBytes documentBytes =
                    document.isEmpty() ? null : documentBytes(input, document);
            Object parsed = generate(api, model, prompt, documentBytes, jsonOutput);
            Map<String, Object> response = new LinkedHashMap<>(document);
            putPromptName(response, promptName);
            response.put("result", parsed);
            responses.add(response);

            if (isClassificationPrompt(promptName)) {
                Map<String, Object> classified = new LinkedHashMap<>(document);
                classified.put("classification", parsed);
                classifiedDocuments.add(classified);
                // Classification prompts route documents into typed lists used by downstream
                // extraction tasks.
                String documentType = documentType(classified);
                if ("GRN".equals(documentType)) {
                    grnDocuments.add(classified);
                } else if ("POD".equals(documentType)) {
                    podDocuments.add(classified);
                }
            } else {
                Map<String, Object> record = new LinkedHashMap<>(document);
                record.put("extracted", parsed);
                records.add(record);
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        putPromptName(output, promptName);
        output.put("model", model);
        output.put("responses", responses);
        output.put("results", responses);
        if (!classifiedDocuments.isEmpty()) {
            output.put("classifiedDocuments", classifiedDocuments);
        }
        if (isClassificationPrompt(promptName)) {
            output.put("grn", grnDocuments);
            output.put("pod", podDocuments);
            output.put("grn docs", grnDocuments);
            output.put("pod docs", podDocuments);
            output.put("grnDocuments", grnDocuments);
            output.put("podDocuments", podDocuments);
        }
        if (!records.isEmpty()) {
            output.put("records", records);
        }
        return output;
    }

    @WorkerTask(RECONCILE_TASK)
    public Map<String, Object> reconcile(Map<String, Object> input) {
        List<Map<String, Object>> grnRecords =
                firstNonEmptyList(
                        listOfMaps(input.get("grnList")), listOfMaps(input.get("grnRecords")));
        List<Map<String, Object>> podRecords =
                firstNonEmptyList(
                        listOfMaps(input.get("podList")), listOfMaps(input.get("podRecords")));
        List<Map<String, Object>> records = recordsFromInput(input);
        if (grnRecords.isEmpty() && podRecords.isEmpty()) {
            grnRecords =
                    records.stream().filter(record -> "GRN".equals(documentType(record))).toList();
            podRecords =
                    records.stream().filter(record -> "POD".equals(documentType(record))).toList();
        } else {
            records = new ArrayList<>();
            records.addAll(grnRecords);
            records.addAll(podRecords);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> documentMatches = new ArrayList<>();
        List<Map<String, Object>> availablePods = new ArrayList<>(podRecords);
        Set<String> matchedPodIds = new LinkedHashSet<>();

        for (Map<String, Object> grn : grnRecords) {
            Match match = findMatchingPod(grn, availablePods);
            if (match.pod() == null) {
                documentMatches.add(
                        documentMatch(grn, null, "MISSING_POD", match.key(), match.strategy()));
                rows.add(
                        reconciliationRow(
                                grn,
                                null,
                                "MISSING_POD",
                                "No POD matched this GRN invoice.",
                                null,
                                null));
                continue;
            }

            Map<String, Object> pod = match.pod();
            String podId = recordId(pod);
            availablePods.removeIf(candidate -> Objects.equals(recordId(candidate), podId));
            matchedPodIds.add(podId);
            documentMatches.add(documentMatch(grn, pod, "MATCHED", match.key(), match.strategy()));
            rows.addAll(compareDocumentCounts(grn, pod));
            rows.addAll(compareLineItems(grn, pod));
        }

        for (Map<String, Object> pod : podRecords) {
            if (!matchedPodIds.contains(recordId(pod))) {
                KeyStrategy keyStrategy = preferredDocumentKey(pod);
                documentMatches.add(
                        documentMatch(
                                null,
                                pod,
                                "MISSING_GRN",
                                keyStrategy.key(),
                                keyStrategy.strategy()));
                rows.add(
                        reconciliationRow(
                                null,
                                pod,
                                "MISSING_GRN",
                                "No GRN matched this POD invoice.",
                                null,
                                null));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("grnDocuments", grnRecords.size());
        summary.put("podDocuments", podRecords.size());
        summary.put("matchedDocuments", countByStatus(documentMatches, "MATCHED"));
        summary.put("missingPodDocuments", countByStatus(documentMatches, "MISSING_POD"));
        summary.put("missingGrnDocuments", countByStatus(documentMatches, "MISSING_GRN"));
        summary.put(
                "matchedRows",
                rows.stream().filter(row -> statusIsMatch(stringValue(row.get("status")))).count());
        summary.put(
                "mismatchRows",
                rows.stream()
                        .filter(row -> !statusIsMatch(stringValue(row.get("status"))))
                        .count());
        summary.put("totalRows", rows.size());

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("summary", summary);
        output.put("records", records);
        output.put("grnRecords", grnRecords);
        output.put("podRecords", podRecords);
        output.put("grnList", grnRecords);
        output.put("podList", podRecords);
        output.put("documentMatches", documentMatches);
        output.put("reconciliationRows", rows);
        return output;
    }

    private Object generate(
            GeminiApi api,
            String model,
            String prompt,
            GeminiDocumentBytes documentBytes,
            boolean jsonOutput) {
        List<GeminiApi.Part> parts = new ArrayList<>();
        parts.add(GeminiApi.Part.text(prompt));
        if (documentBytes != null) {
            parts.add(
                    new GeminiApi.Part(
                            null,
                            null,
                            null,
                            new GeminiApi.InlineData(
                                    documentBytes.mimeType(),
                                    Base64.getEncoder().encodeToString(documentBytes.bytes())),
                            null));
        }
        GeminiApi.Content content = new GeminiApi.Content("user", parts);
        GeminiApi.GenerationConfig config =
                new GeminiApi.GenerationConfig(
                        0.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        jsonOutput ? "application/json" : null,
                        null,
                        null,
                        null);
        try {
            String text = api.generateContent(model, List.of(content), config).text();
            return jsonOutput ? parseJsonText(text) : text;
        } catch (IOException e) {
            throw new GeminiIntegrationException("Gemini generateContent failed", e);
        }
    }

    private GeminiApi geminiApi(Map<String, Object> input, GeminiConnection connection) {
        String baseUrl =
                firstNonBlank(
                        stringValue(input.get("baseUrl")),
                        stringValue(input.get("apiBaseUrl")),
                        System.getProperty("conductor.integrations.gemini.base-url"));
        return GeminiApi.forApiKey(
                httpClient, connection.getApiKey(), baseUrl.isBlank() ? null : baseUrl);
    }

    private Object parseJsonText(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").trim();
            cleaned = cleaned.replaceFirst("```$", "").trim();
        }
        try {
            return objectMapper.readValue(cleaned, Object.class);
        } catch (JsonProcessingException e) {
            throw new GeminiIntegrationException("Gemini response was not valid JSON", e);
        }
    }

    private GeminiDocumentBytes documentBytes(
            Map<String, Object> input, Map<String, Object> document) {
        String localPath = stringValue(document.get("localPath"));
        String mimeType = firstNonBlank(stringValue(document.get("mimeType")), "application/pdf");
        if (!localPath.isBlank()) {
            try {
                return new GeminiDocumentBytes(Files.readAllBytes(Path.of(localPath)), mimeType);
            } catch (IOException e) {
                throw new GeminiIntegrationException(
                        "Unable to read local document " + localPath, e);
            }
        }

        String fileId =
                firstNonBlank(
                        stringValue(document.get("driveFileId")), stringValue(document.get("id")));
        if (fileId.isBlank()) {
            throw new GeminiIntegrationException(
                    "Document must include localPath, driveFileId, or id");
        }
        GDriveConnection connection = gDriveConnection(input);
        return new GeminiDocumentBytes(
                gDriveIntegrationService.downloadFile(connection.getOauthTokenJson(), fileId),
                mimeType);
    }

    private GDriveConnection gDriveConnection(Map<String, Object> input) {
        String connectionId = stringValue(input.get("gdriveConnectionId"));
        if (!connectionId.isBlank()) {
            GDriveConnection connection = gDriveConnectionDAO.getConnection(connectionId);
            if (connection == null) {
                throw new GDriveIntegrationException(
                        "No Google Drive connection found for connectionId " + connectionId);
            }
            return connection;
        }
        return gDriveConnectionDAO.getAllConnections().stream()
                .max(
                        Comparator.comparingLong(
                                connection -> nullSafeTimestamp(connection.getUpdatedAt())))
                .orElseThrow(
                        () ->
                                new GDriveIntegrationException(
                                        "gdriveConnectionId is required to download Drive files"));
    }

    private List<Map<String, Object>> documentsFromInput(Map<String, Object> input) {
        Object documents = input.get("documents");
        if (documents == null) {
            documents = input.get("files");
        }
        if (documents == null) {
            documents = input.get("classifiedDocuments");
        }
        return listOfMaps(documents);
    }

    private List<Map<String, Object>> recordsFromInput(Map<String, Object> input) {
        Object records = input.get("records");
        if (records == null) {
            records = input.get("responses");
        }
        if (records == null) {
            records = input.get("results");
        }
        return listOfMaps(records);
    }

    @SafeVarargs
    private final List<Map<String, Object>> firstNonEmptyList(List<Map<String, Object>>... values) {
        for (List<Map<String, Object>> value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) itemMap));
                }
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            return List.of(new LinkedHashMap<>((Map<String, Object>) map));
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private boolean isClassificationPrompt(String promptName) {
        return CLASSIFY_PROMPTS.contains(promptName)
                || promptName.toLowerCase().contains("classify");
    }

    private String documentType(Map<String, Object> record) {
        Map<String, Object> extracted = extractedPayload(record);
        Map<String, Object> classification = mapValue(record.get("classification"));
        Object rawClassification = record.get("classification");
        String rawClassificationValue =
                rawClassification instanceof Map<?, ?> ? "" : stringValue(rawClassification);
        String value =
                firstNonBlank(
                        stringValue(record.get("document_type")),
                        stringValue(record.get("documentType")),
                        stringValue(extracted.get("document_type")),
                        stringValue(extracted.get("documentType")),
                        rawClassificationValue,
                        stringValue(classification.get("document_type")),
                        stringValue(classification.get("classification")));
        String normalized = value.trim().toUpperCase();
        if (normalized.contains("POD") || normalized.equals("PROOF_OF_DELIVERY")) {
            return "POD";
        }
        if (normalized.contains("GRN") || normalized.contains("GOODS")) {
            return "GRN";
        }
        return "UNKNOWN";
    }

    private Match findMatchingPod(Map<String, Object> grn, List<Map<String, Object>> podRecords) {
        List<IdentifierCandidate> grnCandidates = documentIdentifierCandidates(grn);
        for (IdentifierCandidate grnCandidate : grnCandidates) {
            if (grnCandidate.normalized().isBlank()) {
                continue;
            }
            List<MatchCandidate> matches =
                    matches(podRecords, "normalized", grnCandidate.normalized());
            if (matches.size() == 1) {
                MatchCandidate match = matches.get(0);
                return new Match(
                        match.record(),
                        grnCandidate.normalized(),
                        grnCandidate.field() + "=" + match.candidate().field());
            }
        }
        for (IdentifierCandidate grnCandidate : grnCandidates) {
            if (grnCandidate.digits().isBlank()) {
                continue;
            }
            List<MatchCandidate> matches = matches(podRecords, "digits", grnCandidate.digits());
            if (matches.size() == 1) {
                MatchCandidate match = matches.get(0);
                return new Match(
                        match.record(),
                        grnCandidate.digits(),
                        grnCandidate.field() + "_digits=" + match.candidate().field() + "_digits");
            }
        }
        KeyStrategy keyStrategy = preferredDocumentKey(grn);
        return new Match(null, keyStrategy.key(), keyStrategy.strategy());
    }

    private List<MatchCandidate> matches(
            List<Map<String, Object>> records, String key, String value) {
        List<MatchCandidate> matches = new ArrayList<>();
        for (Map<String, Object> record : records) {
            for (IdentifierCandidate candidate : documentIdentifierCandidates(record)) {
                if (Objects.equals(
                        "normalized".equals(key) ? candidate.normalized() : candidate.digits(),
                        value)) {
                    matches.add(new MatchCandidate(record, candidate));
                }
            }
        }
        return matches;
    }

    private List<IdentifierCandidate> documentIdentifierCandidates(Map<String, Object> record) {
        List<RawIdentifier> rawIdentifiers = new ArrayList<>();
        collectIdentifierValues(extractedPayload(record), "", rawIdentifiers);
        collectIdentifierValues(
                mapValue(record.get("classification")), "classification", rawIdentifiers);
        List<IdentifierCandidate> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (RawIdentifier raw : rawIdentifiers) {
            String normalized = normalize(raw.value());
            String digits = numericKey(raw.value());
            if (normalized.isBlank() && digits.isBlank()) {
                continue;
            }
            String key = raw.field() + "|" + normalized + "|" + digits;
            if (seen.add(key)) {
                candidates.add(
                        new IdentifierCandidate(
                                raw.field(), stringValue(raw.value()), normalized, digits));
            }
        }
        candidates.sort(Comparator.comparing(candidate -> identifierPriority(candidate.field())));
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private void collectIdentifierValues(Object payload, String path, List<RawIdentifier> values) {
        if (payload instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                String nextPath = path.isBlank() ? key : path + "." + key;
                if (isIdentifierField(key)
                        && (value instanceof String || value instanceof Number)) {
                    values.add(new RawIdentifier(nextPath, value));
                }
                collectIdentifierValues(value, nextPath, values);
            }
        } else if (payload instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                collectIdentifierValues(list.get(index), path + "[" + index + "]", values);
            }
        }
    }

    private boolean isIdentifierField(String fieldName) {
        String normalized = normalize(fieldName);
        return normalized.contains("invoice")
                || normalized.contains("documentnumber")
                || normalized.contains("documentno")
                || normalized.contains("referencenumber")
                || normalized.contains("referenceno")
                || normalized.contains("ponumber")
                || normalized.contains("purchaseorder")
                || normalized.contains("billnumber")
                || normalized.contains("billno")
                || normalized.contains("grnnumber")
                || normalized.contains("grnno")
                || normalized.contains("deliverynumber");
    }

    private String identifierPriority(String field) {
        String normalized = normalize(field);
        if (normalized.contains("invoice")) {
            return "0" + field;
        }
        if (normalized.contains("document")) {
            return "1" + field;
        }
        if (normalized.contains("reference")) {
            return "2" + field;
        }
        if (normalized.contains("po") || normalized.contains("purchaseorder")) {
            return "3" + field;
        }
        return "4" + field;
    }

    private KeyStrategy preferredDocumentKey(Map<String, Object> record) {
        for (IdentifierCandidate candidate : documentIdentifierCandidates(record)) {
            if (!candidate.normalized().isBlank()) {
                return new KeyStrategy(candidate.normalized(), candidate.field());
            }
        }
        for (IdentifierCandidate candidate : documentIdentifierCandidates(record)) {
            if (!candidate.digits().isBlank()) {
                return new KeyStrategy(candidate.digits(), candidate.field() + "_digits");
            }
        }
        return new KeyStrategy(recordId(record), "driveFileId");
    }

    private List<Map<String, Object>> compareDocumentCounts(
            Map<String, Object> grn, Map<String, Object> pod) {
        String status =
                itemCount(grn) == itemCount(pod) ? "ITEM_COUNT_MATCHED" : "ITEM_COUNT_MISMATCH";
        String note =
                "ITEM_COUNT_MATCHED".equals(status)
                        ? "GRN and POD have the same number of line items."
                        : "GRN and POD line item counts differ.";
        return List.of(reconciliationRow(grn, pod, status, note, null, null));
    }

    private List<Map<String, Object>> compareLineItems(
            Map<String, Object> grn, Map<String, Object> pod) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> grnItems = lineItems(grn);
        List<Map<String, Object>> podItems = lineItems(pod);
        Map<String, Map<String, Object>> podByItemKey = new LinkedHashMap<>();
        for (Map<String, Object> podItem : podItems) {
            podByItemKey.putIfAbsent(itemKey(podItem), podItem);
        }

        Set<String> grnItemKeys = new LinkedHashSet<>();
        for (Map<String, Object> grnItem : grnItems) {
            String key = itemKey(grnItem);
            grnItemKeys.add(key);
            Map<String, Object> podItem = podByItemKey.get(key);
            if (podItem == null) {
                rows.add(
                        reconciliationRow(
                                grn,
                                pod,
                                "MISSING_POD_ITEM",
                                "No matching POD line item.",
                                grnItem,
                                null));
                continue;
            }
            Double grnQuantity =
                    quantity(
                            firstNonBlankObject(
                                    grnItem.get("grn_qty"),
                                    grnItem.get("quantity"),
                                    grnItem.get("qty_delivered")));
            Double podQuantity =
                    quantity(
                            firstNonBlankObject(
                                    podItem.get("handwritten_quantity"),
                                    podItem.get("quantity"),
                                    podItem.get("printed_quantity")));
            String status =
                    Objects.equals(grnQuantity, podQuantity) ? "MATCHED" : "QUANTITY_MISMATCH";
            rows.add(
                    reconciliationRow(
                            grn,
                            pod,
                            status,
                            "MATCHED".equals(status) ? "" : "GRN and POD quantities differ.",
                            grnItem,
                            podItem));
        }

        for (Map<String, Object> podItem : podItems) {
            if (!grnItemKeys.contains(itemKey(podItem))) {
                rows.add(
                        reconciliationRow(
                                grn,
                                pod,
                                "MISSING_GRN_ITEM",
                                "No matching GRN line item.",
                                null,
                                podItem));
            }
        }
        return rows;
    }

    private Map<String, Object> documentMatch(
            Map<String, Object> grn,
            Map<String, Object> pod,
            String status,
            String key,
            String strategy) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("status", status);
        match.put("matchKey", key);
        match.put("matchStrategy", strategy);
        match.put("grnFile", grn == null ? null : grn.get("name"));
        match.put("podFile", pod == null ? null : pod.get("name"));
        match.put("grnItemCount", itemCount(grn));
        match.put("podItemCount", itemCount(pod));
        match.put(
                "itemCountStatus",
                grn != null && pod != null && itemCount(grn) == itemCount(pod)
                        ? "MATCHED"
                        : "ITEM_COUNT_MISMATCH");
        return match;
    }

    private Map<String, Object> reconciliationRow(
            Map<String, Object> grn,
            Map<String, Object> pod,
            String status,
            String note,
            Map<String, Object> grnItem,
            Map<String, Object> podItem) {
        grnItem = grnItem == null ? Map.of() : grnItem;
        podItem = podItem == null ? Map.of() : podItem;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", status);
        row.put("note", note);
        row.put("matchKey", matchKey(grn != null ? grn : pod));
        row.put("grnFile", grn == null ? null : grn.get("name"));
        row.put("podFile", pod == null ? null : pod.get("name"));
        row.put(
                "invoiceNumber",
                firstNonBlankObject(
                        extractedValue(grn, "invoice_number"),
                        extractedValue(pod, "invoice_number")));
        row.put(
                "poNumber",
                firstNonBlankObject(
                        extractedValue(grn, "po_number"),
                        extractedValue(pod, "purchase_order_number"),
                        extractedValue(pod, "po_number")));
        row.put("itemKey", itemKey(grnItem.isEmpty() ? podItem : grnItem));
        row.put(
                "grnDescription",
                firstNonBlankObject(
                        grnItem.get("product_name"),
                        grnItem.get("description"),
                        grnItem.get("item_name")));
        row.put(
                "podDescription",
                firstNonBlankObject(
                        podItem.get("item_name"),
                        podItem.get("description"),
                        podItem.get("product_name")));
        row.put(
                "grnQuantity",
                firstNonBlankObject(
                        grnItem.get("grn_qty"),
                        grnItem.get("quantity"),
                        grnItem.get("qty_delivered")));
        row.put(
                "podQuantity",
                firstNonBlankObject(
                        podItem.get("handwritten_quantity"),
                        podItem.get("quantity"),
                        podItem.get("printed_quantity")));
        row.put("grnItemCount", itemCount(grn));
        row.put("podItemCount", itemCount(pod));
        return row;
    }

    private String matchKey(Map<String, Object> record) {
        if (record == null) {
            return "";
        }
        for (String field :
                List.of(
                        "invoice_number",
                        "po_number",
                        "purchase_order_number",
                        "reference_number",
                        "document_number")) {
            String value = normalize(extractedValue(record, field));
            if (!value.isBlank()) {
                return field + ":" + value;
            }
        }
        return "file:" + recordId(record);
    }

    private Object extractedValue(Map<String, Object> record, String field) {
        return record == null ? null : extractedPayload(record).get(field);
    }

    private List<Map<String, Object>> lineItems(Map<String, Object> record) {
        if (record == null) {
            return List.of();
        }
        Map<String, Object> extracted = extractedPayload(record);
        Object lineItems = extracted.get("line_items");
        if (lineItems == null) {
            lineItems = extracted.get("debit_note_line_items");
        }
        return listOfMaps(lineItems);
    }

    private Map<String, Object> extractedPayload(Map<String, Object> record) {
        if (record == null) {
            return Map.of();
        }
        Map<String, Object> extracted = mapValue(record.get("extracted"));
        if (!extracted.isEmpty()) {
            return extracted;
        }
        return mapValue(record.get("result"));
    }

    private int itemCount(Map<String, Object> record) {
        return lineItems(record).size();
    }

    private String itemKey(Map<String, Object> item) {
        for (String field :
                List.of(
                        "product_name",
                        "item_name",
                        "description",
                        "sku",
                        "item_code",
                        "product_no")) {
            String value = normalize(item.get(field));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private Double quantity(Object value) {
        if (value == null || stringValue(value).isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher =
                Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(stringValue(value).replace(",", ""));
        return matcher.find() ? Double.valueOf(matcher.group()) : null;
    }

    private String normalize(Object value) {
        return NON_ALPHANUMERIC.matcher(stringValue(value).toLowerCase()).replaceAll("");
    }

    private String numericKey(Object value) {
        java.util.regex.Matcher matcher = DIGITS.matcher(stringValue(value));
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            result.append(matcher.group());
        }
        return result.toString();
    }

    private String recordId(Map<String, Object> record) {
        return firstNonBlank(
                stringValue(record.get("driveFileId")),
                stringValue(record.get("id")),
                stringValue(record.get("name")));
    }

    private Object firstNonBlankObject(Object... values) {
        for (Object value : values) {
            if (value != null && !stringValue(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void putPromptName(Map<String, Object> target, String promptName) {
        if (!promptName.isBlank()) {
            target.put("promptname", promptName);
            target.put("promptName", promptName);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String promptNameValue(Object value) {
        String promptName = stringValue(value).trim();
        return containsIgnoreCase(PROMPT_NAME_PLACEHOLDERS, promptName) ? "" : promptName;
    }

    private String promptValue(Object value) {
        String prompt = stringValue(value).trim();
        return containsIgnoreCase(PROMPT_PLACEHOLDERS, prompt) ? "" : prompt;
    }

    private boolean containsIgnoreCase(Set<String> values, String candidate) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean statusIsMatch(String status) {
        return "MATCHED".equals(status) || "ITEM_COUNT_MATCHED".equals(status);
    }

    private long countByStatus(List<Map<String, Object>> rows, String status) {
        return rows.stream().filter(row -> status.equals(row.get("status"))).count();
    }

    private long nullSafeTimestamp(Long value) {
        return value == null ? 0L : value;
    }

    private record GeminiDocumentBytes(byte[] bytes, String mimeType) {}

    private record Match(Map<String, Object> pod, String key, String strategy) {}

    private record MatchCandidate(Map<String, Object> record, IdentifierCandidate candidate) {}

    private record IdentifierCandidate(
            String field, String value, String normalized, String digits) {}

    private record RawIdentifier(String field, Object value) {}

    private record KeyStrategy(String key, String strategy) {}
}
