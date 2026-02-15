package com.shieldudaram.fightcaves.data;

public final class RewardClaim {
    public String rewardId;
    public int amount;
    public long createdAt;

    public RewardClaim() {
    }

    public RewardClaim(String rewardId, int amount, long createdAt) {
        this.rewardId = rewardId;
        this.amount = amount;
        this.createdAt = createdAt;
    }
}
