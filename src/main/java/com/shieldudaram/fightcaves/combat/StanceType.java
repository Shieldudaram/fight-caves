package com.shieldudaram.fightcaves.combat;

import java.util.Locale;

public enum StanceType {
    WARD_MELEE(AttackType.MELEE),
    WARD_RANGED(AttackType.RANGED),
    WARD_MAGIC(AttackType.MAGIC);

    private final AttackType blocks;

    StanceType(AttackType blocks) {
        this.blocks = blocks;
    }

    public AttackType blocks() {
        return blocks;
    }

    public static StanceType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "melee", "ward_melee" -> WARD_MELEE;
            case "ranged", "ward_ranged" -> WARD_RANGED;
            case "magic", "ward_magic" -> WARD_MAGIC;
            default -> null;
        };
    }

    public String id() {
        return blocks.id();
    }
}
