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
     * Controls the amount used.
     */
    private final AtomicLong used;

    /**
     * The timeout in milliseconds before transitioning from OPEN to HALF_OPEN.
     * A value of 0 means no automatic transition (legacy behavior).
     */
    private final long timeoutMillis;

    /**
     * The timestamp (in milliseconds) when the circuit breaker was last opened.
     */
    private volatile long openedAtMillis;

    /**
     * Creates a new instance of {@link ThresholdCircuitBreaker} and initializes the threshold.
     *
     * @param threshold the threshold.
     */
    public ThresholdCircuitBreaker(final long threshold) {
        this(threshold, 0);
    }

    /**
     * Creates a new instance of {@link ThresholdCircuitBreaker} and initializes the threshold
     * and the timeout for automatic transition to half-open state.
     *
     * @param threshold the threshold.
     * @param timeoutMillis the timeout in milliseconds after which the circuit breaker
     *        transitions from OPEN to HALF_OPEN. A value of 0 means no automatic transition.
     * @since 4.5
     */
    public ThresholdCircuitBreaker(final long threshold, final long timeoutMillis) {
        this.used = new AtomicLong(INITIAL_COUNT);
        this.threshold = threshold;
        this.timeoutMillis = timeoutMillis;
        this.openedAtMillis = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkState() {
        checkThreshold();
        return !isOpen();
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
     * Gets the timeout in milliseconds for automatic transition from OPEN to HALF_OPEN.
     *
     * @return the timeout in milliseconds, or 0 if no automatic transition is configured.
     * @since 4.5
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
        checkThreshold();

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
     */
    @Override
    public void open() {
        final State previous = state.get();
        if (previous != State.OPEN && previous != State.HALF_OPEN && previous != State.PROBING) {
            openedAtMillis = System.currentTimeMillis();
        }
        super.open();
    }

    /**
     * Checks whether the circuit breaker should transition from OPEN to HALF_OPEN
     * based on the configured timeout.
     */
    private void checkThreshold() {
        if (timeoutMillis <= 0) {
            return;
        }
        if (state.get() == State.OPEN) {
            final long elapsed = System.currentTimeMillis() - openedAtMillis;
            if (elapsed >= timeoutMillis) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
            }
        }
    }

}
