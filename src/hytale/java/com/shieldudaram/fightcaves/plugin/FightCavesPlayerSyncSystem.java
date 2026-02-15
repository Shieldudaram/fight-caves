package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.shieldudaram.fightcaves.arena.PlayerReturnSnapshot;

public final class FightCavesPlayerSyncSystem extends EntityTickingSystem<EntityStore> {

    private final Query<EntityStore> query = Query.and(Player.getComponentType(), PlayerRef.getComponentType());

    private final FightCavesTeleportService teleportService;
    private final FightCavesPlayerStateService playerStateService;
    private final FightCavesHudService hudService;

    public FightCavesPlayerSyncSystem(FightCavesTeleportService teleportService,
                                      FightCavesPlayerStateService playerStateService,
                                      FightCavesHudService hudService) {
        this.teleportService = teleportService;
        this.playerStateService = playerStateService;
        this.hudService = hudService;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt,
                     int entityId,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        Holder<EntityStore> holder;
        try {
            holder = EntityUtils.toHolder(entityId, chunk);
        } catch (Throwable ignored) {
            return;
        }

        Player player = holder.getComponent(Player.getComponentType());
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        if (player == null || playerRef == null || playerRef.getUuid() == null) {
            return;
        }

        String uuid = playerRef.getUuid().toString();
        var transform = playerRef.getTransform();
        if (transform != null && transform.getPosition() != null && player.getWorld() != null) {
            PlayerReturnSnapshot snapshot = new PlayerReturnSnapshot();
            snapshot.world = player.getWorld().getName();
            snapshot.x = transform.getPosition().getX();
            snapshot.y = transform.getPosition().getY();
            snapshot.z = transform.getPosition().getZ();
            snapshot.pitch = 0f;
            snapshot.yaw = 0f;
            snapshot.roll = 0f;
            playerStateService.update(uuid, snapshot);
        }

        FightCavesTeleportService.PendingTeleport pending = teleportService.poll(uuid);
        if (pending != null) {
            Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
            scheduleTeleport(player.getWorld(), store, ref, pending);
        }

        hudService.renderForPlayer(uuid, player, playerRef);
    }

    private void scheduleTeleport(World currentWorld,
                                  Store<EntityStore> store,
                                  Ref<EntityStore> ref,
                                  FightCavesTeleportService.PendingTeleport pending) {
        if (currentWorld == null || store == null || ref == null || !ref.isValid() || pending == null) {
            return;
        }

        World targetWorld = Universe.get().getWorld(pending.worldName());
        if (targetWorld == null) {
            return;
        }

        try {
            currentWorld.execute(() -> {
                try {
                    store.putComponent(ref, Teleport.getComponentType(), new Teleport(
                            targetWorld,
                            new Vector3d(pending.x(), pending.y(), pending.z()),
                            new Vector3f(pending.pitch(), pending.yaw(), pending.roll())
                    ));
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
