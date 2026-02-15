package com.shieldudaram.fightcaves.data;

import com.google.gson.reflect.TypeToken;
import com.shieldudaram.fightcaves.util.JsonIo;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClaimRepository {
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, ArrayList<RewardClaim>>>() {
    }.getType();

    private final Object lock = new Object();
    private final Path filePath;
    private final Logger logger;
    private final Map<String, List<RewardClaim>> byPlayerId = new LinkedHashMap<>();

    public ClaimRepository(Path filePath, Logger logger) {
        this.filePath = filePath;
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(ClaimRepository.class.getName()));
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
                    Map<String, ArrayList<RewardClaim>> loaded = JsonIo.GSON.fromJson(reader, MAP_TYPE);
                    if (loaded == null) {
                        return;
                    }
                    for (Map.Entry<String, ArrayList<RewardClaim>> entry : loaded.entrySet()) {
                        if (entry.getKey() == null || entry.getKey().isBlank()) {
                            continue;
                        }
                        ArrayList<RewardClaim> claims = entry.getValue();
                        if (claims == null) claims = new ArrayList<>();
                        byPlayerId.put(entry.getKey(), claims);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to load claims repository.", t);
            }
        }
    }

    public void addClaims(String playerId, List<RewardClaim> claims) {
        if (playerId == null || playerId.isBlank() || claims == null || claims.isEmpty()) {
            return;
        }
        synchronized (lock) {
            List<RewardClaim> list = byPlayerId.computeIfAbsent(playerId, ignored -> new ArrayList<>());
            list.addAll(claims);
            save();
        }
    }

    public void addClaim(String playerId, RewardClaim claim) {
        if (claim == null) return;
        addClaims(playerId, java.util.List.of(claim));
    }

    public List<RewardClaim> claimAll(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return List.of();
        }
        synchronized (lock) {
            List<RewardClaim> existing = byPlayerId.remove(playerId);
            save();
            if (existing == null) {
                return List.of();
            }
            return new ArrayList<>(existing);
        }
    }

    public List<RewardClaim> peek(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return List.of();
        }
        synchronized (lock) {
            List<RewardClaim> existing = byPlayerId.get(playerId);
            if (existing == null) {
                return List.of();
            }
            return new ArrayList<>(existing);
        }
    }

    public void save() {
        synchronized (lock) {
            try {
                Files.createDirectories(filePath.getParent());
                try (Writer writer = Files.newBufferedWriter(filePath)) {
                    JsonIo.GSON.toJson(byPlayerId, writer);
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to save claims repository.", t);
            }
        }
    }
}
