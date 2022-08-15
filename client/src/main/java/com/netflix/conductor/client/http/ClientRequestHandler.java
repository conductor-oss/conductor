/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.client.http;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.model.BulkResponse;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;

public class ClientRequestHandler {
    private final Client client;

    public ClientRequestHandler(
            ClientConfig config, ClientHandler handler, ClientFilter... filters) {
        ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

        // https://github.com/FasterXML/jackson-databind/issues/2683
        if (isNewerJacksonVersion()) {
            objectMapper.registerModule(new JavaTimeModule());
        }

        JacksonJsonProvider provider = new JacksonJsonProvider(objectMapper);
        config.getSingletons().add(provider);

        if (handler == null) {
            this.client = Client.create(config);
        } else {
            this.client = new Client(handler, config);
        }

        for (ClientFilter filter : filters) {
            this.client.addFilter(filter);
        }
    }

    public BulkResponse delete(URI uri, Object body) {
        if (body != null) {
            return client.resource(uri)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .delete(BulkResponse.class, body);
        } else {
            client.resource(uri).delete();
        }
        return null;
    }

    public ClientResponse get(URI uri) {
        return client.resource(uri)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
    }

    public WebResource.Builder getWebResourceBuilder(URI URI, Object entity) {
        return client.resource(URI)
                .type(MediaType.APPLICATION_JSON)
                .entity(entity)
                .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    }

    private boolean isNewerJacksonVersion() {
        Version version = com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION;
        return version.getMajorVersion() == 2 && version.getMinorVersion() >= 12;
    }
}
