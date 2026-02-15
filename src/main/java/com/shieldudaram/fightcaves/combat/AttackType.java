package com.shieldudaram.fightcaves.combat;

import java.util.Locale;

public enum AttackType {
    MELEE,
    RANGED,
    MAGIC;

    public static AttackType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "melee" -> MELEE;
            case "ranged" -> RANGED;
            case "magic" -> MAGIC;
            default -> null;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
