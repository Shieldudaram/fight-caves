package com.shieldudaram.fightcaves.plugin;

import com.shieldudaram.fightcaves.arena.PlayerReturnSnapshot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FightCavesPlayerStateService {
    private final Map<String, PlayerReturnSnapshot> latestByUuid = new ConcurrentHashMap<>();

    public void update(String uuid, PlayerReturnSnapshot snapshot) {
        if (uuid == null || uuid.isBlank() || snapshot == null || snapshot.world == null || snapshot.world.isBlank()) {
            return;
        }
        latestByUuid.put(uuid, copy(snapshot));
    }

    public PlayerReturnSnapshot getCopy(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        PlayerReturnSnapshot snapshot = latestByUuid.get(uuid);
        return snapshot == null ? null : copy(snapshot);
    }

    public void remove(String uuid) {
        if (uuid == null || uuid.isBlank()) return;
        latestByUuid.remove(uuid);
    }

    private static PlayerReturnSnapshot copy(PlayerReturnSnapshot src) {
        PlayerReturnSnapshot copy = new PlayerReturnSnapshot();
        copy.world = src.world;
        copy.x = src.x;
        copy.y = src.y;
        copy.z = src.z;
        copy.pitch = src.pitch;
        copy.yaw = src.yaw;
        copy.roll = src.roll;
        return copy;
    }
}
