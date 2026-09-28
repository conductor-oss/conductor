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
package org.conductoross.conductor.ai.a2a.server;

import java.io.IOException;

/**
 * Sink for {@code message/stream} events. Each {@code event} is a JSON-RPC envelope ({@code
 * {jsonrpc, id, result}}) that the transport (e.g. an {@code SseEmitter}) writes as one SSE {@code
 * data:} frame. Keeping this web-agnostic lets {@link A2AWorkflowAgent} drive the stream without
 * depending on Spring's SSE types, so it stays unit-testable.
 */
@FunctionalInterface
public interface A2AStreamSink {

    /** Emit one streaming event (a JSON-RPC envelope). */
    void event(Object jsonRpcEnvelope) throws IOException;
}
