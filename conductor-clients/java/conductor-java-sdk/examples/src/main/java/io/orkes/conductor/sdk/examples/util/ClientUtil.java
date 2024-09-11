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

import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.http.OrkesAuthentication;

import com.google.common.base.Preconditions;

import static java.lang.System.getenv;

public class ClientUtil {
    private static final String ENV_ROOT_URI = "CONDUCTOR_SERVER_URL";
    private static final String ENV_KEY_ID = "CONDUCTOR_SERVER_AUTH_KEY";
    private static final String ENV_SECRET = "CONDUCTOR_SERVER_AUTH_SECRET";
    private static final ConductorClient CLIENT = getClient();

    public static OrkesClients getOrkesClients() {
        return new OrkesClients(CLIENT);
    }

    public static ConductorClient getClient() {
        if (CLIENT != null) {
            return CLIENT;
        }

        var basePath = getenv(ENV_ROOT_URI);
        Preconditions.checkNotNull(basePath, ENV_ROOT_URI + " env not set");

        ConductorClient.Builder builder = ConductorClient.builder()
                .basePath(basePath)
                .readTimeout(10_000)
                .connectTimeout(10_000)
                .writeTimeout(10_000);

        var keyId = getenv(ENV_KEY_ID);
        var keySecret = getenv(ENV_SECRET);

        if (keyId != null && keySecret != null) {
            builder.addHeaderSupplier(new OrkesAuthentication(keyId, keySecret));
        }

        return builder.build();
    }
}
