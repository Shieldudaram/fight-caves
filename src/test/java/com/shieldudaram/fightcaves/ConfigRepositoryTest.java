package com.shieldudaram.fightcaves;

import com.shieldudaram.fightcaves.config.ConfigRepository;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRepositoryTest {

    @Test
    void loadsAndNormalizesDefaults() throws Exception {
        Path dir = Files.createTempDirectory("fc-config-test");
        ConfigRepository repo = new ConfigRepository(dir, Logger.getLogger("test"));

        FightCavesConfig cfg = repo.get();
        assertEquals(1, cfg.session.maxConcurrentRuns);
        assertEquals("fail_run", cfg.session.disconnectBehavior);
        assertEquals(3, cfg.combat.attackTypes.size());
        assertTrue(Files.exists(repo.getConfigPath()));
    }
}
