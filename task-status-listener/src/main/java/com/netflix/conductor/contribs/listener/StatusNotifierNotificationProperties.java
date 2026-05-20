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

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.status-notifier.notification")
public class StatusNotifierNotificationProperties {

    private String url;

    private String endpointTask;

    /*
     * TBD: list of Task status we are interested in
     */
    private List<String> subscribedTaskStatuses;

    private String endpointWorkflow;

    private List<String> subscribedWorkflowStatuses;

    private String headerPrefer = "";

    private String headerPreferValue = "";

    private int requestTimeOutMsConnect = 100;

    private int requestTimeoutMsread = 300;

    private int requestTimeoutMsConnMgr = 300;

    private int requestRetryCount = 3;

    private int requestRetryCountIntervalMs = 50;

    private int connectionPoolMaxRequest = 3;

    private int connectionPoolMaxRequestPerRoute = 3;

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

    public String getHeaderPrefer() {
        return headerPrefer;
    }

    public void setHeaderPrefer(String headerPrefer) {
        this.headerPrefer = headerPrefer;
    }

    public String getHeaderPreferValue() {
        return headerPreferValue;
    }

    public void setHeaderPreferValue(String headerPreferValue) {
        this.headerPreferValue = headerPreferValue;
    }

    public int getRequestTimeOutMsConnect() {
        return requestTimeOutMsConnect;
    }

    public void setRequestTimeOutMsConnect(int requestTimeOutMsConnect) {
        this.requestTimeOutMsConnect = requestTimeOutMsConnect;
    }

    public int getRequestTimeoutMsread() {
        return requestTimeoutMsread;
    }

    public void setRequestTimeoutMsread(int requestTimeoutMsread) {
        this.requestTimeoutMsread = requestTimeoutMsread;
    }

    public int getRequestTimeoutMsConnMgr() {
        return requestTimeoutMsConnMgr;
    }

    public void setRequestTimeoutMsConnMgr(int requestTimeoutMsConnMgr) {
        this.requestTimeoutMsConnMgr = requestTimeoutMsConnMgr;
    }

    public int getRequestRetryCount() {
        return requestRetryCount;
    }

    public void setRequestRetryCount(int requestRetryCount) {
        this.requestRetryCount = requestRetryCount;
    }

    public int getRequestRetryCountIntervalMs() {
        return requestRetryCountIntervalMs;
    }

    public void setRequestRetryCountIntervalMs(int requestRetryCountIntervalMs) {
        this.requestRetryCountIntervalMs = requestRetryCountIntervalMs;
    }

    public int getConnectionPoolMaxRequest() {
        return connectionPoolMaxRequest;
    }

    public void setConnectionPoolMaxRequest(int connectionPoolMaxRequest) {
        this.connectionPoolMaxRequest = connectionPoolMaxRequest;
    }

    public int getConnectionPoolMaxRequestPerRoute() {
        return connectionPoolMaxRequestPerRoute;
    }

    public void setConnectionPoolMaxRequestPerRoute(int connectionPoolMaxRequestPerRoute) {
        this.connectionPoolMaxRequestPerRoute = connectionPoolMaxRequestPerRoute;
    }

    public List<String> getSubscribedTaskStatuses() {
        return subscribedTaskStatuses;
    }

    public void setSubscribedTaskStatuses(List<String> subscribedTaskStatuses) {
        this.subscribedTaskStatuses = subscribedTaskStatuses;
    }

    public List<String> getSubscribedWorkflowStatuses() {
        return subscribedWorkflowStatuses;
    }

    public void setSubscribedWorkflowStatuses(List<String> subscribedWorkflowStatuses) {
        this.subscribedWorkflowStatuses = subscribedWorkflowStatuses;
    }
}
