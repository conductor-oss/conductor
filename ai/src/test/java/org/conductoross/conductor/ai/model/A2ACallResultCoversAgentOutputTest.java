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
package org.conductoross.conductor.ai.model;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.ai.agent.ConductorAgentResults;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AGENT worker returns this class rather than the TaskResult it built, so the task output is
 * whatever this class declares. An output key with no field here is dropped on the way out,
 * silently.
 *
 * <p>That cost real time once: executedTools and pendingTools never reached an execution, and
 * toolDispatchId not reaching it meant every poll started the agent's tools over again. Nothing in
 * the agent code could show the mistake, because the loss happens after it has finished.
 */
class A2ACallResultCoversAgentOutputTest {

    @Test
    void everyAgentOutputKeyHasAFieldToSurviveIn() throws Exception {
        List<String> declared = new ArrayList<>();
        for (Field field : A2ACallResult.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                declared.add(field.getName());
            }
        }

        List<String> missing = new ArrayList<>();
        for (Field key : ConductorAgentResults.class.getDeclaredFields()) {
            if (Modifier.isStatic(key.getModifiers())
                    && key.getType() == String.class
                    && key.getName().startsWith("KEY_")) {
                String outputKey = (String) key.get(null);
                if (!declared.contains(outputKey)) {
                    missing.add(key.getName() + " (\"" + outputKey + "\")");
                }
            }
        }

        assertThat(missing)
                .as(
                        "A2ACallResult must declare a field per agent output key, or the key never"
                                + " reaches the task output. It declares: %s",
                        declared)
                .isEmpty();
    }
}
