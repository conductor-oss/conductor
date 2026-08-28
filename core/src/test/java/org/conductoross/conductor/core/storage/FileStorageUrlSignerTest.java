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

import java.time.Instant;
import java.util.List;

import org.conductoross.conductor.core.storage.FileStorageUrlSigner.Operation;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner.SignedUrl;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner.VerificationResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FileStorageUrlSignerTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_000);
    private static final long EXPIRATION = 2_000;

    @Test
    public void signsAndVerifiesCanonicalContentRequest() {
        FileStorageUrlSigner signer = signer(key("current", "current-secret"));
        SignedUrl signedUrl =
                signer.sign(Operation.UPLOAD, "workflow", "file", EXPIRATION, "upload", 1);

        assertEquals(
                VerificationResult.VALID,
                signer.verify(
                        Operation.UPLOAD,
                        "workflow",
                        "file",
                        EXPIRATION,
                        "upload",
                        1,
                        signedUrl.getKeyId(),
                        signedUrl.getSignature(),
                        NOW));
    }

    @Test
    public void rejectsTamperedSignedFieldsAndExpiredUrls() {
        FileStorageUrlSigner signer = signer(key("current", "current-secret"));
        SignedUrl signedUrl =
                signer.sign(Operation.DOWNLOAD, "workflow", "file", EXPIRATION, null, null);

        assertEquals(
                VerificationResult.INVALID,
                signer.verify(
                        Operation.DOWNLOAD,
                        "other-workflow",
                        "file",
                        EXPIRATION,
                        null,
                        null,
                        signedUrl.getKeyId(),
                        signedUrl.getSignature(),
                        NOW));
        assertEquals(
                VerificationResult.EXPIRED,
                signer.verify(
                        Operation.DOWNLOAD,
                        "workflow",
                        "file",
                        EXPIRATION,
                        null,
                        null,
                        signedUrl.getKeyId(),
                        signedUrl.getSignature(),
                        Instant.ofEpochSecond(EXPIRATION)));
    }

    @Test
    public void usesFirstKeyForSigningAndAllKeysForVerification() {
        FileStorageUrlSigner oldSigner = signer(key("old", "old-secret"));
        SignedUrl oldUrl =
                oldSigner.sign(Operation.DOWNLOAD, "workflow", "file", EXPIRATION, null, null);
        FileStorageUrlSigner rotatedSigner =
                signer(key("new", "new-secret"), key("old", "old-secret"));

        assertEquals(
                VerificationResult.VALID,
                rotatedSigner.verify(
                        Operation.DOWNLOAD,
                        "workflow",
                        "file",
                        EXPIRATION,
                        null,
                        null,
                        oldUrl.getKeyId(),
                        oldUrl.getSignature(),
                        NOW));
        assertEquals(
                "new",
                rotatedSigner
                        .sign(Operation.DOWNLOAD, "workflow", "file", EXPIRATION, null, null)
                        .getKeyId());
    }

    @Test
    public void validatesOperationAgainstHttpMethod() {
        FileStorageUrlSigner signer = signer(key("current", "current-secret"));

        assertEquals(VerificationResult.VALID, signer.verifyOperation("upload", "PUT"));
        assertEquals(
                VerificationResult.METHOD_NOT_ALLOWED, signer.verifyOperation("download", "PUT"));
        assertEquals(VerificationResult.INVALID, signer.verifyOperation("unknown", "GET"));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsEnabledSigningWithoutKeys() {
        ConductorFileStorageProperties.SigningProperties properties =
                new ConductorFileStorageProperties.SigningProperties();
        properties.setEnabled(true);

        new FileStorageUrlSigner(properties);
    }

    private FileStorageUrlSigner signer(ConductorFileStorageProperties.Key... keys) {
        ConductorFileStorageProperties.SigningProperties properties =
                new ConductorFileStorageProperties.SigningProperties();
        properties.setEnabled(true);
        properties.setKeys(List.of(keys));
        return new FileStorageUrlSigner(properties);
    }

    private ConductorFileStorageProperties.Key key(String id, String secret) {
        ConductorFileStorageProperties.Key key = new ConductorFileStorageProperties.Key();
        key.setId(id);
        key.setSecret(secret);
        return key;
    }
}
