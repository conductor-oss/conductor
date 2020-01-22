package com.netflix.conductor.azureblob;

import com.google.inject.AbstractModule;

import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.storage.AzureBlobPayloadStorage;

public class AzureBlobModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AzureBlobConfiguration.class).to(SystemPropertiesAzureBlobConfiguration.class);
        bind(ExternalPayloadStorage.class).to(AzureBlobPayloadStorage.class);
    }

}
