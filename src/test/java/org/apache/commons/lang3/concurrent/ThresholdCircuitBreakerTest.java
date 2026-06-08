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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.lang3.AbstractLangTest;
import org.apache.commons.lang3.exception.ContextedRuntimeException;
import org.junit.jupiter.api.Test;

/**
 * Test class for {@code ThresholdCircuitBreaker}.
 */
class ThresholdCircuitBreakerTest extends AbstractLangTest {

    /**
     * Threshold used in tests.
     */
    private static final long threshold = 10L;

    private static final long zeroThreshold = 0L;

    /**
     * Tests that closing a {@code ThresholdCircuitBreaker} resets the internal counter.
     */
    @Test
    void testClosingThresholdCircuitBreaker() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        circuit.close();
        // now the internal counter is back at zero, not 9 anymore. So it is safe to increment 9 again
        assertTrue(circuit.incrementAndCheckState(9L), "Internal counter was not reset back to zero");
    }

    /**
     * Tests that we can get the threshold value correctly.
     */
    @Test
    void testGettingThreshold() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        assertEquals(Long.valueOf(threshold), Long.valueOf(circuit.getThreshold()), "Wrong value of threshold");
    }

    /**
     * Tests that the threshold is working as expected when incremented and no exception is thrown.
     */
    @Test
    void testThreshold() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        assertTrue(circuit.incrementAndCheckState(1L), "Circuit opened before reaching the threshold");
    }

    /**
     * Tests that exceeding the threshold raises an exception.
     */
    @Test
    void testThresholdCircuitBreakingException() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        assertFalse(circuit.incrementAndCheckState(2L), "The circuit was supposed to be open after increment above the threshold");
    }

    /**
     * Test that when threshold is zero, the circuit breaker is always open.
     */
    @Test
    void testThresholdEqualsZero() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(zeroThreshold);
        assertFalse(circuit.incrementAndCheckState(0L), "When the threshold is zero, the circuit is supposed to be always open");
    }

    /**
     * Tests concurrent state transitions of a ThresholdCircuitBreaker.
     * This test simulates 100 threads simultaneously calling incrementAndCheckState()
     * with a threshold of 50, and verifies proper concurrent behavior.
     */
    @Test
    void testConcurrentStateTransition() throws InterruptedException {
        final long concurrentThreshold = 50L;
        final int threadCount = 100;
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(concurrentThreshold);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final List<ContextedRuntimeException> exceptions = Collections.synchronizedList(new ArrayList<>());
        final List<PropertyChangeEvent> stateChanges = Collections.synchronizedList(new ArrayList<>());
        
        // Add property change listener to track state transitions
        circuit.addChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                if (AbstractCircuitBreaker.PROPERTY_NAME.equals(evt.getPropertyName())) {
                    stateChanges.add(evt);
                }
            }
        });
        
        // Create and start threads
        final List<Thread> threads = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            final Thread thread = new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to start simultaneously
                    circuit.incrementAndCheckState(1L); // Increment by 1
                } catch (final Exception e) {
                    final ContextedRuntimeException cre = new ContextedRuntimeException("Concurrent test failed in thread " + threadId, e);
                    cre.addContextValue("threshold", concurrentThreshold);
                    cre.addContextValue("threadId", threadId);
                    cre.addContextValue("openState", circuit.isOpen());
                    exceptions.add(cre);
                } finally {
                    doneLatch.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        doneLatch.await();
        
        // Assertions
        assertTrue(circuit.isOpen(), "Circuit should be open after concurrent execution");
        assertEquals(1, stateChanges.size(), "Should have exactly one state transition from closed to open");
        
        // Verify the state transition event
        final PropertyChangeEvent event = stateChanges.get(0);
        assertFalse((Boolean) event.getOldValue(), "State should change from closed (false)");
        assertTrue((Boolean) event.getNewValue(), "State should change to open (true)");
        
        // Verify that all exceptions (if any) have proper context values
        for (final ContextedRuntimeException exception : exceptions) {
            assertEquals(concurrentThreshold, exception.getFirstContextValue("threshold"),
                    "Exception context should contain correct threshold");
            assertTrue(exception.getFirstContextValue("threadId") != null,
                    "Exception context should contain threadId");
            assertTrue(exception.getFirstContextValue("openState") != null,
                    "Exception context should contain openState");
        }
    }

}
