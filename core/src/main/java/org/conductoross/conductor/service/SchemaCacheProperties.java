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
package org.conductoross.conductor.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the schema registry's read-through cache.
 *
 * <p>The cache has properties of its own rather than borrowing another feature's: a cache in front
 * of a backend-agnostic registry should not be configured through properties namespaced to one
 * backend.
 */
@ConfigurationProperties("conductor.app.schema-cache")
public class SchemaCacheProperties {

    /**
     * How long an entry survives after it is written. Zero, the default, disables the cache
     * outright, so there is no separate on/off flag to disagree with it.
     *
     * <p>A non-zero value is also the bound on staleness: invalidation on save and delete only
     * reaches the node that served the write, so on every other node an entry stands until it
     * expires.
     */
    private Duration ttl = Duration.ZERO;

    /** Maximum number of cached entries, counting both by-version and latest-by-name lookups. */
    private int maxSize = 1000;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    /** The cache is on only when a time-to-live is configured. */
    public boolean isEnabled() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
