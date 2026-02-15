package com.shieldudaram.fightcaves.rewards;

import java.util.ArrayList;
import java.util.List;

public final class RewardTable {
    public int version = 1;
    public Completion completion = new Completion();
    public List<UniqueDrop> uniqueDrops = new ArrayList<>();
    public List<PartialTier> partialFailure = new ArrayList<>();

    public static final class Completion {
        public String rewardId = "fight_caves_token";
        public int amount = 25;
    }

    public static final class UniqueDrop {
        public String rewardId;
        public int amount = 1;
        public double chance = 0.02;
    }

    public static final class PartialTier {
        public int minWave;
        public String rewardId;
        public int amount;
    }
}
