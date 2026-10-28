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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.evaluators.ConsoleBridge;

import static org.junit.jupiter.api.Assertions.*;

/** Runs in its own test JVM because the evaluator's host-access policy is startup-only. */
public class GraalJSUnrestrictedHostAccessTest {

    @Test
    void yamlCanEnableUnrestrictedHostAccessBeforeBusinessBeansAreCreated() {
        new ApplicationContextRunner()
                .withInitializer(
                        context -> {
                            try {
                                new YamlPropertySourceLoader()
                                        .load(
                                                "unrestricted-graaljs",
                                                new ClassPathResource("graaljs/unrestricted.yml"))
                                        .forEach(
                                                source ->
                                                        context.getEnvironment()
                                                                .getPropertySources()
                                                                .addFirst(source));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                .withUserConfiguration(GraalJSConfiguration.class)
                .withBean(
                        "startupScriptResult",
                        String.class,
                        () ->
                                (String)
                                        ScriptEvaluator.eval(
                                                "$.object.readValue()",
                                                Map.of("object", new BoundHostObject())))
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertEquals("host-only", context.getBean("startupScriptResult"));
                            assertEquals(
                                    "host-only",
                                    ScriptEvaluator.eval(
                                            "$.object.value",
                                            Map.of("object", new BoundHostObject())));

                            ConsoleBridge console = new ConsoleBridge("unrestricted-task");
                            assertEquals(
                                    1,
                                    ScriptEvaluator.eval(
                                            "console.log('message'); console.logs().size()",
                                            Map.of(),
                                            console));

                            // The opt-in changes Java member access, not the other guest
                            // privileges.
                            assertThrows(
                                    TerminateWorkflowException.class,
                                    () ->
                                            ScriptEvaluator.eval(
                                                    "Java.type('java.lang.System')", Map.of()));
                            assertThrows(
                                    TerminateWorkflowException.class,
                                    () ->
                                            ScriptEvaluator.eval(
                                                    "load('file:///test.js')", Map.of()));
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> ScriptEvaluator.configureHostAccess(true));
                        });
    }

    public static class BoundHostObject {
        public final String value = "host-only";

        public String readValue() {
            return value;
        }
    }
}
