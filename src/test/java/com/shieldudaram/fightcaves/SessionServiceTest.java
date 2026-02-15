package com.shieldudaram.fightcaves;

import com.shieldudaram.fightcaves.combat.StanceSystem;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.content.ContentRepository;
import com.shieldudaram.fightcaves.data.ActiveRunMarkerRepository;
import com.shieldudaram.fightcaves.data.ClaimRepository;
import com.shieldudaram.fightcaves.data.PlayerStats;
import com.shieldudaram.fightcaves.data.RunHistoryRepository;
import com.shieldudaram.fightcaves.data.RunRecord;
import com.shieldudaram.fightcaves.data.StatsRepository;
import com.shieldudaram.fightcaves.rewards.RewardService;
import com.shieldudaram.fightcaves.session.FightCavesEncounterAdapter;
import com.shieldudaram.fightcaves.session.FightCavesSessionService;
import com.shieldudaram.fightcaves.session.FightCavesUiAdapter;
import com.shieldudaram.fightcaves.session.WaveEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionServiceTest {

    @Test
    void queueAndDisconnectFlowWorks() throws Exception {
        Harness harness = harness(FightCavesEncounterAdapter.noop());
        FightCavesSessionService service = harness.service;
        MutableClock clock = harness.clock;

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

    @Test
    void spawnFailureDoesNotCreditAttemptedWave() throws Exception {
        Harness harness = harness(new SpawnFailEncounterAdapter());
        FightCavesSessionService service = harness.service;

        FightCavesSessionService.StartResult start = service.requestStart("p1", "PlayerOne", "command");
        assertEquals(FightCavesSessionService.StartStatus.STARTED, start.status());

        service.tick();
        assertNull(service.status().activeRun());

        List<RunRecord> records = harness.historyRepo.readAll();
        assertEquals(1, records.size());
        assertEquals(0, records.get(0).highestWave);
        assertTrue(records.get(0).endReason.startsWith("spawn_failed"));

        assertTrue(harness.claimRepo.peek("p1").isEmpty());
        PlayerStats stats = harness.statsRepo.getOrCreate("p1", "PlayerOne");
        assertEquals(0, stats.bestWave);
    }

    @Test
    void queuedPlayerIsRetainedWhenAutoStartTemporarilyFails() throws Exception {
        Harness harness = harness(new OneTransientQueueStartFailureAdapter());
        FightCavesSessionService service = harness.service;

        FightCavesSessionService.StartResult first = service.requestStart("p1", "PlayerOne", "command");
        FightCavesSessionService.StartResult second = service.requestStart("p2", "PlayerTwo", "command");
        assertEquals(FightCavesSessionService.StartStatus.STARTED, first.status());
        assertEquals(FightCavesSessionService.StartStatus.QUEUED, second.status());

        service.leave("p1", "test_end_first");
        assertNull(service.status().activeRun());
        assertEquals(1, service.status().queue().size());
        assertEquals("p2", service.status().queue().getFirst().playerId());

        service.tick();
        assertNotNull(service.status().activeRun());
        assertEquals("p2", service.status().activeRun().playerId());
        assertTrue(service.status().queue().isEmpty());
    }

    @Test
    void unknownAliveCountDoesNotPrematurelyAdvanceWave() throws Exception {
        Harness harness = harness(new SequencedAliveCountAdapter(
                FightCavesEncounterAdapter.ALIVE_COUNT_UNKNOWN,
                0
        ));
        FightCavesSessionService service = harness.service;

        FightCavesSessionService.StartResult start = service.requestStart("p1", "PlayerOne", "command");
        assertEquals(FightCavesSessionService.StartStatus.STARTED, start.status());

        service.tick();
        assertNotNull(service.status().activeRun());
        assertEquals("in_wave", service.status().activeRun().state());
        assertEquals(1, service.status().activeRun().currentWave());

        service.tick();
        assertNotNull(service.status().activeRun());
        assertEquals("in_wave", service.status().activeRun().state());
        assertEquals(1, service.status().activeRun().currentWave());

        service.tick();
        assertNotNull(service.status().activeRun());
        assertEquals("prep", service.status().activeRun().state());
        assertEquals(1, service.status().activeRun().currentWave());
    }

    private static Harness harness(FightCavesEncounterAdapter encounterAdapter) throws Exception {
        Path dir = Files.createTempDirectory("fc-session-test");
        ContentRepository contentRepository = new ContentRepository(dir, Logger.getLogger("test"));

        FightCavesConfig cfg = new FightCavesConfig();
        cfg.session.startGraceSeconds = 0;
        cfg.combat.preWavePrepSeconds = 1;
        cfg.rewards.partialFailureEnabled = true;

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
                encounterAdapter,
                FightCavesUiAdapter.noop(),
                clock,
                Logger.getLogger("test")
        );
        return new Harness(service, clock, statsRepo, historyRepo, claimRepo);
    }

    private record Harness(FightCavesSessionService service,
                           MutableClock clock,
                           StatsRepository statsRepo,
                           RunHistoryRepository historyRepo,
                           ClaimRepository claimRepo) {
    }

    private static final class SpawnFailEncounterAdapter implements FightCavesEncounterAdapter {
        @Override
        public RunStartResult onRunStart(String runId, String playerId, String playerName, FightCavesConfig config) {
            return RunStartResult.ok();
        }

        @Override
        public void onRunEnd(String runId, String playerId, boolean success, String reason, FightCavesConfig config) {
        }

        @Override
        public WaveSpawnResult spawnWave(String runId,
                                         String playerId,
                                         int waveNumber,
                                         com.shieldudaram.fightcaves.content.WaveDefinition wave,
                                         com.shieldudaram.fightcaves.content.LoadedContent content,
                                         FightCavesConfig config) {
            return WaveSpawnResult.fail("forced_spawn_failure");
        }

        @Override
        public int countAlive(String runId, int waveNumber) {
            return 0;
        }

        @Override
        public void clearWave(String runId, int waveNumber, String reason) {
        }

        @Override
        public void clearRun(String runId, String reason) {
        }

        @Override
        public void onTrackedEntityDeath(String entityUuid) {
        }
    }

    private static final class OneTransientQueueStartFailureAdapter implements FightCavesEncounterAdapter {
        private boolean failedSecondRunnerStart = false;

        @Override
        public RunStartResult onRunStart(String runId, String playerId, String playerName, FightCavesConfig config) {
            if ("p2".equals(playerId) && !failedSecondRunnerStart) {
                failedSecondRunnerStart = true;
                return RunStartResult.fail("temporary_busy");
            }
            return RunStartResult.ok();
        }

        @Override
        public void onRunEnd(String runId, String playerId, boolean success, String reason, FightCavesConfig config) {
        }

        @Override
        public WaveSpawnResult spawnWave(String runId,
                                         String playerId,
                                         int waveNumber,
                                         com.shieldudaram.fightcaves.content.WaveDefinition wave,
                                         com.shieldudaram.fightcaves.content.LoadedContent content,
                                         FightCavesConfig config) {
            return WaveSpawnResult.ok(1);
        }

        @Override
        public int countAlive(String runId, int waveNumber) {
            return Integer.MAX_VALUE;
        }

        @Override
        public void clearWave(String runId, int waveNumber, String reason) {
        }

        @Override
        public void clearRun(String runId, String reason) {
        }

        @Override
        public void onTrackedEntityDeath(String entityUuid) {
        }
    }

    private static final class SequencedAliveCountAdapter implements FightCavesEncounterAdapter {
        private final int[] values;
        private int index = 0;

        private SequencedAliveCountAdapter(int... values) {
            this.values = values == null ? new int[]{0} : values;
        }

        @Override
        public RunStartResult onRunStart(String runId, String playerId, String playerName, FightCavesConfig config) {
            return RunStartResult.ok();
        }

        @Override
        public void onRunEnd(String runId, String playerId, boolean success, String reason, FightCavesConfig config) {
        }

        @Override
        public WaveSpawnResult spawnWave(String runId,
                                         String playerId,
                                         int waveNumber,
                                         com.shieldudaram.fightcaves.content.WaveDefinition wave,
                                         com.shieldudaram.fightcaves.content.LoadedContent content,
                                         FightCavesConfig config) {
            return WaveSpawnResult.ok(1);
        }

        @Override
        public int countAlive(String runId, int waveNumber) {
            if (index < values.length) {
                return values[index++];
            }
            return values[values.length - 1];
        }

        @Override
        public void clearWave(String runId, int waveNumber, String reason) {
        }

        @Override
        public void clearRun(String runId, String reason) {
        }

        @Override
        public void onTrackedEntityDeath(String entityUuid) {
        }
    }
}
