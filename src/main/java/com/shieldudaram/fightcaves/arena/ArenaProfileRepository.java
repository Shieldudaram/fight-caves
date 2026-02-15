package com.shieldudaram.fightcaves.arena;

import com.shieldudaram.fightcaves.util.JsonIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ArenaProfileRepository {
    private final Object lock = new Object();
    private final Path filePath;
    private final Logger logger;

    public ArenaProfileRepository(Path filePath, Logger logger) {
        this.filePath = filePath;
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(ArenaProfileRepository.class.getName()));
    }

    public ArenaProfile read() {
        synchronized (lock) {
            try {
                if (!Files.exists(filePath)) {
                    return null;
                }
                ArenaProfile profile = JsonIo.read(filePath, ArenaProfile.class);
                if (profile == null) {
                    return null;
                }
                return ArenaProfile.copy(profile);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to read arena profile overrides.", t);
                return null;
            }
        }
    }

    public void save(ArenaProfile profile) {
        if (profile == null) {
            return;
        }
        synchronized (lock) {
            try {
                JsonIo.write(filePath, ArenaProfile.copy(profile));
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to save arena profile overrides.", t);
            }
        }
    }

    public Path getFilePath() {
        return filePath;
    }
}
