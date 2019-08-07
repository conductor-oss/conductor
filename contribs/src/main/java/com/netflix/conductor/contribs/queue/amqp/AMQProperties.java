package com.netflix.conductor.contribs.queue.amqp;

/**
 * Last properties name for AMQP queues
 * Created at 22/03/2019 17:19
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
public enum AMQProperties {

    PROPERTY_CONTENT_TYPE("contentType"),
    PROPERTY_CONTENT_ENCODING("contentEncoding"),
    PROPERTY_IS_DURABLE("durable"),
    PROPERTY_IS_EXCLUSIVE("exclusive"),
    PROPERTY_AUTO_DELETE("autoDelete"),
    PROPERTY_DELIVERY_MODE("deliveryMode"),
    PROPERTY_EXCHANGE_TYPE("exchangeType"),
    PROPERTY_MAX_PRIORITY("maxPriority"),
    PROPERTY_BATCH_SIZE("batchSize"),
    PROPERTY_POLL_TIME_IN_MS("pollTimeInMs"),
    PROPERTY_HOSTS("hosts"),
    PROPERTY_USERNAME("username"),
    PROPERTY_PASSWORD("password"),
    PROPERTY_VIRTUAL_HOST("virtualHost"),
    PROPERTY_PORT("port"),
    PROPERTY_CONNECTION_TIMEOUT("connectionTimeout"),
    PROPERTY_USE_NIO("useNio");

    String propertyName;

    AMQProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public String toString() {
        return propertyName;
    }
}

