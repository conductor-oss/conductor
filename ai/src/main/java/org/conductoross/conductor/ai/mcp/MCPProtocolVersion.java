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
package org.conductoross.conductor.ai.mcp;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The MCP protocol versions Conductor speaks, oldest first, each with what it supports. Code asks a
 * version what it supports instead of comparing version strings; a new version is a new constant.
 */
public enum MCPProtocolVersion {
    /** No {@code MCP-Protocol-Version} header and no {@code structuredContent}. */
    V2025_03_26("2025-03-26", true, false, false),
    /**
     * Adds the {@code MCP-Protocol-Version} header, {@code structuredContent} and {@code
     * outputSchema}.
     */
    V2025_06_18("2025-06-18", true, true, true),
    V2025_11_25("2025-11-25", true, true, true),
    /**
     * Stateless requests with {@code _meta}, {@code resultType}, the Tasks extension and {@code
     * Mcp-Param-*} headers; no {@code initialize}.
     */
    V2026_07_28("2026-07-28", false, true, true);

    private final String value;
    private final boolean initialize;
    private final boolean versionHeader;
    private final boolean structuredContent;

    MCPProtocolVersion(
            String value, boolean initialize, boolean versionHeader, boolean structuredContent) {
        this.value = value;
        this.initialize = initialize;
        this.versionHeader = versionHeader;
        this.structuredContent = structuredContent;
    }

    /** The version as written on the wire, such as {@code 2026-07-28}. */
    public String value() {
        return value;
    }

    /**
     * Whether a client reaches this version with {@code initialize} and the older request form: no
     * {@code _meta}, no {@code resultType}, no Tasks, no {@code Mcp-Param-*} headers.
     */
    public boolean isLegacy() {
        return initialize;
    }

    /** Whether a client of this version sends the {@code MCP-Protocol-Version} header. */
    public boolean sendsVersionHeader() {
        return versionHeader;
    }

    /**
     * Whether tool results may carry {@code structuredContent} and tools an {@code outputSchema}.
     */
    public boolean hasStructuredContent() {
        return structuredContent;
    }

    /** The version with this value, if Conductor speaks it. */
    public static Optional<MCPProtocolVersion> of(String value) {
        return Arrays.stream(values()).filter(v -> v.value.equals(value)).findFirst();
    }

    /** The newest version: the one the stateless request form uses. */
    public static MCPProtocolVersion latest() {
        return values()[values().length - 1];
    }

    /** The newest version served through {@code initialize}: its answer when asked for another. */
    public static MCPProtocolVersion latestLegacy() {
        return Lists.LEGACY.get(0);
    }

    /** The oldest legacy version that sends no version header: a legacy request without one. */
    public static MCPProtocolVersion legacyWithoutVersionHeader() {
        return Lists.LEGACY_WITHOUT_VERSION_HEADER;
    }

    /**
     * The stateless version with this value, if Conductor speaks it: not one reached with
     * initialize.
     */
    public static Optional<MCPProtocolVersion> stateless(String value) {
        return of(value).filter(v -> !v.isLegacy());
    }

    /** The stateless versions, newest first. */
    public static List<MCPProtocolVersion> stateless() {
        return Lists.STATELESS;
    }

    /** The versions served through {@code initialize}, newest first. */
    public static List<MCPProtocolVersion> legacy() {
        return Lists.LEGACY;
    }

    /** Every version, newest first. */
    public static List<MCPProtocolVersion> newestFirst() {
        return Lists.NEWEST_FIRST;
    }

    /** The version lists, built once: the constants never change. */
    private static final class Lists {
        static final List<MCPProtocolVersion> NEWEST_FIRST =
                Arrays.stream(values())
                        .sorted(Comparator.comparingInt(MCPProtocolVersion::ordinal).reversed())
                        .toList();
        static final List<MCPProtocolVersion> STATELESS =
                NEWEST_FIRST.stream().filter(v -> !v.isLegacy()).toList();
        static final List<MCPProtocolVersion> LEGACY =
                NEWEST_FIRST.stream().filter(MCPProtocolVersion::isLegacy).toList();
        static final MCPProtocolVersion LEGACY_WITHOUT_VERSION_HEADER =
                Arrays.stream(values())
                        .filter(v -> v.initialize && !v.versionHeader)
                        .findFirst()
                        .orElseThrow();

        private Lists() {}
    }

    @Override
    public String toString() {
        return value;
    }
}
