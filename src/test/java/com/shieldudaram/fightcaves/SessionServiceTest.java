package com.shieldudaram.fightcaves;

import com.shieldudaram.fightcaves.combat.StanceSystem;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.content.ContentRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionServiceTest {

    @Test
    void queueAndDisconnectFlowWorks() throws Exception {
        Path dir = Files.createTempDirectory("fc-session-test");
        ContentRepository contentRepository = new ContentRepository(dir, Logger.getLogger("test"));

        FightCavesConfig cfg = new FightCavesConfig();
        cfg.session.startGraceSeconds = 0;
        cfg.combat.preWavePrepSeconds = 1;

        StatsRepository statsRepo = new StatsRepository(dir.resolve("stats.json"), Logger.getLogger("test"));
        RunHistoryRepository historyRepo = new RunHistoryRepository(dir.resolve("history.jsonl"), Logger.getLogger("test"));
        ClaimRepository claimRepo = new ClaimRepository(dir.resolve("claims.json"), Logger.getLogger("test"));
        ActiveRunMarkerRepository markerRepo = new ActiveRunMarkerRepository(dir.resolve("active.json"), Logger.getLogger("test"));

        MutableClock clock = new MutableClock(1_000L);

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

        FightCavesSessionService.StartResult first = service.requestStart("p1", "PlayerOne", "command");
        FightCavesSessionService.StartResult second = service.requestStart("p2", "PlayerTwo", "command");

        assertEquals(FightCavesSessionService.StartStatus.STARTED, first.status());
        assertEquals(FightCavesSessionService.StartStatus.QUEUED, second.status());

        // Move into wave 1.
        clock.advance(50_000L);
        service.tick();
        assertNotNull(service.status().activeRun());
        assertEquals("p1", service.status().activeRun().playerId());

        // Disconnect active player: run fails and queued player starts.
        service.onDisconnect("p1");
        assertNotNull(service.status().activeRun());
        assertEquals("p2", service.status().activeRun().playerId());
        assertEquals(0, service.status().queue().size());
    }
}
