package com.shieldudaram.fightcaves.data;

import com.shieldudaram.fightcaves.util.JsonIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ActiveRunMarkerRepository {
    private final Object lock = new Object();
    private final Path filePath;
    private final Logger logger;

    public ActiveRunMarkerRepository(Path filePath, Logger logger) {
        this.filePath = filePath;
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(ActiveRunMarkerRepository.class.getName()));
    }

    public void save(ActiveRunMarker marker) {
        synchronized (lock) {
            try {
                JsonIo.write(filePath, marker);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to save active run marker.", t);
            }
        }
    }

    public ActiveRunMarker read() {
        synchronized (lock) {
            try {
                if (!Files.exists(filePath)) {
                    return null;
                }
                return JsonIo.read(filePath, ActiveRunMarker.class);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to read active run marker.", t);
                return null;
            }
        }
    }

    public void clear() {
        synchronized (lock) {
            try {
                Files.deleteIfExists(filePath);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to clear active run marker.", t);
            }
        }
    }
}
