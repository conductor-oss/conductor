/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.providers.bedrock;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import static org.junit.jupiter.api.Assertions.*;

class BedrockConfigurationTest {

    @Test
    void testDefaultRegion() {
        BedrockConfiguration config = new BedrockConfiguration();
        assertEquals("us-east-1", config.getRegion());
    }

    @Test
    void testGetCreatesBedrockInstance() {
        BedrockConfiguration config = new BedrockConfiguration();
        config.setAccessKey("access-key");
        config.setSecretKey("secret-key");

        Bedrock result = config.get();

        assertNotNull(result);
        assertEquals("bedrock", result.getModelProvider());
    }

    @Test
    void testAwsCredentialsProvider_withAccessKeyAndSecret() {
        BedrockConfiguration config = new BedrockConfiguration();
        config.setAccessKey("my-access-key");
        config.setSecretKey("my-secret-key");

        var provider = config.getAwsCredentialsProvider();

        assertNotNull(provider);
        assertTrue(provider instanceof StaticCredentialsProvider);
    }

    @Test
    void testAwsCredentialsProvider_withBearerToken() {
        BedrockConfiguration config = new BedrockConfiguration();
        config.setBearerToken("my-bearer-token");

        var provider = config.getAwsCredentialsProvider();

        assertNotNull(provider);
        assertTrue(provider instanceof AnonymousCredentialsProvider);
    }

    @Test
    void testIsBearerTokenConfigured_true() {
        BedrockConfiguration config = new BedrockConfiguration();
        config.setBearerToken("token");
        assertTrue(config.isBearerTokenConfigured());
    }

    @Test
    void testIsBearerTokenConfigured_false() {
        BedrockConfiguration config = new BedrockConfiguration();
        assertFalse(config.isBearerTokenConfigured());

        config.setBearerToken("   ");
        assertFalse(config.isBearerTokenConfigured());
    }

    @Test
    void testAwsCredentialsProvider_customProviderPreferred() {
        BedrockConfiguration config = new BedrockConfiguration();
        var customProvider =
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("custom", "credentials"));
        config.setAwsCredentialsProvider(customProvider);

        var result = config.getAwsCredentialsProvider();
        assertEquals(customProvider, result);
    }

    @Test
    void testNoArgsConstructor() {
        BedrockConfiguration config = new BedrockConfiguration();
        assertNull(config.getAccessKey());
        assertNull(config.getSecretKey());
        assertNull(config.getBearerToken());
        assertEquals("us-east-1", config.getRegion());
    }
}
