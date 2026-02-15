package com.shieldudaram.fightcaves.content;

import com.shieldudaram.fightcaves.rewards.RewardTable;
import com.shieldudaram.fightcaves.util.JsonIo;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ContentRepository {
    private static final String CONTENT_ROOT_RESOURCE = "content/fightcaves";

    private final Object lock = new Object();
    private final Logger logger;
    private final Path contentRoot;

    private volatile LoadedContent cached;

    public ContentRepository(Path dataDirectory, Logger logger) {
        this.logger = Objects.requireNonNullElseGet(logger, () -> Logger.getLogger(ContentRepository.class.getName()));
        this.contentRoot = dataDirectory.resolve("content").resolve("fightcaves");
        reload();
    }

    public LoadedContent get() {
        LoadedContent current = cached;
        if (current != null) {
            return current;
        }
        reload();
        return cached;
    }

    public void reload() {
        synchronized (lock) {
            try {
                seedDefault("waves.json");
                seedDefault("enemies.json");
                seedDefault("boss.json");
                seedDefault("rewards.json");

                WaveCatalog waveCatalog = JsonIo.read(contentRoot.resolve("waves.json"), WaveCatalog.class);
                EnemyCatalog enemyCatalog = JsonIo.read(contentRoot.resolve("enemies.json"), EnemyCatalog.class);
                BossDefinition boss = JsonIo.read(contentRoot.resolve("boss.json"), BossDefinition.class);
                RewardTable rewards = JsonIo.read(contentRoot.resolve("rewards.json"), RewardTable.class);

                LoadedContent loaded = validateAndNormalize(waveCatalog, enemyCatalog, boss, rewards);
                cached = loaded;

                JsonIo.write(contentRoot.resolve("waves.json"), loaded.waveCatalog());
                JsonIo.write(contentRoot.resolve("enemies.json"), toCatalog(loaded.enemyById()));
                JsonIo.write(contentRoot.resolve("boss.json"), loaded.boss());
                JsonIo.write(contentRoot.resolve("rewards.json"), loaded.rewards());
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[FightCaves] Failed to load content; falling back to bundled defaults.", t);
                try {
                    restoreBundledDefaults();
                    WaveCatalog waveCatalog = JsonIo.read(contentRoot.resolve("waves.json"), WaveCatalog.class);
                    EnemyCatalog enemyCatalog = JsonIo.read(contentRoot.resolve("enemies.json"), EnemyCatalog.class);
                    BossDefinition boss = JsonIo.read(contentRoot.resolve("boss.json"), BossDefinition.class);
                    RewardTable rewards = JsonIo.read(contentRoot.resolve("rewards.json"), RewardTable.class);
                    cached = validateAndNormalize(waveCatalog, enemyCatalog, boss, rewards);
                } catch (Throwable nested) {
                    throw new IllegalStateException("Could not recover Fight Caves content", nested);
                }
            }
        }
    }

    public Path getContentRoot() {
        return contentRoot;
    }

    private LoadedContent validateAndNormalize(WaveCatalog waveCatalog,
                                               EnemyCatalog enemyCatalog,
                                               BossDefinition boss,
                                               RewardTable rewards) {
        if (waveCatalog == null) waveCatalog = new WaveCatalog();
        if (enemyCatalog == null) enemyCatalog = new EnemyCatalog();
        if (boss == null) boss = new BossDefinition();
        if (rewards == null) rewards = new RewardTable();

        if (waveCatalog.waves == null) waveCatalog.waves = new java.util.ArrayList<>();
        if (waveCatalog.totalWaves <= 0) waveCatalog.totalWaves = 63;

        if (enemyCatalog.enemies == null) enemyCatalog.enemies = new java.util.ArrayList<>();
        Map<String, EnemyArchetype> enemyById = new HashMap<>();
        for (EnemyArchetype enemy : enemyCatalog.enemies) {
            if (enemy == null || enemy.id == null || enemy.id.isBlank()) {
                continue;
            }
            if (enemy.attackStyle == null || enemy.attackStyle.isBlank()) {
                enemy.attackStyle = "melee";
            }
            if (enemy.maxHealth <= 0) enemy.maxHealth = 100;
            if (enemy.baseDamage <= 0f) enemy.baseDamage = 10f;
            if (enemy.difficultyWeight <= 0f) enemy.difficultyWeight = 1f;
            enemyById.put(enemy.id, enemy);
        }

        if (waveCatalog.waves.size() != waveCatalog.totalWaves) {
            throw new IllegalStateException("Expected " + waveCatalog.totalWaves + " waves but found " + waveCatalog.waves.size());
        }

        for (int i = 0; i < waveCatalog.waves.size(); i++) {
            WaveDefinition wave = waveCatalog.waves.get(i);
            int expected = i + 1;
            if (wave == null) {
                throw new IllegalStateException("Wave " + expected + " is null");
            }
            if (wave.wave != expected) {
                throw new IllegalStateException("Wave numbering is not sequential at wave " + expected);
            }
            if (wave.spawns == null || wave.spawns.isEmpty()) {
                throw new IllegalStateException("Wave " + expected + " has no spawns");
            }
            for (WaveDefinition.WaveSpawn spawn : wave.spawns) {
                if (spawn == null || spawn.enemyId == null || spawn.enemyId.isBlank()) {
                    throw new IllegalStateException("Wave " + expected + " has invalid spawn entry");
                }
                if (!enemyById.containsKey(spawn.enemyId)) {
                    throw new IllegalStateException("Wave " + expected + " references unknown enemy: " + spawn.enemyId);
                }
                if (spawn.count <= 0) {
                    throw new IllegalStateException("Wave " + expected + " has non-positive enemy count");
                }
            }
        }

        if (boss.id == null || boss.id.isBlank()) {
            boss.id = "molten_colossus";
        }
        if (boss.displayName == null || boss.displayName.isBlank()) {
            boss.displayName = "Molten Colossus";
        }
        if (boss.finalWave <= 0) {
            boss.finalWave = waveCatalog.totalWaves;
        }
        if (boss.attackCycle == null || boss.attackCycle.isEmpty()) {
            boss.attackCycle = new java.util.ArrayList<>(java.util.List.of("ranged", "magic", "melee"));
        }
        if (boss.healerCount < 0) boss.healerCount = 0;

        if (rewards.completion == null) rewards.completion = new RewardTable.Completion();
        if (rewards.completion.rewardId == null || rewards.completion.rewardId.isBlank()) {
            rewards.completion.rewardId = "fight_caves_token";
        }
        if (rewards.completion.amount <= 0) rewards.completion.amount = 25;
        if (rewards.uniqueDrops == null) rewards.uniqueDrops = new java.util.ArrayList<>();
        if (rewards.partialFailure == null) rewards.partialFailure = new java.util.ArrayList<>();

        for (RewardTable.UniqueDrop uniqueDrop : rewards.uniqueDrops) {
            if (uniqueDrop == null) continue;
            if (uniqueDrop.amount <= 0) uniqueDrop.amount = 1;
            if (uniqueDrop.chance < 0d) uniqueDrop.chance = 0d;
            if (uniqueDrop.chance > 1d) uniqueDrop.chance = 1d;
        }

        for (RewardTable.PartialTier tier : rewards.partialFailure) {
            if (tier == null) continue;
            if (tier.minWave < 1) tier.minWave = 1;
            if (tier.amount < 0) tier.amount = 0;
        }

        return new LoadedContent(waveCatalog, enemyById, boss, rewards);
    }

    private EnemyCatalog toCatalog(Map<String, EnemyArchetype> enemyById) {
        EnemyCatalog catalog = new EnemyCatalog();
        catalog.enemies = new java.util.ArrayList<>(enemyById.values());
        return catalog;
    }

    private void restoreBundledDefaults() throws Exception {
        seedDefault("waves.json", true);
        seedDefault("enemies.json", true);
        seedDefault("boss.json", true);
        seedDefault("rewards.json", true);
    }

    private void seedDefault(String fileName) throws Exception {
        seedDefault(fileName, false);
    }

    private void seedDefault(String fileName, boolean force) throws Exception {
        Path destination = contentRoot.resolve(fileName);
        if (!force && Files.exists(destination)) {
            return;
        }
        Files.createDirectories(destination.getParent());
        String resourcePath = CONTENT_ROOT_RESOURCE + "/" + fileName;
        try (InputStream stream = ContentRepository.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled content resource: " + resourcePath);
            }
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
