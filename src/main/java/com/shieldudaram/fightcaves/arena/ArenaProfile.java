package com.shieldudaram.fightcaves.arena;

import com.shieldudaram.fightcaves.config.FightCavesConfig;

import java.util.ArrayList;
import java.util.List;

public final class ArenaProfile {
    public String world;
    public int[] boundsMin = new int[]{0, 64, 0};
    public int[] boundsMax = new int[]{32, 96, 32};
    public int[] playerSpawn = new int[]{1024, 64, 1024};
    public List<SpawnPoint> spawnPoints = new ArrayList<>();

    public static ArenaProfile fromConfig(FightCavesConfig.Arena arena) {
        ArenaProfile profile = new ArenaProfile();
        if (arena == null) {
            profile.spawnPoints.add(new SpawnPoint("default_1", new int[]{1024, 64, 1024}));
            return profile;
        }

        profile.world = safe(arena.world, arena.templateWorld, "world");
        profile.boundsMin = normalizeVec3(arena.boundsMin, arena.templateMin, new int[]{0, 64, 0});
        profile.boundsMax = normalizeVec3(arena.boundsMax, arena.templateMax, new int[]{32, 96, 32});
        profile.playerSpawn = normalizeVec3(arena.playerSpawn, arena.activeOrigin, new int[]{1024, 64, 1024});

        if (arena.spawnPoints != null && !arena.spawnPoints.isEmpty()) {
            for (SpawnPoint point : arena.spawnPoints) {
                if (point == null || point.id == null || point.id.isBlank()) continue;
                profile.spawnPoints.add(new SpawnPoint(point.id, normalizeVec3(point.pos, null, profile.playerSpawn)));
            }
        }
        if (profile.spawnPoints.isEmpty()) {
            profile.spawnPoints.add(new SpawnPoint("default_1", profile.playerSpawn.clone()));
        }

        return profile;
    }

    public static ArenaProfile merge(ArenaProfile base, ArenaProfile override) {
        ArenaProfile merged = copy(base == null ? new ArenaProfile() : base);
        if (override == null) {
            return merged;
        }

        if (override.world != null && !override.world.isBlank()) {
            merged.world = override.world;
        }
        if (validVec3(override.boundsMin)) {
            merged.boundsMin = override.boundsMin.clone();
        }
        if (validVec3(override.boundsMax)) {
            merged.boundsMax = override.boundsMax.clone();
        }
        if (validVec3(override.playerSpawn)) {
            merged.playerSpawn = override.playerSpawn.clone();
        }
        if (override.spawnPoints != null) {
            merged.spawnPoints = new ArrayList<>();
            for (SpawnPoint point : override.spawnPoints) {
                if (point == null || point.id == null || point.id.isBlank()) continue;
                merged.spawnPoints.add(new SpawnPoint(point.id, normalizeVec3(point.pos, null, merged.playerSpawn)));
            }
        }
        if (merged.spawnPoints == null || merged.spawnPoints.isEmpty()) {
            merged.spawnPoints = new ArrayList<>(List.of(new SpawnPoint("default_1", merged.playerSpawn.clone())));
        }
        return merged;
    }

    public static ArenaProfile copy(ArenaProfile src) {
        ArenaProfile copy = new ArenaProfile();
        if (src == null) {
            return copy;
        }
        copy.world = src.world;
        copy.boundsMin = normalizeVec3(src.boundsMin, null, copy.boundsMin);
        copy.boundsMax = normalizeVec3(src.boundsMax, null, copy.boundsMax);
        copy.playerSpawn = normalizeVec3(src.playerSpawn, null, copy.playerSpawn);
        copy.spawnPoints = new ArrayList<>();
        if (src.spawnPoints != null) {
            for (SpawnPoint point : src.spawnPoints) {
                if (point == null || point.id == null || point.id.isBlank()) continue;
                copy.spawnPoints.add(new SpawnPoint(point.id, normalizeVec3(point.pos, null, copy.playerSpawn)));
            }
        }
        if (copy.spawnPoints.isEmpty()) {
            copy.spawnPoints.add(new SpawnPoint("default_1", copy.playerSpawn.clone()));
        }
        return copy;
    }

    private static String safe(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary;
        if (secondary != null && !secondary.isBlank()) return secondary;
        return fallback;
    }

    private static boolean validVec3(int[] vec) {
        return vec != null && vec.length >= 3;
    }

    private static int[] normalizeVec3(int[] primary, int[] secondary, int[] fallback) {
        if (validVec3(primary)) {
            return new int[]{primary[0], primary[1], primary[2]};
        }
        if (validVec3(secondary)) {
            return new int[]{secondary[0], secondary[1], secondary[2]};
        }
        if (validVec3(fallback)) {
            return new int[]{fallback[0], fallback[1], fallback[2]};
        }
        return new int[]{0, 64, 0};
    }
}
