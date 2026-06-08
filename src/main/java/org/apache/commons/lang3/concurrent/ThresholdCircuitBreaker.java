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

import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple implementation of the <a
 * href="https://martinfowler.com/bliki/CircuitBreaker.html">Circuit Breaker</a> pattern
 * that opens if the requested increment amount is greater than a given threshold.
 *
 * <p>
 * It contains an internal counter that starts in zero, and each call increments the counter by a given amount.
 * If the threshold is zero, the circuit breaker will be in a permanent <em>open</em> state.
 * </p>
 *
 * <p>
 * When a non-zero {@code timeoutMillis} is configured, this circuit breaker supports the
 * <em>half-open</em> state: after the breaker has been in the OPEN state for longer than
 * the configured timeout, it automatically transitions to HALF_OPEN on the next call to
 * {@link #checkState()} or {@link #incrementAndCheckState(Long)}. In the HALF_OPEN state,
 * a single probe request is allowed through via {@link #tryProbe()}; this probe determines
 * whether the monitored subsystem has recovered.
 * </p>
 *
 * <p>
 * An example of use case could be a memory circuit breaker.
 * </p>
 *
 * <pre>
 * long threshold = 10L;
 * ThresholdCircuitBreaker breaker = new ThresholdCircuitBreaker(10L);
 * ...
 * public void handleRequest(Request request) {
 *     long memoryUsed = estimateMemoryUsage(request);
 *     if (breaker.incrementAndCheckState(memoryUsed)) {
 *         // actually handle this request
 *     } else {
 *         // do something else, e.g. send an error code
 *     }
 * }
 * </pre>
 *
 * <p>#Thread safe#</p>
 *
 * @since 3.5
 */
public class ThresholdCircuitBreaker extends AbstractCircuitBreaker<Long> {

    /**
     * The initial value of the internal counter.
     */
    private static final long INITIAL_COUNT = 0L;

    /**
     * The threshold.
     */
    private final long threshold;

    /**
     * The timeout in milliseconds before the circuit breaker automatically
     * transitions from OPEN to HALF_OPEN. A value of 0 means no automatic
     * transition (the breaker stays OPEN indefinitely).
     */
    private final long timeoutMillis;

    /**
     * The system time in milliseconds when the circuit breaker last entered
     * the OPEN state.
     */
    private long openedTimeMillis;

    /**
     * Controls the amount used.
     */
    private final AtomicLong used;

    /**
     * Creates a new instance of {@link ThresholdCircuitBreaker} and initializes the threshold.
     * No timeout is configured, so the circuit breaker will never automatically transition
     * from OPEN to HALF_OPEN.
     *
     * @param threshold the threshold.
     */
    public ThresholdCircuitBreaker(final long threshold) {
        this(threshold, 0);
    }

    /**
     * Creates a new instance of {@link ThresholdCircuitBreaker} and initializes the
     * threshold and the timeout for automatic transition to the HALF_OPEN state.
     *
     * @param threshold the threshold; a value of 0 puts the breaker in permanent OPEN state
     * @param timeoutMillis the timeout in milliseconds before auto-transitioning from
     *                      OPEN to HALF_OPEN; a value of 0 disables auto-transition
     */
    public ThresholdCircuitBreaker(final long threshold, final long timeoutMillis) {
        super();
        this.used = new AtomicLong(INITIAL_COUNT);
        this.threshold = threshold;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Before checking the state, this method evaluates whether the timeout
     * has elapsed (via {@link #checkThreshold()}) and auto-transitions from
     * OPEN to HALF_OPEN if appropriate. The circuit breaker is considered
     * usable (returns <strong>true</strong>) when in the CLOSED or HALF_OPEN state.
     * </p>
     */
    @Override
    public boolean checkState() {
        checkThreshold();
        return isClosed() || isHalfOpen();
    }

    /**
     * Checks whether the configured timeout has elapsed since the circuit breaker
     * entered the OPEN state, and if so, atomically transitions the state from
     * OPEN to HALF_OPEN.
     *
     * <p>
     * This method is called by {@link #checkState()} before evaluating the
     * current state. It has no effect if {@code timeoutMillis} is 0 or if the
     * breaker is not currently in the OPEN state.
     * </p>
     */
    public void checkThreshold() {
        if (timeoutMillis > 0 && state.get() == State.OPEN) {
            final long elapsed = System.currentTimeMillis() - openedTimeMillis;
            if (elapsed >= timeoutMillis) {
                transitionToHalfOpen();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resets the internal counter back to its initial value (zero).</p>
     */
    @Override
    public void close() {
        super.close();
        this.used.set(INITIAL_COUNT);
    }

    /**
     * Gets the threshold.
     *
     * @return the threshold
     */
    public long getThreshold() {
        return threshold;
    }

    /**
     * Gets the timeout in milliseconds for automatic transition from OPEN
     * to HALF_OPEN.
     *
     * @return the timeout in milliseconds, or 0 if no timeout is configured
     */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the threshold is zero, the circuit breaker will be in a permanent <em>open</em> state.</p>
     */
    @Override
    public boolean incrementAndCheckState(final Long increment) {
        if (threshold == 0) {
            open();
        }

        final long used = this.used.addAndGet(increment);
        if (used > threshold) {
            open();
        }

        return checkState();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Records the current system time so that the timeout-based transition
     * to HALF_OPEN can be evaluated later.</p>
     */
    @Override
    public void open() {
        super.open();
        this.openedTimeMillis = System.currentTimeMillis();
    }
}