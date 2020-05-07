package com.netflix.conductor.tests.utils;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.jetty.server.JettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;

@Singleton
public class TestJettyServer {

    private static final Logger logger = LoggerFactory.getLogger(TestJettyServer.class);

    public static final int SERVER_PORT = 8903;
    public static final String API_ROOT = String.format("http://localhost:%d/api/", SERVER_PORT);

    private final JettyServer jettyServer;

    @PostConstruct
    public void init() {
        try {
            jettyServer.start();
        } catch (Exception e) {
            logger.error("Error starting the server ", e);
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            jettyServer.stop();
        } catch (Exception e) {
            logger.error("Error stopping the server ", e);
            throw new RuntimeException(e);
        }
    }

    public TestJettyServer() {
        jettyServer = new JettyServer(SERVER_PORT, false);
    }


    public static TaskClient getTaskClient() {
        TaskClient taskClient = new TaskClient();
        taskClient.setRootURI(API_ROOT);
        return taskClient;
    }

    public static WorkflowClient getWorkflowClient() {
        WorkflowClient workflowClient = new WorkflowClient();
        workflowClient.setRootURI(API_ROOT);
        return  workflowClient;
    }

    public static MetadataClient getMetaDataClient() {
        MetadataClient metadataClient = new MetadataClient();
        metadataClient.setRootURI(API_ROOT);
        return metadataClient;
    }

}
