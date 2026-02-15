package com.shieldudaram.fightcaves.session;

import com.shieldudaram.fightcaves.combat.StanceSystem;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.content.LoadedContent;
import com.shieldudaram.fightcaves.content.WaveDefinition;
import com.shieldudaram.fightcaves.data.ActiveRunMarker;
import com.shieldudaram.fightcaves.data.ActiveRunMarkerRepository;
import com.shieldudaram.fightcaves.data.ClaimRepository;
import com.shieldudaram.fightcaves.data.PlayerStats;
import com.shieldudaram.fightcaves.data.RewardClaim;
import com.shieldudaram.fightcaves.data.RunHistoryRepository;
import com.shieldudaram.fightcaves.data.RunRecord;
import com.shieldudaram.fightcaves.data.StatsRepository;
import com.shieldudaram.fightcaves.rewards.RewardService;
import com.shieldudaram.fightcaves.rewards.RewardTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class FightCavesSessionService {

    public enum StartStatus {
        STARTED,
        QUEUED,
        ALREADY_ACTIVE,
        ALREADY_QUEUED,
        INVALID,
        BUSY
    }

    public enum LeaveStatus {
        LEFT_ACTIVE,
        LEFT_QUEUE,
        NOT_FOUND
    }

    private enum RunState {
        PREP,
        IN_WAVE
    }

    private final Supplier<FightCavesConfig> configSupplier;
    private final Supplier<LoadedContent> contentSupplier;
    private final StatsRepository statsRepository;
    private final RunHistoryRepository runHistoryRepository;
    private final ClaimRepository claimRepository;
    private final ActiveRunMarkerRepository markerRepository;
    private final RewardService rewardService;
    private final StanceSystem stanceSystem;
    private final WaveEngine waveEngine;
    private final LongSupplier clock;
    private final Logger logger;

    private final ArrayDeque<PlayerEntry> queue = new ArrayDeque<>();

    private FightCavesEncounterAdapter encounterAdapter;
    private FightCavesUiAdapter uiAdapter;
    private ActiveRun active;

    public FightCavesSessionService(Supplier<FightCavesConfig> configSupplier,
                                    Supplier<LoadedContent> contentSupplier,
                                    StatsRepository statsRepository,
                                    RunHistoryRepository runHistoryRepository,
                                    ClaimRepository claimRepository,
                                    ActiveRunMarkerRepository markerRepository,
                                    RewardService rewardService,
                                    StanceSystem stanceSystem,
                                    WaveEngine waveEngine,
                                    FightCavesEncounterAdapter encounterAdapter,
                                    FightCavesUiAdapter uiAdapter,
                                    LongSupplier clock,
                                    Logger logger) {
        this.configSupplier = configSupplier;
        this.contentSupplier = contentSupplier;
        this.statsRepository = statsRepository;
        this.runHistoryRepository = runHistoryRepository;
        this.claimRepository = claimRepository;
        this.markerRepository = markerRepository;
        this.rewardService = rewardService;
        this.stanceSystem = stanceSystem;
        this.waveEngine = waveEngine == null ? new WaveEngine() : waveEngine;
        this.encounterAdapter = encounterAdapter == null ? FightCavesEncounterAdapter.noop() : encounterAdapter;
        this.uiAdapter = uiAdapter == null ? FightCavesUiAdapter.noop() : uiAdapter;
        this.clock = clock;
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(FightCavesSessionService.class.getName()));
    }

    public synchronized void bindAdapters(FightCavesEncounterAdapter encounterAdapter, FightCavesUiAdapter uiAdapter) {
        this.encounterAdapter = encounterAdapter == null ? FightCavesEncounterAdapter.noop() : encounterAdapter;
        this.uiAdapter = uiAdapter == null ? FightCavesUiAdapter.noop() : uiAdapter;
    }

    public synchronized RecoveryResult recoverIfNeeded() {
        ActiveRunMarker marker = markerRepository.read();
        if (marker == null || marker.playerId == null || marker.playerId.isBlank()) {
            return new RecoveryResult(false, "none");
        }

        long now = clock.getAsLong();
        RunRecord record = new RunRecord();
        record.runId = marker.runId == null ? "recovered-unknown" : marker.runId;
        record.playerId = marker.playerId;
        record.playerName = marker.playerName;
        record.startedAt = marker.startedAt;
        record.endedAt = now;
        record.durationMs = Math.max(0L, now - marker.startedAt);
        record.success = false;
        record.highestWave = Math.max(0, marker.wave);
        record.endReason = "server_restart";
        runHistoryRepository.append(record);

        statsRepository.update(marker.playerId, marker.playerName, stats -> {
            stats.bestWave = Math.max(stats.bestWave, record.highestWave);
            stats.currentStreak = 0;
            stats.lastRunAt = now;
        });

        markerRepository.clear();
        return new RecoveryResult(true, marker.playerId);
    }

    public synchronized StartResult requestStart(String playerId, String playerName, String source) {
        if (playerId == null || playerId.isBlank()) {
            return new StartResult(StartStatus.INVALID, "Players only.", 0);
        }

        FightCavesConfig cfg = configSupplier.get();
        if (cfg == null || cfg.session == null) {
            return new StartResult(StartStatus.BUSY, "Config unavailable.", 0);
        }

        if (active != null && active.playerId.equals(playerId)) {
            return new StartResult(StartStatus.ALREADY_ACTIVE, "You are already in an active run.", 0);
        }

        for (PlayerEntry entry : queue) {
            if (entry.playerId.equals(playerId)) {
                return new StartResult(StartStatus.ALREADY_QUEUED, "You are already queued.", queuePosition(playerId));
            }
        }

        long now = clock.getAsLong();
        if (active == null) {
            return startNow(playerId, playerName, source, now);
        }

        if (!cfg.session.queueEnabled) {
            return new StartResult(StartStatus.BUSY, "A run is already active.", 0);
        }
        if (queue.size() >= cfg.session.queueLimit) {
            return new StartResult(StartStatus.BUSY, "Queue is full.", 0);
        }

        queue.addLast(new PlayerEntry(playerId, playerName, source, now));
        return new StartResult(StartStatus.QUEUED, "Added to queue.", queue.size());
    }

    public synchronized LeaveResult leave(String playerId, String reason) {
        if (playerId == null || playerId.isBlank()) {
            return new LeaveResult(LeaveStatus.NOT_FOUND, "Not in queue or active run.");
        }

        if (active != null && active.playerId.equals(playerId)) {
            finishActive(false, normalizeReason(reason, "left_run"));
            return new LeaveResult(LeaveStatus.LEFT_ACTIVE, "Active run ended.");
        }

        PlayerEntry queued = null;
        for (PlayerEntry entry : queue) {
            if (entry.playerId.equals(playerId)) {
                queued = entry;
                break;
            }
        }
        if (queued != null) {
            queue.remove(queued);
            return new LeaveResult(LeaveStatus.LEFT_QUEUE, "Removed from queue.");
        }

        return new LeaveResult(LeaveStatus.NOT_FOUND, "Not in queue or active run.");
    }

    public synchronized void onDisconnect(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return;
        }

        if (active != null && active.playerId.equals(playerId)) {
            finishActive(false, "disconnect");
            return;
        }

        queue.removeIf(entry -> entry.playerId.equals(playerId));
    }

    public synchronized void onTrackedEntityDeath(String entityUuid) {
        if (entityUuid == null || entityUuid.isBlank()) {
            return;
        }
        encounterAdapter.onTrackedEntityDeath(entityUuid);
    }

    public synchronized void tick() {
        long now = clock.getAsLong();
        if (active == null) {
            maybeStartNext(now);
            return;
        }

        LoadedContent content = contentSupplier.get();
        FightCavesConfig cfg = configSupplier.get();
        if (content == null || cfg == null) {
            finishActive(false, "content_unavailable");
            return;
        }

        int totalWaves = Math.max(1, content.totalWaves());
        if (active.state == RunState.PREP) {
            if (now < active.nextTransitionAtMs) {
                return;
            }
            int nextWave = active.currentWave + 1;
            if (nextWave > totalWaves) {
                finishActive(true, "completed");
                return;
            }

            WaveDefinition wave = content.waves().get(nextWave - 1);
            active.state = RunState.IN_WAVE;
            active.currentWave = nextWave;
            active.nextTransitionAtMs = 0L;

            FightCavesEncounterAdapter.WaveSpawnResult spawn = encounterAdapter.spawnWave(
                    active.runId,
                    active.playerId,
                    nextWave,
                    wave,
                    content,
                    cfg
            );
            if (!spawn.success()) {
                String reason = spawn.message() == null || spawn.message().isBlank()
                        ? "spawn_failed"
                        : "spawn_failed:" + spawn.message();
                finishActive(false, reason);
                return;
            }

            active.enemiesRemaining = Math.max(0, spawn.spawnedCount());
            active.lastUiEnemies = -1;

            if (!isKillGated(cfg)) {
                long waveDuration = waveEngine.computeWaveDurationMillis(content, cfg, nextWave);
                active.nextTransitionAtMs = now + waveDuration;
            }

            markerRepository.save(toMarker(active));
            uiAdapter.onWaveChanged(
                    active.runId,
                    active.playerId,
                    active.currentWave,
                    totalWaves,
                    "in_wave",
                    active.enemiesRemaining,
                    wave != null && wave.bossWave
            );
            active.lastUiEnemies = active.enemiesRemaining;
            return;
        }

        int alive = Math.max(0, encounterAdapter.countAlive(active.runId, active.currentWave));
        active.enemiesRemaining = alive;

        if (active.lastUiEnemies != alive) {
            uiAdapter.onWaveChanged(
                    active.runId,
                    active.playerId,
                    active.currentWave,
                    totalWaves,
                    "in_wave",
                    alive,
                    isBossWave(content, active.currentWave)
            );
            active.lastUiEnemies = alive;
        }

        if (isKillGated(cfg)) {
            if (alive > 0) {
                return;
            }
        } else {
            if (now < active.nextTransitionAtMs && alive > 0) {
                return;
            }
        }

        completeCurrentWave(now, content, cfg);
    }

    public synchronized StatusSnapshot status() {
        ActiveRunSnapshot activeSnapshot = null;
        if (active != null) {
            activeSnapshot = new ActiveRunSnapshot(
                    active.runId,
                    active.playerId,
                    active.playerName,
                    active.currentWave,
                    active.highestWave,
                    active.startedAtMs,
                    active.nextTransitionAtMs,
                    active.state.name().toLowerCase(Locale.ROOT),
                    active.enemiesRemaining
            );
        }

        ArrayList<QueueEntrySnapshot> queueSnapshots = new ArrayList<>();
        int i = 1;
        for (PlayerEntry entry : queue) {
            queueSnapshots.add(new QueueEntrySnapshot(i++, entry.playerId, entry.playerName));
        }

        return new StatusSnapshot(activeSnapshot, queueSnapshots);
    }

    public synchronized List<RewardClaim> claimRewards(String playerId) {
        return claimRepository.claimAll(playerId);
    }

    public synchronized boolean setStance(String playerId, String stance) {
        return stanceSystem.setStance(playerId, stance);
    }

    public synchronized String getStance(String playerId) {
        return stanceSystem.getStance(playerId).id();
    }

    public synchronized AdminResult adminStop() {
        if (active == null) {
            return new AdminResult(false, "No active run.");
        }
        finishActive(false, "admin_stop");
        return new AdminResult(true, "Active run stopped.");
    }

    public synchronized AdminResult adminComplete(String targetPlayerIdOrName) {
        if (active == null) {
            return new AdminResult(false, "No active run.");
        }
        if (targetPlayerIdOrName != null && !targetPlayerIdOrName.isBlank()) {
            if (!targetPlayerIdOrName.equalsIgnoreCase(active.playerId)
                    && !targetPlayerIdOrName.equalsIgnoreCase(active.playerName)) {
                return new AdminResult(false, "Target is not the active runner.");
            }
        }
        LoadedContent content = contentSupplier.get();
        int totalWaves = (content == null) ? 63 : content.totalWaves();
        active.currentWave = Math.max(active.currentWave, totalWaves);
        active.highestWave = Math.max(active.highestWave, active.currentWave);
        finishActive(true, "admin_complete");
        return new AdminResult(true, "Run force-completed.");
    }

    public synchronized AdminResult adminSkipWave() {
        if (active == null) {
            return new AdminResult(false, "No active run.");
        }
        if (active.state != RunState.IN_WAVE || active.currentWave <= 0) {
            return new AdminResult(false, "No active wave to skip.");
        }

        long now = clock.getAsLong();
        encounterAdapter.clearWave(active.runId, active.currentWave, "admin_skipwave");
        active.adminEvents.add("skipwave:wave=" + active.currentWave + ":at=" + now);
        active.enemiesRemaining = 0;
        active.lastUiEnemies = 0;

        LoadedContent content = contentSupplier.get();
        FightCavesConfig cfg = configSupplier.get();
        if (content == null || cfg == null) {
            finishActive(false, "content_unavailable");
            return new AdminResult(true, "Wave skipped.");
        }

        completeCurrentWave(now, content, cfg);
        return new AdminResult(true, "Skipped current wave.");
    }

    public synchronized AdminResult adminGrant(String playerId, String rewardId, int amount) {
        if (playerId == null || playerId.isBlank()) {
            return new AdminResult(false, "Player id is required.");
        }
        if (rewardId == null || rewardId.isBlank()) {
            return new AdminResult(false, "Reward id is required.");
        }
        if (amount <= 0) {
            return new AdminResult(false, "Amount must be positive.");
        }
        claimRepository.addClaim(playerId, new RewardClaim(rewardId, amount, clock.getAsLong()));
        return new AdminResult(true, "Granted reward claim.");
    }

    public synchronized AdminResult adminResetStats(String playerIdOrName) {
        if (playerIdOrName == null || playerIdOrName.isBlank()) {
            return new AdminResult(false, "Player id or name is required.");
        }

        String targetId = playerIdOrName;
        PlayerStats byName = statsRepository.findByName(playerIdOrName);
        if (byName != null && byName.playerId != null && !byName.playerId.isBlank()) {
            targetId = byName.playerId;
        }

        boolean removed = statsRepository.reset(targetId);
        if (!removed) {
            return new AdminResult(false, "No stats found for player.");
        }
        return new AdminResult(true, "Player stats reset.");
    }

    private StartResult startNow(String playerId, String playerName, String source, long now) {
        FightCavesConfig cfg = configSupplier.get();
        LoadedContent content = contentSupplier.get();
        if (cfg == null || content == null) {
            return new StartResult(StartStatus.BUSY, "Content unavailable.", 0);
        }

        String safeName = (playerName == null || playerName.isBlank()) ? playerId : playerName;
        String runId = "fc-" + now + "-" + UUID.randomUUID().toString().substring(0, 8);

        FightCavesEncounterAdapter.RunStartResult prep = encounterAdapter.onRunStart(runId, playerId, safeName, cfg);
        if (prep == null || !prep.success()) {
            String message = (prep == null || prep.message() == null || prep.message().isBlank())
                    ? "Arena preparation failed."
                    : prep.message();
            return new StartResult(StartStatus.BUSY, message, 0);
        }

        ActiveRun run = new ActiveRun();
        run.runId = runId;
        run.playerId = playerId;
        run.playerName = safeName;
        run.source = source;
        run.startedAtMs = now;
        run.currentWave = 0;
        run.highestWave = 0;
        run.state = RunState.PREP;
        run.nextTransitionAtMs = now + Math.max(0, cfg.session.startGraceSeconds) * 1000L;
        run.enemiesRemaining = 0;
        run.lastUiEnemies = -1;

        this.active = run;
        markerRepository.save(toMarker(run));
        statsRepository.getOrCreate(playerId, safeName);
        uiAdapter.onRunStarted(runId, playerId, safeName, Math.max(1, content.totalWaves()));

        return new StartResult(StartStatus.STARTED, "Run started.", 0);
    }

    private void completeCurrentWave(long now, LoadedContent content, FightCavesConfig cfg) {
        if (active == null || content == null || cfg == null) {
            return;
        }

        int totalWaves = Math.max(1, content.totalWaves());
        active.highestWave = Math.max(active.highestWave, active.currentWave);
        active.enemiesRemaining = 0;

        if (active.currentWave >= totalWaves) {
            finishActive(true, "completed");
            return;
        }

        active.state = RunState.PREP;
        long prepMillis = Math.max(0, cfg.combat.preWavePrepSeconds) * 1000L;
        active.nextTransitionAtMs = now + prepMillis;
        active.lastUiEnemies = -1;
        markerRepository.save(toMarker(active));

        uiAdapter.onWaveChanged(
                active.runId,
                active.playerId,
                active.currentWave,
                totalWaves,
                "prep",
                0,
                isBossWave(content, active.currentWave)
        );
    }

    private void finishActive(boolean success, String reason) {
        if (active == null) {
            return;
        }

        ActiveRun finishing = active;
        active = null;
        markerRepository.clear();

        long now = clock.getAsLong();
        LoadedContent content = contentSupplier.get();
        FightCavesConfig cfg = configSupplier.get();
        int totalWaves = (content == null) ? 63 : content.totalWaves();
        int highestWave = Math.max(finishing.highestWave, finishing.currentWave);

        RewardTable rewardTable = content == null ? null : content.rewards();
        List<RewardClaim> rewards = rewardService.generateRewards(
                success,
                highestWave,
                totalWaves,
                rewardTable,
                finishing.runId,
                now
        );

        boolean allowPartial = cfg != null && cfg.rewards != null && cfg.rewards.partialFailureEnabled;
        if (!success && !allowPartial) {
            rewards = List.of();
        }
        claimRepository.addClaims(finishing.playerId, rewards);

        long duration = Math.max(0L, now - finishing.startedAtMs);
        statsRepository.update(finishing.playerId, finishing.playerName, stats -> {
            stats.bestWave = Math.max(stats.bestWave, highestWave);
            stats.lastRunAt = now;
            if (success) {
                stats.completionCount += 1;
                stats.currentStreak += 1;
                if (stats.bestTimeMs == 0L || duration < stats.bestTimeMs) {
                    stats.bestTimeMs = duration;
                }
            } else {
                stats.currentStreak = 0;
            }
        });

        RunRecord record = new RunRecord();
        record.runId = finishing.runId;
        record.playerId = finishing.playerId;
        record.playerName = finishing.playerName;
        record.startedAt = finishing.startedAtMs;
        record.endedAt = now;
        record.durationMs = duration;
        record.success = success;
        record.highestWave = highestWave;
        record.endReason = normalizeReason(reason, success ? "completed" : "failed");
        record.rewards = new ArrayList<>(rewards);
        record.adminEvents = new ArrayList<>(finishing.adminEvents);
        runHistoryRepository.append(record);

        stanceSystem.clear(finishing.playerId);
        try {
            encounterAdapter.clearRun(finishing.runId, reason);
            encounterAdapter.onRunEnd(finishing.runId, finishing.playerId, success, reason, cfg);
        } catch (Throwable t) {
            logger.warning("[FightCaves] Encounter cleanup failed for run " + finishing.runId + ": " + t.getMessage());
        }

        try {
            uiAdapter.onRunEnded(
                    finishing.runId,
                    finishing.playerId,
                    finishing.playerName,
                    success,
                    normalizeReason(reason, success ? "completed" : "failed"),
                    highestWave,
                    totalWaves
            );
        } catch (Throwable ignored) {
        }

        maybeStartNext(now);
    }

    private void maybeStartNext(long now) {
        if (active != null) {
            return;
        }
        PlayerEntry next = queue.pollFirst();
        if (next == null) {
            return;
        }
        startNow(next.playerId, next.playerName, "queue", now);
    }

    private int queuePosition(String playerId) {
        int i = 1;
        for (PlayerEntry entry : queue) {
            if (entry.playerId.equals(playerId)) {
                return i;
            }
            i++;
        }
        return 0;
    }

    private static ActiveRunMarker toMarker(ActiveRun run) {
        ActiveRunMarker marker = new ActiveRunMarker();
        marker.runId = run.runId;
        marker.playerId = run.playerId;
        marker.playerName = run.playerName;
        marker.startedAt = run.startedAtMs;
        marker.wave = run.currentWave;
        return marker;
    }

    private static String normalizeReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason;
    }

    private static boolean isKillGated(FightCavesConfig cfg) {
        if (cfg == null || cfg.combat == null || cfg.combat.waveAdvanceMode == null) {
            return true;
        }
        String mode = cfg.combat.waveAdvanceMode.trim().toLowerCase(Locale.ROOT);
        return mode.isBlank() || "kills_only".equals(mode);
    }

    private static boolean isBossWave(LoadedContent content, int waveNumber) {
        if (content == null || waveNumber <= 0 || waveNumber > content.totalWaves()) {
            return false;
        }
        WaveDefinition wave = content.waves().get(waveNumber - 1);
        return wave != null && wave.bossWave;
    }

    private static final class ActiveRun {
        String runId;
        String playerId;
        String playerName;
        String source;
        long startedAtMs;
        int currentWave;
        int highestWave;
        long nextTransitionAtMs;
        RunState state;
        int enemiesRemaining;
        int lastUiEnemies;
        List<String> adminEvents = new ArrayList<>();
    }

    private record PlayerEntry(String playerId, String playerName, String source, long queuedAtMs) {
    }

    public record StartResult(StartStatus status, String message, int queuePosition) {
    }

    public record LeaveResult(LeaveStatus status, String message) {
    }

    public record RecoveryResult(boolean recovered, String playerId) {
    }

    public record AdminResult(boolean success, String message) {
    }

    public record ActiveRunSnapshot(String runId,
                                    String playerId,
                                    String playerName,
                                    int currentWave,
                                    int highestWave,
                                    long startedAtMs,
                                    long nextTransitionAtMs,
                                    String state,
                                    int enemiesRemaining) {
    }

    public record QueueEntrySnapshot(int position, String playerId, String playerName) {
    }

    public record StatusSnapshot(ActiveRunSnapshot activeRun, List<QueueEntrySnapshot> queue) {
    }
}
