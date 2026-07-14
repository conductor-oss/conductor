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
package org.conductoross.conductor.ai.agentspan.runtime.context;

import java.util.Optional;

/**
 * ThreadLocal wrapper for {@link RequestContext}.
 *
 * <p>Set by the host at the start of each request (the standalone server's {@code AuthFilter}, or
 * an embedding application's security adapter) and cleared in a finally block. Read anywhere
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> HOLDER = new InheritableThreadLocal<>();

    private RequestContextHolder() {}

    public static void set(RequestContext ctx) {
        HOLDER.set(ctx);
    }

    public static Optional<RequestContext> get() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
