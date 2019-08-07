package com.netflix.conductor.contribs.queue.amqp;

/**
 * Last properties name for AMQP queues
 * Created at 22/03/2019 17:19
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
public enum AMQQueryParameters {

    PARAM_EXCHANGE_TYPE("exchangeType"),
    PARAM_ROUTING_KEY("routingKey"),
    PARAM_DELIVERY_MODE("deliveryMode"),
    PARAM_DURABLE("durable"),
    PARAM_EXCLUSIVE("exclusive"),
    PARAM_AUTO_DELETE("autoDelete"),
    PARAM_MAX_PRIORITY("maxPriority");

    String propertyName;

    AMQQueryParameters(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public String toString() {
        return propertyName;
    }
}

