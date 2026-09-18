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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Skill storage is host-supplied, and not every backend has an implementation — a host may run
 * Postgres stores and nothing for MySQL, for example. Requiring it made the whole agent runtime
 * un-startable on such a backend, since {@link SkillRegistryService} took both stores as mandatory
 * constructor arguments. Skills are a capability, not a precondition for running an agent.
 */
class OptionalSkillStorageTest {

    @Configuration
    @Import(SkillRegistryService.class)
    static class NoSkillStorage {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withPropertyValues("conductor.integrations.ai.enabled=true")
                    .withUserConfiguration(NoSkillStorage.class);

    @Test
    void registryStartsWithNoSkillStorage() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(SkillRegistryService.class);
                    assertThat(ctx.getBean(SkillRegistryService.class).isSkillStorageAvailable())
                            .isFalse();
                });
    }

    /** "Which skills exist" has a correct answer without storage: none. */
    @Test
    void listDegradesToEmptyWithNoSkillStorage() {
        runner.run(ctx -> assertThat(ctx.getBean(SkillRegistryService.class).list(true)).isEmpty());
    }

    /** Anything needing the bytes fails loudly rather than with an NPE. */
    @Test
    void skillLookupFailsClearlyWithNoSkillStorage() {
        runner.run(
                ctx ->
                        assertThatThrownBy(
                                        () ->
                                                ctx.getBean(SkillRegistryService.class)
                                                        .get("some-skill", "1"))
                                .isInstanceOf(UnsupportedOperationException.class)
                                .hasMessageContaining("Skills are unavailable"));
    }
}
