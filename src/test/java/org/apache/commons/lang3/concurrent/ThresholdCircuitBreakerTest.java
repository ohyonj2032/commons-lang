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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.AbstractLangTest;
import org.apache.commons.lang3.exception.ContextedException;
import org.junit.Test;

/**
 * Test class for {@code ThresholdCircuitBreaker}.
 */
public class ThresholdCircuitBreakerTest extends AbstractLangTest {

    /**
     * Threshold used in tests.
     */
    private static final long threshold = 10L;

    private static final long zeroThreshold = 0L;

    /**
     * Tests that closing a {@code ThresholdCircuitBreaker} resets the internal counter.
     */
    @Test
    public void testClosingThresholdCircuitBreaker() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        circuit.close();
        // now the internal counter is back at zero, not 9 anymore. So it is safe to increment 9 again
        assertTrue("Internal counter was not reset back to zero", circuit.incrementAndCheckState(9L));
    }

    /**
     * Tests that we can get the threshold value correctly.
     */
    @Test
    public void testGettingThreshold() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        assertEquals("Wrong value of threshold", Long.valueOf(threshold), Long.valueOf(circuit.getThreshold()));
    }

    /**
     * Tests that the threshold is working as expected when incremented and no exception is thrown.
     */
    @Test
    public void testThreshold() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        assertTrue("Circuit opened before reaching the threshold", circuit.incrementAndCheckState(1L));
    }

    /**
     * Tests that exceeding the threshold raises an exception.
     */
    @Test
    public void testThresholdCircuitBreakingException() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        assertFalse("The circuit was supposed to be open after increment above the threshold", circuit.incrementAndCheckState(2L));
    }

    /**
     * Test that when threshold is zero, the circuit breaker is always open.
     */
    @Test
    public void testThresholdEqualsZero() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(zeroThreshold);
        assertFalse("When the threshold is zero, the circuit is supposed to be always open", circuit.incrementAndCheckState(0L));
    }

    /**
     * Tests concurrent state transitions to ensure thread safety and correct ContextedException handling.
     */
    @Test
    public void testConcurrentStateTransition() throws InterruptedException {
        final long testThreshold = 50L;
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(testThreshold);
        final int threadCount = 100;

        final CountDownLatch readyLatch = new CountDownLatch(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);

        final List<ContextedException> exceptions = new CopyOnWriteArrayList<>();
        final AtomicInteger stateChanges = new AtomicInteger(0);

        circuit.addChangeListener(evt -> stateChanges.incrementAndGet());

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    circuit.incrementAndCheckState(1L);
                } catch (Exception e) {
                    if (e instanceof ContextedException) {
                        exceptions.add((ContextedException) e);
                    } else if (e.getCause() instanceof ContextedException) {
                        exceptions.add((ContextedException) e.getCause());
                    }
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        readyLatch.await(); // Wait for all threads to be ready
        startLatch.countDown(); // Release all threads at once
        doneLatch.await(); // Wait for all threads to finish

        // Assert final state is OPEN and state changed only once
        assertTrue("Circuit should be open", circuit.isOpen());
        assertEquals("State should only change once", 1, stateChanges.get());

        // Assert that we collected at least one ContextedException
        assertFalse("Should collect at least one ContextedException", exceptions.isEmpty());

        // Assert ContextedException contains correct threshold and trigger count
        for (ContextedException ex : exceptions) {
            Object thresholdCtx = ex.getFirstContextValue("threshold");
            assertNotNull("Context should contain 'threshold'", thresholdCtx);
            assertEquals("Threshold context value should be 50", 50L, ((Number) thresholdCtx).longValue());

            boolean hasCount = false;
            for (org.apache.commons.lang3.tuple.Pair<String, Object> pair : ex.getContextEntries()) {
                if (pair.getValue() instanceof Number) {
                    long val = ((Number) pair.getValue()).longValue();
                    if (val > 50L) {
                        hasCount = true;
                        break;
                    }
                }
            }
            assertTrue("Context should contain trigger count > 50", hasCount);
        }
    }

}
