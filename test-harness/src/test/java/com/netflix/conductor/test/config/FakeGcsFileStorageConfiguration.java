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
package com.netflix.conductor.test.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.conductoross.conductor.core.storage.FileStorage;
import org.conductoross.conductor.gcs.config.GcsFileStorageProperties;
import org.conductoross.conductor.gcs.storage.GcsFileStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

/**
 * Replaces the production file-storage GCS bean with a fake-gcs-server-pointed {@link FileStorage}.
 * The spec calls {@link #setEndpoint(String)} in {@code setupSpec} before the Spring context is
 * refreshed. GCS {@code signUrl()} requires a {@link ServiceAccountCredentials} with a private key,
 * so we generate a throwaway RSA keypair at startup — fake-gcs-server does not verify the
 * signature, only its structural presence.
 */
@TestConfiguration
@ConditionalOnProperty(name = "conductor.file-storage.type", havingValue = "gcs")
public class FakeGcsFileStorageConfiguration {

    private static String endpoint;

    public static void setEndpoint(String e) {
        endpoint = e;
    }

    @Bean
    @Primary
    public FileStorage fakeGcsFileStorage(GcsFileStorageProperties properties) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        ServiceAccountCredentials credentials =
                ServiceAccountCredentials.newBuilder()
                        .setClientEmail("fake@fake.iam.gserviceaccount.com")
                        .setPrivateKey(kp.getPrivate())
                        .setProjectId(properties.getProjectId())
                        // Return a canned access token so OAuth requests don't hit real Google.
                        .setHttpTransportFactory(() -> FAKE_OAUTH_TRANSPORT)
                        .build();
        StorageOptions.Builder builder =
                StorageOptions.newBuilder()
                        .setProjectId(properties.getProjectId())
                        .setCredentials(credentials);
        if (endpoint != null) {
            builder.setHost(endpoint);
        }
        Storage storage = builder.build().getService();
        if (storage.get(properties.getBucketName()) == null) {
            storage.create(BucketInfo.of(properties.getBucketName()));
        }
        return new GcsFileStorage(properties, storage);
    }

    private static final HttpTransport FAKE_OAUTH_TRANSPORT =
            new HttpTransport() {
                @Override
                protected LowLevelHttpRequest buildRequest(String method, String url) {
                    return new LowLevelHttpRequest() {
                        @Override
                        public void addHeader(String name, String value) {}

                        @Override
                        public LowLevelHttpResponse execute() {
                            return new LowLevelHttpResponse() {
                                private final byte[] body =
                                        "{\"access_token\":\"fake\",\"token_type\":\"Bearer\",\"expires_in\":3600}"
                                                .getBytes();

                                @Override
                                public InputStream getContent() {
                                    return new ByteArrayInputStream(body);
                                }

                                @Override
                                public String getContentEncoding() {
                                    return null;
                                }

                                @Override
                                public long getContentLength() {
                                    return body.length;
                                }

                                @Override
                                public String getContentType() {
                                    return "application/json";
                                }

                                @Override
                                public String getStatusLine() {
                                    return "HTTP/1.1 200 OK";
                                }

                                @Override
                                public int getStatusCode() {
                                    return 200;
                                }

                                @Override
                                public String getReasonPhrase() {
                                    return "OK";
                                }

                                @Override
                                public int getHeaderCount() {
                                    return 0;
                                }

                                @Override
                                public String getHeaderName(int index) {
                                    return null;
                                }

                                @Override
                                public String getHeaderValue(int index) {
                                    return null;
                                }
                            };
                        }
                    };
                }
            };
}
