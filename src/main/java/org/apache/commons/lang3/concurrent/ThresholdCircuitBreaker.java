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

import java.util.concurrent.atomic.AtomicLong;

public class ThresholdCircuitBreaker extends AbstractCircuitBreaker<Long> {

    private static final long INITIAL_COUNT = 0L;

    private final long threshold;

    private final AtomicLong used;

    private volatile long lastThresholdExceededValue;

    public ThresholdCircuitBreaker(final long threshold) {
        this.used = new AtomicLong(INITIAL_COUNT);
        this.threshold = threshold;
    }

    @Override
    public boolean checkState() {
        return !isOpen();
    }

    @Override
    protected void onClose() {
        this.used.set(INITIAL_COUNT);
    }

    public long getThreshold() {
        return threshold;
    }

    public long getLastThresholdExceededValue() {
        return lastThresholdExceededValue;
    }

    @Override
    public boolean incrementAndCheckState(final Long increment) {
        if (threshold == 0) {
            open();
        }

        final long currentUsed = this.used.addAndGet(increment);
        if (currentUsed > threshold) {
            this.lastThresholdExceededValue = currentUsed;
            open();
        }

        return checkState();
    }

}
