package com.shieldudaram.fightcaves.data;

import com.google.gson.reflect.TypeToken;
import com.shieldudaram.fightcaves.util.JsonIo;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StatsRepository {
    private static final Type LIST_TYPE = new TypeToken<ArrayList<PlayerStats>>() {
    }.getType();

    private final Object lock = new Object();
    private final Path filePath;
    private final Logger logger;
    private final Map<String, PlayerStats> byPlayerId = new LinkedHashMap<>();

    public StatsRepository(Path filePath, Logger logger) {
        this.filePath = filePath;
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(StatsRepository.class.getName()));
        reload();
    }

    public void reload() {
        synchronized (lock) {
            byPlayerId.clear();
            try {
                if (!Files.exists(filePath)) {
                    save();
                    return;
                }
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    ArrayList<PlayerStats> list = JsonIo.GSON.fromJson(reader, LIST_TYPE);
                    if (list == null) {
                        return;
                    }
                    for (PlayerStats stats : list) {
                        if (stats == null || stats.playerId == null || stats.playerId.isBlank()) {
                            continue;
                        }
                        byPlayerId.put(stats.playerId, normalize(stats));
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to load player stats repository.", t);
            }
        }
    }

    public PlayerStats getOrCreate(String playerId, String fallbackName) {
        synchronized (lock) {
            PlayerStats current = byPlayerId.get(playerId);
            if (current == null) {
                current = new PlayerStats();
                current.playerId = playerId;
                current.lastKnownName = fallbackName;
                byPlayerId.put(playerId, current);
            }
            if (fallbackName != null && !fallbackName.isBlank()) {
                current.lastKnownName = fallbackName;
            }
            return current;
        }
    }

    public void update(String playerId, String fallbackName, Consumer<PlayerStats> mutator) {
        synchronized (lock) {
            PlayerStats stats = getOrCreate(playerId, fallbackName);
            mutator.accept(stats);
            normalize(stats);
            save();
        }
    }

    public boolean reset(String playerId) {
        synchronized (lock) {
            PlayerStats removed = byPlayerId.remove(playerId);
            save();
            return removed != null;
        }
    }

    public PlayerStats findByName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        synchronized (lock) {
            for (PlayerStats stats : byPlayerId.values()) {
                if (stats == null || stats.lastKnownName == null) {
                    continue;
                }
                if (stats.lastKnownName.equalsIgnoreCase(playerName)) {
                    return stats;
                }
            }
            return null;
        }
    }

    public Collection<PlayerStats> all() {
        synchronized (lock) {
            return new ArrayList<>(byPlayerId.values());
        }
    }

    public void save() {
        synchronized (lock) {
            try {
                Files.createDirectories(filePath.getParent());
                try (Writer writer = Files.newBufferedWriter(filePath)) {
                    JsonIo.GSON.toJson(byPlayerId.values(), writer);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to save player stats repository.", t);
            }
        }
    }

    public Path getFilePath() {
        return filePath;
    }

    private static PlayerStats normalize(PlayerStats stats) {
        if (stats.playerId == null) stats.playerId = "";
        if (stats.lastKnownName == null) stats.lastKnownName = "";
        if (stats.bestWave < 0) stats.bestWave = 0;
        if (stats.bestTimeMs < 0) stats.bestTimeMs = 0;
        if (stats.completionCount < 0) stats.completionCount = 0;
        if (stats.currentStreak < 0) stats.currentStreak = 0;
        if (stats.lastRunAt < 0) stats.lastRunAt = 0;
        return stats;
    }
}
