package com.netflix.conductor.jetty.server;

import com.netflix.conductor.core.config.Configuration;

public interface JettyServerConfiguration extends Configuration {
    String ENABLED_PROPERTY_NAME = "conductor.jetty.server.enabled";
    boolean ENABLED_DEFAULT_VALUE = true;

    String PORT_PROPERTY_NAME = "conductor.jetty.server.port";
    int PORT_DEFAULT_VALUE = 8080;

    String JOIN_PROPERTY_NAME = "conductor.jetty.server.join";
    boolean JOIN_DEFAULT_VALUE = true;

    default boolean isEnabled(){
        return getBooleanProperty(ENABLED_PROPERTY_NAME, ENABLED_DEFAULT_VALUE);
    }

    default int getPort() {
        return getIntProperty(PORT_PROPERTY_NAME, PORT_DEFAULT_VALUE);
    }

    default boolean isJoin(){
        return getBooleanProperty(JOIN_PROPERTY_NAME, JOIN_DEFAULT_VALUE);
    }
}
