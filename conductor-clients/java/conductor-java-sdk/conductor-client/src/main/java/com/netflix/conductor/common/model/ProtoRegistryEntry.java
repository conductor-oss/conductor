package com.netflix.conductor.common.model;

import lombok.Data;

@Data
public class ProtoRegistryEntry {
    private final String serviceName;
    private final String filename;
    private final byte[] data;
}
