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
package com.netflix.conductor.client.config;

/**
 * A default implementation of {@link ConductorClientConfiguration} where external payload storage
 * is disabled.
 */
public class DefaultConductorClientConfiguration implements ConductorClientConfiguration {

    @Override
    public int getWorkflowInputPayloadThresholdKB() {
        return 5120;
    }

    @Override
    public int getWorkflowInputMaxPayloadThresholdKB() {
        return 10240;
    }

    @Override
    public int getTaskOutputPayloadThresholdKB() {
        return 3072;
    }

    @Override
    public int getTaskOutputMaxPayloadThresholdKB() {
        return 10240;
    }

    @Override
    public boolean isExternalPayloadStorageEnabled() {
        return false;
    }
}
