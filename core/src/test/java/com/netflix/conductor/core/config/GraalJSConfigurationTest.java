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
import com.netflix.conductor.core.exception.TerminateWorkflowException;

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
    void hostAccessIsRestrictedByDefaultAndDoesNotWarn() {
        contextRunner.run(
                context -> {
                    assertNull(context.getStartupFailure());
                    Map<String, Object> input = new HashMap<>();
                    input.put("value", 1);
                    // Data reads remain available under the restricted default.
                    assertEquals(1, ScriptEvaluator.eval("$.get('value')", input));
                    // Arbitrary host method invocation (here, Map mutation) is not exposed.
                    assertThrows(
                            TerminateWorkflowException.class,
                            () -> ScriptEvaluator.eval("$.put('other', 2)", input));
                    // The unrestricted opt-out warning must NOT fire when restricted is in effect.
                    assertFalse(
                            logOutput
                                    .toString()
                                    .contains("GraalJS unrestricted Java host access is enabled"),
                            logOutput.toString());
                });
    }

    @Test
    void invalidHostAccessConfigurationFailsStartup() {
        contextRunner
                .withPropertyValues("conductor.app.graaljs.restricted-host-access=invalid")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
