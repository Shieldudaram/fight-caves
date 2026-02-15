package com.shieldudaram.fightcaves.content;

import com.shieldudaram.fightcaves.rewards.RewardTable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoadedContent {
    private final WaveCatalog waveCatalog;
    private final Map<String, EnemyArchetype> enemyById;
    private final BossDefinition boss;
    private final RewardTable rewards;

    public LoadedContent(WaveCatalog waveCatalog,
                         Map<String, EnemyArchetype> enemyById,
                         BossDefinition boss,
                         RewardTable rewards) {
        this.waveCatalog = waveCatalog;
        this.enemyById = Collections.unmodifiableMap(new LinkedHashMap<>(enemyById));
        this.boss = boss;
        this.rewards = rewards;
    }

    public WaveCatalog waveCatalog() {
        return waveCatalog;
    }

    public Map<String, EnemyArchetype> enemyById() {
        return enemyById;
    }

    public BossDefinition boss() {
        return boss;
    }

    public RewardTable rewards() {
        return rewards;
    }

    public List<WaveDefinition> waves() {
        return waveCatalog.waves;
    }

    public int totalWaves() {
        return waveCatalog.totalWaves;
    }
}
