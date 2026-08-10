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

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

/**
 * Configuration for the server-side file-storage feature. Bound to {@code
 * conductor.file-storage.*}. When {@code enabled=false}, no file-storage beans are created and the
 * {@code /api/files} endpoints return 404.
 */
@ConfigurationProperties("conductor.file-storage")
public class FileStorageProperties {

    /** Feature flag — entire file-storage feature gated on this. Default: {@code false}. */
    private boolean enabled = false;

    /** Storage backend selector: {@code local}, {@code s3}, {@code azure-blob}, {@code gcs}. */
    private String type = "local";

    /** Presigned URL TTL. Default: 60 seconds. */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration signedUrlExpiration = Duration.ofSeconds(60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Duration getSignedUrlExpiration() {
        return signedUrlExpiration;
    }

    public void setSignedUrlExpiration(Duration signedUrlExpiration) {
        this.signedUrlExpiration = signedUrlExpiration;
    }
}
