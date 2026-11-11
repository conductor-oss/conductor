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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;

/**
 * Engine state of a native (code-defined) workflow that has no task to live on. Everything else
 * about the code's progress is on the tasks it paused on ({@link TaskContinuation}), so this stays
 * small however long the execution runs.
 */
@ProtoMessage
public class NativeState {

    @ProtoField(id = 1)
    private int protocol;

    @ProtoField(id = 2)
    private String codeType;

    @ProtoField(id = 3)
    private int continuationTimeoutSeconds;

    @ProtoField(id = 4)
    private long step;

    /** The main lane's first continuation, which runs before any task exists. */
    @ProtoField(id = 5)
    private TaskContinuation bootstrap;

    /** The handler lane: signal and update handlers schedule no tasks. Null without handlers. */
    @ProtoField(id = 6)
    private TaskContinuation handlers;

    @ProtoField(id = 7)
    private Map<String, Object> replies = new HashMap<>();

    @ProtoField(id = 8)
    private List<String> seen = new ArrayList<>();

    /**
     * The continuation (queue message id) leased right now. One continuation of an execution runs
     * at a time, on any host: reference names and recorded values are allocated from what its
     * replay saw, so two running at once could pick the same ones.
     */
    @ProtoField(id = 9)
    private String activeContinuation;

    /** When {@link #activeContinuation}'s lease ends, in epoch millis. */
    @ProtoField(id = 10)
    private long activeUntil;

    public String getActiveContinuation() {
        return activeContinuation;
    }

    public void setActiveContinuation(String activeContinuation) {
        this.activeContinuation = activeContinuation;
    }

    public long getActiveUntil() {
        return activeUntil;
    }

    public void setActiveUntil(long activeUntil) {
        this.activeUntil = activeUntil;
    }

    public int getProtocol() {
        return protocol;
    }

    public void setProtocol(int protocol) {
        this.protocol = protocol;
    }

    public String getCodeType() {
        return codeType;
    }

    public void setCodeType(String codeType) {
        this.codeType = codeType;
    }

    public int getContinuationTimeoutSeconds() {
        return continuationTimeoutSeconds;
    }

    public void setContinuationTimeoutSeconds(int continuationTimeoutSeconds) {
        this.continuationTimeoutSeconds = continuationTimeoutSeconds;
    }

    public long getStep() {
        return step;
    }

    public void setStep(long step) {
        this.step = step;
    }

    public TaskContinuation getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(TaskContinuation bootstrap) {
        this.bootstrap = bootstrap;
    }

    public TaskContinuation getHandlers() {
        return handlers;
    }

    public void setHandlers(TaskContinuation handlers) {
        this.handlers = handlers;
    }

    public Map<String, Object> getReplies() {
        return replies;
    }

    public void setReplies(Map<String, Object> replies) {
        this.replies = replies;
    }

    public List<String> getSeen() {
        return seen;
    }

    public void setSeen(List<String> seen) {
        this.seen = seen;
    }
}
