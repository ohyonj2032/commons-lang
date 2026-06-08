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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.AbstractLangTest;
import org.apache.commons.lang3.exception.ContextedException;
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
     * Tests concurrent state transition when multiple threads simultaneously
     * increment past the threshold. Verifies that:
     * <ul>
     *   <li>The final state is OPEN</li>
     *   <li>Only one state transition from CLOSED to OPEN occurs</li>
     *   <li>Any ContextedException thrown contains correct context (threshold and used value)</li>
     * </ul>
     */
    @Test
    void testConcurrentStateTransition() throws Exception {
        final int threadCount = 100;
        final long testThreshold = 50L;
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(testThreshold);

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);

        final List<ContextedException> contextedExceptions = new CopyOnWriteArrayList<>();
        final List<Throwable> otherExceptions = new CopyOnWriteArrayList<>();

        final AtomicInteger openTransitionCount = new AtomicInteger(0);
        circuit.addChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                if (AbstractCircuitBreaker.PROPERTY_NAME.equals(evt.getPropertyName())
                        && Boolean.TRUE.equals(evt.getNewValue())) {
                    openTransitionCount.incrementAndGet();
                }
            }
        });

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    circuit.incrementAndCheckState(1L);
                } catch (final Throwable t) {
                    if (t instanceof ContextedException) {
                        contextedExceptions.add((ContextedException) t);
                    } else {
                        otherExceptions.add(t);
                    }
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "All threads should complete within timeout");

        assertTrue(otherExceptions.isEmpty(),
                "No unexpected exceptions should be thrown during concurrent access, but got: " + otherExceptions);

        assertTrue(circuit.isOpen(),
                "Circuit should be OPEN after concurrent increments above threshold");

        assertEquals(1, openTransitionCount.get(),
                "State should transition from CLOSED to OPEN exactly once");

        for (final ContextedException ce : contextedExceptions) {
            final Object thresholdValue = ce.getFirstContextValue("threshold");
            assertNotNull(thresholdValue, "ContextedException should contain threshold context");
            assertEquals(testThreshold, ((Number) thresholdValue).longValue(),
                    "ContextedException threshold context should be " + testThreshold);

            final Object usedValue = ce.getFirstContextValue("used");
            assertNotNull(usedValue, "ContextedException should contain used context");
            assertTrue(((Number) usedValue).longValue() > testThreshold,
                    "ContextedException used value should exceed threshold, actual: " + usedValue);
        }
    }
}
