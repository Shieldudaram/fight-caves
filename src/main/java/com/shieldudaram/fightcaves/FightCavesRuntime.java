package com.shieldudaram.fightcaves;

import com.shieldudaram.fightcaves.arena.ArenaProfile;
import com.shieldudaram.fightcaves.arena.ArenaProfileRepository;
import com.shieldudaram.fightcaves.arena.SpawnPoint;
import com.shieldudaram.fightcaves.commands.CommandResult;
import com.shieldudaram.fightcaves.commands.FightCavesCommandContext;
import com.shieldudaram.fightcaves.commands.FightCavesCommandRouter;
import com.shieldudaram.fightcaves.combat.AttackType;
import com.shieldudaram.fightcaves.combat.StanceSystem;
import com.shieldudaram.fightcaves.config.ConfigRepository;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.content.ContentRepository;
import com.shieldudaram.fightcaves.data.ActiveRunMarkerRepository;
import com.shieldudaram.fightcaves.data.ClaimRepository;
import com.shieldudaram.fightcaves.data.PlayerStats;
import com.shieldudaram.fightcaves.data.RewardClaim;
import com.shieldudaram.fightcaves.data.RunHistoryRepository;
import com.shieldudaram.fightcaves.data.StatsRepository;
import com.shieldudaram.fightcaves.rewards.RewardService;
import com.shieldudaram.fightcaves.session.FightCavesEncounterAdapter;
import com.shieldudaram.fightcaves.session.FightCavesSessionService;
import com.shieldudaram.fightcaves.session.FightCavesUiAdapter;
import com.shieldudaram.fightcaves.session.WaveEngine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

public final class FightCavesRuntime {
    private final Path dataRoot;
    private final Logger logger;
    private final LongSupplier clock;

    private final ConfigRepository configRepository;
    private final ContentRepository contentRepository;
    private final StatsRepository statsRepository;
    private final RunHistoryRepository runHistoryRepository;
    private final ClaimRepository claimRepository;
    private final ActiveRunMarkerRepository markerRepository;
    private final ArenaProfileRepository arenaProfileRepository;

    private final StanceSystem stanceSystem;
    private final FightCavesSessionService sessionService;
    private final FightCavesCommandRouter commandRouter;

    private ArenaProfile arenaOverrides;
    private final Map<String, ArenaMarks> arenaMarksByAdmin = new LinkedHashMap<>();

    private static final class ArenaMarks {
        private FightCavesCommandContext.PositionSnapshot mark1;
        private FightCavesCommandContext.PositionSnapshot mark2;
    }

    public FightCavesRuntime(Path dataRoot) {
        this(dataRoot, System::currentTimeMillis, Logger.getLogger(FightCavesRuntime.class.getName()));
    }

    public FightCavesRuntime(Path dataRoot, LongSupplier clock, Logger logger) {
        this.dataRoot = dataRoot == null ? Path.of(".") : dataRoot;
        this.clock = Objects.requireNonNullElse(clock, System::currentTimeMillis);
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(FightCavesRuntime.class.getName()));

        this.configRepository = new ConfigRepository(this.dataRoot, this.logger);
        this.contentRepository = new ContentRepository(this.dataRoot, this.logger);
        this.arenaProfileRepository = new ArenaProfileRepository(this.dataRoot.resolve("fight_caves_arena_profile.json"), this.logger);
        this.arenaOverrides = this.arenaProfileRepository.read();

        FightCavesConfig cfg = effectiveConfig();
        this.statsRepository = new StatsRepository(this.dataRoot.resolve(cfg.persistence.playerStatsFile), this.logger);
        this.runHistoryRepository = new RunHistoryRepository(this.dataRoot.resolve(cfg.persistence.runHistoryFile), this.logger);
        this.claimRepository = new ClaimRepository(this.dataRoot.resolve(cfg.persistence.claimsFile), this.logger);
        this.markerRepository = new ActiveRunMarkerRepository(this.dataRoot.resolve(cfg.persistence.activeRunMarkerFile), this.logger);

        this.stanceSystem = new StanceSystem();

        this.sessionService = new FightCavesSessionService(
                this::effectiveConfig,
                this.contentRepository::get,
                this.statsRepository,
                this.runHistoryRepository,
                this.claimRepository,
                this.markerRepository,
                new RewardService(),
                this.stanceSystem,
                new WaveEngine(),
                FightCavesEncounterAdapter.noop(),
                FightCavesUiAdapter.noop(),
                this.clock,
                this.logger
        );

