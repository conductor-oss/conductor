/*
 * Copyright 2018 Netflix, Inc.
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

public interface ConductorClientConfiguration {

    /**
     * @return the workflow input payload size threshold in KB, beyond which the payload will be
     *     processed based on {@link
     *     ConductorClientConfiguration#isExternalPayloadStorageEnabled()}.
     */
    int getWorkflowInputPayloadThresholdKB();

    /**
     * @return the max value of workflow input payload size threshold in KB, beyond which the
     *     payload will be rejected regardless external payload storage is enabled.
     */
    int getWorkflowInputMaxPayloadThresholdKB();

    /**
     * @return the task output payload size threshold in KB, beyond which the payload will be
     *     processed based on {@link
     *     ConductorClientConfiguration#isExternalPayloadStorageEnabled()}.
     */
    int getTaskOutputPayloadThresholdKB();

    /**
     * @return the max value of task output payload size threshold in KB, beyond which the payload
     *     will be rejected regardless external payload storage is enabled.
     */
    int getTaskOutputMaxPayloadThresholdKB();

    /**
     * @return the flag which controls the use of external storage for storing workflow/task input
     *     and output JSON payloads with size greater than threshold. If it is set to true, the
     *     payload is stored in external location. If it is set to false, the payload is rejected
     *     and the task/workflow execution fails.
     */
    boolean isExternalPayloadStorageEnabled();
}
