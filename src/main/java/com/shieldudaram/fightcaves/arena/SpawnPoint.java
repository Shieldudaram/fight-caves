package com.shieldudaram.fightcaves.arena;

public final class SpawnPoint {
    public String id;
    public int[] pos = new int[]{0, 0, 0};

    public SpawnPoint() {
    }

    public SpawnPoint(String id, int[] pos) {
        this.id = id;
        this.pos = pos == null ? new int[]{0, 0, 0} : pos;
    }
}
