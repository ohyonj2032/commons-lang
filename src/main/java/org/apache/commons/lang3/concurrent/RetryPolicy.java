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

import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ContextedException;
import org.apache.commons.lang3.function.FailableFunction;

public final class RetryPolicy {

    private final int maxRetries;

    public RetryPolicy(final int maxRetries) {
        Validate.isTrue(maxRetries >= 0, "maxRetries must not be negative");
        this.maxRetries = maxRetries;
    }

    public <T, R, E extends Exception> R execute(final T input, final FailableFunction<? super T, R, E> function)
            throws ContextedException {
        Objects.requireNonNull(function, "function");
        Exception lastException = null;
        int attempts = 0;
        while (attempts <= maxRetries) {
            try {
                return function.apply(input);
            } catch (final Exception e) {
                lastException = e;
                attempts++;
            }
        }
        throw new ContextedException("Retry limit exceeded", lastException)
                .addContextValue("input", input)
                .addContextValue("maxRetries", Integer.valueOf(maxRetries))
                .addContextValue("attempts", Integer.valueOf(attempts));
    }

    public int getMaxRetries() {
        return maxRetries;
    }
}
