package com.shieldudaram.fightcaves;

import com.shieldudaram.fightcaves.data.RewardClaim;
import com.shieldudaram.fightcaves.rewards.RewardService;
import com.shieldudaram.fightcaves.rewards.RewardTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardServiceTest {

    @Test
    void successAlwaysGrantsCompletionReward() {
        RewardTable table = new RewardTable();
        table.completion.rewardId = "fight_caves_token";
        table.completion.amount = 25;

        RewardService service = new RewardService();
        List<RewardClaim> rewards = service.generateRewards(true, 63, 63, table, "run-123", 1000L);

        assertFalse(rewards.isEmpty());
        assertTrue(rewards.stream().anyMatch(claim -> "fight_caves_token".equals(claim.rewardId) && claim.amount == 25));
    }

    @Test
    void failureGrantsTierRewardWhenConfigured() {
        RewardTable table = new RewardTable();
        RewardTable.PartialTier tier20 = new RewardTable.PartialTier();
        tier20.minWave = 20;
        tier20.rewardId = "fight_caves_token";
        tier20.amount = 5;

        RewardTable.PartialTier tier40 = new RewardTable.PartialTier();
        tier40.minWave = 40;
        tier40.rewardId = "fight_caves_token";
        tier40.amount = 12;

        table.partialFailure = java.util.List.of(tier20, tier40);

        RewardService service = new RewardService();
        List<RewardClaim> rewards = service.generateRewards(false, 41, 63, table, "run-456", 2000L);

        assertTrue(rewards.stream().anyMatch(claim -> "fight_caves_token".equals(claim.rewardId) && claim.amount == 12));
    }
}
