package com.netflix.conductor.grpc.server;

import com.netflix.conductor.core.config.Configuration;

public interface GRPCServerConfiguration extends Configuration {
    String ENABLED_PROPERTY_NAME = "conductor.grpc.server.enabled";
    boolean ENABLED_DEFAULT_VALUE = false;

    String PORT_PROPERTY_NAME = "conductor.grpc.server.port";
    int PORT_DEFAULT_VALUE = 8090;

    default boolean isEnabled(){
       return getBooleanProperty(ENABLED_PROPERTY_NAME, ENABLED_DEFAULT_VALUE);
    }

    default int getPort(){
        return getIntProperty(PORT_PROPERTY_NAME, PORT_DEFAULT_VALUE);
    }
}
