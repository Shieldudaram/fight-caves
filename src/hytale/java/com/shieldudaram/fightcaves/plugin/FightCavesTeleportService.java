package com.shieldudaram.fightcaves.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FightCavesTeleportService {

    public record PendingTeleport(String worldName,
                                  double x,
                                  double y,
                                  double z,
                                  float pitch,
                                  float yaw,
                                  float roll) {
    }

    private final Map<String, PendingTeleport> pendingByUuid = new ConcurrentHashMap<>();

    public void queueTeleport(String uuid,
                              String worldName,
                              double x,
                              double y,
                              double z,
                              float pitch,
                              float yaw,
                              float roll) {
        if (uuid == null || uuid.isBlank()) return;
        if (worldName == null || worldName.isBlank()) return;
        pendingByUuid.put(uuid, new PendingTeleport(worldName, x, y, z, pitch, yaw, roll));
    }

    public PendingTeleport poll(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        return pendingByUuid.remove(uuid);
    }
}
