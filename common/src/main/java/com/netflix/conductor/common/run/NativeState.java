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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Engine state of a native (code-defined) workflow: the lanes that have no task to live on, and the
 * markers of the tasks the code paused on ({@link #getMarkers()}). It is on the workflow row, which
 * only code under the workflow's lock saves. The code type and the continuation timeout are the
 * definition's {@code metadata.native}, which the execution carries.
 */
@ProtoMessage
public class NativeState {

    /**
     * The main lane's first continuation, which runs before any task exists. Always {@code main}
     * #1, so neither is stored.
     */
    @ProtoField(id = 1)
    private TaskContinuation bootstrap;

    /**
     * The handler lane: signal and update handlers schedule no tasks. Null without handlers. Its
     * lane is always {@code handlers}, so it is not stored; its number counts its runs.
     */
    @ProtoField(id = 2)
    private TaskContinuation handlers;

    @ProtoField(id = 3)
    private Map<String, Object> replies = new HashMap<>();

    /**
     * The task domain of the worker that keeps this execution's code in memory between
     * continuations, parked where it paused, so it can continue without replaying. Named by that
     * worker in the completion that applied last; null when no worker holds the execution. The next
     * continuation is offered to this domain's queue first. Read over HTTP only.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String pinnedTo;

    /**
     * Names the completion that applied last, as its worker chose: a parked execution may continue
     * only if its worker's stamp is still this one. Null after anything else changed what the code
     * has done (a rerun, a retry of the code, a rejected fragment). Read over HTTP only.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String stamp;

    /**
     * The marker of every task the code paused on, by the task's id: which continuation runs when
     * the task finishes, and how far it got. Kept here, on the workflow row, which only code under
     * the workflow's lock saves, and not on the task's row, which a worker's result or a system
     * task's run also saves, without the lock, from a copy that may be older than the marker. The
     * API shows each on its task ({@code Task.continuation}). Read over HTTP only.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, TaskContinuation> markers = new HashMap<>();

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

    public String getPinnedTo() {
        return pinnedTo;
    }

    public void setPinnedTo(String pinnedTo) {
        this.pinnedTo = pinnedTo;
    }

    public String getStamp() {
        return stamp;
    }

    public void setStamp(String stamp) {
        this.stamp = stamp;
    }

    public Map<String, TaskContinuation> getMarkers() {
        return markers;
    }

    public void setMarkers(Map<String, TaskContinuation> markers) {
        this.markers = markers == null ? new HashMap<>() : markers;
    }

    /** This state without the markers: what the API shows, with each marker on its task. */
    public NativeState withoutMarkers() {
        NativeState copy = new NativeState();
        copy.bootstrap = bootstrap;
        copy.handlers = handlers;
        copy.replies = replies;
        copy.pinnedTo = pinnedTo;
        copy.stamp = stamp;
        return copy;
    }
}
