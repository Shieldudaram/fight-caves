package com.shieldudaram.fightcaves;

import com.shieldudaram.fightcaves.combat.StanceSystem;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.content.ContentRepository;
import com.shieldudaram.fightcaves.data.ActiveRunMarker;
import com.shieldudaram.fightcaves.data.ActiveRunMarkerRepository;
import com.shieldudaram.fightcaves.data.ClaimRepository;
import com.shieldudaram.fightcaves.data.RunHistoryRepository;
import com.shieldudaram.fightcaves.data.StatsRepository;
import com.shieldudaram.fightcaves.rewards.RewardService;
import com.shieldudaram.fightcaves.session.FightCavesEncounterAdapter;
import com.shieldudaram.fightcaves.session.FightCavesSessionService;
import com.shieldudaram.fightcaves.session.FightCavesUiAdapter;
import com.shieldudaram.fightcaves.session.WaveEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestartRecoveryTest {

    @Test
    void marksActiveRunFailedOnStartupRecovery() throws Exception {
        Path dir = Files.createTempDirectory("fc-recovery-test");
        ContentRepository contentRepository = new ContentRepository(dir, Logger.getLogger("test"));

        FightCavesConfig cfg = new FightCavesConfig();
        StatsRepository statsRepo = new StatsRepository(dir.resolve("stats.json"), Logger.getLogger("test"));
        RunHistoryRepository historyRepo = new RunHistoryRepository(dir.resolve("history.jsonl"), Logger.getLogger("test"));
        ClaimRepository claimRepo = new ClaimRepository(dir.resolve("claims.json"), Logger.getLogger("test"));
        ActiveRunMarkerRepository markerRepo = new ActiveRunMarkerRepository(dir.resolve("active.json"), Logger.getLogger("test"));

        ActiveRunMarker marker = new ActiveRunMarker();
        marker.runId = "run-old";
        marker.playerId = "p1";
        marker.playerName = "PlayerOne";
        marker.startedAt = 1000L;
        marker.wave = 27;
        markerRepo.save(marker);

        MutableClock clock = new MutableClock(20_000L);

        FightCavesSessionService service = new FightCavesSessionService(
                () -> cfg,
                contentRepository::get,
                statsRepo,
                historyRepo,
                claimRepo,
                markerRepo,
                new RewardService(),
                new StanceSystem(),
                new WaveEngine(),
                FightCavesEncounterAdapter.noop(),
                FightCavesUiAdapter.noop(),
                clock,
                Logger.getLogger("test")
        );

        FightCavesSessionService.RecoveryResult recovered = service.recoverIfNeeded();

        assertTrue(recovered.recovered());
        assertFalse(historyRepo.readAll().isEmpty());
        assertTrue(markerRepo.read() == null);
    }
}
