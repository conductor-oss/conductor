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
package org.conductoross.conductor.ai.agentspan.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;

/**
 * Makes the embedded {@code conductor-agentspan} status listener coexist with any
 * operator-configured status publisher.
 *
 * <p>The agentspan library registers {@code AgentEventListener} as a {@code @Primary} bean for both
 * {@link WorkflowStatusListener} and {@link TaskStatusListener} (it streams agent events over SSE).
 * Conductor, however, injects a <b>single</b> listener bean of each type, so {@code @Primary} alone
 * would make agentspan's listener silently shadow a configured {@code queue_publisher} / {@code
 * kafka} / {@code archive} / {@code workflow_publisher} (or {@code task_publisher}) — and {@code
 * conductor.integrations.ai.enabled} defaults to {@code true}, so this would affect default
 * servers.
 *
 * <p>Active only in embedded mode ({@code agentspan.embedded=true}, set by {@link
 * org.conductoross.conductor.ai.agentspan.AgentSpanEmbeddedEnvironmentPostProcessor}), this
 * configuration:
 *
 * <ol>
 *   <li>demotes agentspan's {@code AgentEventListener} from {@code @Primary} (via a {@link
 *       BeanFactoryPostProcessor} that edits the bean definition before instantiation), and
 *   <li>registers composite {@code @Primary} listeners ({@link
 *       AgentSpanCompositeWorkflowStatusListener}, {@link AgentSpanCompositeTaskStatusListener})
 *       that fan every callback out to <i>all</i> registered listeners — agentspan's listener and
 *       the configured publisher alike.
 * </ol>
 *
 * <p>The composite becomes the sole {@code @Primary} candidate, so the single-bean injection points
 * in {@code WorkflowExecutorOps}/{@code ExecutionService} resolve to it, and every listener
 * receives every event.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "agentspan.embedded", havingValue = "true")
public class AgentSpanListenerCoexistenceConfiguration {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AgentSpanListenerCoexistenceConfiguration.class);

    /**
     * Fully-qualified name of the agentspan listener. Matched by name rather than imported type to
     * avoid coupling to a library-internal class and to keep the post-processor free of side
     * effects (no premature class loading).
     */
    private static final String AGENT_EVENT_LISTENER_CLASS =
            "dev.agentspan.runtime.service.AgentEventListener";

    /**
     * Demotes agentspan's {@code @Primary AgentEventListener} to an ordinary candidate so the
     * composite beans below can become the sole {@code @Primary} listeners. Declared {@code static}
     * so the post-processor is instantiated early (before regular bean instantiation) without
     * forcing the enclosing configuration to initialise prematurely. Runs after {@code
     * ConfigurationClassPostProcessor} (which registers agentspan's component-scanned definition),
     * so the target definition is present; it is a no-op if agentspan is absent or already
     * non-primary.
     */
    @Bean
    public static BeanFactoryPostProcessor agentSpanListenerPrimaryDemoter() {
        return beanFactory -> {
            for (String name : beanFactory.getBeanDefinitionNames()) {
                BeanDefinition definition = beanFactory.getBeanDefinition(name);
                if (AGENT_EVENT_LISTENER_CLASS.equals(definition.getBeanClassName())
                        && definition.isPrimary()) {
                    definition.setPrimary(false);
                    LOGGER.info(
                            "Demoted agentspan listener bean '{}' from @Primary so workflow/task "
                                    + "status listeners can coexist via the composite",
                            name);
                }
            }
        };
    }

    @Bean
    @Primary
    public WorkflowStatusListener agentSpanCompositeWorkflowStatusListener(
            ObjectProvider<WorkflowStatusListener> workflowStatusListeners) {
        return new AgentSpanCompositeWorkflowStatusListener(workflowStatusListeners);
    }

    @Bean
    @Primary
    public TaskStatusListener agentSpanCompositeTaskStatusListener(
            ObjectProvider<TaskStatusListener> taskStatusListeners) {
        return new AgentSpanCompositeTaskStatusListener(taskStatusListeners);
    }
}
