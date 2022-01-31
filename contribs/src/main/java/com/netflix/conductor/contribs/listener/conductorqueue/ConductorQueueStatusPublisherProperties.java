/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.contribs.listener.conductorqueue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.workflow-status-listener.queue-publisher")
public class ConductorQueueStatusPublisherProperties {

    private String successQueue = "_callbackSuccessQueue";

    private String failureQueue = "_callbackFailureQueue";

    private String finalizeQueue = "_callbackFinalizeQueue";

    public String getSuccessQueue() {
        return successQueue;
    }

    public void setSuccessQueue(String successQueue) {
        this.successQueue = successQueue;
    }

    public String getFailureQueue() {
        return failureQueue;
    }

    public void setFailureQueue(String failureQueue) {
        this.failureQueue = failureQueue;
    }

    public String getFinalizeQueue() {
        return finalizeQueue;
    }

    public void setFinalizeQueue(String finalizeQueue) {
        this.finalizeQueue = finalizeQueue;
    }
}
