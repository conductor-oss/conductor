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
package org.conductoross.conductor.core.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs and verifies version 1 Conductor file-content URLs. Origins are intentionally excluded from
 * the canonical value so proxy host rewriting does not invalidate a URL.
 */
public class FileStorageUrlSigner {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String VERSION = "v1";

    private final ConductorFileStorageProperties.SigningProperties properties;
    private final Map<String, ConductorFileStorageProperties.Key> keysById;

    public FileStorageUrlSigner(ConductorFileStorageProperties.SigningProperties properties) {
        this.properties = properties;
        properties.validate();
        this.keysById =
                properties.getKeys().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        ConductorFileStorageProperties.Key::getId,
                                        Function.identity()));
    }

    /** Signs a content request using the first configured key, the active key during rotation. */
    public SignedUrl sign(
            Operation operation,
            String workflowId,
            String fileId,
            long expirationEpochSeconds,
            String uploadId,
            Integer partNumber) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("File-storage URL signing is not enabled");
        }

        ConductorFileStorageProperties.Key signingKey = properties.getKeys().get(0);
        return new SignedUrl(
                signingKey.getId(),
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                hmac(
                                        canonicalize(
                                                operation,
                                                workflowId,
                                                fileId,
                                                expirationEpochSeconds,
                                                uploadId,
                                                partNumber),
                                        signingKey)));
    }

    /**
     * Verifies key identity, expiration, and HMAC in constant time. The caller supplies the
     * operation derived from the request method to enforce upload/download separation.
     */
    public VerificationResult verify(
            Operation operation,
            String workflowId,
            String fileId,
            long expirationEpochSeconds,
            String uploadId,
            Integer partNumber,
            String keyId,
            String signature,
            Instant now) {
        if (!properties.isEnabled()
                || operation == null
                || isBlank(keyId)
                || isBlank(signature)
                || now == null) {
            return VerificationResult.INVALID;
        }
        if (expirationEpochSeconds <= now.getEpochSecond()) {
            return VerificationResult.EXPIRED;
        }

        ConductorFileStorageProperties.Key key = keysById.get(keyId);
        if (key == null) {
            return VerificationResult.UNKNOWN_KEY;
        }

        try {
            byte[] supplied = Base64.getUrlDecoder().decode(signature);
            byte[] expected =
                    hmac(
                            canonicalize(
                                    operation,
                                    workflowId,
                                    fileId,
                                    expirationEpochSeconds,
                                    uploadId,
                                    partNumber),
                            key);
            return MessageDigest.isEqual(expected, supplied)
                    ? VerificationResult.VALID
                    : VerificationResult.INVALID;
        } catch (IllegalArgumentException exception) {
            return VerificationResult.INVALID;
        }
    }

    /** Validates that the signed operation matches the HTTP transfer operation. */
    public VerificationResult verifyOperation(String operation, String httpMethod) {
        Operation signedOperation = Operation.fromValue(operation);
        if (signedOperation == null) {
            return VerificationResult.INVALID;
        }
        return signedOperation.matchesHttpMethod(httpMethod)
                ? VerificationResult.VALID
                : VerificationResult.METHOD_NOT_ALLOWED;
    }

    private String canonicalize(
            Operation operation,
            String workflowId,
            String fileId,
            long expirationEpochSeconds,
            String uploadId,
            Integer partNumber) {
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }
        return String.join(
                "\n",
                VERSION,
                operation.getValue(),
                nullToEmpty(workflowId),
                nullToEmpty(fileId),
                String.valueOf(expirationEpochSeconds),
                nullToEmpty(uploadId),
                partNumber == null ? "" : String.valueOf(partNumber));
    }

    private byte[] hmac(String canonical, ConductorFileStorageProperties.Key key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(
                    new SecretKeySpec(
                            key.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign file-storage URL", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Operations encoded into signed content URLs. */
    public enum Operation {
        UPLOAD("upload", "PUT"),
        DOWNLOAD("download", "GET");

        private final String value;
        private final String httpMethod;

        Operation(String value, String httpMethod) {
            this.value = value;
            this.httpMethod = httpMethod;
        }

        public String getValue() {
            return value;
        }

        public boolean matchesHttpMethod(String candidate) {
            return httpMethod.equalsIgnoreCase(candidate);
        }

        public static Operation fromValue(String value) {
            for (Operation operation : values()) {
                if (operation.value.equals(value)) {
                    return operation;
                }
            }
            return null;
        }
    }

    /** Query parameters to append to a signed content URL. */
    public static class SignedUrl {

        private final String keyId;
        private final String signature;

        public SignedUrl(String keyId, String signature) {
            this.keyId = keyId;
            this.signature = signature;
        }

        public String getKeyId() {
            return keyId;
        }

        public String getSignature() {
            return signature;
        }
    }

    /** Signature validation result for mapping by the HTTP transfer layer. */
    public enum VerificationResult {
        VALID,
        INVALID,
        EXPIRED,
        UNKNOWN_KEY,
        METHOD_NOT_ALLOWED
    }
}
