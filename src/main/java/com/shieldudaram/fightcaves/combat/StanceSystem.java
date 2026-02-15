package com.shieldudaram.fightcaves.combat;

import com.shieldudaram.fightcaves.config.FightCavesConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StanceSystem {
    private final Map<String, StanceType> byPlayerId = new ConcurrentHashMap<>();

    public StanceType getStance(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return StanceType.WARD_RANGED;
        }
        return byPlayerId.getOrDefault(playerId, StanceType.WARD_RANGED);
    }

    public boolean setStance(String playerId, String stanceRaw) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }
        StanceType stance = StanceType.fromString(stanceRaw);
        if (stance == null) {
            return false;
        }
        byPlayerId.put(playerId, stance);
        return true;
    }

    public float damageMultiplier(AttackType attackType, StanceType stance, FightCavesConfig config) {
        if (attackType == null || stance == null || config == null || config.combat == null) {
            return 1.0f;
        }
        if (stance.blocks() == attackType) {
            return config.combat.correctStanceDamageMultiplier;
        }
        return config.combat.wrongStanceDamageMultiplier;
    }

    public float damageMultiplier(String playerId, AttackType attackType, FightCavesConfig config) {
        return damageMultiplier(attackType, getStance(playerId), config);
    }

    public void clear(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return;
        }
        byPlayerId.remove(playerId);
    }
}
