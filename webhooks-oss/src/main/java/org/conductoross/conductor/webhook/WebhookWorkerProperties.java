package org.conductoross.conductor.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("conductor.webhook.worker")
public class WebhookWorkerProperties {

    public static final String WEBHOOK_QUEUE = "_webhook_queue";

    private int threadCount = 1;

    private int pollingInterval = 1000;       //in millisecond

    private int pollBatchSize = 10;

    private int lastRunWorkflowIdSize = 10;

    public int getLastRunWorkflowIdSize() {
        return lastRunWorkflowIdSize;
    }

    public void setLastRunWorkflowIdSize(int lastRunWorkflowIdSize) {
        this.lastRunWorkflowIdSize = lastRunWorkflowIdSize;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(int pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public int getPollBatchSize() {
        return pollBatchSize;
    }

    public void setPollBatchSize(int pollBatchSize) {
        this.pollBatchSize = pollBatchSize;
    }
}
