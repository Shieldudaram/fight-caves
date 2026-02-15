package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.shieldudaram.fightcaves.FightCavesRuntime;
import com.shieldudaram.fightcaves.arena.ArenaProfile;
import com.shieldudaram.fightcaves.arena.SpawnPoint;
import com.shieldudaram.fightcaves.content.LoadedContent;
import com.shieldudaram.fightcaves.content.WaveDefinition;
import com.shieldudaram.fightcaves.session.TrackedEnemy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class FightCavesEnemySpawnService {
    private final FightCavesRuntime runtime;
    private final HytaleLogger logger;

    private final Map<String, TrackedEnemy> trackedByEntityId = new ConcurrentHashMap<>();
    private final Map<String, List<String>> entityIdsByRun = new ConcurrentHashMap<>();
    private final Map<String, List<String>> entityIdsByRunWave = new ConcurrentHashMap<>();

    public FightCavesEnemySpawnService(FightCavesRuntime runtime, HytaleLogger logger) {
        this.runtime = runtime;
        this.logger = logger;
    }

    public int spawnWave(String runId, int waveNumber, WaveDefinition wave, LoadedContent content) throws IllegalStateException {
        ArenaProfile profile = runtime.getArenaProfile();
        if (profile == null || profile.world == null || profile.world.isBlank()) {
            throw new IllegalStateException("Arena world is not configured.");
        }

        World world = Universe.get().getWorld(profile.world);
        if (world == null) {
            throw new IllegalStateException("Arena world not loaded: " + profile.world);
        }

        List<SpawnPoint> points = profile.spawnPoints;
        if (points == null || points.isEmpty()) {
            throw new IllegalStateException("No arena spawn points configured.");
        }

        if (wave == null || wave.spawns == null || wave.spawns.isEmpty()) {
            throw new IllegalStateException("Wave has no spawn entries.");
        }

        int spawned = 0;
        int index = 0;
        for (WaveDefinition.WaveSpawn spawn : wave.spawns) {
            if (spawn == null || spawn.enemyId == null || spawn.enemyId.isBlank() || spawn.count <= 0) {
                continue;
            }

            for (int i = 0; i < spawn.count; i++) {
                SpawnPoint point = points.get(Math.floorMod(index++, points.size()));
                if (point == null || point.pos == null || point.pos.length < 3) {
                    continue;
                }

                String uuid = spawnNpc(world, point.pos[0], point.pos[1], point.pos[2]);
                if (uuid == null) {
                    continue;
                }

                TrackedEnemy tracked = new TrackedEnemy();
                tracked.runId = runId;
                tracked.wave = waveNumber;
                tracked.entityUuid = uuid;
                tracked.enemyId = spawn.enemyId;
                tracked.world = profile.world;
                tracked.alive = true;

                trackedByEntityId.put(uuid, tracked);
                entityIdsByRun.computeIfAbsent(runId, ignored -> Collections.synchronizedList(new ArrayList<>())).add(uuid);
                entityIdsByRunWave.computeIfAbsent(runWaveKey(runId, waveNumber), ignored -> Collections.synchronizedList(new ArrayList<>())).add(uuid);
                spawned++;
            }
        }

        logger.atInfo().log("[FightCaves] Spawned wave %d with %d tracked NPC stand-ins.", waveNumber, spawned);
        return spawned;
    }

    public int countAlive(String runId, int waveNumber) {
        List<String> ids = entityIdsByRunWave.get(runWaveKey(runId, waveNumber));
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        int alive = 0;
        List<String> copy;
        synchronized (ids) {
            copy = new ArrayList<>(ids);
        }

        for (String uuid : copy) {
            TrackedEnemy tracked = trackedByEntityId.get(uuid);
            if (tracked == null) {
                continue;
            }
            if (!tracked.alive) {
                continue;
            }
            if (!isAlive(tracked)) {
                tracked.alive = false;
                continue;
            }
            alive++;
        }
        return alive;
    }

    public void clearWave(String runId, int waveNumber) {
        String key = runWaveKey(runId, waveNumber);
        List<String> ids = entityIdsByRunWave.remove(key);
        if (ids == null) {
            return;
        }

        List<String> runList = entityIdsByRun.get(runId);
        List<String> copy;
        synchronized (ids) {
            copy = new ArrayList<>(ids);
        }
        for (String uuid : copy) {
            despawnTrackedEntity(uuid);
            if (runList != null) {
                runList.remove(uuid);
            }
        }
    }

    public void clearRun(String runId) {
        List<String> ids = entityIdsByRun.remove(runId);
        if (ids == null) {
            return;
        }

        List<String> copy;
        synchronized (ids) {
            copy = new ArrayList<>(ids);
        }
        for (String uuid : copy) {
            despawnTrackedEntity(uuid);
        }

        String prefix = runId + ":";
        entityIdsByRunWave.keySet().removeIf(key -> key != null && key.startsWith(prefix));
    }

    public void onTrackedEntityDeath(String entityUuid) {
        TrackedEnemy tracked = trackedByEntityId.get(entityUuid);
        if (tracked != null) {
            tracked.alive = false;
        }
    }

    private void despawnTrackedEntity(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        TrackedEnemy tracked = trackedByEntityId.remove(uuid);
        if (tracked == null) {
            return;
        }
        tracked.alive = false;

        UUID parsed = parseUuid(uuid);
        if (parsed == null || tracked.world == null || tracked.world.isBlank()) {
            return;
        }

        World world = Universe.get().getWorld(tracked.world);
        if (world == null) {
            return;
        }

        runOnWorldThread(world, () -> {
            Entity entity = world.getEntity(parsed);
            if (entity != null && !entity.wasRemoved()) {
                entity.remove();
            }
            return null;
        });
    }

    private boolean isAlive(TrackedEnemy tracked) {
        if (tracked == null || tracked.entityUuid == null || tracked.world == null) {
            return false;
        }
        UUID parsed = parseUuid(tracked.entityUuid);
        if (parsed == null) {
            return false;
        }

        World world = Universe.get().getWorld(tracked.world);
        if (world == null) {
            return false;
        }

        Boolean alive = runOnWorldThread(world, () -> {
            Entity entity = world.getEntity(parsed);
            return entity != null && !entity.wasRemoved();
        });
        return Boolean.TRUE.equals(alive);
    }

    private String spawnNpc(World world, int x, int y, int z) {
        if (world == null) return null;

        return runOnWorldThread(world, () -> {
            NPCEntity npc = new NPCEntity(world);
            NPCEntity spawned = world.spawnEntity(
                    npc,
                    new Vector3d(x + 0.5d, y, z + 0.5d),
                    new Vector3f()
            );
            if (spawned == null || spawned.getUuid() == null) {
                return null;
            }
            return spawned.getUuid().toString();
        });
    }

    private static String runWaveKey(String runId, int wave) {
        return runId + ":" + wave;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private <T> T runOnWorldThread(World world, Supplier<T> action) {
        if (world == null || action == null) return null;
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store != null && store.isInThread()) {
                return action.get();
            }
        } catch (Throwable t) {
            logger.atWarning().withCause(t).log("[FightCaves] Failed world-thread check.");
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            world.execute(() -> {
                try {
                    future.complete(action.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future.get(3, TimeUnit.SECONDS);
        } catch (Throwable t) {
            logger.atWarning().withCause(t).log("[FightCaves] World-thread execution failed.");
            return null;
        }
    }
}
