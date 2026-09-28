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
package org.conductoross.conductor.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Gates the A2A <b>server</b> (exposing Conductor workflows as A2A agents) on {@code
 * conductor.a2a.server.enabled=true}. Independent of the LLM/AI integration flag — the server side
 * only needs core workflow/metadata services.
 */
public class A2AServerEnabledCondition extends AllNestedConditions {
    public A2AServerEnabledCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(
            name = "conductor.a2a.server.enabled",
            havingValue = "true",
            matchIfMissing = false)
    static class A2AServerEnabled {}
}
