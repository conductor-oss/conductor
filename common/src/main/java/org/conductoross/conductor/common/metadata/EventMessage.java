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
package org.conductoross.conductor.common.metadata;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EventMessage {
    public static final String DEAD_LETTER_QUEUE = "DEAD_LETTER_QUEUE";
    private String id;
    private String eventTarget;
    private EventType eventType;
    private String payload;
    private Object fullPayload;
    private EventMessageStatus status;
    private String statusDescription;
    private long createdAt;

    public EventMessage() {
        this.createdAt = System.currentTimeMillis();
    }

    public static enum EventMessageStatus {
        RECEIVED, // can later be marked as HANDLED or REJECTED
        HANDLED,
        REJECTED
    }

    public static enum EventType {
        WEBHOOK,
        MESSAGE // For example, SQS, Kafka
    }
}