        this.commandRouter = new FightCavesCommandRouter(this);
        FightCavesSessionService.RecoveryResult recoveryResult = this.sessionService.recoverIfNeeded();
        if (recoveryResult.recovered()) {
            this.logger.info("[FightCaves] Recovered unfinished run for player " + recoveryResult.playerId() + " as failed.");
        }
    }

    public synchronized void bindPlatformAdapters(FightCavesEncounterAdapter encounterAdapter, FightCavesUiAdapter uiAdapter) {
        sessionService.bindAdapters(encounterAdapter, uiAdapter);
    }

    public CommandResult handleCommand(String rawCommand, FightCavesCommandContext context) {
        return commandRouter.execute(rawCommand, context == null ? FightCavesCommandContext.console() : context);
    }

    public void tick() {
        sessionService.tick();
    }

    public void onPlayerDisconnect(String playerId) {
        sessionService.onDisconnect(playerId);
    }

    public void onTrackedEntityDeath(String entityUuid) {
        sessionService.onTrackedEntityDeath(entityUuid);
    }

    public FightCavesSessionService.StartResult startRun(String playerId, String playerName, String source) {
        return sessionService.requestStart(playerId, playerName, source);
    }

    public FightCavesSessionService.LeaveResult leaveRun(String playerId, String reason) {
        return sessionService.leave(playerId, reason);
    }

    public boolean isActiveRunner(String playerId) {
        FightCavesSessionService.StatusSnapshot status = sessionService.status();
        return status.activeRun() != null
                && status.activeRun().playerId() != null
                && status.activeRun().playerId().equals(playerId);
    }

    public float resolveDamageMultiplier(String playerId, AttackType attackType) {
        return stanceSystem.damageMultiplier(playerId, attackType, effectiveConfig());
    }

    public List<RewardClaim> claimRewards(String playerId) {
        return sessionService.claimRewards(playerId);
    }

    public boolean setStance(String playerId, String stance) {
        return sessionService.setStance(playerId, stance);
    }

    public String getStance(String playerId) {
        return sessionService.getStance(playerId);
    }

    public FightCavesSessionService.AdminResult adminStop() {
        return sessionService.adminStop();
    }

    public FightCavesSessionService.AdminResult adminComplete(String playerId) {
        return sessionService.adminComplete(playerId);
    }

    public FightCavesSessionService.AdminResult adminSkipWave() {
        return sessionService.adminSkipWave();
    }

    public FightCavesSessionService.AdminResult adminGrant(String playerId, String rewardId, int amount) {
        return sessionService.adminGrant(playerId, rewardId, amount);
    }

    public FightCavesSessionService.AdminResult adminResetStats(String playerIdOrName) {
        return sessionService.adminResetStats(playerIdOrName);
    }

    public synchronized void reload() {
        configRepository.reload();
        contentRepository.reload();
        arenaOverrides = arenaProfileRepository.read();
    }

    public String resolvePlayerId(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        FightCavesSessionService.StatusSnapshot status = sessionService.status();
        if (status.activeRun() != null) {
            if (identifier.equalsIgnoreCase(status.activeRun().playerId())
                    || identifier.equalsIgnoreCase(status.activeRun().playerName())) {
                return status.activeRun().playerId();
            }
        }

        for (FightCavesSessionService.QueueEntrySnapshot entry : status.queue()) {
            if (identifier.equalsIgnoreCase(entry.playerId())
                    || identifier.equalsIgnoreCase(entry.playerName())) {
                return entry.playerId();
            }
        }

        PlayerStats byName = statsRepository.findByName(identifier);
        if (byName != null) {
            return byName.playerId;
        }

        return identifier;
    }

    public String formatStatus(String viewerPlayerId) {
        FightCavesSessionService.StatusSnapshot status = sessionService.status();
        FightCavesConfig cfg = effectiveConfig();
        String prefix = cfg.messages.prefix;

        if (status.activeRun() == null) {
            return prefix + " No active run. Queue size: " + status.queue().size();
        }

        FightCavesSessionService.ActiveRunSnapshot active = status.activeRun();
        String focus = (viewerPlayerId != null && viewerPlayerId.equals(active.playerId())) ? " (you)" : "";
        String next = "-";
        if ("prep".equalsIgnoreCase(active.state())) {
            long now = clock.getAsLong();
            long secondsToNext = Math.max(0L, (active.nextTransitionAtMs() - now) / 1000L);
            next = secondsToNext + "s";
        }

        return prefix + " Active run: "
                + active.playerName() + focus
                + " | wave=" + active.currentWave()
                + " | best=" + active.highestWave()
                + " | state=" + active.state()
                + " | enemies=" + active.enemiesRemaining()
                + " | next=" + next
                + " | queue=" + status.queue().size();
    }

    public String formatQueue() {
        FightCavesSessionService.StatusSnapshot status = sessionService.status();
        FightCavesConfig cfg = effectiveConfig();
        String prefix = cfg.messages.prefix;

        if (status.queue().isEmpty()) {
            return prefix + " Queue is empty.";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (FightCavesSessionService.QueueEntrySnapshot entry : status.queue()) {
            joiner.add(entry.position() + ":" + entry.playerName());
        }
        return prefix + " Queue -> " + joiner;
    }

    public boolean handleNpcEntry(String playerId, String playerName, String npcTypeId, String heldItemId) {
        FightCavesConfig cfg = effectiveConfig();
        if (!cfg.entry.npcEnabled || !cfg.npc.enabled) {
            return false;
        }

        boolean matchedNpc = npcTypeId != null
                && cfg.npc.npcTypeId != null
                && npcTypeId.equalsIgnoreCase(cfg.npc.npcTypeId);

        boolean matchedFallbackItem = heldItemId != null
                && cfg.npc.fallbackInteractionItemId != null
                && heldItemId.equalsIgnoreCase(cfg.npc.fallbackInteractionItemId);

        if (!matchedNpc && !matchedFallbackItem) {
            return false;
        }

        FightCavesSessionService.StartResult result = sessionService.requestStart(playerId, playerName, "npc");
        return result.status() == FightCavesSessionService.StartStatus.STARTED
                || result.status() == FightCavesSessionService.StartStatus.QUEUED;
    }

    public synchronized String adminArenaMark(String adminId, int markIndex, FightCavesCommandContext.PositionSnapshot position) {
        if (position == null || position.world() == null || position.world().isBlank()) {
            return "Player position unavailable.";
        }
        ArenaMarks marks = arenaMarksByAdmin.computeIfAbsent(adminId == null ? "console" : adminId, k -> new ArenaMarks());
        if (markIndex == 1) {
            marks.mark1 = position;
            return "Arena mark1 set to " + formatPos(position);
        }
        if (markIndex == 2) {
            marks.mark2 = position;
            return "Arena mark2 set to " + formatPos(position);
        }
        return "Invalid mark index.";
    }

    public synchronized String adminArenaSpawnAdd(String spawnId, FightCavesCommandContext.PositionSnapshot position) {
        if (spawnId == null || spawnId.isBlank()) {
            return "Spawn id is required.";
        }
        if (position == null || position.world() == null || position.world().isBlank()) {
            return "Player position unavailable.";
        }

        ArenaProfile profile = mutableOverrideProfile();
        profile.world = position.world();

        boolean replaced = false;
        for (SpawnPoint point : profile.spawnPoints) {
            if (point == null || point.id == null) continue;
            if (!point.id.equalsIgnoreCase(spawnId)) continue;
            point.pos = new int[]{position.blockX(), position.blockY(), position.blockZ()};
            replaced = true;
            break;
        }
        if (!replaced) {
            profile.spawnPoints.add(new SpawnPoint(spawnId, new int[]{position.blockX(), position.blockY(), position.blockZ()}));
        }
        saveArenaOverrides(profile);
        return replaced
                ? "Updated spawn point '" + spawnId + "' to " + formatPos(position)
                : "Added spawn point '" + spawnId + "' at " + formatPos(position);
    }

    public synchronized String adminArenaSpawnRemove(String spawnId) {
        if (spawnId == null || spawnId.isBlank()) {
            return "Spawn id is required.";
        }

        ArenaProfile profile = mutableOverrideProfile();
        int before = profile.spawnPoints.size();
        profile.spawnPoints.removeIf(point -> point != null && point.id != null && point.id.equalsIgnoreCase(spawnId));
        if (profile.spawnPoints.isEmpty()) {
            profile.spawnPoints.add(new SpawnPoint("default_1", profile.playerSpawn.clone()));
        }
        saveArenaOverrides(profile);
        return before == profile.spawnPoints.size()
                ? "No spawn point found for id '" + spawnId + "'."
                : "Removed spawn point '" + spawnId + "'.";
    }

    public synchronized String adminArenaSpawnList() {
        ArenaProfile profile = effectiveArenaProfile();
        if (profile.spawnPoints == null || profile.spawnPoints.isEmpty()) {
            return "Spawn points: none";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (SpawnPoint point : profile.spawnPoints) {
            if (point == null || point.id == null || point.id.isBlank() || point.pos == null || point.pos.length < 3) continue;
            joiner.add(point.id + "=" + point.pos[0] + "/" + point.pos[1] + "/" + point.pos[2]);
        }
        String text = joiner.toString();
        return text.isBlank() ? "Spawn points: none" : "Spawn points: " + text;
    }

    public synchronized String adminArenaSave(String adminId) {
        ArenaMarks marks = arenaMarksByAdmin.get(adminId == null ? "console" : adminId);
        if (marks == null || marks.mark1 == null || marks.mark2 == null) {
            return "Set mark1 and mark2 first.";
        }
        if (!marks.mark1.world().equalsIgnoreCase(marks.mark2.world())) {
            return "mark1 and mark2 must be in the same world.";
        }

        int minX = Math.min(marks.mark1.blockX(), marks.mark2.blockX());
        int minY = Math.min(marks.mark1.blockY(), marks.mark2.blockY());
        int minZ = Math.min(marks.mark1.blockZ(), marks.mark2.blockZ());
        int maxX = Math.max(marks.mark1.blockX(), marks.mark2.blockX());
        int maxY = Math.max(marks.mark1.blockY(), marks.mark2.blockY());
        int maxZ = Math.max(marks.mark1.blockZ(), marks.mark2.blockZ());

        ArenaProfile profile = mutableOverrideProfile();
        profile.world = marks.mark1.world();
        profile.boundsMin = new int[]{minX, minY, minZ};
        profile.boundsMax = new int[]{maxX, maxY, maxZ};
        saveArenaOverrides(profile);
        return "Saved arena bounds in world '" + profile.world + "' min="
                + minX + "/" + minY + "/" + minZ
                + " max=" + maxX + "/" + maxY + "/" + maxZ;
    }

    public synchronized String adminArenaShow() {
        ArenaProfile profile = effectiveArenaProfile();
        return "Arena world=" + safe(profile.world)
                + " boundsMin=" + vec(profile.boundsMin)
                + " boundsMax=" + vec(profile.boundsMax)
                + " playerSpawn=" + vec(profile.playerSpawn)
                + " spawns=" + (profile.spawnPoints == null ? 0 : profile.spawnPoints.size());
    }

    public synchronized String getPrefix() {
        return effectiveConfig().messages.prefix;
    }

    public synchronized String getAdminPermission() {
        return effectiveConfig().ops.adminPermission;
    }

    public synchronized FightCavesConfig getConfig() {
        return effectiveConfig();
    }

    public synchronized ArenaProfile getArenaProfile() {
        return effectiveArenaProfile();
    }

    public String debugSummary() {
        FightCavesSessionService.StatusSnapshot status = sessionService.status();
        return "active=" + (status.activeRun() != null)
                + ", queue=" + status.queue().size();
    }

    private synchronized FightCavesConfig effectiveConfig() {
        FightCavesConfig cfg = configRepository.get();
        ArenaProfile profile = effectiveArenaProfile();
        applyArenaProfile(cfg, profile);
        return cfg;
    }

    private synchronized ArenaProfile effectiveArenaProfile() {
        FightCavesConfig cfg = configRepository.get();
        ArenaProfile base = ArenaProfile.fromConfig(cfg.arena);
        return ArenaProfile.merge(base, arenaOverrides);
    }

    private synchronized ArenaProfile mutableOverrideProfile() {
        if (arenaOverrides == null) {
            arenaOverrides = ArenaProfile.copy(effectiveArenaProfile());
        }
        return arenaOverrides;
    }

    private synchronized void saveArenaOverrides(ArenaProfile profile) {
        arenaOverrides = ArenaProfile.copy(profile);
        arenaProfileRepository.save(arenaOverrides);
    }

    private static void applyArenaProfile(FightCavesConfig cfg, ArenaProfile profile) {
        if (cfg == null || cfg.arena == null || profile == null) {
            return;
        }
        cfg.arena.world = profile.world;
        cfg.arena.boundsMin = profile.boundsMin == null ? cfg.arena.boundsMin : profile.boundsMin.clone();
        cfg.arena.boundsMax = profile.boundsMax == null ? cfg.arena.boundsMax : profile.boundsMax.clone();
        cfg.arena.playerSpawn = profile.playerSpawn == null ? cfg.arena.playerSpawn : profile.playerSpawn.clone();
        cfg.arena.templateWorld = profile.world;
        cfg.arena.templateMin = cfg.arena.boundsMin.clone();
        cfg.arena.templateMax = cfg.arena.boundsMax.clone();
        cfg.arena.activeOrigin = cfg.arena.playerSpawn.clone();
        cfg.arena.spawnPoints = new ArrayList<>();
        if (profile.spawnPoints != null) {
            for (SpawnPoint point : profile.spawnPoints) {
                if (point == null || point.id == null || point.id.isBlank()) continue;
                cfg.arena.spawnPoints.add(new SpawnPoint(point.id, point.pos == null ? null : point.pos.clone()));
            }
        }
        if (cfg.arena.spawnPoints.isEmpty()) {
            cfg.arena.spawnPoints.add(new SpawnPoint("default_1", cfg.arena.playerSpawn.clone()));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String vec(int[] pos) {
        if (pos == null || pos.length < 3) return "?/?/?";
        return pos[0] + "/" + pos[1] + "/" + pos[2];
    }

    private static String formatPos(FightCavesCommandContext.PositionSnapshot position) {
        return position.world() + " " + position.blockX() + "/" + position.blockY() + "/" + position.blockZ();
    }
}
