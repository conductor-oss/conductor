/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.controller;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.conductoross.conductor.dao.SecretsDAO;
import org.conductoross.conductor.model.secret.CredentialMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

/**
 * REST controller for secrets — Conductor-parity contract.
 *
 * <p>Mirrors {@code io.orkes.conductor.server.rest.SecretResource} (v1) and {@code
 * SecretResourceV2}. Auth: every endpoint requires a logged-in principal (the host's request filter
 * populates the request context).
 *
 * <ul>
 *   <li>{@code POST /api/secrets} — list names ({@code List<String>})
 *   <li>{@code GET /api/secrets} — list names user can grant access to ({@code Set<String>})
 *   <li>{@code GET /api/secrets/v2} — richer metadata (name, partial, timestamps)
 *   <li>{@code GET /api/secrets/{key}} — plaintext value (text/plain)
 *   <li>{@code PUT /api/secrets/{key}} — upsert; raw-string body (max 65535 chars)
 *   <li>{@code DELETE /api/secrets/{key}} — delete (200 OK, Conductor parity)
 *   <li>{@code GET /api/secrets/{key}/exists} — boolean
 * </ul>
 */
@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
public class SecretController {

    private static final Logger log = LoggerFactory.getLogger(SecretController.class);

    /** Mirrors Conductor's {@code SecretsService.ALLOWED_SECRET_NAME_PATTERN}. */
    static final String KEY_PATTERN = "[a-zA-Z0-9_-]+";

    static final int MAX_KEY_LENGTH = 65535;
    private static final Pattern KEY_REGEX = Pattern.compile(KEY_PATTERN);

    private final SecretsDAO secretsDAO;

    // ── List ──────────────────────────────────────────────────────────

    /** POST /api/secrets — list all secret names (Conductor's primary listing endpoint). */
    @PostMapping
    public ResponseEntity<List<String>> listAllNames() {
        return ResponseEntity.ok(secretsDAO.listSecretNames());
    }

    /**
     * GET /api/secrets — list names the caller can grant access to. Returns {@code Set<String>}
     * (Conductor parity — deduplication, no ordering guarantee). In OSS (no RBAC) this is the same
     * set as POST.
     */
    @GetMapping
    public ResponseEntity<Set<String>> listGrantable() {
        return ResponseEntity.ok(new LinkedHashSet<>(secretsDAO.listSecretNames()));
    }

    /**
     * GET /api/secrets/v2 — list with metadata (mirrors Conductor's SecretResourceV2). Returns name
     * + partial value + timestamps. Used by the CLI and UI.
     */
    @GetMapping("/v2")
    public ResponseEntity<List<CredentialMeta>> listWithMeta() {
        return ResponseEntity.ok(secretsDAO.listWithMeta());
    }

    // ── Value CRUD ────────────────────────────────────────────────────

    /** GET /api/secrets/{key} — plaintext value. */
    @GetMapping(value = "/{key}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getSecret(@PathVariable("key") String key) {
        ResponseEntity<?> err = validateKey(key);
        if (err != null) return ResponseEntity.status(err.getStatusCode()).build();
        String value = secretsDAO.getSecret(key);
        if (value == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(value);
    }

    /** PUT /api/secrets/{key} — upsert; body is the raw secret value (max 65535 chars). */
    @PutMapping(
            value = "/{key}",
            consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<?> putSecret(
            @PathVariable("key") String key, @RequestBody(required = false) String value) {
        ResponseEntity<?> err = validateKey(key);
        if (err != null) return err;
        if (value == null || value.isEmpty()) {
            return ResponseEntity.badRequest().body("value is required");
        }
        try {
            secretsDAO.putSecret(key, value);
            return ResponseEntity.ok().build();
        } catch (UnsupportedOperationException e) {
            return readOnlyBackend(e);
        }
    }

    /** DELETE /api/secrets/{key} — returns 200 OK (Conductor parity). */
    @DeleteMapping("/{key}")
    public ResponseEntity<?> deleteSecret(@PathVariable("key") String key) {
        ResponseEntity<?> err = validateKey(key);
        if (err != null) return err;
        try {
            secretsDAO.deleteSecret(key);
            return ResponseEntity.ok().build();
        } catch (UnsupportedOperationException e) {
            return readOnlyBackend(e);
        }
    }

    /** GET /api/secrets/{key}/exists. */
    @GetMapping(value = "/{key}/exists", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Boolean> exists(@PathVariable("key") String key) {
        ResponseEntity<?> err = validateKey(key);
        if (err != null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(secretsDAO.secretExists(key));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Validates a secret key against Conductor's name rules. Returns a 400 ResponseEntity if
     * invalid, null if valid.
     */
    private ResponseEntity<?> validateKey(String key) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body("key must not be blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            return ResponseEntity.badRequest().body("key must be at most 65535 characters");
        }
        if (!KEY_REGEX.matcher(key).matches()) {
            return ResponseEntity.badRequest()
                    .body(
                            "key must match pattern "
                                    + KEY_PATTERN
                                    + " (alphanumeric, underscore, dash only)");
        }
        return null;
    }

    /**
     * Environment and disabled secret stores are intentionally read-only. Surface that capability
     * limitation as a stable HTTP contract instead of leaking an internal 500 to SDK callers.
     */
    private ResponseEntity<String> readOnlyBackend(UnsupportedOperationException e) {
        log.info("Secret mutation rejected because the configured backend is read-only");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(e.getMessage());
    }
}
