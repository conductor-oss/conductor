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
package com.netflix.conductor.contribs.queue.amqp.util;

/** @author Ritu Parathody */
public class AMQPConstants {

    /** this when set will create a rabbitmq queue */
    public static String AMQP_QUEUE_TYPE = "amqp_queue";
    /** this when set will create a rabbitmq exchange */
    public static String AMQP_EXCHANGE_TYPE = "amqp_exchange";

    public static String PROPERTY_KEY_TEMPLATE = "conductor.event-queues.amqp.%s";

    /** default content type for the message read from rabbitmq */
    public static String DEFAULT_CONTENT_TYPE = "application/json";

    /** default encoding for the message read from rabbitmq */
    public static String DEFAULT_CONTENT_ENCODING = "UTF-8";

    /** default rabbitmq exchange type */
    public static String DEFAULT_EXCHANGE_TYPE = "topic";

    /**
     * default rabbitmq durability When set to true the queues are persisted to the disk.
     *
     * <p>{@see <a href="https://www.rabbitmq.com/queues.html">RabbitMQ</a>}.
     */
    public static boolean DEFAULT_DURABLE = true;

    /**
     * default rabbitmq exclusivity When set to true the queues can be only used by one connection.
     *
     * <p>{@see <a href="https://www.rabbitmq.com/queues.html">RabbitMQ</a>}.
     */
    public static boolean DEFAULT_EXCLUSIVE = false;

    /**
     * default rabbitmq auto delete When set to true the queues will be deleted when the last
     * consumer is cancelled
     *
     * <p>{@see <a href="https://www.rabbitmq.com/queues.html">RabbitMQ</a>}.
     */
    public static boolean DEFAULT_AUTO_DELETE = false;
    /**
     * default rabbitmq delivery mode This is a property of the message When set to 1 the will be
     * non persistent and 2 will be persistent {@see <a
     * href="https://www.rabbitmq.com/releases/rabbitmq-java-client/v3.5.4/rabbitmq-java-client-javadoc-3.5.4/com/rabbitmq/client/MessageProperties.html>
     * Message Properties</a>}.
     */
    public static int DEFAULT_DELIVERY_MODE = 2;
    /**
     * default rabbitmq delivery mode This is a property of the channel limit to get the number of
     * unacknowledged messages. {@see <a
     * href="https://www.rabbitmq.com/consumer-prefetch.html>Consumer Prefetch</a>}.
     */
    public static int DEFAULT_BATCH_SIZE = 1;
    /**
     * default rabbitmq delivery mode This is a property of the amqp implementation which sets teh
     * polling time to drain the in-memory queue.
     */
    public static int DEFAULT_POLL_TIME_MS = 100;
}
