package com.netflix.conductor.contribs.queue.amqp;

/**
 * Created at 26/03/2019 10:40
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
interface AMQConstants {
    String AMQP_QUEUE_TYPE = "amqp";
    String AMQP_EXCHANGE_TYPE = "amqp_exchange";

    String PROPERTY_KEY_TEMPLATE = "workflow.event.queues.amqp.%s";

    String DEFAULT_CONTENT_TYPE = "application/json";
    String DEFAULT_CONTENT_ENCODING = "UTF-8";
    String DEFAULT_EXCHANGE_TYPE = "topic";

    boolean DEFAULT_DURABLE = true;
    boolean DEFAULT_EXCLUSIVE = false;
    boolean DEFAULT_AUTO_DELETE = false;

    int DEFAULT_DELIVERY_MODE = 2; // Persistent messages
    int DEFAULT_BATCH_SIZE = 1;
    int DEFAULT_POLL_TIME_MS = 100;
}
