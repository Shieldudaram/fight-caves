package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.RunWhenPausedSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.shieldudaram.fightcaves.FightCavesRuntime;

import java.util.concurrent.atomic.AtomicLong;

public final class FightCavesTickSystem extends TickingSystem<EntityStore> implements RunWhenPausedSystem<EntityStore> {

    private static final long STEP_NANOS = 50_000_000L; // 20Hz

    private final FightCavesRuntime runtime;
    private final AtomicLong nextTickAtNanos = new AtomicLong(0L);

    public FightCavesTickSystem(FightCavesRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void tick(float deltaTimeSeconds, int index, Store<EntityStore> store) {
        long now = System.nanoTime();
        long next = nextTickAtNanos.get();

        if (next == 0L) {
            if (nextTickAtNanos.compareAndSet(0L, now + STEP_NANOS)) {
                runtime.tick();
            }
            return;
        }

        if (now < next) {
            return;
        }

        if (nextTickAtNanos.compareAndSet(next, now + STEP_NANOS)) {
            runtime.tick();
        }
    }
}
