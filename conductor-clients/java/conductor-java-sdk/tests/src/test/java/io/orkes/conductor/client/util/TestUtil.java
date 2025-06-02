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

public class TestUtil {
    private static int RETRY_ATTEMPT_LIMIT = 4;

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
}
