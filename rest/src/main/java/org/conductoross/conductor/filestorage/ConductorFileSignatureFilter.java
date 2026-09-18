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

import java.io.IOException;
import java.time.Clock;

import org.conductoross.conductor.core.storage.FileStorageUrlSigner;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner.Operation;
import org.conductoross.conductor.core.storage.FileStorageUrlSigner.VerificationResult;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Verifies optional signed URLs only for raw Conductor file-content transfers. */
public class ConductorFileSignatureFilter extends OncePerRequestFilter {

    private static final String CONTENT_PATH_PREFIX = "/api/files/content/";

    private final boolean enabled;
    private final Clock clock;
    private final FileStorageUrlSigner signer;

    public ConductorFileSignatureFilter(boolean enabled, FileStorageUrlSigner signer) {
        this(enabled, signer, Clock.systemUTC());
    }

    ConductorFileSignatureFilter(boolean enabled, FileStorageUrlSigner signer, Clock clock) {
        this.enabled = enabled;
        this.signer = signer;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(CONTENT_PATH_PREFIX)) {
            return true;
        }
        String remainder = path.substring(CONTENT_PATH_PREFIX.length());
        String[] segments = remainder.split("/", -1);
        return segments.length != 2 || segments[0].isBlank() || segments[1].isBlank();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String operation = request.getParameter("op");
        String expiration = request.getParameter("exp");
        String keyId = request.getParameter("kid");
        String signature = request.getParameter("sig");
        if (operation == null || expiration == null || keyId == null || signature == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        long expirationEpochSeconds;
        try {
            expirationEpochSeconds = Long.parseLong(expiration);
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String[] pathSegments = contentPathSegments(request);
        VerificationResult verification =
                signer.verify(
                        Operation.fromValue(operation),
                        pathSegments[0],
                        pathSegments[1],
                        expirationEpochSeconds,
                        null,
                        null,
                        keyId,
                        signature,
                        clock.instant());
        if (verification != VerificationResult.VALID) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (signer.verifyOperation(operation, request.getMethod())
                == VerificationResult.METHOD_NOT_ALLOWED) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String[] contentPathSegments(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.substring(CONTENT_PATH_PREFIX.length()).split("/", -1);
    }
}
