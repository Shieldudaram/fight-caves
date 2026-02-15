package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.shieldudaram.fightcaves.FightCavesRuntime;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.content.LoadedContent;
import com.shieldudaram.fightcaves.content.WaveDefinition;
import com.shieldudaram.fightcaves.session.FightCavesEncounterAdapter;

public final class FightCavesEncounterService implements FightCavesEncounterAdapter {
    private final FightCavesArenaService arenaService;
    private final FightCavesEnemySpawnService enemySpawnService;

    public FightCavesEncounterService(FightCavesRuntime runtime,
                                      FightCavesTeleportService teleportService,
                                      FightCavesPlayerStateService playerStateService,
                                      HytaleLogger logger) {
        this.arenaService = new FightCavesArenaService(runtime, teleportService, playerStateService);
        this.enemySpawnService = new FightCavesEnemySpawnService(runtime, logger);
    }

    @Override
    public RunStartResult onRunStart(String runId, String playerId, String playerName, FightCavesConfig config) {
        return arenaService.onRunStart(runId, playerId);
    }

    @Override
    public void onRunEnd(String runId, String playerId, boolean success, String reason, FightCavesConfig config) {
        arenaService.onRunEnd(runId, playerId, config);
    }

    @Override
    public WaveSpawnResult spawnWave(String runId,
                                     String playerId,
                                     int waveNumber,
                                     WaveDefinition wave,
                                     LoadedContent content,
                                     FightCavesConfig config) {
        try {
            int spawned = enemySpawnService.spawnWave(runId, waveNumber, wave, content);
            return WaveSpawnResult.ok(spawned);
        } catch (Throwable t) {
            return WaveSpawnResult.fail(t.getMessage() == null ? "spawn_failed" : t.getMessage());
        }
    }

    @Override
    public int countAlive(String runId, int waveNumber) {
        return enemySpawnService.countAlive(runId, waveNumber);
    }

    @Override
    public void clearWave(String runId, int waveNumber, String reason) {
        enemySpawnService.clearWave(runId, waveNumber);
    }

    @Override
    public void clearRun(String runId, String reason) {
        enemySpawnService.clearRun(runId);
    }

    @Override
    public void onTrackedEntityDeath(String entityUuid) {
        enemySpawnService.onTrackedEntityDeath(entityUuid);
    }
}
