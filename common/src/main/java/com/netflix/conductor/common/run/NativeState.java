/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.common.run;

import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Engine state of a native (code-defined) workflow that has no task to live on. Everything else
 * about the code's progress is on the tasks it paused on ({@link TaskContinuation}), so this stays
 * small however long the execution runs. The code type and the continuation timeout are the
 * definition's {@code metadata.native}, which the execution carries.
 */
@ProtoMessage
public class NativeState {

    /**
     * The main lane's first continuation, which runs before any task exists. Always {@code main}
     * #1, so neither is stored.
     */
    @ProtoField(id = 1)
    @JsonIgnoreProperties({"lane", "n"})
    private TaskContinuation bootstrap;

    /**
     * The handler lane: signal and update handlers schedule no tasks. Null without handlers. Its
     * lane is always {@code handlers}, so it is not stored; its number counts its runs.
     */
    @ProtoField(id = 2)
    @JsonIgnoreProperties({"lane"})
    private TaskContinuation handlers;

    @ProtoField(id = 3)
    private Map<String, Object> replies = new HashMap<>();

    public TaskContinuation getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(TaskContinuation bootstrap) {
        if (bootstrap != null) {
            bootstrap.setLane("main");
            bootstrap.setN(1);
        }
        this.bootstrap = bootstrap;
    }

    public TaskContinuation getHandlers() {
        return handlers;
    }

    public void setHandlers(TaskContinuation handlers) {
        if (handlers != null) {
            handlers.setLane("handlers");
        }
        this.handlers = handlers;
    }

    public Map<String, Object> getReplies() {
        return replies;
    }

    public void setReplies(Map<String, Object> replies) {
        this.replies = replies;
    }
}
