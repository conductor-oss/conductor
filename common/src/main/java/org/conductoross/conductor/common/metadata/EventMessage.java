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
