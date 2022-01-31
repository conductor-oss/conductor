/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.core;

/** Store the authentication context, app or username or both */
public class WorkflowContext {

    public static final ThreadLocal<WorkflowContext> THREAD_LOCAL =
            InheritableThreadLocal.withInitial(() -> new WorkflowContext("", ""));

    private final String clientApp;

    private final String userName;

    public WorkflowContext(String clientApp) {
        this.clientApp = clientApp;
        this.userName = null;
    }

    public WorkflowContext(String clientApp, String userName) {
        this.clientApp = clientApp;
        this.userName = userName;
    }

    public static WorkflowContext get() {
        return THREAD_LOCAL.get();
    }

    public static void set(WorkflowContext ctx) {
        THREAD_LOCAL.set(ctx);
    }

    public static void unset() {
        THREAD_LOCAL.remove();
    }

    /** @return the clientApp */
    public String getClientApp() {
        return clientApp;
    }

    /** @return the username */
    public String getUserName() {
        return userName;
    }
}
