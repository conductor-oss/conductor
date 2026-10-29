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

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.netflix.conductor.core.events.ScriptEvaluator;

import static org.junit.jupiter.api.Assertions.*;

class GraalJSConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(GraalJSConfiguration.class);

    private final StringWriter logOutput = new StringWriter();
    private Logger logger;
    private Level previousLevel;
    private WriterAppender appender;

    @BeforeEach
    void captureStartupWarning() {
        logger = (Logger) LogManager.getLogger(ScriptEvaluator.class);
        previousLevel = logger.getLevel();
        appender =
                WriterAppender.newBuilder()
                        .setName("graaljs-startup-warning-test")
                        .setTarget(logOutput)
                        .setLayout(
                                PatternLayout.newBuilder().withPattern("%level %message%n").build())
                        .build();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
    }

    @AfterEach
    void restoreLogger() {
        if (logger != null) {
            if (appender != null) {
                logger.removeAppender(appender);
            }
            logger.setLevel(previousLevel);
        }
        if (appender != null) {
            appender.stop();
        }
    }

    @Test
    void hostAccessIsEnabledByDefaultAndWarnsAtStartup() {
        contextRunner.run(
                context -> {
                    assertNull(context.getStartupFailure());
                    // The warning must appear at startup, before any script is evaluated.
                    assertTransitionWarning();
                    Map<String, Object> input = new HashMap<>();
                    assertNull(ScriptEvaluator.eval("$.put('value', 1)", input));
                    assertEquals(1, input.get("value"));
                });
    }

    @Test
    void unrestrictedHostAccessCanBeExplicitlyRetained() {
        contextRunner
                .withPropertyValues("conductor.app.graaljs.restricted-host-access=false")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertTransitionWarning();
                            assertEquals(
                                    "java.util.HashMap",
                                    ScriptEvaluator.eval(
                                            "$.getClass().getName()", new HashMap<>()));
                        });
    }

    @Test
    void invalidHostAccessConfigurationFailsStartup() {
        contextRunner
                .withPropertyValues("conductor.app.graaljs.restricted-host-access=invalid")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    private void assertTransitionWarning() {
        String output = logOutput.toString();
        assertTrue(
                output.contains("WARN GraalJS unrestricted Java host access is enabled"), output);
        assertTrue(output.contains("will be disabled by default in a future release"), output);
        assertTrue(
                output.contains(
                        "explicitly set conductor.app.graaljs.restricted-host-access=false"),
                output);
    }
}
