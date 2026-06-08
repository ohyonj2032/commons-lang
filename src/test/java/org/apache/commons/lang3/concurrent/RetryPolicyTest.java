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

import static org.apache.commons.lang3.LangAssertions.assertIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.AbstractLangTest;
import org.apache.commons.lang3.arch.Processor;
import org.apache.commons.lang3.arch.Processor.Arch;
import org.apache.commons.lang3.arch.Processor.Type;
import org.apache.commons.lang3.exception.ContextedException;
import org.junit.jupiter.api.Test;

class RetryPolicyTest extends AbstractLangTest {

    @Test
    void testExecuteRetriesUntilSuccess() throws ContextedException {
        final AtomicInteger attempts = new AtomicInteger();
        final RetryPolicy retryPolicy = new RetryPolicy(2);

        final String value = retryPolicy.execute("value", input -> {
            if (attempts.getAndIncrement() < 2) {
                throw new IOException("boom");
            }
            return input + attempts.get();
        });

        assertEquals("value3", value);
        assertEquals(3, attempts.get());
    }

    @Test
    void testExecuteThrowsContextedExceptionAfterLimit() {
        final AtomicInteger attempts = new AtomicInteger();
        final RetryPolicy retryPolicy = new RetryPolicy(1);
        final IOException failure = new IOException("boom");

        final ContextedException exception = assertThrows(ContextedException.class,
                () -> retryPolicy.execute("value", input -> {
                    attempts.incrementAndGet();
                    throw failure;
                }));

        assertSame(failure, exception.getCause());
        assertEquals(Integer.valueOf(1), exception.getFirstContextValue("maxRetries"));
        assertEquals(Integer.valueOf(2), exception.getFirstContextValue("attempts"));
        assertEquals(2, attempts.get());
    }

    @Test
    void testNegativeMaxRetries() {
        assertIllegalArgumentException(() -> new RetryPolicy(-1));
    }

    @Test
    void testProcessorProcessWithRetry() throws ContextedException {
        final AtomicInteger attempts = new AtomicInteger();
        final Processor processor = new Processor(Arch.BIT_64, Type.X86);

        final String value = processor.processWithRetry(input -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IOException("boom");
            }
            return input.toString();
        }, 1);

        assertEquals("x86 64-bit", value);
        assertEquals(2, attempts.get());
    }
}
