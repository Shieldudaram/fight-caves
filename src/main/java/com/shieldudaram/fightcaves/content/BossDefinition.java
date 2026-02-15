package com.shieldudaram.fightcaves.content;

import java.util.ArrayList;
import java.util.List;

public final class BossDefinition {
    public int version = 1;
    public String id;
    public String displayName;
    public int finalWave = 63;
    public List<String> attackCycle = new ArrayList<>();
    public int healerCount = 4;
    public boolean healersAttackPlayer = true;
}
