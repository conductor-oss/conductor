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
package io.orkes.conductor.client.util;

import org.junit.jupiter.api.Assertions;

import com.netflix.conductor.client.http.ConductorClient;

import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.http.OrkesAuthentication;

public class ClientTestUtil {
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

        String basePath = getEnv(ENV_ROOT_URI);
        Assertions.assertNotNull(basePath, ENV_ROOT_URI + " env not set");
        String keyId = getEnv(ENV_KEY_ID);
        Assertions.assertNotNull(keyId, ENV_KEY_ID + " env not set");
        String keySecret = getEnv(ENV_SECRET);
        Assertions.assertNotNull(keySecret, ENV_SECRET + " env not set");

        return ConductorClient.builder()
                .basePath(basePath)
                .addHeaderSupplier(new OrkesAuthentication(keyId, keySecret))
                .readTimeout(30_000)
                .connectTimeout(30_000)
                .writeTimeout(30_000)
                .build();
    }

    private static String getEnv(String key) {
        return System.getenv(key);
    }
}
