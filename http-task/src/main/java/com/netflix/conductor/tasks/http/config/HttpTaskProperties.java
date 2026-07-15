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
package com.netflix.conductor.tasks.http.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("conductor.tasks.http")
public class HttpTaskProperties {

    /**
     * List of regular expressions that define allowed URIs for HttpTask. If empty, all URIs are
     * allowed.
     */
    private List<String> urlAllowList = new ArrayList<>();

    public List<String> getUrlAllowList() {
        return urlAllowList;
    }

    public void setUrlAllowList(List<String> urlAllowList) {
        this.urlAllowList = urlAllowList;
    }
}
