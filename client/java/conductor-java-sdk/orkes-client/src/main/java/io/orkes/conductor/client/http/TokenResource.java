/*
 * Copyright 2022 Orkes, Inc.
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

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;

import io.orkes.conductor.client.model.ConductorUser;
import io.orkes.conductor.client.model.GenerateTokenRequest;
import io.orkes.conductor.client.model.TokenResponse;

import com.fasterxml.jackson.core.type.TypeReference;


public class TokenResource {

    private final ConductorClient client;

    public TokenResource(ConductorClient client) {
        this.client = client;
    }

    public ConductorClientResponse<TokenResponse> generate(GenerateTokenRequest body) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/token")
                .body(body)
                .build();

        return client.execute(request, new TypeReference<>() {
        });
    }

    public ConductorClientResponse<ConductorUser> getUserInfo() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/token/userInfo")
                .build();

        return client.execute(request, new TypeReference<>() {
        });
    }
}
