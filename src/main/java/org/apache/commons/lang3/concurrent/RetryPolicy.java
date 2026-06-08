package org.apache.commons.lang3.concurrent;

import org.apache.commons.lang3.exception.ContextedException;
import org.apache.commons.lang3.function.FailableFunction;

/**
 * A retry policy for executing failable functions.
 *
 * @since 3.15.0
 */
public class RetryPolicy {

    /**
     * Executes the given function with retry logic.
     *
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     * @param <E> the type of exception thrown by the function
     * @param function the function to execute
     * @param input the input to the function
     * @param maxRetries the maximum number of retries
     * @return the result of the function
     * @throws ContextedException if the maximum number of retries is exceeded
     */
    public static <T, R, E extends Exception> R execute(final FailableFunction<T, R, E> function, final T input, final int maxRetries) throws ContextedException {
        int attempt = 0;
        while (true) {
            try {
                return function.apply(input);
            } catch (final Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw new ContextedException("Operation failed after " + maxRetries + " retries", e)
                            .addContextValue("maxRetries", maxRetries)
                            .addContextValue("attempt", attempt);
                }
            }
        }
    }
}
