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
package com.netflix.conductor.postgres.dao;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.conductoross.conductor.dao.SecretsDAO;
import org.conductoross.conductor.model.secret.CredentialMeta;
import org.springframework.core.retry.RetryTemplate;

import tools.jackson.databind.ObjectMapper;

/**
 * PostgreSQL-backed SecretsDAO. Secrets are AES-256-GCM encrypted at rest using the key in
 * CONDUCTOR_SECRETS_ENCRYPTION_KEY env var (must be 32 bytes base64-encoded). Registered as a @Bean
 * in PostgresConfiguration when conductor.secrets.type=postgres.
 */
public class PostgresSecretsDAO extends PostgresBaseDAO implements SecretsDAO {

    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN = 128;

    private static final String UPSERT =
            "INSERT INTO conductor_secrets (name, value, created_at, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (name) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at";

    private static final String SELECT = "SELECT value FROM conductor_secrets WHERE name = ?";
    private static final String EXISTS = "SELECT 1 FROM conductor_secrets WHERE name = ?";
    private static final String LIST = "SELECT name FROM conductor_secrets ORDER BY name";
    private static final String DELETE = "DELETE FROM conductor_secrets WHERE name = ?";

    private static final SecureRandom DEV_SECURE_RANDOM = new SecureRandom();

    private final SecretKey encKey;

    public PostgresSecretsDAO(
            RetryTemplate retryTemplate,
            ObjectMapper objectMapper,
            DataSource dataSource,
            String encKeyB64) {
        super(retryTemplate, objectMapper, dataSource);
        this.encKey = resolveKey(encKeyB64);
    }

    // ── SecretsDAO ────────────────────────────────────────────────────────────

    @Override
    public String getSecret(String name) {
        String encrypted =
                queryWithTransaction(
                        SELECT, q -> q.addParameter(name).executeAndFetchFirst(String.class));
        return encrypted == null ? null : decrypt(encrypted);
    }

    @Override
    public boolean secretExists(String name) {
        Integer hit =
                queryWithTransaction(
                        EXISTS, q -> q.addParameter(name).executeAndFetchFirst(Integer.class));
        return hit != null;
    }

    @Override
    public List<String> listSecretNames() {
        return queryWithTransaction(LIST, q -> q.executeAndFetch(String.class));
    }

    @Override
    public void putSecret(String name, String value) {
        long now = System.currentTimeMillis();
        executeWithTransaction(
                UPSERT,
                q ->
                        q.addParameter(name)
                                .addParameter(encrypt(value))
                                .addParameter(now)
                                .addParameter(now)
                                .executeUpdate());
    }

    @Override
    public void deleteSecret(String name) {
        executeWithTransaction(DELETE, q -> q.addParameter(name).executeUpdate());
    }

    @Override
    public List<CredentialMeta> listWithMeta() {
        return queryWithTransaction(
                "SELECT name, value FROM conductor_secrets ORDER BY name",
                q ->
                        q.executeAndFetch(
                                rs -> {
                                    List<CredentialMeta> result = new ArrayList<>();
                                    while (rs.next()) {
                                        String name = rs.getString("name");
                                        String plain = decrypt(rs.getString("value"));
                                        result.add(
                                                CredentialMeta.builder()
                                                        .name(name)
                                                        .partial(maskPartial(plain))
                                                        .build());
                                    }
                                    return result;
                                }));
    }

    // ── Encryption helpers ────────────────────────────────────────────────────

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined =
                    ByteBuffer.allocate(iv.length + ciphertext.length)
                            .put(iv)
                            .put(ciphertext)
                            .array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt secret", e);
        }
    }

    private String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LEN);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LEN, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt secret", e);
        }
    }

    private static SecretKey resolveKey(String b64) {
        if (b64 == null || b64.isBlank()) {
            // Dev-mode fallback: ephemeral random key (NOT for production — secrets cannot be
            // decrypted after restart). Set CONDUCTOR_SECRETS_ENCRYPTION_KEY in production.
            org.slf4j.LoggerFactory.getLogger(PostgresSecretsDAO.class)
                    .warn(
                            "CONDUCTOR_SECRETS_ENCRYPTION_KEY not set — using ephemeral dev-mode key. "
                                    + "Set a 32-byte base64 key in production.");
            byte[] devKey = new byte[32];
            DEV_SECURE_RANDOM.nextBytes(devKey);
            return new SecretKeySpec(devKey, "AES");
        }
        byte[] raw = Base64.getDecoder().decode(b64);
        if (raw.length != 32) {
            throw new IllegalArgumentException(
                    "CONDUCTOR_SECRETS_ENCRYPTION_KEY must be exactly 32 bytes (256 bits) when base64-decoded, got "
                            + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    private static String maskPartial(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.length() <= 8) return "...";
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
