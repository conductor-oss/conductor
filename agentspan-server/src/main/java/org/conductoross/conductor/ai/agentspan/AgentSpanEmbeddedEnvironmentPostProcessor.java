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
package org.conductoross.conductor.ai.agentspan;

import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Embeds the {@code conductor-agentspan} library in <b>embedded mode</b> whenever {@code
 * conductor.integrations.ai.enabled=true}, by setting {@code agentspan.embedded=true} early (before
 * condition evaluation).
 *
 * <p>This activates the library's {@code AgentSpanAutoConfiguration} (which registers the agent
 * runtime, services, REST controllers and the agent-aware {@code LLM_CHAT_COMPLETE} mapper) while
 * <b>disabling</b> its {@code HTTP}/{@code HUMAN}/{@code JOIN} task-bean overrides — those library
 * configs are gated on {@code agentspan.embedded=false}. Conductor's native system tasks therefore
 * remain the registered beans and are extended in place via the {@code __agentspan_ctx__} task
 * input (see {@code Join}/{@code Human}), mirroring how orkes-conductor embeds AgentSpan. This
 * avoids replacing core task beans (which would break {@code @Autowired} of the core task types and
 * require {@code spring.main.allow-bean-definition-overriding}).
 *
 * <p>Honors an explicit {@code agentspan.embedded} setting if one is already present.
 */
public class AgentSpanEmbeddedEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String AI_ENABLED = "conductor.integrations.ai.enabled";
    private static final String EMBEDDED = "agentspan.embedded";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        boolean aiEnabled = environment.getProperty(AI_ENABLED, Boolean.class, false);
        if (aiEnabled && !environment.containsProperty(EMBEDDED)) {
            environment
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "agentspanEmbedded",
                                    Collections.singletonMap(EMBEDDED, "true")));
        }
    }
}
