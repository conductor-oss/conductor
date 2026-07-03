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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the fan-out semantics that let agentspan's listener coexist with a configured publisher:
 * the composite broadcasts to every other listener, excludes itself (no recursion), isolates
 * failures, and preserves each delegate's own {@code *IfEnabled} gating.
 */
class AgentSpanCompositeWorkflowStatusListenerTest {

    /** Real listener that records which callbacks it received. */
    private static final class RecordingListener implements WorkflowStatusListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onWorkflowCompleted(WorkflowModel workflow) {
            events.add("completed:" + workflow.getWorkflowId());
        }

        @Override
        public void onWorkflowTerminated(WorkflowModel workflow) {
            events.add("terminated:" + workflow.getWorkflowId());
        }
    }

    /** Listener that always throws, to prove failures are isolated from siblings. */
    private static final class ThrowingListener implements WorkflowStatusListener {
        boolean invoked = false;

        @Override
        public void onWorkflowCompleted(WorkflowModel workflow) {
            invoked = true;
            throw new RuntimeException("boom");
        }

        @Override
        public void onWorkflowTerminated(WorkflowModel workflow) {
            throw new RuntimeException("boom");
        }
    }

    /**
     * Minimal {@link ObjectProvider} backed by a mutable list. The composite only ever calls {@link
     * ObjectProvider#stream()}, so the remaining methods are intentionally unsupported.
     */
    private static ObjectProvider<WorkflowStatusListener> providerOf(
            List<WorkflowStatusListener> registry) {
        return new ObjectProvider<>() {
            @Override
            public Stream<WorkflowStatusListener> stream() {
                return registry.stream();
            }

            @Override
            public WorkflowStatusListener getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public WorkflowStatusListener getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public WorkflowStatusListener getIfAvailable() {
                throw new UnsupportedOperationException();
            }

            @Override
            public WorkflowStatusListener getIfUnique() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static WorkflowModel workflow(String id, boolean listenerEnabled) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(id);
        WorkflowDef def = new WorkflowDef();
        def.setName("test_wf");
        def.setWorkflowStatusListenerEnabled(listenerEnabled);
        workflow.setWorkflowDefinition(def);
        return workflow;
    }

    @Test
    void fansOutToAllDelegatesAndExcludesItself() {
        List<WorkflowStatusListener> registry = new ArrayList<>();
        AgentSpanCompositeWorkflowStatusListener composite =
                new AgentSpanCompositeWorkflowStatusListener(providerOf(registry));
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        // Include the composite itself to prove it is filtered out (otherwise this would recurse
        // infinitely and overflow the stack).
        registry.add(composite);
        registry.add(first);
        registry.add(second);

        composite.onWorkflowCompleted(workflow("wf-1", true));

        assertEquals(List.of("completed:wf-1"), first.events);
        assertEquals(List.of("completed:wf-1"), second.events);
    }

    @Test
    void isolatesFailingDelegateFromTheRest() {
        List<WorkflowStatusListener> registry = new ArrayList<>();
        AgentSpanCompositeWorkflowStatusListener composite =
                new AgentSpanCompositeWorkflowStatusListener(providerOf(registry));
        RecordingListener before = new RecordingListener();
        ThrowingListener throwing = new ThrowingListener();
        RecordingListener after = new RecordingListener();
        registry.add(composite);
        registry.add(before);
        registry.add(throwing);
        registry.add(after);

        // Must not propagate the delegate's exception.
        composite.onWorkflowCompleted(workflow("wf-2", true));

        assertTrue(throwing.invoked);
        assertEquals(List.of("completed:wf-2"), before.events);
        assertEquals(List.of("completed:wf-2"), after.events);
    }

    @Test
    void ifEnabledPathHonoursEachDelegateGating() {
        List<WorkflowStatusListener> registry = new ArrayList<>();
        AgentSpanCompositeWorkflowStatusListener composite =
                new AgentSpanCompositeWorkflowStatusListener(providerOf(registry));
        RecordingListener listener = new RecordingListener();
        registry.add(composite);
        registry.add(listener);

        // Delegate uses the interface default *IfEnabled gating: enabled -> delivered.
        composite.onWorkflowTerminatedIfEnabled(workflow("wf-on", true));
        // Disabled -> the delegate's own gating suppresses delivery.
        composite.onWorkflowTerminatedIfEnabled(workflow("wf-off", false));

        assertEquals(List.of("terminated:wf-on"), listener.events);
        assertFalse(listener.events.contains("terminated:wf-off"));
    }
}
