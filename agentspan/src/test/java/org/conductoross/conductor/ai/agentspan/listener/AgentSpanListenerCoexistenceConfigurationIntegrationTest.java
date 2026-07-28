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

import org.conductoross.conductor.ai.agentspan.runtime.service.AgentEventListener;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentStreamRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.listener.TaskStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the coexistence configuration with AgentSpan's real primary listener and proves that the
 * composite becomes the injection target while both real listeners still receive callbacks.
 */
class AgentSpanListenerCoexistenceConfigurationIntegrationTest {

    @Test
    void embeddedContextDemotesAgentListenerAndSelectsCompositesAsPrimary() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("agentspan.embedded=true").applyTo(context);
            context.register(AgentSpanListenerCoexistenceConfiguration.class);
            context.registerBean(AgentStreamRegistry.class);
            context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
            context.registerBean(AgentEventListener.class);
            context.registerBean(RecordingPublisher.class);
            context.refresh();

            WorkflowStatusListener workflowListener = context.getBean(WorkflowStatusListener.class);
            TaskStatusListener taskListener = context.getBean(TaskStatusListener.class);
            RecordingPublisher publisher = context.getBean(RecordingPublisher.class);

            assertThat(workflowListener)
                    .isInstanceOf(AgentSpanCompositeWorkflowStatusListener.class);
            assertThat(taskListener).isInstanceOf(AgentSpanCompositeTaskStatusListener.class);
            assertThat(
                            context.getBeanFactory()
                                    .getBeanDefinition(
                                            context.getBeanNamesForType(AgentEventListener.class)[
                                                    0])
                                    .isPrimary())
                    .isFalse();

            workflowListener.onWorkflowCompletedIfEnabled(workflow());
            taskListener.onTaskCompletedIfEnabled(task());

            assertThat(publisher.workflowCompletions).isEqualTo(1);
            assertThat(publisher.taskCompletions).isEqualTo(1);
        }
    }

    @Test
    void nonEmbeddedContextDoesNotRegisterCoexistenceBeans() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("agentspan.embedded=false").applyTo(context);
            context.register(AgentSpanListenerCoexistenceConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AgentSpanCompositeWorkflowStatusListener.class))
                    .isEmpty();
            assertThat(context.getBeansOfType(AgentSpanCompositeTaskStatusListener.class))
                    .isEmpty();
        }
    }

    private static WorkflowModel workflow() {
        WorkflowDef definition = new WorkflowDef();
        definition.setName("coexistence-integration");
        definition.setWorkflowStatusListenerEnabled(true);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-id");
        workflow.setWorkflowDefinition(definition);
        return workflow;
    }

    private static TaskModel task() {
        TaskModel task = new TaskModel();
        task.setTaskId("task-id");
        task.setWorkflowInstanceId("workflow-id");
        task.setTaskType("SET_VARIABLE");
        task.setReferenceTaskName("set-variable");
        return task;
    }

    static final class RecordingPublisher implements WorkflowStatusListener, TaskStatusListener {
        int workflowCompletions;
        int taskCompletions;

        @Override
        public void onWorkflowCompleted(WorkflowModel workflow) {
            workflowCompletions++;
        }

        @Override
        public void onWorkflowTerminated(WorkflowModel workflow) {}

        @Override
        public void onTaskCompleted(TaskModel task) {
            taskCompletions++;
        }
    }
}
