package com.shieldudaram.fightcaves.session;

import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.content.EnemyArchetype;
import com.shieldudaram.fightcaves.content.LoadedContent;
import com.shieldudaram.fightcaves.content.WaveDefinition;

public final class WaveEngine {

    public long computeWaveDurationMillis(LoadedContent content, FightCavesConfig config, int waveNumber) {
        if (content == null || waveNumber <= 0 || waveNumber > content.totalWaves()) {
            return 20_000L;
        }

        WaveDefinition wave = content.waves().get(waveNumber - 1);
        long base = 14_000L + (waveNumber * 260L);

        double weight = 0d;
        for (WaveDefinition.WaveSpawn spawn : wave.spawns) {
            if (spawn == null) continue;
            EnemyArchetype enemy = content.enemyById().get(spawn.enemyId);
            double enemyWeight = enemy == null ? 1.0d : Math.max(0.5d, enemy.difficultyWeight);
            weight += enemyWeight * Math.max(1, spawn.count);
        }

        long weighted = (long) Math.round(weight * 920L);
        long duration = base + weighted;

        if (wave.bossWave) {
            duration += 30_000L;
        }

        // Cap duration so the full 63-wave run stays in the 35-50 minute target with prep windows.
        long min = 12_000L;
        long max = 58_000L;
        if (duration < min) duration = min;
        if (duration > max) duration = max;

        if (config != null && config.combat != null) {
            if ("moderately_strict".equalsIgnoreCase(config.combat.timingStrictness)) {
                return duration;
            }
            if ("very_strict".equalsIgnoreCase(config.combat.timingStrictness)) {
                return Math.max(10_000L, duration - 3_500L);
            }
            if ("lenient".equalsIgnoreCase(config.combat.timingStrictness)) {
                return duration + 4_000L;
            }
        }

        return duration;
    }
}
