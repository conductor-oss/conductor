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
package io.orkes.conductor.sdk.examples.util;

import com.netflix.conductor.client.http.ConductorClient;

import io.orkes.conductor.client.ApiClient;
import io.orkes.conductor.client.OrkesClients;

public class ClientUtil {

    private static final ConductorClient CLIENT = ApiClient.builder()
            .useEnvVariables(true)
            .readTimeout(10_000)
            .connectTimeout(10_000)
            .writeTimeout(10_000)
            .build();;

    public static OrkesClients getOrkesClients() {
        return new OrkesClients(CLIENT);
    }

    public static ConductorClient getClient() {
       return CLIENT;
    }
}
