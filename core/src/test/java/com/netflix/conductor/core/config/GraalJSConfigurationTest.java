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
package com.netflix.conductor.core.config;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.exception.TerminateWorkflowException;

import static org.junit.jupiter.api.Assertions.*;

class GraalJSConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(GraalJSConfiguration.class);

    @Test
    void hostAccessIsRestrictedByDefault() {
        contextRunner.run(
                context -> {
                    assertNull(context.getStartupFailure());
                    assertThrows(
                            TerminateWorkflowException.class,
                            () -> ScriptEvaluator.eval("$.put('value', 1)", new HashMap<>()));
                });
    }

    @Test
    void restrictedHostAccessCanBeExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("conductor.app.graaljs.restricted-host-access=true")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertThrows(
                                    TerminateWorkflowException.class,
                                    () ->
                                            ScriptEvaluator.eval(
                                                    "$.put('value', 1)", new HashMap<>()));
                        });
    }

    @Test
    void invalidHostAccessConfigurationFailsStartup() {
        contextRunner
                .withPropertyValues("conductor.app.graaljs.restricted-host-access=invalid")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
