/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.contribs.queue.nats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workflow", name = "nats_stream.event.queue.enabled", havingValue = "true")
public class NATSProperties {

    @Value("${io.nats.streaming.clusterId:test-cluster}")
    private String clusterId;

    @Value("${io.nats.streaming.durableName:#{null}}")
    private String durableName;

    @Value("${io.nats.streaming.url:#{T(io.nats.client.Nats).DEFAULT_URL}")
    private String natsUrl;

    public String getClusterId() {
        return clusterId;
    }

    public String getDurableName() {
        return durableName;
    }

    public String getNatsUrl() {
        return natsUrl;
    }
}
