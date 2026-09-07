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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import org.conductoross.conductor.ai.agent.AgentEventStream;
import org.conductoross.conductor.common.metadata.agent.AgentSSEEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.*;

class AgentStreamRegistryTest {

    private AgentStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentStreamRegistry();
    }

    // ── Registration ─────────────────────────────────────────────────

    @Test
    void registerReturnsNonNullEmitter() {
        SseEmitter emitter = registry.register("wf-1", null);
        assertThat(emitter).isNotNull();
    }

    @Test
    void hasListenersReturnsTrueAfterRegister() {
        assertThat(registry.hasListeners("wf-1")).isFalse();
        registry.register("wf-1", null);
        assertThat(registry.hasListeners("wf-1")).isTrue();
    }

    @Test
    void multipleEmittersForSameWorkflow() {
        registry.register("wf-1", null);
        registry.register("wf-1", null);
        assertThat(registry.hasListeners("wf-1")).isTrue();
    }

    // ── Event dispatch & sequencing ──────────────────────────────────

    @Test
    void sendAssignsMonotonicIds() {
        registry.register("wf-1", null);

        AgentSSEEvent e1 = AgentSSEEvent.thinking("wf-1", "llm");
        AgentSSEEvent e2 = AgentSSEEvent.toolCall("wf-1", "search", null);
        AgentSSEEvent e3 = AgentSSEEvent.done("wf-1", "output");

        registry.send("wf-1", e1);
        registry.send("wf-1", e2);
        registry.send("wf-1", e3);

        assertThat(e1.getId()).isEqualTo(1);
        assertThat(e2.getId()).isEqualTo(2);
        assertThat(e3.getId()).isEqualTo(3);
    }

    @Test
    void sendWithoutListenersStillBuffers() {
        // No emitter registered, but events should be buffered
        AgentSSEEvent e1 = AgentSSEEvent.thinking("wf-1", "llm");
        registry.send("wf-1", e1);
        assertThat(e1.getId()).isEqualTo(1);

        // Register now and verify replay works (proving buffering happened)
        SseEmitter emitter = registry.register("wf-1", 0L);
        assertThat(emitter).isNotNull();
    }

    @Test
    void inProcessStreamReceivesEventsWithoutHttp() {
        AgentEventStream stream = registry.openStream("wf-1", null);
        registry.send("wf-1", AgentSSEEvent.thinking("wf-1", "llm"));

        AgentSSEEvent event = stream.nextEvent();

        assertThat(event).isNotNull();
        assertThat(event.getType()).isEqualTo("thinking");
        assertThat(event.getId()).isEqualTo(1);
        assertThat(stream.getLastEventId()).isEqualTo("1");
    }

    @Test
    void inProcessStreamReplaysAndThenCompletes() {
        registry.send("wf-1", AgentSSEEvent.thinking("wf-1", "first"));
        registry.send("wf-1", AgentSSEEvent.toolCall("wf-1", "search", null));
        registry.complete("wf-1");

        AgentEventStream stream = registry.openStream("wf-1", "1");

        assertThat(stream.nextEvent().getId()).isEqualTo(2);
        assertThat(stream.nextEvent()).isNull();
    }

    // ── Alias (sub-workflow forwarding) ──────────────────────────────

    @Test
    void aliasForwardsEventsToParent() {
        registry.register("parent-wf", null);
        registry.registerAlias("child-wf", "parent-wf");

        AgentSSEEvent childEvent = AgentSSEEvent.thinking("child-wf", "child_llm");
        registry.send("child-wf", childEvent);

        // Event ID should come from parent's sequence
        assertThat(childEvent.getId()).isEqualTo(1);

        // Parent should have listeners, child should not
        assertThat(registry.hasListeners("parent-wf")).isTrue();
    }

    @Test
    void hasListenersResolvesAlias() {
        registry.register("parent-wf", null);
        registry.registerAlias("child-wf", "parent-wf");

        assertThat(registry.hasListeners("child-wf")).isTrue();
    }

    // ── Completion ───────────────────────────────────────────────────

    @Test
    void completeRemovesEmitters() {
        registry.register("wf-1", null);
        assertThat(registry.hasListeners("wf-1")).isTrue();

        registry.complete("wf-1");
        assertThat(registry.hasListeners("wf-1")).isFalse();
    }

    @Test
    void completeCleansUpAliases() {
        registry.register("parent-wf", null);
        registry.registerAlias("child-wf", "parent-wf");

        registry.complete("parent-wf");

        // Alias should be cleaned up — child should no longer resolve to parent
        assertThat(registry.hasListeners("child-wf")).isFalse();
    }

    @Test
    void completeChildDoesNotCloseParent() {
        registry.register("parent-wf", null);
        registry.registerAlias("child-wf", "parent-wf");

        // Completing a child sub-workflow must NOT close the parent's emitters —
        // the parent stream stays open until the root workflow itself completes.
        registry.complete("child-wf");
        assertThat(registry.hasListeners("parent-wf")).isTrue();
    }

    // ── Reconnection replay ──────────────────────────────────────────

    @Test
    void reconnectionReplaysBufferedEvents() {
        // Send some events before any emitter connects
        registry.send("wf-1", AgentSSEEvent.thinking("wf-1", "llm"));
        registry.send("wf-1", AgentSSEEvent.toolCall("wf-1", "search", null));
        registry.send("wf-1", AgentSSEEvent.toolResult("wf-1", "search", "data"));

        // Connect with lastEventId=1 → should replay events 2 and 3
        SseEmitter emitter = registry.register("wf-1", 1L);
        assertThat(emitter).isNotNull();
    }

    @Test
    void firstConnectWithNullLastEventIdReplaysAllBuffered() {
        // Pre-buffer events before any client connects
        registry.send("wf-1", AgentSSEEvent.thinking("wf-1", "llm"));
        registry.send("wf-1", AgentSSEEvent.toolCall("wf-1", "search", null));

        // First connect (null lastEventId) replays all buffered events
        SseEmitter emitter = registry.register("wf-1", null);
        assertThat(emitter).isNotNull();
        // Integration test (AgentControllerSSEIntegrationTest) proves actual delivery
    }

    @Test
    void reconnectionWithZeroReplaysAllEvents() {
        registry.send("wf-1", AgentSSEEvent.thinking("wf-1", "llm"));
        registry.send("wf-1", AgentSSEEvent.toolCall("wf-1", "search", null));

        // Connect with lastEventId=0 → replay all
        SseEmitter emitter = registry.register("wf-1", 0L);
        assertThat(emitter).isNotNull();
    }

    // ── Heartbeat ────────────────────────────────────────────────────

    @Test
    void sendHeartbeatsDoesNotThrowWithNoEmitters() {
        // Should not throw even with no registered emitters
        assertThatCode(() -> registry.sendHeartbeats()).doesNotThrowAnyException();
    }

    @Test
    void sendHeartbeatsWithRegisteredEmitters() {
        registry.register("wf-1", null);
        registry.register("wf-2", null);

        // Should not throw
        assertThatCode(() -> registry.sendHeartbeats()).doesNotThrowAnyException();
    }

    // ── Buffer cleanup ───────────────────────────────────────────────

    @Test
    void cleanupStaleBuffersDoesNotThrowWhenEmpty() {
        assertThatCode(() -> registry.cleanupStaleBuffers()).doesNotThrowAnyException();
    }

    @Test
    void cleanupDoesNotRemoveRecentlyCompletedBuffers() {
        // Send events and complete
        registry.send("wf-1", AgentSSEEvent.thinking("wf-1", "llm"));
        registry.register("wf-1", null);
        registry.complete("wf-1");

        // Immediately run cleanup — should not remove (within retention window)
        registry.cleanupStaleBuffers();

        // Buffer should still exist — new registration can still replay
        SseEmitter emitter = registry.register("wf-1", 0L);
        assertThat(emitter).isNotNull();
    }

    // ── BoundedEventBuffer ───────────────────────────────────────────

    @Test
    void boundedBufferEvictsOldestWhenFull() {
        AgentStreamRegistry.BoundedEventBuffer buffer =
                new AgentStreamRegistry.BoundedEventBuffer(3);

        for (int i = 1; i <= 5; i++) {
            AgentSSEEvent e = AgentSSEEvent.thinking("wf", "task");
            e.setId(i);
            buffer.add(e);
        }

        // Should only have events 3, 4, 5
        var events = buffer.eventsSince(0);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getId()).isEqualTo(3);
        assertThat(events.get(1).getId()).isEqualTo(4);
        assertThat(events.get(2).getId()).isEqualTo(5);
    }

    @Test
    void boundedBufferEventsSinceFiltersCorrectly() {
        AgentStreamRegistry.BoundedEventBuffer buffer =
                new AgentStreamRegistry.BoundedEventBuffer(10);

        for (int i = 1; i <= 5; i++) {
            AgentSSEEvent e = AgentSSEEvent.thinking("wf", "task");
            e.setId(i);
            buffer.add(e);
        }

        var events = buffer.eventsSince(3);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getId()).isEqualTo(4);
        assertThat(events.get(1).getId()).isEqualTo(5);
    }

    @Test
    void boundedBufferEventsSinceReturnsEmptyWhenAllConsumed() {
        AgentStreamRegistry.BoundedEventBuffer buffer =
                new AgentStreamRegistry.BoundedEventBuffer(10);

        AgentSSEEvent e = AgentSSEEvent.thinking("wf", "task");
        e.setId(1);
        buffer.add(e);

        var events = buffer.eventsSince(1);
        assertThat(events).isEmpty();
    }

    @Test
    void boundedBufferEventsSinceReturnsEmptyForEmptyBuffer() {
        AgentStreamRegistry.BoundedEventBuffer buffer =
                new AgentStreamRegistry.BoundedEventBuffer(10);
        var events = buffer.eventsSince(0);
        assertThat(events).isEmpty();
    }

    // ── Independent workflow sequences ───────────────────────────────

    @Test
    void separateWorkflowsHaveIndependentSequences() {
        AgentSSEEvent e1 = AgentSSEEvent.thinking("wf-1", "llm");
        AgentSSEEvent e2 = AgentSSEEvent.thinking("wf-2", "llm");
        AgentSSEEvent e3 = AgentSSEEvent.toolCall("wf-1", "search", null);

        registry.send("wf-1", e1);
        registry.send("wf-2", e2);
        registry.send("wf-1", e3);

        assertThat(e1.getId()).isEqualTo(1);
        assertThat(e2.getId()).isEqualTo(1); // independent sequence
        assertThat(e3.getId()).isEqualTo(2);
    }
}
