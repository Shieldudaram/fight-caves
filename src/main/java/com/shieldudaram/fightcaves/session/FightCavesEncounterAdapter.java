package com.shieldudaram.fightcaves.session;

import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.content.LoadedContent;
import com.shieldudaram.fightcaves.content.WaveDefinition;

public interface FightCavesEncounterAdapter {

    record RunStartResult(boolean success, String message) {
        public static RunStartResult ok() {
            return new RunStartResult(true, "ok");
        }

        public static RunStartResult fail(String message) {
            return new RunStartResult(false, message == null ? "failed" : message);
        }
    }

    record WaveSpawnResult(boolean success, int spawnedCount, String message) {
        public static WaveSpawnResult ok(int spawnedCount) {
            return new WaveSpawnResult(true, Math.max(0, spawnedCount), "ok");
        }

        public static WaveSpawnResult fail(String message) {
            return new WaveSpawnResult(false, 0, message == null ? "failed" : message);
        }
    }

    RunStartResult onRunStart(String runId, String playerId, String playerName, FightCavesConfig config);

    void onRunEnd(String runId, String playerId, boolean success, String reason, FightCavesConfig config);

    WaveSpawnResult spawnWave(String runId,
                              String playerId,
                              int waveNumber,
                              WaveDefinition wave,
                              LoadedContent content,
                              FightCavesConfig config);

    int countAlive(String runId, int waveNumber);

    void clearWave(String runId, int waveNumber, String reason);

    void clearRun(String runId, String reason);

    void onTrackedEntityDeath(String entityUuid);

    static FightCavesEncounterAdapter noop() {
        return new FightCavesEncounterAdapter() {
            @Override
            public RunStartResult onRunStart(String runId, String playerId, String playerName, FightCavesConfig config) {
                return RunStartResult.ok();
            }

            @Override
            public void onRunEnd(String runId, String playerId, boolean success, String reason, FightCavesConfig config) {
            }

            @Override
            public WaveSpawnResult spawnWave(String runId,
                                             String playerId,
                                             int waveNumber,
                                             WaveDefinition wave,
                                             LoadedContent content,
                                             FightCavesConfig config) {
                int spawned = 0;
                if (wave != null && wave.spawns != null) {
                    for (WaveDefinition.WaveSpawn spawn : wave.spawns) {
                        if (spawn == null) continue;
                        spawned += Math.max(0, spawn.count);
                    }
                }
                return WaveSpawnResult.ok(spawned);
            }

            @Override
            public int countAlive(String runId, int waveNumber) {
                // Keep tests deterministic without a world adapter by requiring explicit wave completion action.
                return Integer.MAX_VALUE;
            }

            @Override
            public void clearWave(String runId, int waveNumber, String reason) {
            }

            @Override
            public void clearRun(String runId, String reason) {
            }

            @Override
            public void onTrackedEntityDeath(String entityUuid) {
            }
        };
    }
}
