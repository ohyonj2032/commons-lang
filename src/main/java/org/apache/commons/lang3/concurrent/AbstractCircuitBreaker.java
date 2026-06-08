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
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractCircuitBreaker<T> implements CircuitBreaker<T> {

    protected enum State {

        CLOSED {

            @Override
            public State oppositeState() {
                return OPEN;
            }
        },

        OPEN {

            @Override
            public State oppositeState() {
                return CLOSED;
            }
        };

        public abstract State oppositeState();
    }

    public static final String PROPERTY_NAME = "open";

    protected static boolean isOpen(final State state) {
        return state == State.OPEN;
    }

    protected final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    private final PropertyChangeSupport changeSupport;

    public AbstractCircuitBreaker() {
        changeSupport = new PropertyChangeSupport(this);
    }

    public void addChangeListener(final PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    protected boolean changeState(final State expectedState, final State newState) {
        if (state.compareAndSet(expectedState, newState)) {
            changeSupport.firePropertyChange(PROPERTY_NAME, !isOpen(newState), isOpen(newState));
            return true;
        }
        return false;
    }

    @Override
    public abstract boolean checkState();

    @Override
    public void close() {
        if (changeState(State.OPEN, State.CLOSED)) {
            onClose();
        }
    }

    protected void onClose() {
    }

    @Override
    public abstract boolean incrementAndCheckState(T increment);

    @Override
    public boolean isClosed() {
        return !isOpen();
    }

    @Override
    public boolean isOpen() {
        return isOpen(state.get());
    }

    @Override
    public void open() {
        if (changeState(State.CLOSED, State.OPEN)) {
            onOpen();
        }
    }

    protected void onOpen() {
    }

    public void removeChangeListener(final PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

}
