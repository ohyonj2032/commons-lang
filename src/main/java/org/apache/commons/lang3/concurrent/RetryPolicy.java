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
 * Provides retry policy logic for executing operations with automatic retry on failure.
 *
 * <p>
 * When a {@link FailableFunction} throws an exception during execution, the retry policy
 * will attempt to re-execute the function up to the specified maximum number of retries.
 * If all retry attempts are exhausted, a {@link ContextedException} is thrown with
 * contextual information about the failure.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 * <pre>
 *   String result = RetryPolicy.processWithRetry(
 *       input -&gt; remoteService.call(input),
 *       "requestPayload",
 *       3
 *   );
 * </pre>
 *
 * @since 3.21.0
 */
public final class RetryPolicy {

    private RetryPolicy() {
    }

    /**
     * Executes the given function with retry support.
     * <p>
     * When the function throws an exception, it will be retried up to {@code maxRetries} times.
     * If the function still fails after all retries, a {@link ContextedException} is thrown
     * wrapping the last caught exception.
     * </p>
     *
     * @param <T> the type of the input to the function.
     * @param <R> the type of the result of the function.
     * @param <E> the type of exception thrown by the function.
     * @param function the function to execute with retry.
     * @param input the input to the function.
     * @param maxRetries the maximum number of retry attempts, must be &gt;= 0.
     * @return the result of the function.
     * @throws ContextedException if the function fails after all retries.
     * @throws IllegalArgumentException if {@code maxRetries} is negative.
     * @throws NullPointerException if {@code function} is null.
     */
    public static <T, R, E extends Exception> R processWithRetry(final FailableFunction<T, R, E> function, final T input, final int maxRetries) {
        if (function == null) {
            throw new NullPointerException("function");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
        }
        int attempt = 0;
        Exception lastException = null;
        while (attempt <= maxRetries) {
            try {
                return function.apply(input);
            } catch (final Exception e) {
                lastException = e;
                attempt++;
            }
        }
        throw new ContextedException("Failed after " + maxRetries + " retries", lastException)
                .addContextValue("Max Retries", maxRetries)
                .addContextValue("Attempts", attempt);
    }
}