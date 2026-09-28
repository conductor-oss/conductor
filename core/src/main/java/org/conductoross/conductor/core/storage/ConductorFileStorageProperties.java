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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Configuration for the Conductor-managed file-storage backend. */
@Validated
@ConfigurationProperties("conductor.file-storage.conductor")
public class ConductorFileStorageProperties {

    private Path directory =
            Path.of(System.getProperty("java.io.tmpdir"), "conductor/files-uploaded");

    /** Optional public origin used when the inbound request does not identify it correctly. */
    private String baseUrl;

    /** Maximum accepted content size in bytes. */
    @Positive private long maxSize = 100L * 1024 * 1024;

    @Valid private SigningProperties signing = new SigningProperties();

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public SigningProperties getSigning() {
        return signing;
    }

    public void setSigning(SigningProperties signing) {
        this.signing = signing;
    }

    /** Optional HMAC signing configuration for content URLs. */
    public static class SigningProperties {

        private boolean enabled;
        private List<@Valid Key> keys = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<@Valid Key> getKeys() {
            return keys;
        }

        public void setKeys(List<@Valid Key> keys) {
            this.keys = keys == null ? new ArrayList<>() : new ArrayList<>(keys);
        }

        @AssertTrue(message = "at least one signing key is required when signing is enabled")
        public boolean isValid() {
            return !enabled || (keys != null && !keys.isEmpty());
        }

        /** Throws a startup-friendly error for unusable key configuration. */
        public void validate() {
            if (!enabled) {
                return;
            }
            if (keys == null || keys.isEmpty()) {
                throw new IllegalStateException(
                        "conductor.file-storage.conductor.signing.keys is required when signing is enabled");
            }
            Set<String> ids = new HashSet<>();
            for (Key key : keys) {
                if (key == null || isBlank(key.getId()) || isBlank(key.getSecret())) {
                    throw new IllegalStateException(
                            "Each conductor.file-storage.conductor.signing.keys entry requires id and secret");
                }
                if (!ids.add(key.getId())) {
                    throw new IllegalStateException(
                            "conductor.file-storage.conductor.signing.keys contains duplicate id: "
                                    + key.getId());
                }
            }
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    /** An ordered signing key. The first key signs new URLs; every key verifies existing URLs. */
    public static class Key {

        @NotBlank private String id;
        @NotBlank private String secret;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
