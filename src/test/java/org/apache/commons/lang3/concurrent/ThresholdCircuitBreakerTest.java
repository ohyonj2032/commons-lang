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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * Tests that exceeding the threshold raises a {@link CircuitBreakingException}
     * wrapping a {@link ContextedException} with context information.
     */
    @Test
    void testThresholdCircuitBreakingException() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(threshold);
        circuit.incrementAndCheckState(9L);
        final CircuitBreakingException ex = assertThrows(CircuitBreakingException.class,
                () -> circuit.incrementAndCheckState(2L),
                "Expected CircuitBreakingException when threshold exceeded");
        assertTrue(ex.getCause() instanceof ContextedException,
                "Cause should be ContextedException");
        final ContextedException ce = (ContextedException) ex.getCause();
        assertTrue(ce.getMessage().contains("Threshold exceeded"),
                "Message should contain 'Threshold exceeded'");
    }

    /**
     * Test that when threshold is zero, the circuit breaker is always open
     * and throws a {@link CircuitBreakingException}.
     */
    @Test
    void testThresholdEqualsZero() {
        final ThresholdCircuitBreaker circuit = new ThresholdCircuitBreaker(zeroThreshold);
        final CircuitBreakingException ex = assertThrows(CircuitBreakingException.class,
                () -> circuit.incrementAndCheckState(0L),
                "When the threshold is zero, the circuit should throw CircuitBreakingException");
        assertTrue(ex.getCause() instanceof ContextedException,
                "Cause should be ContextedException");
        final ContextedException ce = (ContextedException) ex.getCause();
        assertTrue(ce.getMessage().contains("Threshold is zero"),
                "Message should contain 'Threshold is zero'");
    }

}
