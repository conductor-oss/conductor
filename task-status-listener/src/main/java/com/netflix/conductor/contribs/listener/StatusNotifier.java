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
package com.netflix.conductor.contribs.listener;

public class StatusNotifier {

    private String url;

    private String endpointTask;

    private String endpointWorkflow;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEndpointTask() {
        return endpointTask;
    }

    public void setEndpointTask(String endpointTask) {
        this.endpointTask = endpointTask;
    }

    public String getEndpointWorkflow() {
        return endpointWorkflow;
    }

    public void setEndpointWorkflow(String endpointWorkflow) {
        this.endpointWorkflow = endpointWorkflow;
    }
}
