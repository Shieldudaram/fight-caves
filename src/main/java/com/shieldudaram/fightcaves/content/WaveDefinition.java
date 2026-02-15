package com.shieldudaram.fightcaves.content;

import java.util.ArrayList;
import java.util.List;

public final class WaveDefinition {

    public int wave;
    public boolean bossWave;
    public List<WaveSpawn> spawns = new ArrayList<>();

    public static final class WaveSpawn {
        public String enemyId;
        public int count;

        public WaveSpawn() {
        }

        public WaveSpawn(String enemyId, int count) {
            this.enemyId = enemyId;
            this.count = count;
        }
    }
}
