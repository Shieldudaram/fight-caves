package com.shieldudaram.fightcaves.rewards;

import com.shieldudaram.fightcaves.data.RewardClaim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class RewardService {

    public List<RewardClaim> generateRewards(boolean success,
                                             int highestWave,
                                             int totalWaves,
                                             RewardTable rewardTable,
                                             String runId,
                                             long timestampMs) {
        if (rewardTable == null) {
            return List.of();
        }

        ArrayList<RewardClaim> rewards = new ArrayList<>();
        if (success) {
            rewards.add(new RewardClaim(
                    rewardTable.completion.rewardId,
                    Math.max(1, rewardTable.completion.amount),
                    timestampMs
            ));

            long seed = Objects.hash(runId, highestWave, totalWaves, rewardTable.version);
            Random random = new Random(seed);
            for (RewardTable.UniqueDrop uniqueDrop : rewardTable.uniqueDrops) {
                if (uniqueDrop == null || uniqueDrop.rewardId == null || uniqueDrop.rewardId.isBlank()) {
                    continue;
                }
                if (random.nextDouble() <= uniqueDrop.chance) {
                    rewards.add(new RewardClaim(uniqueDrop.rewardId, Math.max(1, uniqueDrop.amount), timestampMs));
                }
            }
            return rewards;
        }

        if (rewardTable.partialFailure == null || rewardTable.partialFailure.isEmpty()) {
            return rewards;
        }

        rewardTable.partialFailure
                .stream()
                .filter(Objects::nonNull)
                .filter(tier -> highestWave >= tier.minWave)
                .max(Comparator.comparingInt(tier -> tier.minWave))
                .ifPresent(tier -> {
                    if (tier.rewardId != null && !tier.rewardId.isBlank() && tier.amount > 0) {
                        rewards.add(new RewardClaim(tier.rewardId, tier.amount, timestampMs));
                    }
                });

        return rewards;
    }
}
