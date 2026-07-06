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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ZohoBooksIntegrationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().getObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String DEFAULT_REGION = "in";
    private static final String DEFAULT_ACCOUNTS_URL = accountsUrlForRegion(DEFAULT_REGION);
    private static final String DEFAULT_API_URL = booksApiUrlForRegion(DEFAULT_REGION);
    private static final String DEFAULT_SCOPE = "ZohoBooks.bills.READ";
    private static final String DEFAULT_BILL_STATUSES = "all";
    private static final int PAGE_SIZE = 200;
    private static final int DEFAULT_TOKEN_EXPIRY_SECONDS = 3600;
    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 300;
    private static final ConcurrentMap<String, CachedToken> TOKEN_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Object> TOKEN_LOCKS = new ConcurrentHashMap<>();

    private final HttpClient httpClient;
    private final String accountsUrl;
    private final String apiUrl;
    private final String scope;
    private final List<String> invoiceStatuses;
    private final String globalClientId;
    private final String globalClientSecret;

    public ZohoBooksIntegrationService() {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                environmentValue(
                        "CONDUCTOR_ZOHO_ACCOUNTS_URL",
                        accountsUrlForRegion(environmentValue("ZOHO_REGION", DEFAULT_REGION))),
                environmentValue(
                        "CONDUCTOR_ZOHO_BOOKS_API_URL",
                        booksApiUrlForRegion(environmentValue("ZOHO_REGION", DEFAULT_REGION))),
                environmentValue("CONDUCTOR_ZOHO_BOOKS_SCOPE", DEFAULT_SCOPE),
                environmentValue(
                        "CONDUCTOR_ZOHO_BOOKS_BILL_STATUSES",
                        environmentValue(
                                "CONDUCTOR_ZOHO_BOOKS_INVOICE_STATUSES", DEFAULT_BILL_STATUSES)),
                optionalEnvironmentValue("CONDUCTOR_ZOHO_BOOKS_CLIENT_ID"),
                optionalEnvironmentValue("CONDUCTOR_ZOHO_BOOKS_CLIENT_SECRET"));
    }

    ZohoBooksIntegrationService(
            HttpClient httpClient,
            String accountsUrl,
            String apiUrl,
            String scope,
            String invoiceStatuses) {
        this(httpClient, accountsUrl, apiUrl, scope, invoiceStatuses, null, null);
    }

    ZohoBooksIntegrationService(
            HttpClient httpClient,
            String accountsUrl,
            String apiUrl,
            String scope,
            String invoiceStatuses,
            String globalClientId,
            String globalClientSecret) {
        this.httpClient = httpClient;
        this.accountsUrl = trimTrailingSlash(accountsUrl);
        this.apiUrl = trimTrailingSlash(apiUrl);
        this.scope = required(scope, "Zoho Books OAuth scope is required");
        this.invoiceStatuses = commaSeparatedValues(invoiceStatuses);
        this.globalClientId = blankToNull(globalClientId);
        this.globalClientSecret = blankToNull(globalClientSecret);
    }

    public ZohoBooksConnection saveConnection(ZohoBooksConnectionRequest request) {
        if (request == null) {
            throw new ZohoBooksIntegrationException("Request body is required");
        }
        return new ZohoBooksConnection(
                required(request.getConnectionId(), "Zoho Books connectionId is required"),
                connectionClientId(request),
                connectionClientSecret(request),
                required(request.getOrganizationId(), "Zoho Books organizationId is required"));
    }

    public ZohoBooksFetchResponse fetchInvoiceDocuments(
            ZohoBooksConnection connection, ZohoBooksFetchRequest request) {
        if (connection == null) {
            throw new ZohoBooksIntegrationException("Zoho Books connection is required");
        }
        List<String> invoiceNumbers = billNumbers(request);
        String accessToken = accessToken(connection);
        ZohoBooksFetchResponse response = new ZohoBooksFetchResponse();
        response.setConnectionId(connection.getConnectionId());
        response.setBillNumbers(invoiceNumbers);
        List<String> invoiceIds = new ArrayList<>();
        List<Map<String, Object>> bills = new ArrayList<>();
        List<Map<String, Object>> grnList = new ArrayList<>();
        List<Map<String, Object>> podList = new ArrayList<>();

        for (String invoiceNumber : invoiceNumbers) {
            Map<String, Object> billSummary =
                    findBillByNumber(connection, accessToken, invoiceNumber);
            String billId = stringValue(billSummary.get("bill_id"));
            if (isBlank(billId)) {
                throw new ZohoBooksIntegrationException(
                        "Zoho Books bill " + invoiceNumber + " did not include bill_id");
            }

            Map<String, Object> bill = getBill(connection, accessToken, billId);
            invoiceIds.add(billId);
            bills.add(bill);
            grnList.addAll(classifiedRecords(bill, true, invoiceNumber, billId));
            podList.addAll(classifiedRecords(bill, false, invoiceNumber, billId));
        }

        response.setBillIds(invoiceIds);
        response.setBills(bills);
        response.setGrnList(grnList);
        response.setPodList(podList);
        if (invoiceNumbers.size() == 1) {
            response.setBillNumber(invoiceNumbers.get(0));
            response.setBillId(invoiceIds.get(0));
            response.setBill(bills.get(0));
        }
        return response;
    }

    public ZohoBooksInvoicesResponse fetchInvoices(ZohoBooksConnection connection) {
        if (connection == null) {
            throw new ZohoBooksIntegrationException("Zoho Books connection is required");
        }
        String accessToken = accessToken(connection);
        Map<String, Map<String, Object>> invoicesById = new LinkedHashMap<>();
        for (String status : invoiceStatuses) {
            List<Map<String, Object>> invoices = fetchBills(connection, accessToken, status);
            invoices.forEach(
                    invoice -> {
                        String invoiceId = stringValue(invoice.get("bill_id"));
                        String key = isBlank(invoiceId) ? invoice.toString() : invoiceId;
                        invoicesById.putIfAbsent(key, invoice);
                    });
            if (isAllStatus(status) && !invoices.isEmpty()) {
                break;
            }
        }

        List<Map<String, Object>> hydratedInvoices = new ArrayList<>();
        for (Map<String, Object> invoice : invoicesById.values()) {
            String invoiceId = stringValue(invoice.get("bill_id"));
            if (isBlank(invoiceId)) {
                hydratedInvoices.add(invoice);
            } else {
                hydratedInvoices.add(getBill(connection, accessToken, invoiceId));
            }
        }

        ZohoBooksInvoicesResponse response = new ZohoBooksInvoicesResponse();
        response.setConnectionId(connection.getConnectionId());
        response.setInvoices(hydratedInvoices);
        return response;
    }

    private List<String> billNumbers(ZohoBooksFetchRequest request) {
        if (request == null) {
            throw new ZohoBooksIntegrationException("billNumbers is required");
        }
        List<String> invoiceNumbers = new ArrayList<>();
        if (request.getBillNumbers() != null) {
            request.getBillNumbers().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !isBlank(value))
                    .forEach(invoiceNumbers::add);
        }
        if (invoiceNumbers.isEmpty() && request.getInvoiceNumbers() != null) {
            request.getInvoiceNumbers().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !isBlank(value))
                    .forEach(invoiceNumbers::add);
        }
        if (invoiceNumbers.isEmpty() && !isBlank(request.getBillNumber())) {
            invoiceNumbers.add(request.getBillNumber().trim());
        }
        if (invoiceNumbers.isEmpty() && !isBlank(request.getInvoiceNumber())) {
            invoiceNumbers.add(request.getInvoiceNumber().trim());
        }
        if (invoiceNumbers.isEmpty()) {
            throw new ZohoBooksIntegrationException("billNumbers is required");
        }
        return invoiceNumbers;
    }

    private String accessToken(ZohoBooksConnection connection) {
        String tokenCacheKey = tokenCacheKey(connection);
        CachedToken cachedToken = TOKEN_CACHE.get(tokenCacheKey);
        if (cachedToken != null && cachedToken.isUsable()) {
            return cachedToken.accessToken;
        }

        Object lock = TOKEN_LOCKS.computeIfAbsent(tokenCacheKey, ignored -> new Object());
        synchronized (lock) {
            cachedToken = TOKEN_CACHE.get(tokenCacheKey);
            if (cachedToken != null && cachedToken.isUsable()) {
                return cachedToken.accessToken;
            }
            return fetchAndCacheAccessToken(connection, tokenCacheKey);
        }
    }

    private String fetchAndCacheAccessToken(ZohoBooksConnection connection, String tokenCacheKey) {
        OAuthClient client = oauthClient(connection);
        String body =
                form(
                        orderedParams(
                                "grant_type",
                                "client_credentials",
                                "client_id",
                                client.clientId(),
                                "client_secret",
                                client.clientSecret(),
                                "scope",
                                scope));
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(accountsUrl + "/oauth/v2/token"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        Map<String, Object> response = sendJson(request, "Unable to authenticate with Zoho Books");
        String accessToken = stringValue(response.get("access_token"));
        if (isBlank(accessToken)) {
            throw new ZohoBooksIntegrationException(
                    "Zoho Books token response did not include access_token: "
                            + tokenErrorMessage(response));
        }
        int expiresIn = integerValue(response.get("expires_in"), DEFAULT_TOKEN_EXPIRY_SECONDS);
        TOKEN_CACHE.put(tokenCacheKey, new CachedToken(accessToken, expiresIn));
        return accessToken;
    }

    private String tokenCacheKey(ZohoBooksConnection connection) {
        OAuthClient client = oauthClient(connection);
        return sha256(client.clientId() + ":" + connection.getOrganizationId() + ":" + scope);
    }

    private Map<String, Object> findBillByNumber(
            ZohoBooksConnection connection, String accessToken, String invoiceNumber) {
        for (String status : invoiceStatuses) {
            for (SearchFilter searchFilter : searchFilters(invoiceNumber)) {
                Map<String, Object> invoice =
                        findBillByNumber(
                                connection, accessToken, invoiceNumber, status, searchFilter);
                if (!invoice.isEmpty()) {
                    return invoice;
                }
            }
        }
        throw new ZohoBooksIntegrationException(
                "No Zoho Books bill found for billNumber "
                        + invoiceNumber
                        + " in statuses "
                        + invoiceStatuses);
    }

    private Map<String, Object> findBillByNumber(
            ZohoBooksConnection connection,
            String accessToken,
            String invoiceNumber,
            String status,
            SearchFilter searchFilter) {
        int page = 1;
        while (true) {
            Map<String, Object> response =
                    sendAuthenticatedJson(
                            connection,
                            accessToken,
                            invoiceListUri(connection, searchFilter, status, page),
                            "Unable to list Zoho Books bills");
            List<Map<String, Object>> invoices = objectList(response.get("bills"));
            for (Map<String, Object> invoice : invoices) {
                if (matchesInvoiceNumber(invoice, invoiceNumber)) {
                    return invoice;
                }
            }
            if (!hasMorePages(response.get("page_context"))) {
                return Map.of();
            }
            page++;
        }
    }

    private URI invoiceListUri(
            ZohoBooksConnection connection, SearchFilter searchFilter, String status, int page) {
        return billsUri(connection, status, page, searchFilter);
    }

    private URI billsUri(ZohoBooksConnection connection, String status, int page) {
        return billsUri(connection, status, page, null);
    }

    private URI billsUri(
            ZohoBooksConnection connection, String status, int page, SearchFilter searchFilter) {
        String statusParameter =
                isAllStatus(status)
                        ? "&filter_by=" + encode("Status.All")
                        : "&status=" + encode(status);
        String searchParameter =
                searchFilter == null
                        ? ""
                        : "&" + encode(searchFilter.name()) + "=" + encode(searchFilter.value());
        URI uri =
                URI.create(
                        apiUrl
                                + "/bills?organization_id="
                                + encode(connection.getOrganizationId())
                                + statusParameter
                                + "&per_page="
                                + PAGE_SIZE
                                + "&page="
                                + page
                                + searchParameter);
        return uri;
    }

    private List<Map<String, Object>> fetchBills(
            ZohoBooksConnection connection, String accessToken, String status) {
        List<Map<String, Object>> invoices = new ArrayList<>();
        int page = 1;
        while (true) {
            Map<String, Object> response =
                    sendAuthenticatedJson(
                            connection,
                            accessToken,
                            billsUri(connection, status, page),
                            "Unable to list Zoho Books bills");
            invoices.addAll(objectList(response.get("bills")));
            if (!hasMorePages(response.get("page_context"))) {
                return invoices;
            }
            page++;
        }
    }

    private boolean hasMorePages(Object pageContext) {
        if (pageContext instanceof Map<?, ?>) {
            return Boolean.TRUE.equals(objectMap(pageContext).get("has_more_page"));
        }
        if (pageContext instanceof List<?>) {
            List<?> contexts = (List<?>) pageContext;
            if (!contexts.isEmpty()) {
                return hasMorePages(contexts.get(0));
            }
        }
        return false;
    }

    private boolean isAllStatus(String status) {
        return "all".equalsIgnoreCase(status);
    }

    private List<SearchFilter> searchFilters(String invoiceNumber) {
        return List.of(
                new SearchFilter("bill_number", invoiceNumber),
                new SearchFilter("bill_number_contains", invoiceNumber),
                new SearchFilter("reference_number", invoiceNumber),
                new SearchFilter("reference_number_contains", invoiceNumber),
                new SearchFilter("search_text", invoiceNumber));
    }

    private boolean matchesInvoiceNumber(Map<String, Object> invoice, String invoiceNumber) {
        return equalsField(invoice, "bill_number", invoiceNumber)
                || equalsField(invoice, "reference_number", invoiceNumber)
                || equalsField(invoice, "bill_id", invoiceNumber);
    }

    private boolean equalsField(Map<String, Object> invoice, String field, String expected) {
        return expected.equalsIgnoreCase(Objects.toString(invoice.get(field), "").trim());
    }

    private Map<String, Object> getBill(
            ZohoBooksConnection connection, String accessToken, String invoiceId) {
        URI uri =
                URI.create(
                        apiUrl
                                + "/bills/"
                                + encode(invoiceId)
                                + "?organization_id="
                                + encode(connection.getOrganizationId()));
        Map<String, Object> response =
                sendAuthenticatedJson(
                        connection, accessToken, uri, "Unable to get Zoho Books bill");
        Map<String, Object> invoice = objectMap(response.get("bill"));
        if (invoice.isEmpty()) {
            throw new ZohoBooksIntegrationException(
                    "Zoho Books bill response did not include bill");
        }
        return invoice;
    }

    private HttpRequest get(URI uri, String accessToken) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Zoho-oauthtoken " + accessToken)
                .GET()
                .build();
    }

    private Map<String, Object> sendAuthenticatedJson(
            ZohoBooksConnection connection,
            String accessTokenHint,
            URI uri,
            String failureMessage) {
        String currentAccessToken =
                isBlank(accessTokenHint) ? accessToken(connection) : accessTokenHint;
        CachedToken cachedToken = TOKEN_CACHE.get(tokenCacheKey(connection));
        if (cachedToken != null && cachedToken.isUsable()) {
            currentAccessToken = cachedToken.accessToken;
        }
        HttpResponse<String> response = send(get(uri, currentAccessToken), failureMessage);
        if (response.statusCode() == 401) {
            currentAccessToken = refreshAccessToken(connection);
            response = send(get(uri, currentAccessToken), failureMessage);
        }
        return readJson(response, failureMessage);
    }

    private Map<String, Object> sendJson(HttpRequest request, String failureMessage) {
        return readJson(send(request, failureMessage), failureMessage);
    }

    private HttpResponse<String> send(HttpRequest request, String failureMessage) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ZohoBooksIntegrationException(failureMessage, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ZohoBooksIntegrationException(failureMessage + " was interrupted", e);
        }
    }

    private Map<String, Object> readJson(HttpResponse<String> response, String failureMessage) {
        try {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ZohoBooksIntegrationException(
                        failureMessage + " with HTTP " + response.statusCode());
            }
            return OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
        } catch (IOException e) {
            throw new ZohoBooksIntegrationException(failureMessage, e);
        }
    }

    private String refreshAccessToken(ZohoBooksConnection connection) {
        String tokenCacheKey = tokenCacheKey(connection);
        Object lock = TOKEN_LOCKS.computeIfAbsent(tokenCacheKey, ignored -> new Object());
        synchronized (lock) {
            TOKEN_CACHE.remove(tokenCacheKey);
            return fetchAndCacheAccessToken(connection, tokenCacheKey);
        }
    }

    private List<Map<String, Object>> classifiedRecords(
            Map<String, Object> invoice,
            boolean grnRecords,
            String invoiceNumber,
            String invoiceId) {
        List<Map<String, Object>> records = new ArrayList<>();
        collectClassifiedRecords(invoice, grnRecords, records);
        records.replaceAll(record -> invoiceScopedRecord(record, invoiceNumber, invoiceId));
        return records;
    }

    private Map<String, Object> invoiceScopedRecord(
            Map<String, Object> record, String invoiceNumber, String invoiceId) {
        Map<String, Object> scopedRecord = new LinkedHashMap<>(record);
        scopedRecord.putIfAbsent("billNumber", invoiceNumber);
        scopedRecord.putIfAbsent("billId", invoiceId);
        scopedRecord.putIfAbsent("invoiceNumber", invoiceNumber);
        scopedRecord.putIfAbsent("invoiceId", invoiceId);
        return scopedRecord;
    }

    private void collectClassifiedRecords(
            Object value, boolean grnRecords, List<Map<String, Object>> records) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> record = normalizeMap((Map<?, ?>) value);
            String searchable = searchableText(record);
            if (grnRecords ? isGrn(searchable) : isPod(searchable)) {
                records.add(record);
            }
            record.values().forEach(child -> collectClassifiedRecords(child, grnRecords, records));
        } else if (value instanceof List<?>) {
            ((List<?>) value)
                    .forEach(child -> collectClassifiedRecords(child, grnRecords, records));
        }
    }

    private boolean isGrn(String value) {
        return value.contains("grn")
                || value.contains("goods received")
                || value.contains("goods receipt")
                || value.contains("goods receipt note");
    }

    private boolean isPod(String value) {
        return value.contains("pod")
                || value.contains("proof of delivery")
                || value.contains("delivery note")
                || value.contains("delivery challan")
                || value.contains("packing slip");
    }

    private String searchableText(Map<String, Object> record) {
        StringBuilder builder = new StringBuilder();
        record.forEach(
                (key, value) -> {
                    builder.append(' ').append(key);
                    if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
                        builder.append(' ').append(Objects.toString(value, ""));
                    }
                });
        return builder.toString().toLowerCase();
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return normalizeMap((Map<?, ?>) value);
        }
        return Map.of();
    }

    private List<Map<String, Object>> objectList(Object value) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map<?, ?>) {
                    records.add(normalizeMap((Map<?, ?>) item));
                }
            }
        }
        return records;
    }

    private Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
        return normalized;
    }

    private String form(Map<String, String> values) {
        List<String> params = new ArrayList<>();
        values.forEach((key, value) -> params.add(encode(key) + "=" + encode(value)));
        return String.join("&", params);
    }

    private Map<String, String> orderedParams(String... values) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            params.put(values[i], values[i + 1]);
        }
        return params;
    }

    private String tokenErrorMessage(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "empty response";
        }
        Map<String, Object> safeResponse = new HashMap<>(response);
        safeResponse.remove("access_token");
        safeResponse.remove("refresh_token");
        return safeResponse.toString();
    }

    private int integerValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !isBlank((String) value)) {
            return Integer.parseInt(((String) value).trim());
        }
        return fallback;
    }

    private static String environmentValue(String key, String fallback) {
        String value = System.getenv(key);
        return isBlank(value) ? fallback : value.trim();
    }

    private static String accountsUrlForRegion(String region) {
        return "https://accounts.zoho." + zohoDomainSuffix(region);
    }

    private static String booksApiUrlForRegion(String region) {
        return "https://www.zohoapis." + zohoDomainSuffix(region) + "/books/v3";
    }

    private static String zohoDomainSuffix(String region) {
        String normalized = isBlank(region) ? DEFAULT_REGION : region.trim().toLowerCase();
        return switch (normalized) {
            case "us", "com" -> "com";
            case "eu" -> "eu";
            case "in" -> "in";
            case "au", "com.au" -> "com.au";
            case "jp" -> "jp";
            case "ca" -> "ca";
            case "sa" -> "sa";
            default -> normalized;
        };
    }

    private static String optionalEnvironmentValue(String key) {
        String value = System.getenv(key);
        return blankToNull(value);
    }

    private String connectionClientId(ZohoBooksConnectionRequest request) {
        if (hasGlobalOAuthClient()) {
            return null;
        }
        return required(request.getClientId(), "Zoho Books clientId is required");
    }

    private String connectionClientSecret(ZohoBooksConnectionRequest request) {
        if (hasGlobalOAuthClient()) {
            return null;
        }
        return required(request.getClientSecret(), "Zoho Books clientSecret is required");
    }

    private OAuthClient oauthClient(ZohoBooksConnection connection) {
        String clientId = hasGlobalOAuthClient() ? globalClientId : connection.getClientId();
        String clientSecret =
                hasGlobalOAuthClient() ? globalClientSecret : connection.getClientSecret();
        if (isBlank(clientId) || isBlank(clientSecret)) {
            throw new ZohoBooksIntegrationException(
                    "Zoho Books OAuth client is required. Configure CONDUCTOR_ZOHO_BOOKS_CLIENT_ID "
                            + "and CONDUCTOR_ZOHO_BOOKS_CLIENT_SECRET, or save client credentials on the connection.");
        }
        return new OAuthClient(clientId.trim(), clientSecret.trim());
    }

    private boolean hasGlobalOAuthClient() {
        return !isBlank(globalClientId) && !isBlank(globalClientSecret);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private List<String> commaSeparatedValues(String value) {
        return Arrays.stream(required(value, "Zoho Books bill statuses are required").split(","))
                .map(String::trim)
                .filter(item -> !isBlank(item))
                .toList();
    }

    private static String required(String value, String message) {
        if (isBlank(value)) {
            throw new ZohoBooksIntegrationException(message);
        }
        return value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new ZohoBooksIntegrationException(
                    "Unable to create Zoho Books token cache key", e);
        }
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = required(value, "Zoho Books URL is required");
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static void clearTokenCacheForTests() {
        TOKEN_CACHE.clear();
        TOKEN_LOCKS.clear();
    }

    private static class CachedToken {
        private final String accessToken;
        private final long expiresAtMillis;

        private CachedToken(String accessToken, int expiresInSeconds) {
            this.accessToken = accessToken;
            this.expiresAtMillis =
                    System.currentTimeMillis() + (Math.max(1, expiresInSeconds) * 1000L);
        }

        private boolean isUsable() {
            return expiresAtMillis - System.currentTimeMillis()
                    > TOKEN_EXPIRY_BUFFER_SECONDS * 1000L;
        }
    }

    private record SearchFilter(String name, String value) {}

    private record OAuthClient(String clientId, String clientSecret) {}
}
