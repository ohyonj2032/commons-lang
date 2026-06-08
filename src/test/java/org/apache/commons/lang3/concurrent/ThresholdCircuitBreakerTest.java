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

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
     * High-concurrency stress test that simulates 100 threads concurrently calling
     * {@code incrementAndCheckState()} with a threshold of 50. Verifies that:
     * <ul>
     *   <li>The circuit breaker eventually transitions to OPEN state</li>
     *   <li>Only one state transition from CLOSED to OPEN occurs</li>
     *   <li>All {@code ContextedException} instances thrown upon circuit opening
     *       carry correct context values for threshold and the triggering count</li>
     * </ul>
     */
    @Test
    void testConcurrentStateTransition() throws Exception {
        final long testThreshold = 50L;
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(testThreshold);
        final int threadCount = 100;

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final Queue<ContextedException> exceptions = new ConcurrentLinkedQueue<>();
        final AtomicInteger openTransitionCount = new AtomicInteger(0);

        circuit.addChangeListener(event -> {
            if ("open".equals(event.getPropertyName()) && Boolean.TRUE.equals(event.getNewValue())) {
                openTransitionCount.incrementAndGet();
            }
        });

        final Field usedField = ThresholdCircuitBreaker.class.getDeclaredField("used");
        usedField.setAccessible(true);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    if (!circuit.incrementAndCheckState(1L)) {
                        final AtomicLong used = (AtomicLong) usedField.get(circuit);
                        final long currentCount = used.get();
                        throw new ContextedException("Circuit breaker opened")
                                .addContextValue("threshold", testThreshold)
                                .addContextValue("count", currentCount);
                    }
                } catch (final ContextedException ex) {
                    exceptions.add(ex);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (final IllegalAccessException e) {
                    throw new RuntimeException("Failed to access used field via reflection", e);
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertTrue(circuit.isOpen(), "Circuit should be OPEN after exceeding threshold");
        assertEquals(1, openTransitionCount.get(), "Only one state transition from CLOSED to OPEN should occur");
        assertFalse(exceptions.isEmpty(), "There should be at least one ContextedException collected");

        for (final ContextedException ex : exceptions) {
            assertEquals(Long.valueOf(testThreshold), ex.getFirstContextValue("threshold"),
                    "Context should contain correct threshold");
            final Object countObj = ex.getFirstContextValue("count");
            assertTrue(countObj != null, "Context count value must not be null");
            final long count = ((Number) countObj).longValue();
            assertTrue(count > testThreshold, "Count should be greater than threshold, but was " + count);
        }
    }
}