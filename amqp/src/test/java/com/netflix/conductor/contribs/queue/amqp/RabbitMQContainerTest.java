package com.netflix.conductor.contribs.queue.amqp;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class RabbitMQContainerTest {

    @Container
    private static final RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3-management");

    @Test
    public void testRabbitMQContainerStarts() {
        assertTrue(rabbitMQContainer.isRunning());
    }
}
