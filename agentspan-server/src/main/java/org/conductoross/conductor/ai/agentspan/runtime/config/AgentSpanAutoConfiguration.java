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
package org.conductoross.conductor.ai.agentspan.runtime.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Registers the AgentSpan beans by component-scanning the {@code
 * org.conductoross.conductor.ai.agentspan.runtime} namespace — agent domain, compilers, services,
 * controllers, system tasks, and whichever SPI implementations are present on the classpath.
 *
 * <p>Loaded via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so an
 * embedding host (e.g. the Conductor server, orkes-conductor) gets the beans without adding the
 * AgentSpan runtime packages to its own component scan.
 *
 * <p><b>Activation contract.</b> Merely having this library on the classpath must not change a host
 * application's behavior. The configuration activates only when the host explicitly opts in with
 * {@code agentspan.embedded=true} (the Conductor server derives this from {@code
 * conductor.integrations.ai.enabled}). Without the flag, the host runs as stock Conductor — no
 * AgentSpan beans, controllers, or system-task overrides are registered.
 *
 * <p>Excludes this class from the scan, since it is processed directly as an auto-configuration
 * rather than as a component-scanned candidate.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
@ComponentScan(
        basePackages = "org.conductoross.conductor.ai.agentspan.runtime",
        excludeFilters = {
            @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern =
                            "org\\.conductoross\\.conductor\\.ai\\.agentspan\\.runtime\\.config\\.AgentSpanAutoConfiguration")
        })
public class AgentSpanAutoConfiguration {}
