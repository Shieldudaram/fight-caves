package com.shieldudaram.fightcaves.plugin;

import com.shieldudaram.fightcaves.FightCavesRuntime;
import com.shieldudaram.fightcaves.arena.ArenaProfile;
import com.shieldudaram.fightcaves.arena.PlayerReturnSnapshot;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.session.FightCavesEncounterAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FightCavesArenaService {
    private final FightCavesRuntime runtime;
    private final FightCavesTeleportService teleportService;
    private final FightCavesPlayerStateService playerStateService;

    private final Map<String, PlayerReturnSnapshot> returnSnapshotByRun = new ConcurrentHashMap<>();

    public FightCavesArenaService(FightCavesRuntime runtime,
                                  FightCavesTeleportService teleportService,
                                  FightCavesPlayerStateService playerStateService) {
        this.runtime = runtime;
        this.teleportService = teleportService;
        this.playerStateService = playerStateService;
    }

    public FightCavesEncounterAdapter.RunStartResult onRunStart(String runId, String playerId) {
        ArenaProfile profile = runtime.getArenaProfile();
        if (profile == null || profile.world == null || profile.world.isBlank()) {
            return FightCavesEncounterAdapter.RunStartResult.fail("Arena world is not configured.");
        }

        PlayerReturnSnapshot latest = playerStateService.getCopy(playerId);
        if (latest != null && latest.world != null && !latest.world.isBlank()) {
            returnSnapshotByRun.put(runId, latest);
        }

        int[] spawn = profile.playerSpawn;
        if (spawn == null || spawn.length < 3) {
            return FightCavesEncounterAdapter.RunStartResult.fail("Arena player spawn is invalid.");
        }

        teleportService.queueTeleport(
                playerId,
                profile.world,
                spawn[0] + 0.5d,
                spawn[1],
                spawn[2] + 0.5d,
                0f,
                0f,
                0f
        );
        return FightCavesEncounterAdapter.RunStartResult.ok();
    }

    public void onRunEnd(String runId, String playerId, FightCavesConfig config) {
        PlayerReturnSnapshot back = returnSnapshotByRun.remove(runId);
        if (config == null || config.arena == null || !config.arena.returnPlayerOnExit) {
            return;
        }
        if (back != null && back.world != null && !back.world.isBlank()) {
            teleportService.queueTeleport(
                    playerId,
                    back.world,
                    back.x,
                    back.y,
                    back.z,
                    back.pitch,
                    back.yaw,
                    back.roll
            );
            return;
        }

        int[] fallback = config.arena.spectatorSpawn;
        String world = config.arena.world;
        if (fallback == null || fallback.length < 3 || world == null || world.isBlank()) {
            return;
        }
        teleportService.queueTeleport(
                playerId,
                world,
                fallback[0] + 0.5d,
                fallback[1],
                fallback[2] + 0.5d,
                0f,
                0f,
                0f
        );
    }
}
