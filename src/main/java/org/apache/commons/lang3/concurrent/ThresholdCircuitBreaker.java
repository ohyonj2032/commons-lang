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
     * Stores the counter value snapshot captured at the moment the threshold was
     * exceeded. This provides a consistent, race-free snapshot for callers that
     * need to include the triggering value in exception contexts, rather than
     * reading the potentially stale or reset {@link #used} counter directly.
     */
    private final AtomicLong lastTriggeredValue;

    /**
     * Creates a new instance of {@link ThresholdCircuitBreaker} and initializes the threshold.
     *
     * @param threshold the threshold.
     */
    public ThresholdCircuitBreaker(final long threshold) {
        this.used = new AtomicLong(INITIAL_COUNT);
        this.threshold = threshold;
        this.lastTriggeredValue = new AtomicLong(INITIAL_COUNT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkState() {
        return !isOpen();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Resets the internal counter back to its initial value (zero). The counter
     * reset is executed atomically within the state transition window via
     * {@link #changeState(State, Runnable)}, ensuring that the counter is only
     * cleared when the state actually transitions from OPEN to CLOSED.
     * </p>
     */
    @Override
    public void close() {
        changeState(State.CLOSED, () -> this.used.set(INITIAL_COUNT));
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
     * Gets the last counter value snapshot that triggered the circuit breaker
     * to open. This value is captured at the exact moment the threshold was
     * exceeded, providing a consistent snapshot for exception context reporting
     * even if the counter has since been reset by a concurrent {@link #close()}.
     *
     * @return the counter value at the moment of the last threshold breach
     */
    public long getLastTriggeredValue() {
        return lastTriggeredValue.get();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * If the threshold is zero, the circuit breaker will be in a permanent
     * <em>open</em> state.
     * </p>
     *
     * <p>
     * This method captures the state version <em>before</em> reading the
     * internal counter, and uses {@link #tryChangeState(State, State, long)}
     * to perform a single-shot CAS that is aborted if a concurrent
     * {@link #close()} (or any other state transition) has occurred between
     * the version capture and the CAS. This prevents the circuit breaker from
     * reopening based on stale counter data after an explicit close.
     * </p>
     */
    @Override
    public boolean incrementAndCheckState(final Long increment) {
        if (threshold == 0) {
            lastTriggeredValue.set(0L);
            open();
            return checkState();
        }

        final long currentVersion = getStateVersion();
        final long used = this.used.addAndGet(increment);
        if (used > threshold) {
            lastTriggeredValue.set(used);
            tryChangeState(State.CLOSED, State.OPEN, currentVersion);
        }

        return checkState();
    }

}