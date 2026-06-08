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

import java.util.concurrent.atomic.AtomicReference;

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
     * Immutable snapshot combining circuit breaker state and counter value.
     * This enables atomic state transitions that include counter updates,
     * preventing race conditions between state changes and counter resets.
     */
    private static final class StateAndCounter {

        private final AbstractCircuitBreaker.State state;
        private final long counter;

        StateAndCounter(final AbstractCircuitBreaker.State state, final long counter) {
            this.state = state;
            this.counter = counter;
        }

        AbstractCircuitBreaker.State getState() {
            return state;
        }

        long getCounter() {
            return counter;
        }

        StateAndCounter withState(final AbstractCircuitBreaker.State newState) {
            return new StateAndCounter(newState, this.counter);
        }

        StateAndCounter withCounter(final long newCounter) {
            return new StateAndCounter(this.state, newCounter);
        }

        StateAndCounter withStateAndCounter(final AbstractCircuitBreaker.State newState, final long newCounter) {
            return new StateAndCounter(newState, newCounter);
        }
    }

    /**
     * The initial value of the internal counter.
     */
    private static final long INITIAL_COUNT = 0L;

    /**
     * The threshold.
     */
    private final long threshold;

    /**
     * Combined state and counter for atomic operations.
     * This replaces the separate AtomicReference<State> and AtomicLong used
     * in the original implementation, eliminating the race condition between
     * incrementAndCheckState() and close().
     */
    private final AtomicReference<StateAndCounter> stateAndCounter = new AtomicReference<>(
            new StateAndCounter(State.CLOSED, INITIAL_COUNT));

    /**
     * Creates a new instance of {@link ThresholdCircuitBreaker} and initializes the threshold.
     *
     * @param threshold the threshold.
     */
    public ThresholdCircuitBreaker(final long threshold) {
        this.threshold = threshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkState() {
        return stateAndCounter.get().getState() == State.CLOSED;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Atomically resets both the state and counter using a single CAS loop.
     * This eliminates the race condition where a concurrent thread could detect
     * a threshold breach and attempt to open the circuit breaker while close()
     * is executing.</p>
     *
     * <p>The fix ensures that state transitions and counter resets happen as a
     * single atomic operation. If another thread has already transitioned the
     * state between reading and updating, the CAS will fail and retry with the
     * latest state.</p>
     */
    @Override
    public void close() {
        StateAndCounter current;
        StateAndCounter updated;
        do {
            current = stateAndCounter.get();
            if (current.getState() == State.CLOSED && current.getCounter() == INITIAL_COUNT) {
                return;
            }
            updated = current.withStateAndCounter(State.CLOSED, INITIAL_COUNT);
        } while (!stateAndCounter.compareAndSet(current, updated));

        fireStateChange(true, false);
        super.state.set(State.CLOSED);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the threshold is zero, the circuit breaker will be in a permanent <em>open</em> state.</p>
     *
     * <p>This method uses a CAS loop to atomically update both the counter and state,
     * ensuring that the counter value captured at threshold breach is consistent with
     * the state transition. This prevents the race condition where the counter could
     * be reset by a concurrent close() call before the exception context is captured.</p>
     *
     * <p>The counter value at the moment of threshold breach is embedded in the
     * StateAndCounter snapshot, so even if another thread calls close() immediately
     * after, the exception will carry the correct count value.</p>
     */
    @Override
    public boolean incrementAndCheckState(final Long increment) {
        if (threshold == 0) {
            open();
            return false;
        }

        StateAndCounter current;
        StateAndCounter updated;
        long newValue;
        boolean thresholdBreached;
        do {
            current = stateAndCounter.get();
            if (current.getState() == State.OPEN) {
                return false;
            }
            newValue = current.getCounter() + increment;
            thresholdBreached = newValue > threshold;
            if (thresholdBreached) {
                updated = current.withStateAndCounter(State.OPEN, newValue);
            } else {
                updated = current.withCounter(newValue);
            }
        } while (!stateAndCounter.compareAndSet(current, updated));

        if (thresholdBreached) {
            fireStateChange(false, true);
            super.state.set(State.OPEN);
        }

        return !thresholdBreached;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() {
        StateAndCounter current;
        StateAndCounter updated;
        do {
            current = stateAndCounter.get();
            if (current.getState() == State.OPEN) {
                return;
            }
            updated = current.withState(State.OPEN);
        } while (!stateAndCounter.compareAndSet(current, updated));

        fireStateChange(false, true);
        super.state.set(State.OPEN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen() {
        return stateAndCounter.get().getState() == State.OPEN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return !isOpen();
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
     * Gets the current usage count.
     *
     * @return the current usage count
     */
    public long getUsed() {
        return stateAndCounter.get().getCounter();
    }

    /**
     * Fires a state change event to registered listeners.
     *
     * @param oldValue the old open state (false = closed, true = open)
     * @param newValue the new open state
     */
    private void fireStateChange(final boolean oldValue, final boolean newValue) {
        changeSupport.firePropertyChange(PROPERTY_NAME, oldValue, newValue);
    }

}
