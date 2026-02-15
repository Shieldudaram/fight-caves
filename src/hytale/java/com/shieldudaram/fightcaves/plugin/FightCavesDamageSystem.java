package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.shieldudaram.fightcaves.FightCavesRuntime;
import com.shieldudaram.fightcaves.combat.AttackType;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

public final class FightCavesDamageSystem extends DamageEventSystem {

    private final FightCavesRuntime runtime;

    public FightCavesDamageSystem(FightCavesRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void handle(int index,
                       @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk,
                       @NonNullDecl Store<EntityStore> store,
                       @NonNullDecl CommandBuffer<EntityStore> commandBuffer,
                       @NonNullDecl Damage damage) {
        if (damage.isCancelled()) {
            return;
        }

        Ref<EntityStore> victimRef = archetypeChunk.getReferenceTo(index);
        PlayerRef victimPlayerRef = store.getComponent(victimRef, PlayerRef.getComponentType());
        if (victimPlayerRef == null || victimPlayerRef.getUuid() == null) {
            return;
        }

        String playerId = victimPlayerRef.getUuid().toString();
        if (!runtime.isActiveRunner(playerId)) {
            return;
        }

        AttackType attackType = inferAttackType(damage);
        if (attackType == null) {
            attackType = AttackType.MELEE;
        }

        float originalDamage = damage.getAmount();
        float multiplier = runtime.resolveDamageMultiplier(playerId, attackType);
        float scaledDamage = Math.max(0f, originalDamage * multiplier);
        damage.setAmount(scaledDamage);

        try {
            EntityStatMap stats = store.getComponent(victimRef, EntityStatMap.getComponentType());
            if (stats == null) {
                return;
            }
            EntityStatValue healthStat = stats.get(DefaultEntityStatTypes.getHealth());
            float health = (healthStat == null) ? 0f : healthStat.get();
            if (health <= 0f) {
                return;
            }

            if ((health - scaledDamage) <= 0.0001f) {
                damage.setCancelled(true);
                runtime.leaveRun(playerId, "death");
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @NonNullDecl
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }

    private static AttackType inferAttackType(Damage damage) {
        if (damage == null) {
            return AttackType.MELEE;
        }

        if (damage.getSource() instanceof Damage.ProjectileSource) {
            return AttackType.RANGED;
        }

        DamageCause cause = damage.getCause();
        if (cause == null || cause.getId() == null) {
            return AttackType.MELEE;
        }

        String id = cause.getId().toLowerCase();
        if (id.contains("projectile") || id.contains("ranged")) {
            return AttackType.RANGED;
        }
        if (id.contains("magic") || id.contains("arcane")) {
            return AttackType.MAGIC;
        }

        return AttackType.MELEE;
    }
}
