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
package org.conductoross.conductor.filestorage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.conductoross.conductor.core.storage.ConductorFileStorageProperties;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner.Operation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConductorFileSignatureFilterTest {

    private static final String SECRET = "test-secret";
    private static final long NOW = 1_800_000_000L;
    private static final long EXPIRATION = NOW + 60;

    private ConductorFileSignatureFilter filter;
    private FileStorageUrlSigner signer;

    @Before
    public void setUp() {
        signer = new FileStorageUrlSigner(signingProperties());
        filter =
                new ConductorFileSignatureFilter(
                        true, signer, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    @Test
    public void allowsValidUploadSignature() throws Exception {
        MockHttpServletRequest request =
                signedRequest("PUT", "upload", "wf-1", "file-1", EXPIRATION);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = new boolean[1];

        filter.doFilter(request, response, (req, res) -> invoked[0] = true);

        assertTrue(invoked[0]);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void rejectsUnsignedRequestWhenSigningEnabled() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("PUT", "/api/files/content/wf-1/file-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertEquals(403, response.getStatus());
    }

    @Test
    public void rejectsExpiredUnknownAndTamperedSignatures() throws Exception {
        assertForbidden(signedRequest("GET", "download", "wf-1", "file-1", NOW));

        MockHttpServletRequest unknownKey =
                signedRequest("GET", "download", "wf-1", "file-1", EXPIRATION);
        unknownKey.setParameter("kid", "unknown");
        assertForbidden(unknownKey);

        MockHttpServletRequest tampered =
                signedRequest("GET", "download", "wf-1", "file-1", EXPIRATION);
        tampered.setParameter("sig", "tampered");
        assertForbidden(tampered);
    }

    @Test
    public void rejectsOperationMethodMismatchWithoutInvokingChain() throws Exception {
        MockHttpServletRequest request =
                signedRequest("PUT", "download", "wf-1", "file-1", EXPIRATION);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = new boolean[1];

        filter.doFilter(request, response, (req, res) -> invoked[0] = true);

        assertEquals(405, response.getStatus());
        assertTrue(!invoked[0]);
    }

    @Test
    public void skipsNonContentRoutes() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/files/wf-1/file-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] invoked = new boolean[1];

        filter.doFilter(request, response, (req, res) -> invoked[0] = true);

        assertTrue(invoked[0]);
    }

    private MockHttpServletRequest signedRequest(
            String method, String operation, String workflowId, String fileId, long expiration) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        method, "/api/files/content/" + workflowId + "/" + fileId);
        request.setParameter("op", operation);
        request.setParameter("exp", Long.toString(expiration));
        request.setParameter("kid", "k1");
        request.setParameter(
                "sig",
                signer.sign(
                                Operation.fromValue(operation),
                                workflowId,
                                fileId,
                                expiration,
                                null,
                                null)
                        .getSignature());
        return request;
    }

    private ConductorFileStorageProperties.SigningProperties signingProperties() {
        ConductorFileStorageProperties.Key key = new ConductorFileStorageProperties.Key();
        key.setId("k1");
        key.setSecret(SECRET);

        ConductorFileStorageProperties.SigningProperties properties =
                new ConductorFileStorageProperties.SigningProperties();
        properties.setEnabled(true);
        properties.setKeys(List.of(key));
        return properties;
    }

    private void assertForbidden(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertEquals(403, response.getStatus());
    }
}
