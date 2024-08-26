/*
 * Copyright 2022 Conductor Authors.
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
import java.util.Map;

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
        return delete(uri, Map.of(), body);
    }

    public BulkResponse delete(URI uri, Map<String, Object> headers, Object body) {
        if (body != null) {
            return getWebResourceBuilder(client.resource(uri), headers)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .delete(BulkResponse.class, body);
        } else {
            getWebResourceBuilder(client.resource(uri), headers).delete();
        }
        return null;
    }

    public ClientResponse get(URI uri) {
        return get(uri, Map.of());
    }

    public ClientResponse get(URI uri, Map<String, Object> headers) {
        return getWebResourceBuilder(client.resource(uri), headers)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)
                .get(ClientResponse.class);
    }

    public WebResource.Builder getWebResourceBuilder(URI uri, Object entity) {
        return getWebResourceBuilder(uri, Map.of(), entity);
    }

    public WebResource.Builder getWebResourceBuilder(
            URI uri, Map<String, Object> headers, Object entity) {
        return getWebResourceBuilder(client.resource(uri), headers)
                .type(MediaType.APPLICATION_JSON)
                .entity(entity)
                .accept(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON);
    }

    private WebResource.Builder getWebResourceBuilder(
            WebResource resource, Map<String, Object> headers) {
        WebResource.Builder builder = resource.getRequestBuilder();
        if (headers == null || headers.isEmpty()) {
            return builder;
        }

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            builder = builder.header(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    private boolean isNewerJacksonVersion() {
        Version version = com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION;
        return version.getMajorVersion() == 2 && version.getMinorVersion() >= 12;
    }
}
