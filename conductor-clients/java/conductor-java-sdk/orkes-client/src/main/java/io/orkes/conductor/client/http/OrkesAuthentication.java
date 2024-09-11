/*
 * Copyright 2024 Orkes, Inc.
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
package io.orkes.conductor.client.http;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.HeaderSupplier;

import io.orkes.conductor.client.model.GenerateTokenRequest;
import io.orkes.conductor.client.model.TokenResponse;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class OrkesAuthentication implements HeaderSupplier {

    public static final String PROP_TOKEN_REFRESH_INTERVAL = "CONDUCTOR_SECURITY_TOKEN_REFRESH_INTERVAL";
    private static final Logger LOGGER = LoggerFactory.getLogger(OrkesAuthentication.class);
    private static final String TOKEN_CACHE_KEY = "TOKEN";
    private final ScheduledExecutorService tokenRefreshService = Executors.newSingleThreadScheduledExecutor(
            new BasicThreadFactory.Builder()
                    .namingPattern("OrkesAuthenticationSupplier Token Refresh %d")
                    .daemon(true)
                    .build());

    private final Cache<String, String> tokenCache;
    private final String keyId;
    private final String keySecret;
    private final long tokenRefreshInSeconds;

    private TokenResource tokenResource;

    public OrkesAuthentication(String keyId, String keySecret) {
        this(keyId, keySecret, 0);
    }

    public OrkesAuthentication(String keyId, String keySecret, long tokenRefreshInSeconds) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.tokenRefreshInSeconds = getTokenRefreshInSeconds(tokenRefreshInSeconds);
        this.tokenCache = CacheBuilder.newBuilder().expireAfterWrite(this.tokenRefreshInSeconds, TimeUnit.SECONDS).build();
        LOGGER.info("Setting token refresh interval to {} seconds", this.tokenRefreshInSeconds);
    }

    private long getTokenRefreshInSeconds(long tokenRefreshInSeconds) {
        if (tokenRefreshInSeconds == 0) {
            String refreshInterval = System.getenv(PROP_TOKEN_REFRESH_INTERVAL);
            if (refreshInterval == null) {
                refreshInterval = System.getProperty(PROP_TOKEN_REFRESH_INTERVAL);
            }

            if (refreshInterval != null) {
                try {
                    return Integer.parseInt(refreshInterval);
                } catch (Exception ignored) {
                }
            }

            return 2700; // 45 minutes
        }

        return tokenRefreshInSeconds;
    }

    @Override
    public void init(ConductorClient client) {
        this.tokenResource = new TokenResource(client);
        scheduleTokenRefresh();
        try {
            getToken();
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    @Override
    public Map<String, String> get(String method, String path) {
        if ("/token".equalsIgnoreCase(path)) {
            return Map.of();
        }

        return Map.of("X-Authorization", getToken());
    }

    public String getToken() {
        try {
            return tokenCache.get(TOKEN_CACHE_KEY, this::refreshToken);
        } catch (ExecutionException e) {
            return null;
        }
    }

    private void scheduleTokenRefresh() {
        long refreshInterval = Math.max(30, tokenRefreshInSeconds - 30); // why?
        LOGGER.info("Starting token refresh thread to run at every {} seconds", refreshInterval);
        tokenRefreshService.scheduleAtFixedRate(this::refreshToken, refreshInterval, refreshInterval, TimeUnit.SECONDS);
    }

    private String refreshToken() {
        LOGGER.debug("Refreshing token @ {}", Instant.now());
        if (keyId == null || keySecret == null) {
            throw new RuntimeException("KeyId and KeySecret must be set in order to get an authentication token");
        }

        GenerateTokenRequest generateTokenRequest = new GenerateTokenRequest(keyId, keySecret);
        TokenResponse response = tokenResource.generate(generateTokenRequest).getData();
        return response.getToken();
    }
}
