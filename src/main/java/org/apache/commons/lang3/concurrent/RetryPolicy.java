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

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ContextedException;
import org.apache.commons.lang3.function.FailableFunction;

/**
 * A retry policy that wraps a function and retries it when it throws an exception.
 * <p>
 * This class provides a mechanism to retry a function up to a specified number of times.
 * If the function fails beyond the maximum number of retries, a {@link ContextedException}
 * is thrown with information about the attempts.
 * </p>
 *
 * @since 3.22.0
 */
public class RetryPolicy {

    /**
     * Private constructor to prevent instantiation.
     */
    private RetryPolicy() {
        // Empty
    }

    /**
     * Executes the given function with retry logic.
     * <p>
     * If the function throws an exception, it will be retried up to {@code maxRetries} times.
     * After the maximum number of retries is exceeded, a {@link ContextedException} is thrown.
     * </p>
     *
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     * @param <E> the type of exception thrown by the function
     * @param function the function to execute
     * @param input the input to the function
     * @param maxRetries the maximum number of retries (must be >= 0)
     * @return the result of the function
     * @throws ContextedException if the function fails after all retries
     */
    public static <T, R, E extends Throwable> R executeWithRetry(
            final FailableFunction<T, R, E> function,
            final T input,
            final int maxRetries) throws ContextedException {
        Validate.notNull(function, "function");
        Validate.isTrue(maxRetries >= 0, "maxRetries must be >= 0");

        int attempts = 0;
        Throwable lastException = null;

        while (attempts <= maxRetries) {
            try {
                return function.apply(input);
            } catch (final Throwable t) {
                lastException = t;
                attempts++;
            }
        }

        throw new ContextedException("Function failed after " + (maxRetries + 1) + " attempts", lastException)
                .addContextValue("maxRetries", maxRetries)
                .addContextValue("attempts", attempts);
    }
}
