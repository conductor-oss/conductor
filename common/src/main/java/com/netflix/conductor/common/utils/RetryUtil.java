/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.common.utils;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicate;

import static java.lang.String.format;

/**
 * Utility class that deals with retries in case of transient failures.
 *
 * <p><b>Note:</b> Create a new {@link RetryUtil} for every operation that needs to retried for the
 * stated retries.
 *
 * <p><b>Limitations:</b>
 *
 * <ul>
 *   <li>The current implementation does not make a distinction between transient and non transient
 *       errors. There is no categorization of transient and non transient failure in Conductor.
 *       Once the exception hierarchy is available in Conductor, this class implementation can be
 *       changed to short circuit the non transient errors.
 *   <li>Currently only couple of wait strategies are implemented {@link
 *       WaitStrategies#exponentialWait()} and {@link WaitStrategies#randomWait(long, TimeUnit)}
 *       with fixed attributes for each of the strategies.
 *   <li>The retry limit is not configurable and is hard coded to 3
 * </ul>
 *
 * @param <T> The type of the object that will be returned by the flaky supplier function
 */
@SuppressWarnings("UnstableApiUsage")
public class RetryUtil<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryUtil.class);

    private final AtomicInteger internalNumberOfRetries = new AtomicInteger();

    /**
     * A helper method which has the ability to execute a flaky supplier function and retry in case
     * of failures.
     *
     * @param supplierCommand: Any function that is flaky and needs multiple retries.
     * @param throwablePredicate: A Guava {@link Predicate} housing the exceptional criteria to
     *     perform informed filtering before retrying.
     * @param resultRetryPredicate: a predicate to be evaluated for a valid condition of the
     *     expected result
     * @param retryCount: Number of times the function is to be retried before failure
     * @param shortDescription: A short description of the function that will be used in logging and
     *     error propagation. The intention of this description is to provide context for
     *     Operability.
     * @param operationName: The name of the function for traceability in logs
     * @return an instance of return type of the supplierCommand
     * @throws RuntimeException in case of failed attempts to get T, which needs to be returned by
     *     the supplierCommand. The instance of the returned exception has:
     *     <ul>
     *       <li>A message with shortDescription and operationName with the number of retries made
     *       <li>And a reference to the original exception generated during the last {@link Attempt}
     *           of the retry
     *     </ul>
     */
    @SuppressWarnings("Guava")
    public T retryOnException(
            Supplier<T> supplierCommand,
            Predicate<Throwable> throwablePredicate,
            Predicate<T> resultRetryPredicate,
            int retryCount,
            String shortDescription,
            String operationName)
            throws RuntimeException {

        Retryer<T> retryer =
                RetryerBuilder.<T>newBuilder()
                        .retryIfException(
                                Optional.ofNullable(throwablePredicate).orElse(exception -> true))
                        .retryIfResult(
                                Optional.ofNullable(resultRetryPredicate).orElse(result -> false))
                        .withWaitStrategy(
                                WaitStrategies.join(
                                        WaitStrategies.exponentialWait(1000, 90, TimeUnit.SECONDS),
                                        WaitStrategies.randomWait(
                                                100,
                                                TimeUnit.MILLISECONDS,
                                                500,
                                                TimeUnit.MILLISECONDS)))
                        .withStopStrategy(StopStrategies.stopAfterAttempt(retryCount))
                        .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                        .withRetryListener(
                                new RetryListener() {
                                    @Override
                                    public <V> void onRetry(Attempt<V> attempt) {
                                        LOGGER.debug(
                                                "Attempt # {}, {} millis since first attempt. Operation: {}, description:{}",
                                                attempt.getAttemptNumber(),
                                                attempt.getDelaySinceFirstAttempt(),
                                                operationName,
                                                shortDescription);
                                        internalNumberOfRetries.incrementAndGet();
                                    }
                                })
                        .build();

        try {
            return retryer.call(supplierCommand::get);
        } catch (ExecutionException executionException) {
            String errorMessage =
                    format(
                            "Operation '%s:%s' failed for the %d time in RetryUtil",
                            operationName, shortDescription, internalNumberOfRetries.get());
            LOGGER.debug(errorMessage);
            throw new RuntimeException(errorMessage, executionException.getCause());
        } catch (RetryException retryException) {
            String errorMessage =
                    format(
                            "Operation '%s:%s' failed after retrying %d times, retry limit %d",
                            operationName, shortDescription, internalNumberOfRetries.get(), 3);
            LOGGER.error(errorMessage, retryException.getLastFailedAttempt().getExceptionCause());
            throw new RuntimeException(
                    errorMessage, retryException.getLastFailedAttempt().getExceptionCause());
        }
    }
}
