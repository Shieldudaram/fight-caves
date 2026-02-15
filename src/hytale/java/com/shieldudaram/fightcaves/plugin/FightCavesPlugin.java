package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.shieldudaram.fightcaves.FightCavesRuntime;
import com.shieldudaram.fightcaves.ui.FightCavesUiAssetContract;

import java.util.List;

import javax.annotation.Nonnull;

public final class FightCavesPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private FightCavesRuntime runtime;
    private FightCavesHudService hudService;
    private FightCavesPlayerStateService playerStateService;

    public FightCavesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Loaded %s v%s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up FightCavesPlugin...");
        try {
            this.runtime = new FightCavesRuntime(this.getDataDirectory());
            this.playerStateService = new FightCavesPlayerStateService();
            FightCavesTeleportService teleportService = new FightCavesTeleportService();

            FightCavesUiAssetContract.ValidationResult uiValidation =
                    FightCavesUiAssetContract.validate(this.getClass().getClassLoader());
            if (!uiValidation.manifestIncludesAssetPack()) {
                LOGGER.atSevere().log("[FightCaves-UI] manifest IncludesAssetPack=false. Fight Caves HUD assets cannot load.");
            }
            List<String> missingUiDocs = uiValidation.missingUiDocuments();
            if (!missingUiDocs.isEmpty()) {
                LOGGER.atSevere().log("[FightCaves-UI] Missing required UI docs in plugin assets: %s", String.join(", ", missingUiDocs));
            }
            boolean uiAssetsReady = uiValidation.ready();
            if (!uiAssetsReady) {
                LOGGER.atWarning().log("[FightCaves-UI] Wave HUD disabled until UI asset contract is fixed.");
            }

            FightCavesMultipleHudBridge hudBridge = new FightCavesMultipleHudBridge(LOGGER);
            boolean multipleHudReady = true;
            try {
                hudBridge.requireReadyOrThrow();
            } catch (IllegalStateException exception) {
                multipleHudReady = false;
                LOGGER.atWarning().withCause(exception).log("[FightCaves-HUD] MultipleHUD integration failed. HUD rendering disabled for this session.");
            }

            boolean hudRenderingEnabled = uiAssetsReady && multipleHudReady;
            if (!hudRenderingEnabled) {
                LOGGER.atWarning().log("[FightCaves-HUD] HUD rendering disabled. uiAssetsReady=%s multipleHudReady=%s",
                        uiAssetsReady,
                        multipleHudReady);
            }

            this.hudService = new FightCavesHudService(runtime, hudBridge, LOGGER, hudRenderingEnabled);
            FightCavesEncounterService encounterService = new FightCavesEncounterService(
                    runtime,
                    teleportService,
                    playerStateService,
                    LOGGER
            );
            runtime.bindPlatformAdapters(encounterService, hudService);

            this.getCommandRegistry().registerCommand(new FightCavesCommand(runtime));
            this.getEntityStoreRegistry().registerSystem(new FightCavesTickSystem(runtime));
            this.getEntityStoreRegistry().registerSystem(new FightCavesDamageSystem(runtime));
            this.getEntityStoreRegistry().registerSystem(new FightCavesNpcLifecycleSystem(runtime));
            this.getEntityStoreRegistry().registerSystem(new FightCavesPlayerSyncSystem(teleportService, playerStateService, hudService));

            this.getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
            this.getEventRegistry().registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);

            LOGGER.atInfo().log("[FightCaves] Plugin setup complete.");
        } catch (RuntimeException exception) {
            LOGGER.atWarning().withCause(exception).log("[FightCaves] Failed to initialize plugin.");
            throw exception;
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        if (event == null || runtime == null || event.getPlayerRef() == null || event.getPlayerRef().getUuid() == null) {
            return;
        }
        String playerId = event.getPlayerRef().getUuid().toString();
        runtime.onPlayerDisconnect(playerId);
        if (hudService != null) {
            hudService.onPlayerDisconnect(playerId);
        }
        if (playerStateService != null) {
            playerStateService.remove(playerId);
        }
    }

    private void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || runtime == null) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (event.getActionType() != InteractionType.Use) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null || player.getPlayerRef() == null || player.getPlayerRef().getUuid() == null) {
            return;
        }

        String npcTypeId = null;
        Entity targetEntity = event.getTargetEntity();
        if (targetEntity instanceof INonPlayerCharacter npc) {
            npcTypeId = npc.getNPCTypeId();
        }

        ItemStack held = event.getItemInHand();
        String heldItemId = (held == null || held.isEmpty()) ? null : held.getItemId();

        boolean accepted = runtime.handleNpcEntry(
                player.getPlayerRef().getUuid().toString(),
                player.getDisplayName(),
                npcTypeId,
                heldItemId
        );

        if (accepted) {
            player.sendMessage(Message.raw(runtime.getPrefix() + " Entry accepted. Use /fightcaves status."));
        }
    }
}
