package com.shieldudaram.fightcaves.config;

import com.shieldudaram.fightcaves.arena.SpawnPoint;
import com.shieldudaram.fightcaves.util.JsonIo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigRepository {
    private static final String DEFAULT_RESOURCE = "config/fight_caves_config.json";

    private final Object lock = new Object();
    private final Path configPath;
    private final Logger logger;

    private volatile FightCavesConfig cached;

    public ConfigRepository(Path dataDirectory, Logger logger) {
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(ConfigRepository.class.getName()));
        this.configPath = dataDirectory.resolve("config").resolve("fight_caves_config.json");
        reload();
    }

    public FightCavesConfig get() {
        FightCavesConfig current = cached;
        if (current != null) {
            return current;
        }
        FightCavesConfig defaults = normalize(new FightCavesConfig());
        cached = defaults;
        return defaults;
    }

    public void reload() {
        synchronized (lock) {
            try {
                seedDefaultIfMissing();
                FightCavesConfig loaded = JsonIo.read(configPath, FightCavesConfig.class);
                cached = normalize(loaded == null ? new FightCavesConfig() : loaded);
                save(cached);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to load config; using defaults.", t);
                FightCavesConfig fallback = normalize(new FightCavesConfig());
                cached = fallback;
                save(fallback);
            }
        }
    }

    public void saveCurrent() {
        save(get());
    }

    public void save(FightCavesConfig cfg) {
        if (cfg == null) {
            return;
        }
        synchronized (lock) {
            try {
                JsonIo.write(configPath, cfg);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to save config.", t);
            }
        }
    }

    public Path getConfigPath() {
        return configPath;
    }

    private void seedDefaultIfMissing() throws Exception {
        if (Files.exists(configPath)) {
            return;
        }
        Files.createDirectories(configPath.getParent());
        try (InputStream stream = ConfigRepository.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                JsonIo.write(configPath, new FightCavesConfig());
                return;
            }
            Files.copy(stream, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static FightCavesConfig normalize(FightCavesConfig cfg) {
        if (cfg == null) {
            cfg = new FightCavesConfig();
        }

        if (cfg.entry == null) cfg.entry = new FightCavesConfig.Entry();
        if (cfg.arena == null) cfg.arena = new FightCavesConfig.Arena();
        if (cfg.session == null) cfg.session = new FightCavesConfig.Session();
        if (cfg.combat == null) cfg.combat = new FightCavesConfig.Combat();
        if (cfg.rewards == null) cfg.rewards = new FightCavesConfig.Rewards();
        if (cfg.persistence == null) cfg.persistence = new FightCavesConfig.Persistence();
        if (cfg.npc == null) cfg.npc = new FightCavesConfig.Npc();
        if (cfg.messages == null) cfg.messages = new FightCavesConfig.Messages();
        if (cfg.ui == null) cfg.ui = new FightCavesConfig.Ui();
        if (cfg.ops == null) cfg.ops = new FightCavesConfig.Ops();

        if (cfg.arena.world == null || cfg.arena.world.isBlank()) {
            cfg.arena.world = (cfg.arena.templateWorld == null || cfg.arena.templateWorld.isBlank())
                    ? "world"
                    : cfg.arena.templateWorld;
        }
        cfg.arena.boundsMin = normalizeVec3(cfg.arena.boundsMin, cfg.arena.templateMin, new int[]{0, 64, 0});
        cfg.arena.boundsMax = normalizeVec3(cfg.arena.boundsMax, cfg.arena.templateMax, new int[]{32, 96, 32});
        cfg.arena.playerSpawn = normalizeVec3(cfg.arena.playerSpawn, cfg.arena.activeOrigin, new int[]{1024, 64, 1024});
        cfg.arena.spectatorSpawn = normalizeVec3(cfg.arena.spectatorSpawn, cfg.arena.playerSpawn, new int[]{1032, 68, 1032});
        if (cfg.arena.spawnPoints == null) {
            cfg.arena.spawnPoints = new ArrayList<>();
        }
        normalizeSpawnPoints(cfg.arena.spawnPoints, cfg.arena.playerSpawn);
        if (cfg.arena.spawnPoints.isEmpty()) {
            cfg.arena.spawnPoints = new ArrayList<>(List.of(new SpawnPoint("default_1", cfg.arena.playerSpawn.clone())));
        }

        if (cfg.session.maxConcurrentRuns <= 0) cfg.session.maxConcurrentRuns = 1;
        if (cfg.session.disconnectBehavior == null || cfg.session.disconnectBehavior.isBlank()) {
            cfg.session.disconnectBehavior = "fail_run";
        }
        if (cfg.session.queueLimit <= 0) cfg.session.queueLimit = 64;
        if (cfg.session.startGraceSeconds < 0) cfg.session.startGraceSeconds = 0;

        if (cfg.combat.attackTypes == null || cfg.combat.attackTypes.isEmpty()) {
            cfg.combat.attackTypes = new java.util.ArrayList<>(java.util.List.of("melee", "ranged", "magic"));
        }
        if (cfg.combat.preWavePrepSeconds < 0) cfg.combat.preWavePrepSeconds = 6;
        if (cfg.combat.correctStanceDamageMultiplier <= 0f) cfg.combat.correctStanceDamageMultiplier = 0.55f;
        if (cfg.combat.wrongStanceDamageMultiplier <= 0f) cfg.combat.wrongStanceDamageMultiplier = 1.00f;
        if (cfg.combat.waveAdvanceMode == null || cfg.combat.waveAdvanceMode.isBlank()) {
            cfg.combat.waveAdvanceMode = "kills_only";
        }

        if (cfg.rewards.farmControl == null || cfg.rewards.farmControl.isBlank()) cfg.rewards.farmControl = "none";
        if (cfg.rewards.completionTokenId == null || cfg.rewards.completionTokenId.isBlank()) {
            cfg.rewards.completionTokenId = "fight_caves_token";
        }

        if (cfg.persistence.playerStatsFile == null || cfg.persistence.playerStatsFile.isBlank()) {
            cfg.persistence.playerStatsFile = "fight_caves_player_stats.json";
        }
        if (cfg.persistence.runHistoryFile == null || cfg.persistence.runHistoryFile.isBlank()) {
            cfg.persistence.runHistoryFile = "fight_caves_run_history.jsonl";
        }
        if (cfg.persistence.claimsFile == null || cfg.persistence.claimsFile.isBlank()) {
            cfg.persistence.claimsFile = "fight_caves_claims.json";
        }
        if (cfg.persistence.activeRunMarkerFile == null || cfg.persistence.activeRunMarkerFile.isBlank()) {
            cfg.persistence.activeRunMarkerFile = "fight_caves_active_run.json";
        }

        if (cfg.messages.prefix == null || cfg.messages.prefix.isBlank()) cfg.messages.prefix = "[FightCaves]";
        if (cfg.messages.locale == null || cfg.messages.locale.isBlank()) cfg.messages.locale = "en_us";
        if (cfg.ui.waveHudMode == null || cfg.ui.waveHudMode.isBlank()) cfg.ui.waveHudMode = "multiplehud_optional";

        if (cfg.ops.adminPermission == null || cfg.ops.adminPermission.isBlank()) {
            cfg.ops.adminPermission = "fightcaves.admin";
        }

        return cfg;
    }

    private static int[] normalizeVec3(int[] primary, int[] secondary, int[] fallback) {
        if (isVec3(primary)) {
            return new int[]{primary[0], primary[1], primary[2]};
        }
        if (isVec3(secondary)) {
            return new int[]{secondary[0], secondary[1], secondary[2]};
        }
        if (isVec3(fallback)) {
            return new int[]{fallback[0], fallback[1], fallback[2]};
        }
        return new int[]{0, 64, 0};
    }

    private static boolean isVec3(int[] vec) {
        return vec != null && vec.length >= 3;
    }

    private static void normalizeSpawnPoints(List<SpawnPoint> points, int[] fallbackPos) {
        if (points == null) {
            return;
        }
        points.removeIf(point -> point == null || point.id == null || point.id.isBlank());
        for (SpawnPoint point : points) {
            if (point == null) continue;
            point.pos = normalizeVec3(point.pos, fallbackPos, new int[]{1024, 64, 1024});
        }
    }
}
