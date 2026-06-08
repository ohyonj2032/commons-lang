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
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static final int concurrentThreads = 100;

    private static final long concurrentThreshold = 50L;

    private static final long concurrentTriggerCount = concurrentThreshold + 1L;

    private static final int stressIterations = 25;

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

    @Test
    void testConcurrentStateTransition() throws Exception {
        final Queue<ContextedException> failures = new ConcurrentLinkedQueue<>();

        for (int iteration = 0; iteration < stressIterations; iteration++) {
            runConcurrentStateTransitionIteration(iteration, failures);
        }

        for (final ContextedException failure : failures) {
            assertEquals(Long.valueOf(concurrentThreshold), failure.getFirstContextValue("threshold"),
                    "Wrong threshold stored in ContextedException");
            assertEquals(Long.valueOf(concurrentTriggerCount), failure.getFirstContextValue("count"),
                    "Wrong triggering count stored in ContextedException");
        }

        if (!failures.isEmpty()) {
            fail(failures.peek().getMessage());
        }
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

    private ContextedException newFailure(final String message, final Throwable cause) {
        return new ContextedException(message, cause)
                .setContextValue("threshold", Long.valueOf(concurrentThreshold))
                .setContextValue("count", Long.valueOf(concurrentTriggerCount));
    }

    private ContextedException newFailure(final String message) {
        return newFailure(message, null);
    }

    private void runConcurrentStateTransitionIteration(final int iteration, final Queue<ContextedException> failures)
            throws InterruptedException {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(concurrentThreshold);
        final AtomicInteger openTransitions = new AtomicInteger();
        final AtomicInteger openResponses = new AtomicInteger();
        final CountDownLatch readyLatch = new CountDownLatch(concurrentThreads);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(concurrentThreads);
        final ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);

        circuit.addChangeListener(event -> {
            if (Boolean.TRUE.equals(event.getNewValue())) {
                openTransitions.incrementAndGet();
            }
        });

        for (int thread = 0; thread < concurrentThreads; thread++) {
            executorService.execute(() -> {
                readyLatch.countDown();
                try {
                    if (!startLatch.await(5, TimeUnit.SECONDS)) {
                        failures.add(newFailure("Workers did not start concurrently in iteration " + iteration));
                        return;
                    }
                    if (!circuit.incrementAndCheckState(1L)) {
                        openResponses.incrementAndGet();
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.add(newFailure("Interrupted while exercising ThresholdCircuitBreaker in iteration " + iteration, e));
                } catch (final Exception e) {
                    failures.add(newFailure("Unexpected exception while exercising ThresholdCircuitBreaker in iteration " + iteration, e));
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        if (!readyLatch.await(5, TimeUnit.SECONDS)) {
            failures.add(newFailure("Not all workers became ready in iteration " + iteration));
        }
        startLatch.countDown();
        if (!doneLatch.await(10, TimeUnit.SECONDS)) {
            failures.add(newFailure("Workers did not finish in iteration " + iteration));
        }

        executorService.shutdown();
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            failures.add(newFailure("Executor did not terminate in iteration " + iteration));
            executorService.shutdownNow();
        }

        if (!circuit.isOpen()) {
            failures.add(newFailure("Circuit breaker was expected to be open in iteration " + iteration));
        }
        if (openTransitions.get() != 1) {
            failures.add(newFailure("Expected exactly one open state transition in iteration " + iteration + " but got "
                    + openTransitions.get()));
        }
        if (openResponses.get() != concurrentThreads - concurrentThreshold) {
            failures.add(newFailure("Expected exactly " + (concurrentThreads - concurrentThreshold)
                    + " open responses in iteration " + iteration + " but got " + openResponses.get()));
        }
    }
}
