package com.shieldudaram.fightcaves.data;

import com.google.gson.Gson;
import com.shieldudaram.fightcaves.util.JsonIo;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RunHistoryRepository {
    private static final Gson COMPACT_GSON = new Gson();

    private final Object lock = new Object();
    private final Path filePath;
    private final Logger logger;

    public RunHistoryRepository(Path filePath, Logger logger) {
        this.filePath = filePath;
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(RunHistoryRepository.class.getName()));
    }

    public void append(RunRecord record) {
        if (record == null) {
            return;
        }
        synchronized (lock) {
            try {
                Files.createDirectories(filePath.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(
                        filePath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                )) {
                    writer.write(COMPACT_GSON.toJson(record));
                    writer.newLine();
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to append run history.", t);
            }
        }
    }

    public List<RunRecord> readAll() {
        synchronized (lock) {
            List<RunRecord> records = new ArrayList<>();
            try {
                if (!Files.exists(filePath)) {
                    return records;
                }
                List<String> lines = Files.readAllLines(filePath);
                for (String line : lines) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    try {
                        RunRecord record = COMPACT_GSON.fromJson(line, RunRecord.class);
                        if (record != null) {
                            records.add(record);
                        }
                    } catch (Throwable ignored) {
                        // Ignore malformed historical lines and keep reading.
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to read run history.", t);
            }
            return records;
        }
    }
}
