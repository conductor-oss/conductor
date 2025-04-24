/* 
 * Copyright 2020 Conductor Authors.
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

import com.netflix.conductor.common.metadata.events.EventHandler.Action;

import lombok.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventExecution {

    public enum Status {

        IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    }

    private String id;

    private String messageId;

    private String name;

    private String event;

    private long created;

    private Status status;

    private Action.Type action;

    private Map<String, Object> output = new HashMap<>();

    public EventExecution(String id, String messageId) {
        this.id = id;
        this.messageId = messageId;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventExecution execution = (EventExecution) o;
        return created == execution.created && Objects.equals(id, execution.id) && Objects.equals(messageId, execution.messageId) && Objects.equals(name, execution.name) && Objects.equals(event, execution.event) && status == execution.status && action == execution.action && Objects.equals(output, execution.output);
    }

    public int hashCode() {
        return Objects.hash(id, messageId, name, event, created, status, action, output);
    }
}