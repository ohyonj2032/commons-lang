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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for circuit breakers.
 *
 * @param <T> the type of the value monitored by this circuit breaker
 * @since 3.5
 */
public abstract class AbstractCircuitBreaker<T> implements CircuitBreaker<T> {

    /**
     * Enumerates the different states of a circuit breaker. This class also contains some logic for performing state transitions. This is done to avoid complex
     * if-conditions in the code of {@link CircuitBreaker}.
     */
    protected enum State {

        /** The closed state. */
        CLOSED {

            /**
             * {@inheritDoc}
             */
            @Override
            public State oppositeState() {
                return OPEN;
            }
        },

        /** The open state. */
        OPEN {

            /**
             * {@inheritDoc}
             */
            @Override
            public State oppositeState() {
                return CLOSED;
            }
        };

        /**
         * Returns the opposite state to the represented state. This is useful
         * for flipping the current state.
         *
         * @return the opposite state
         */
        public abstract State oppositeState();
    }

    /**
     * The name of the <em>open</em> property as it is passed to registered
     * change listeners.
     */
    public static final String PROPERTY_NAME = "open";

    /**
     * Converts the given state value to a boolean <em>open</em> property.
     *
     * @param state the state to be converted
     * @return the boolean open flag
     */
    protected static boolean isOpen(final State state) {
        return state == State.OPEN;
    }

    /** The current state of this circuit breaker. */
    protected final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    /**
     * A monotonically increasing version counter that is incremented on every
     * successful state transition. Used by subclasses to detect concurrent
     * state modifications and prevent stale-state reopen after close().
     */
    protected final AtomicLong stateVersion = new AtomicLong();

    /** An object for managing change listeners registered at this instance. */
    private final PropertyChangeSupport changeSupport;

    /**
     * Creates an {@link AbstractCircuitBreaker}. It also creates an internal {@link PropertyChangeSupport}.
     */
    public AbstractCircuitBreaker() {
        changeSupport = new PropertyChangeSupport(this);
    }

    /**
     * Adds a change listener to this circuit breaker. This listener is notified whenever
     * the state of this circuit breaker changes. If the listener is
     * {@code null}, it is silently ignored.
     *
     * @param listener the listener to be added
     */
    public void addChangeListener(final PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Changes the internal state of this circuit breaker. If there is actually a change
     * of the state value, all registered change listeners are notified.
     *
     * <p>
     * The state version counter is incremented atomically after the state transition
     * and after the optional {@code onSuccess} callback completes, ensuring that any
     * thread capturing the version before the callback (e.g. counter reset) will see
     * the version change and abort its stale state transition.
     * </p>
     *
     * @param newState the new state to be set
     */
    protected void changeState(final State newState) {
        changeState(newState, null);
    }

    /**
     * Changes the internal state of this circuit breaker with an optional callback
     * that is executed atomically within the state transition window. The callback
     * runs <em>before</em> the version counter is incremented, so that any concurrent
     * thread that captured the old version will see the version mismatch and abort.
     * This guarantees the atomicity linkage between state switching and subclass
     * side effects (e.g. counter reset).
     *
     * @param newState the new state to be set
     * @param onSuccess a callback executed after the CAS succeeds but before the
     *                  version counter is incremented; may be {@code null}
     */
    protected void changeState(final State newState, final Runnable onSuccess) {
        if (state.compareAndSet(newState.oppositeState(), newState)) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            changeSupport.firePropertyChange(PROPERTY_NAME, !isOpen(newState), isOpen(newState));
            stateVersion.incrementAndGet();
        }
    }

    /**
     * Attempts a single-shot state transition from {@code expectedState} to
     * {@code newState}, but only if the state version matches {@code expectedVersion}.
     * This is a non-retrying CAS used by subclasses in {@code incrementAndCheckState}
     * to prevent reopening the breaker based on stale counter data after a concurrent
     * {@code close()} call.
     *
     * <p>
     * If the version has changed since the caller captured it, this method returns
     * {@code false} immediately without attempting the CAS, because a concurrent
     * state transition (e.g. {@code close()}) has already occurred.
     * </p>
     *
     * @param expectedState the expected current state
     * @param newState the desired new state
     * @param expectedVersion the state version captured before reading the counter
     * @return {@code true} if the state was successfully transitioned
     */
    protected boolean tryChangeState(final State expectedState, final State newState,
            final long expectedVersion) {
        if (stateVersion.get() != expectedVersion) {
            return false;
        }
        if (state.compareAndSet(expectedState, newState)) {
            changeSupport.firePropertyChange(PROPERTY_NAME, !isOpen(newState), isOpen(newState));
            stateVersion.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Returns the current state version. Subclasses use this to capture a version
     * snapshot before reading their internal counters, enabling detection of
     * concurrent state transitions.
     *
     * @return the current state version
     */
    protected long getStateVersion() {
        return stateVersion.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract boolean checkState();

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        changeState(State.CLOSED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract boolean incrementAndCheckState(T increment);

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return !isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen() {
        return isOpen(state.get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() {
        changeState(State.OPEN);
    }

    /**
     * Removes the specified change listener from this circuit breaker.
     *
     * @param listener the listener to be removed
     */
    public void removeChangeListener(final PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

}