/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Captures whether AgentSpan is running embedded in a host (e.g. orkes-conductor) into a static
 * flag, so compile-time code reached through plain (non-Spring) helpers — e.g. {@code ToolCompiler}
 * — can branch on it. Populated once at startup from the {@code agentspan.embedded} property
 * (default {@code false} for the standalone server). Compilation is request-driven, so the value is
 * always set before any compile runs.
 */
@Component
public class EmbeddedMode {

    private static volatile boolean embedded = false;

    @Value("${agentspan.embedded:false}")
    public void setEmbedded(boolean value) {
        embedded = value;
    }

    public static boolean isEmbedded() {
        return embedded;
    }
}
