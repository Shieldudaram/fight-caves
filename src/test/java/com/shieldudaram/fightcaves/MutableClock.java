package com.shieldudaram.fightcaves;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

final class MutableClock implements LongSupplier {
    private final AtomicLong now = new AtomicLong();

    MutableClock(long initialValue) {
        this.now.set(initialValue);
    }

    @Override
    public long getAsLong() {
        return now.get();
    }

    void set(long value) {
        now.set(value);
    }

    void advance(long millis) {
        now.addAndGet(millis);
    }
}
