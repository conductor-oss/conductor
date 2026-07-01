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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gemini.GeminiConnection;
import org.conductoross.conductor.common.integrations.gemini.GeminiConnectionRequest;
import org.conductoross.conductor.common.integrations.gemini.GeminiIntegrationService;
import org.conductoross.conductor.core.dao.InMemoryGDriveConnectionDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiWorkflowWorkersTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Test
    void geminiConnectionStoresPromptName() {
        GeminiIntegrationService service = new GeminiIntegrationService();
        GeminiConnectionRequest request = new GeminiConnectionRequest();
        request.setConnectionId("gemini-default");
        request.setApiKey("test-api-key");
        request.setModel("gemini-2.5-flash");
        request.setPromptName("attachment_classify");

        GeminiConnection connection = service.saveConnection(request);

        assertEquals("attachment_classify", connection.getPromptName());
        assertEquals(
                "attachment_classify", service.getConnection("gemini-default").getPromptName());
    }

    @Test
    void reconcileUsesExplicitGrnAndPodLists() {
        GeminiWorkflowWorkers workers =
                new GeminiWorkflowWorkers(
                        new GeminiIntegrationService(),
                        new GDriveIntegrationService(),
                        new InMemoryGDriveConnectionDAO());

        Map<String, Object> grn = record("grn-1.pdf", "GRN", "INV-100", "Widget", "10", "grn_qty");
        Map<String, Object> pod =
                record("pod-1.pdf", "POD", "INV-100", "Widget", "10", "handwritten_quantity");

        Map<String, Object> output =
                workers.reconcile(Map.of("grnList", List.of(grn), "podList", List.of(pod)));

        Map<?, ?> summary = (Map<?, ?>) output.get("summary");
        assertEquals(1L, summary.get("matchedDocuments"));
        assertEquals(0L, summary.get("missingPodDocuments"));
        assertEquals(0L, summary.get("missingGrnDocuments"));
        assertEquals(List.of(grn), output.get("grnList"));
        assertEquals(List.of(pod), output.get("podList"));
    }

    @Test
    void reconcileMatchesGeminiResultPayloadsByInvoiceDigits() {
        GeminiWorkflowWorkers workers =
                new GeminiWorkflowWorkers(
                        new GeminiIntegrationService(),
                        new GDriveIntegrationService(),
                        new InMemoryGDriveConnectionDAO());

        Map<String, Object> grn =
                Map.of(
                        "id",
                        "grn-drive-file-id",
                        "name",
                        "BLR-INV-012584-GRN.pdf",
                        "classification",
                        Map.of("classification", "grn"),
                        "result",
                        Map.of(
                                "supplier_bill_number",
                                "BRL-INV-012584",
                                "purchase_order_no",
                                "BLR073P1T3N",
                                "line_items",
                                List.of(
                                        Map.of(
                                                "product_name",
                                                "Avocado Hass Imported",
                                                "grn_qty",
                                                230,
                                                "qty_delivered",
                                                230),
                                        Map.of(
                                                "product_name",
                                                "Avocado Large Premium",
                                                "grn_qty",
                                                1,
                                                "qty_delivered",
                                                1))));
        Map<String, Object> pod =
                Map.of(
                        "id",
                        "pod-drive-file-id",
                        "name",
                        "BLR-INV-012584-POD.jpeg",
                        "classification",
                        Map.of("classification", "pod"),
                        "result",
                        Map.of(
                                "invoice_number",
                                "BLR-INV-012584",
                                "purchase_order_number",
                                "BLR-SO-07125",
                                "line_items",
                                List.of(
                                        Map.of(
                                                "item_name",
                                                "Snacky Avocado Regular",
                                                "handwritten_quantity",
                                                171),
                                        Map.of(
                                                "item_name",
                                                "Avocado Large Premium",
                                                "handwritten_quantity",
                                                1),
                                        Map.of(
                                                "item_name",
                                                "Avocado Hass Imported",
                                                "handwritten_quantity",
                                                230))));

        Map<String, Object> output =
                workers.reconcile(Map.of("grnList", List.of(grn), "podList", List.of(pod)));

        Map<?, ?> summary = (Map<?, ?>) output.get("summary");
        assertEquals(1L, summary.get("matchedDocuments"));
        assertEquals(0L, summary.get("missingPodDocuments"));
        assertEquals(0L, summary.get("missingGrnDocuments"));

        List<?> documentMatches = (List<?>) output.get("documentMatches");
        Map<?, ?> documentMatch = (Map<?, ?>) documentMatches.get(0);
        assertEquals("MATCHED", documentMatch.get("status"));
        assertEquals("012584", documentMatch.get("matchKey"));
        assertEquals(
                "supplier_bill_number_digits=invoice_number_digits",
                documentMatch.get("matchStrategy"));
        assertEquals(2, documentMatch.get("grnItemCount"));
        assertEquals(3, documentMatch.get("podItemCount"));
    }

    @Test
    void runGeminiExecutesPromptOnceWithoutFiles() throws Exception {
        MockWebServer server = startServer();
        try {
            server.enqueue(geminiResponse("done"));
            GeminiWorkflowWorkers workers = newWorkers();

            Map<String, Object> output =
                    workers.runGemini(
                            Map.of(
                                    "apiKey",
                                    "test-api-key",
                                    "baseUrl",
                                    baseUrl(server),
                                    "model",
                                    "gemini-2.5-flash",
                                    "prompt",
                                    "Summarize the current status.",
                                    "jsonOutput",
                                    false));

            List<?> responses = (List<?>) output.get("responses");
            assertEquals(1, responses.size());
            assertEquals("done", ((Map<?, ?>) responses.get(0)).get("result"));
            assertEquals(1, requestParts(server.takeRequest()).size());
        } finally {
            server.shutdown();
        }
    }

    @Test
    void runGeminiLoopsFilesAndSplitsAttachmentClassifyOutput(@TempDir Path tempDir)
            throws Exception {
        Path grnFile = tempDir.resolve("grn.pdf");
        Path podFile = tempDir.resolve("pod.pdf");
        Files.writeString(grnFile, "grn");
        Files.writeString(podFile, "pod");
        MockWebServer server = startServer();
        String previousPromptDir = System.getProperty("conductor.integrations.gemini.prompt-dir");
        String promptDir = promptDir();
        System.setProperty("conductor.integrations.gemini.prompt-dir", promptDir);
        try {
            server.enqueue(geminiResponse("{\"classification\":\"grn\"}"));
            server.enqueue(geminiResponse("{\"classification\":\"pod\"}"));
            GeminiWorkflowWorkers workers = newWorkers();

            Map<String, Object> output =
                    workers.runGemini(
                            Map.of(
                                    "apiKey",
                                    "test-api-key",
                                    "baseUrl",
                                    baseUrl(server),
                                    "model",
                                    "gemini-2.5-flash",
                                    "promptname",
                                    Path.of(promptDir, "attachment_classify.j2").toString(),
                                    "prompt",
                                    "enter your prompt",
                                    "files",
                                    List.of(
                                            Map.of(
                                                    "name",
                                                    "grn.pdf",
                                                    "localPath",
                                                    grnFile.toString(),
                                                    "mimeType",
                                                    "application/pdf"),
                                            Map.of(
                                                    "name",
                                                    "pod.pdf",
                                                    "localPath",
                                                    podFile.toString(),
                                                    "mimeType",
                                                    "application/pdf")),
                                    "jsonOutput",
                                    true));

            assertEquals(1, ((List<?>) output.get("grn")).size());
            assertEquals(1, ((List<?>) output.get("pod")).size());
            assertEquals(output.get("grn"), output.get("grn docs"));
            assertEquals(output.get("pod"), output.get("pod docs"));
            assertEquals(output.get("grn"), output.get("grnDocuments"));
            assertEquals(output.get("pod"), output.get("podDocuments"));

            List<?> firstRequestParts = requestParts(server.takeRequest());
            List<?> secondRequestParts = requestParts(server.takeRequest());
            assertEquals(2, firstRequestParts.size());
            assertEquals(2, secondRequestParts.size());
            assertTrue(
                    String.valueOf(((Map<?, ?>) firstRequestParts.get(0)).get("text"))
                            .contains("You are classifying"));
        } finally {
            restorePromptDir(previousPromptDir);
            server.shutdown();
        }
    }

    private Map<String, Object> record(
            String name,
            String documentType,
            String invoiceNumber,
            String itemName,
            String quantity,
            String quantityField) {
        return Map.of(
                "name",
                name,
                "extracted",
                Map.of(
                        "document_type",
                        documentType,
                        "invoice_number",
                        invoiceNumber,
                        "line_items",
                        List.of(Map.of("product_name", itemName, quantityField, quantity))));
    }

    private GeminiWorkflowWorkers newWorkers() {
        return new GeminiWorkflowWorkers(
                new GeminiIntegrationService(),
                new GDriveIntegrationService(),
                new InMemoryGDriveConnectionDAO());
    }

    private MockWebServer startServer() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        return server;
    }

    private String baseUrl(MockWebServer server) {
        return server.url("").toString().replaceAll("/$", "");
    }

    private String promptDir() {
        Path rootPromptDir = Path.of("prompts");
        if (Files.exists(rootPromptDir.resolve("attachment_classify.j2"))) {
            return rootPromptDir.toAbsolutePath().normalize().toString();
        }
        return Path.of("..", "prompts").toAbsolutePath().normalize().toString();
    }

    private void restorePromptDir(String previousPromptDir) {
        if (previousPromptDir == null) {
            System.clearProperty("conductor.integrations.gemini.prompt-dir");
        } else {
            System.setProperty("conductor.integrations.gemini.prompt-dir", previousPromptDir);
        }
    }

    private MockResponse geminiResponse(String text) throws Exception {
        String escapedText = objectMapper.writeValueAsString(text);
        return new MockResponse()
                .setResponseCode(200)
                .setBody(
                        """
                        {"candidates":[{"content":{"role":"model","parts":[{"text":%s}]},"finishReason":"STOP"}]}
                        """
                                .formatted(escapedText))
                .addHeader("Content-Type", "application/json");
    }

    private List<?> requestParts(RecordedRequest request) throws Exception {
        Map<?, ?> body = objectMapper.readValue(request.getBody().readUtf8(), Map.class);
        List<?> contents = (List<?>) body.get("contents");
        Map<?, ?> content = (Map<?, ?>) contents.get(0);
        return (List<?>) content.get("parts");
    }
}
