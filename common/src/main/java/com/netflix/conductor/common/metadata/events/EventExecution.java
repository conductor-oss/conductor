/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.common.metadata.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.netflix.conductor.annotations.protogen.ProtoEnum;
import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;

@ProtoMessage
public class EventExecution {

    @ProtoEnum
    public enum Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    @ProtoField(id = 1)
    private String id;

    @ProtoField(id = 2)
    private String messageId;

    @ProtoField(id = 3)
    private String name;

    @ProtoField(id = 4)
    private String event;

    @ProtoField(id = 5)
    private long created;

    @ProtoField(id = 6)
    private Status status;

    @ProtoField(id = 7)
    private Action.Type action;

    @ProtoField(id = 8)
    private Map<String, Object> output = new HashMap<>();

    public EventExecution() {}

    public EventExecution(String id, String messageId) {
        this.id = id;
        this.messageId = messageId;
    }

    /** @return the id */
    public String getId() {
        return id;
    }

    /** @param id the id to set */
    public void setId(String id) {
        this.id = id;
    }

    /** @return the messageId */
    public String getMessageId() {
        return messageId;
    }

    /** @param messageId the messageId to set */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /** @return the name */
    public String getName() {
        return name;
    }

    /** @param name the name to set */
    public void setName(String name) {
        this.name = name;
    }

    /** @return the event */
    public String getEvent() {
        return event;
    }

    /** @param event the event to set */
    public void setEvent(String event) {
        this.event = event;
    }

    /** @return the created */
    public long getCreated() {
        return created;
    }

    /** @param created the created to set */
    public void setCreated(long created) {
        this.created = created;
    }

    /** @return the status */
    public Status getStatus() {
        return status;
    }

    /** @param status the status to set */
    public void setStatus(Status status) {
        this.status = status;
    }

    /** @return the action */
    public Action.Type getAction() {
        return action;
    }

    /** @param action the action to set */
    public void setAction(Action.Type action) {
        this.action = action;
    }

    /** @return the output */
    public Map<String, Object> getOutput() {
        return output;
    }

    /** @param output the output to set */
    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventExecution execution = (EventExecution) o;
        return created == execution.created
                && Objects.equals(id, execution.id)
                && Objects.equals(messageId, execution.messageId)
                && Objects.equals(name, execution.name)
                && Objects.equals(event, execution.event)
                && status == execution.status
                && action == execution.action
                && Objects.equals(output, execution.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, messageId, name, event, created, status, action, output);
    }
}
