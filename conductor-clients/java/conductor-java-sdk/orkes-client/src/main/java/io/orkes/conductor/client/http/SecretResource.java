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
package io.orkes.conductor.client.http;

import java.util.List;
import java.util.Set;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientResponse;

import io.orkes.conductor.client.model.TagObject;

import com.fasterxml.jackson.core.type.TypeReference;

public class SecretResource {
    private final ConductorClient client;

    public SecretResource(ConductorClient client) {
        this.client = client;
    }

    public void deleteSecret(String key) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.DELETE)
                .path("/secrets/{key}")
                .addPathParam("key", key)
                .build();

        client.execute(request);
    }

    public String getSecret(String key) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.GET)
                .path("/secrets/{key}")
                .addPathParam("key", key)
                .build();

        ConductorClientResponse<String> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();

    }

    public Set<String> listAllSecretNames() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.GET)
                .path("/secrets")
                .build();

        ConductorClientResponse<Set<String>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }


    public List<String> listSecretsThatUserCanGrantAccessTo() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.GET)
                .path("/secrets")
                .build();

        ConductorClientResponse<List<String>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void putSecret(String body, String key) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.PUT)
                .path("/secrets/{key}")
                .addPathParam("key", key)
                .body(body)
                .build();

        client.execute(request);
    }

    public Boolean secretExists(String key) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.GET)
                .path("/secrets/{key}/exists")
                .addPathParam("key", key)
                .build();

        ConductorClientResponse<Boolean> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void putTagForSecret(String key, List<TagObject> body) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.PUT)
                .path("/secrets/{key}/tags")
                .addPathParam("key", key)
                .body(body)
                .build();

        client.execute(request);

    }

    public void deleteTagForSecret(List<TagObject> body, String key) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.DELETE)
                .path("/secrets/{key}/tags")
                .addPathParam("key", key)
                .body(body)
                .build();

        client.execute(request);
    }


    public List<TagObject> getTags(String key) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(ConductorClientRequest.Method.GET)
                .path("/secrets/{key}/tags")
                .addPathParam("key", key)
                .build();

        ConductorClientResponse<List<TagObject>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }
}
