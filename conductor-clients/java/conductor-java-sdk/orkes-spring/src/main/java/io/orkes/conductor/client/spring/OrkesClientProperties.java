/*
 * Copyright 2024 Conductor Authors.
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
package io.orkes.conductor.client.spring;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;


@Component
@Getter
public class OrkesClientProperties {
    private final String keyId;
    private final String secret;
    // for backwards compatibility
    private final String conductorServerUrl;
    private final String securityKeyId;
    private final String securitySecret;

    public OrkesClientProperties(@Value("${conductor.client.keyId:${conductor.client.key-id:#{null}}}") String keyId,
                                 @Value("${conductor.client.secret:#{null}}") String secret,
                                 // for backwards compatibility
                                 @Value("${conductor.server.url:#{null}}") String conductorServerUrl,
                                 @Value("${conductor.security.client.keyId:${conductor.security.client.key-id:#{null}}}") String securityKeyId,
                                 @Value("${conductor.security.client.secret:#{null}}") String securitySecret) {
        this.keyId = keyId;
        this.secret = secret;
        this.conductorServerUrl = conductorServerUrl;
        this.securityKeyId = securityKeyId;
        this.securitySecret = securitySecret;
    }
}
