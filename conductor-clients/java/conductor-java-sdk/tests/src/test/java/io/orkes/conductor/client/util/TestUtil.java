/*
 * Copyright 2023 Conductor Authors.
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
package io.orkes.conductor.client.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import io.orkes.conductor.client.http.OrkesWorkflowClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeoutException;

public class TestUtil {
    private static int RETRY_ATTEMPT_LIMIT = 4;
    protected static ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    public static void retryMethodCall(VoidRunnableWithException function)
            throws Exception {
        Exception lastException = null;
        for (int retryCounter = 0; retryCounter < RETRY_ATTEMPT_LIMIT; retryCounter += 1) {
            try {
                function.run();
                return;
            } catch (Exception e) {
                lastException = e;
                System.out.println("Attempt " + (retryCounter + 1) + " failed: " + e.getMessage());
                try {
                    Thread.sleep(1000 * (1 << retryCounter)); // Sleep for 2^retryCounter second(s) before retrying
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
        throw lastException;
    }

    public static Object retryMethodCall(RunnableWithException function)
            throws Exception {
        Exception lastException = null;
        for (int retryCounter = 0; retryCounter < RETRY_ATTEMPT_LIMIT; retryCounter += 1) {
            try {
                return function.run();
            } catch (Exception e) {
                lastException = e;
                System.out.println("Attempt " + (retryCounter + 1) + " failed: " + e.getMessage());
                try {
                    Thread.sleep(1000 * (1 << retryCounter)); // Sleep for 2^retryCounter second(s) before retrying
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
        throw lastException;
    }

    @FunctionalInterface
    public interface RunnableWithException {
        Object run() throws Exception;
    }

    @FunctionalInterface
    public interface VoidRunnableWithException {
        void run() throws Exception;
    }

    public static WorkflowDef getWorkflowDef(String path) throws IOException {
        InputStream inputStream = TestUtil.class.getResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("No file found at " + path);
        }
        return objectMapper.readValue(new InputStreamReader(inputStream), WorkflowDef.class);
    }

    /**
     * Waits for a workflow to reach the expected status with polling
     *
     * @param workflowId the workflow ID to monitor
     * @param expectedStatus the expected workflow status
     * @param maxWaitTimeMs maximum time to wait in milliseconds
     * @param pollIntervalMs polling interval in milliseconds
     * @return the final workflow details
     * @throws TimeoutException if the workflow doesn't reach expected status within maxWaitTime
     */
    public static Workflow waitForWorkflowStatus(OrkesWorkflowClient workflowClient,
                                                 String workflowId,
                                                 Workflow.WorkflowStatus expectedStatus,
                                                 long maxWaitTimeMs,
                                                 long pollIntervalMs) throws Exception {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + maxWaitTimeMs;

        while (System.currentTimeMillis() < endTime) {
            Workflow workflowDetails = workflowClient.getWorkflow(workflowId, true);

            if (expectedStatus.equals(workflowDetails.getStatus())) {
                return workflowDetails; // Success!
            }

            // Check if workflow failed or terminated
            if (workflowDetails.getStatus() == Workflow.WorkflowStatus.FAILED ||
                    workflowDetails.getStatus() == Workflow.WorkflowStatus.TERMINATED) {
                throw new RuntimeException("Workflow ended in unexpected state: " + workflowDetails.getStatus());
            }

            Thread.sleep(pollIntervalMs);
        }

        // Timeout
        Workflow finalState = workflowClient.getWorkflow(workflowId, true);
        throw new TimeoutException(
                String.format("Workflow did not reach status %s within %dms. Current status: %s",
                        expectedStatus, maxWaitTimeMs, finalState.getStatus()));
    }
}
