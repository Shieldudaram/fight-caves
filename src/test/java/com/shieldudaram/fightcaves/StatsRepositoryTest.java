package com.shieldudaram.fightcaves;

import com.shieldudaram.fightcaves.data.PlayerStats;
import com.shieldudaram.fightcaves.data.StatsRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatsRepositoryTest {

    @Test
    void persistsAndReloadsStats() throws Exception {
        Path dir = Files.createTempDirectory("fc-stats-test");
        Path statsFile = dir.resolve("fight_caves_player_stats.json");

        StatsRepository repo = new StatsRepository(statsFile, Logger.getLogger("test"));
        repo.update("player-1", "PlayerOne", stats -> {
            stats.bestWave = 42;
            stats.completionCount = 2;
        });

        StatsRepository reloaded = new StatsRepository(statsFile, Logger.getLogger("test"));
        PlayerStats stats = reloaded.getOrCreate("player-1", "PlayerOne");

        assertNotNull(stats);
        assertEquals(42, stats.bestWave);
        assertEquals(2, stats.completionCount);
    }
}
