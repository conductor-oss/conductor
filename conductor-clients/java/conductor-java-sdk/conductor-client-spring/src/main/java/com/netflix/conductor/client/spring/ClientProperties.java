/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.client.spring;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conductor.client")
public class ClientProperties {

    public static class Timeout {
        private int connect = -1;
        private int read = -1;
        private int write = -1;

        public int getConnect() {
            return connect;
        }

        public void setConnect(int connect) {
            this.connect = connect;
        }

        public int getRead() {
            return read;
        }

        public void setRead(int read) {
            this.read = read;
        }

        public int getWrite() {
            return write;
        }

        public void setWrite(int write) {
            this.write = write;
        }
    }

    private String rootUri;

    private String basePath;

    private String workerNamePrefix = "workflow-worker-%d";

    private int threadCount = 1;

    private Duration sleepWhenRetryDuration = Duration.ofMillis(500);

    private int updateRetryCount = 3;

    private Map<String, String> taskToDomain = new HashMap<>();

    private Map<String, Integer> taskThreadCount = new HashMap<>();

    private int shutdownGracePeriodSeconds = 10;

    private int taskPollTimeout = 100;

    private Timeout timeout = new Timeout();

    private boolean verifyingSsl = true;

    public void setVerifyingSsl(boolean verifyingSsl) {
        this.verifyingSsl = verifyingSsl;
    }

    public boolean isVerifyingSsl() {
        return verifyingSsl;
    }

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    public String getRootUri() {
        return rootUri;
    }

    public void setRootUri(String rootUri) {
        this.rootUri = rootUri;
    }

    public String getWorkerNamePrefix() {
        return workerNamePrefix;
    }

    public void setWorkerNamePrefix(String workerNamePrefix) {
        this.workerNamePrefix = workerNamePrefix;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public Duration getSleepWhenRetryDuration() {
        return sleepWhenRetryDuration;
    }

    public void setSleepWhenRetryDuration(Duration sleepWhenRetryDuration) {
        this.sleepWhenRetryDuration = sleepWhenRetryDuration;
    }

    public int getUpdateRetryCount() {
        return updateRetryCount;
    }

    public void setUpdateRetryCount(int updateRetryCount) {
        this.updateRetryCount = updateRetryCount;
    }

    public Map<String, String> getTaskToDomain() {
        return taskToDomain;
    }

    public void setTaskToDomain(Map<String, String> taskToDomain) {
        this.taskToDomain = taskToDomain;
    }

    public int getShutdownGracePeriodSeconds() {
        return shutdownGracePeriodSeconds;
    }

    public void setShutdownGracePeriodSeconds(int shutdownGracePeriodSeconds) {
        this.shutdownGracePeriodSeconds = shutdownGracePeriodSeconds;
    }

    public Map<String, Integer> getTaskThreadCount() {
        return taskThreadCount;
    }

    public void setTaskThreadCount(Map<String, Integer> taskThreadCount) {
        this.taskThreadCount = taskThreadCount;
    }

    public int getTaskPollTimeout() {
        return taskPollTimeout;
    }

    public void setTaskPollTimeout(int taskPollTimeout) {
        this.taskPollTimeout = taskPollTimeout;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
