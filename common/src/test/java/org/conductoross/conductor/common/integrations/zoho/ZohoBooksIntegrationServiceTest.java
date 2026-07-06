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
package org.conductoross.conductor.common.integrations.zoho;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZohoBooksIntegrationServiceTest {

    @AfterEach
    void clearTokenCache() {
        ZohoBooksIntegrationService.clearTokenCacheForTests();
    }

    @Test
    void saveConnectionUsesGlobalOAuthClientWithoutStoringClientCredentials() {
        ZohoBooksIntegrationService service =
                service(
                        "https://accounts.example.test",
                        "https://books.example.test",
                        "global-id",
                        "global-secret");
        ZohoBooksConnectionRequest request = request("zoho-prod", null, null, "organization-id");

        ZohoBooksConnection connection = service.saveConnection(request);

        assertEquals("zoho-prod", connection.getConnectionId());
        assertNull(connection.getClientId());
        assertNull(connection.getClientSecret());
        assertEquals("organization-id", connection.getOrganizationId());
    }

    @Test
    void saveConnectionRequiresClientCredentialsWhenGlobalOAuthClientIsMissing() {
        ZohoBooksIntegrationService service =
                service("https://accounts.example.test", "https://books.example.test", null, null);
        ZohoBooksConnectionRequest request = request("zoho-prod", null, null, "organization-id");

        ZohoBooksIntegrationException exception =
                assertThrows(
                        ZohoBooksIntegrationException.class, () -> service.saveConnection(request));

        assertEquals("Zoho Books clientId is required", exception.getMessage());
    }

    @Test
    void fetchInvoicesCachesGlobalOAuthTokenAcrossServiceInstances() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/oauth/v2/token", exchange -> handleToken(exchange, tokenRequests));
        server.createContext("/books/v3/bills", this::handleBills);
        server.start();

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            ZohoBooksIntegrationService service =
                    service(baseUrl, baseUrl + "/books/v3", "global-id", "global-secret");
            ZohoBooksIntegrationService secondService =
                    service(baseUrl, baseUrl + "/books/v3", "global-id", "global-secret");

            ZohoBooksInvoicesResponse response =
                    service.fetchInvoices(
                            new ZohoBooksConnection("one", null, null, "organization-one"));
            secondService.fetchInvoices(
                    new ZohoBooksConnection("two", null, null, "organization-one"));

            assertEquals(1, tokenRequests.get());
            assertEquals(
                    List.of(Map.of("line_item_id", "line-one")),
                    response.getInvoices().get(0).get("line_items"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchInvoiceDocumentsSearchesBillsByBillNumber() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        AtomicInteger listRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/oauth/v2/token", exchange -> handleToken(exchange, tokenRequests));
        server.createContext(
                "/books/v3/bills",
                exchange -> {
                    if (exchange.getRequestURI().getPath().endsWith("/bill-one")) {
                        respond(
                                exchange,
                                "{\"bill\":{\"bill_id\":\"bill-one\",\"bill_number\":\"BILL-001\","
                                        + "\"documents\":[{\"file_name\":\"GRN-001.pdf\"},{\"file_name\":\"POD-001.pdf\"}]}}");
                        return;
                    }
                    listRequests.incrementAndGet();
                    String query = exchange.getRequestURI().getRawQuery();
                    assertTrue(query.contains("bill_number=BILL-001"));
                    assertFalse(query.contains("invoice_number"));
                    respond(
                            exchange,
                            "{\"bills\":[{\"bill_id\":\"bill-one\",\"bill_number\":\"BILL-001\"}],"
                                    + "\"page_context\":{\"has_more_page\":false}}");
                });
        server.start();

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            ZohoBooksIntegrationService service =
                    service(baseUrl, baseUrl + "/books/v3", "global-id", "global-secret");
            ZohoBooksFetchRequest request = new ZohoBooksFetchRequest();
            request.setBillNumber("BILL-001");

            ZohoBooksFetchResponse response =
                    service.fetchInvoiceDocuments(
                            new ZohoBooksConnection("one", null, null, "organization-one"),
                            request);

            assertEquals(1, tokenRequests.get());
            assertEquals(1, listRequests.get());
            assertEquals("bill-one", response.getBillId());
            assertEquals("BILL-001", response.getBillNumber());
            assertEquals("BILL-001", response.getBill().get("bill_number"));
            assertEquals("bill-one", response.getBills().get(0).get("bill_id"));
            assertEquals(1, response.getGrnList().size());
            assertEquals(1, response.getPodList().size());
            assertEquals("bill-one", response.getGrnList().get(0).get("billId"));
        } finally {
            server.stop(0);
        }
    }

    private ZohoBooksIntegrationService service(
            String accountsUrl, String apiUrl, String clientId, String clientSecret) {
        return new ZohoBooksIntegrationService(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                accountsUrl,
                apiUrl,
                "ZohoBooks.bills.READ",
                "all",
                clientId,
                clientSecret);
    }

    private ZohoBooksConnectionRequest request(
            String connectionId, String clientId, String clientSecret, String organizationId) {
        ZohoBooksConnectionRequest request = new ZohoBooksConnectionRequest();
        request.setConnectionId(connectionId);
        request.setClientId(clientId);
        request.setClientSecret(clientSecret);
        request.setOrganizationId(organizationId);
        return request;
    }

    private void handleToken(HttpExchange exchange, AtomicInteger tokenRequests)
            throws IOException {
        tokenRequests.incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("client_id=global-id"));
        assertTrue(body.contains("client_secret=global-secret"));
        respond(exchange, "{\"access_token\":\"access-token\",\"expires_in\":3600}");
    }

    private void handleBills(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getPath().endsWith("/bill-one")) {
            respond(
                    exchange,
                    "{\"bill\":{\"bill_id\":\"bill-one\",\"line_items\":[{\"line_item_id\":\"line-one\"}]}}");
            return;
        }
        respond(
                exchange,
                "{\"bills\":[{\"bill_id\":\"bill-one\"}],\"page_context\":{\"has_more_page\":false}}");
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
