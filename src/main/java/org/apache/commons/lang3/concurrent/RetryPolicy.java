/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.lang3.concurrent;

import org.apache.commons.lang3.exception.ContextedException;
import org.apache.commons.lang3.function.FailableFunction;

/**
 * A utility class that provides retry logic for executing functions that may throw exceptions.
 * <p>
 * When a {@link FailableFunction} throws an exception, this class will retry the execution
 * up to a specified maximum number of times. If all retry attempts fail, a
 * {@link ContextedException} is thrown containing the last exception and retry context information.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * <pre>
 * RetryPolicy policy = new RetryPolicy(3);
 * String result = policy.execute(input, this::riskyOperation);
 * </pre>
 *
 * @since 3.21.0
 */
public class RetryPolicy {

    /**
     * The default maximum number of retry attempts.
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * The maximum number of retry attempts.
     */
    private final int maxRetries;

    /**
     * Constructs a RetryPolicy with the default maximum retry count.
     */
    public RetryPolicy() {
        this(DEFAULT_MAX_RETRIES);
    }

    /**
     * Constructs a RetryPolicy with the specified maximum retry count.
     *
     * @param maxRetries the maximum number of retry attempts
     * @throws IllegalArgumentException if maxRetries is negative
     */
    public RetryPolicy(final int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative: " + maxRetries);
        }
        this.maxRetries = maxRetries;
    }

    /**
     * Executes the given function with retry logic.
     * <p>
     * If the function throws an exception, it will be retried up to {@code maxRetries} times.
     * If all retries fail, a {@link ContextedException} is thrown containing the last exception
     * and retry context information.
     * </p>
     *
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     * @param <E> the type of thrown exception or error
     * @param input the input to pass to the function
     * @param function the function to execute
     * @return the result of the successful function execution
     * @throws ContextedException if all retry attempts fail
     */
    public <T, R, E extends Throwable> R execute(final T input, final FailableFunction<T, R, E> function) throws ContextedException {
        Exception lastException = null;
        final int totalAttempts = maxRetries + 1;

        for (int attempt = 0; attempt < totalAttempts; attempt++) {
            try {
                return function.apply(input);
            } catch (final Throwable e) {
                lastException = e instanceof Exception ? (Exception) e : new Exception(e);
            }
        }

        throw new ContextedException(
                "Failed after " + totalAttempts + " attempts",
                lastException
        ).addContextValue("MaxRetries", maxRetries)
         .addContextValue("TotalAttempts", totalAttempts);
    }

    /**
     * Executes the given function with retry logic, without input.
     * <p>
     * If the function throws an exception, it will be retried up to {@code maxRetries} times.
     * If all retries fail, a {@link ContextedException} is thrown containing the last exception
     * and retry context information.
     * </p>
     *
     * @param <R> the type of the result of the function
     * @param <E> the type of thrown exception or error
     * @param function the function to execute
     * @return the result of the successful function execution
     * @throws ContextedException if all retry attempts fail
     */
    public <R, E extends Throwable> R execute(final FailableFunction<Void, R, E> function) throws ContextedException {
        return execute(null, function);
    }

    /**
     * Gets the maximum number of retry attempts.
     *
     * @return the maximum number of retry attempts
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Creates a RetryPolicy with the specified maximum retry count.
     *
     * @param maxRetries the maximum number of retry attempts
     * @return a new RetryPolicy instance
     * @throws IllegalArgumentException if maxRetries is negative
     */
    public static RetryPolicy of(final int maxRetries) {
        return new RetryPolicy(maxRetries);
    }

    /**
     * Creates a RetryPolicy with the default maximum retry count.
     *
     * @return a new RetryPolicy instance
     */
    public static RetryPolicy defaults() {
        return new RetryPolicy();
    }
}
