package com.shieldudaram.fightcaves.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.shieldudaram.fightcaves.FightCavesRuntime;
import com.shieldudaram.fightcaves.config.FightCavesConfig;
import com.shieldudaram.fightcaves.session.FightCavesUiAdapter;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FightCavesHudService implements FightCavesUiAdapter {
    private static final String HUD_SLOT_ID = "FightCaves_Wave";

    private record WaveState(String runId,
                             String playerId,
                             int wave,
                             int totalWaves,
                             String phase,
                             int enemiesRemaining,
                             boolean bossWave) {
    }

    private final FightCavesRuntime runtime;
    private final FightCavesMultipleHudBridge multipleHudBridge;
    private final HytaleLogger logger;
    private final AtomicBoolean runtimeDisableLogged = new AtomicBoolean(false);

    private final Map<String, WaveState> stateByPlayer = new ConcurrentHashMap<>();
    private final Map<String, FightCavesWaveHud> hudByPlayer = new ConcurrentHashMap<>();
    private final Set<String> shownHudByPlayer = ConcurrentHashMap.newKeySet();
    private final Map<String, String> lastRenderedKeyByPlayer = new ConcurrentHashMap<>();
    private final Map<String, ArrayDeque<String>> pendingMessagesByPlayer = new ConcurrentHashMap<>();

    private volatile boolean hudRenderingEnabled;

    public FightCavesHudService(FightCavesRuntime runtime,
                                FightCavesMultipleHudBridge multipleHudBridge,
                                HytaleLogger logger,
                                boolean hudRenderingEnabled) {
        this.runtime = runtime;
        this.multipleHudBridge = multipleHudBridge;
        this.logger = logger;
        this.hudRenderingEnabled = hudRenderingEnabled;
    }

    @Override
    public void onRunStarted(String runId, String playerId, String playerName, int totalWaves) {
        stateByPlayer.put(playerId, new WaveState(runId, playerId, 0, totalWaves, "prep", 0, false));
        lastRenderedKeyByPlayer.remove(playerId);
        maybeQueueMessage(playerId, "Run started. Prepare for wave 1.");
    }

    @Override
    public void onWaveChanged(String runId,
                              String playerId,
                              int waveNumber,
                              int totalWaves,
                              String phase,
                              int enemiesRemaining,
                              boolean bossWave) {
        WaveState previous = stateByPlayer.get(playerId);
        WaveState current = new WaveState(runId, playerId, waveNumber, totalWaves, phase, enemiesRemaining, bossWave);
        stateByPlayer.put(playerId, current);

        FightCavesConfig config = runtime.getConfig();
        if (!config.ui.chatFallbackOnWaveChange) {
            return;
        }

        boolean waveChanged = previous == null || previous.wave != waveNumber;
        boolean phaseChanged = previous == null || !safe(previous.phase).equalsIgnoreCase(safe(phase));
        if (!waveChanged && !phaseChanged) {
            return;
        }

        if ("in_wave".equalsIgnoreCase(phase)) {
            String text = "Wave " + Math.max(1, waveNumber) + " started.";
            if (bossWave) text += " Final wave.";
            maybeQueueMessage(playerId, text);
            return;
        }
        if ("prep".equalsIgnoreCase(phase) && waveNumber > 0) {
            maybeQueueMessage(playerId, "Wave " + waveNumber + " cleared.");
        }
    }

    @Override
    public void onRunEnded(String runId,
                           String playerId,
                           String playerName,
                           boolean success,
                           String reason,
                           int highestWave,
                           int totalWaves) {
        stateByPlayer.remove(playerId);
        lastRenderedKeyByPlayer.remove(playerId);
        if (success) {
            maybeQueueMessage(playerId, "Run complete. Wave " + totalWaves + " cleared.");
        } else {
            maybeQueueMessage(playerId, "Run failed at wave " + highestWave + ". Reason: " + reason);
        }
    }

    public void onPlayerDisconnect(String playerId) {
        if (playerId == null || playerId.isBlank()) return;
        stateByPlayer.remove(playerId);
        shownHudByPlayer.remove(playerId);
        lastRenderedKeyByPlayer.remove(playerId);
        pendingMessagesByPlayer.remove(playerId);
        multipleHudBridge.clearWarnedPlayer(playerId);
    }

    public void renderForPlayer(String uuid, Player player, PlayerRef playerRef) {
        if (uuid == null || uuid.isBlank() || player == null || playerRef == null) return;

        drainMessages(uuid, player);

        WaveState state = stateByPlayer.get(uuid);
        if (state == null) {
            hide(uuid, player, playerRef);
            return;
        }

        FightCavesConfig config = runtime.getConfig();
        if (!config.ui.waveHudEnabled) {
            hide(uuid, player, playerRef);
            return;
        }

        String mode = safe(config.ui.waveHudMode).toLowerCase();
        if (!"multiplehud_optional".equals(mode)) {
            hide(uuid, player, playerRef);
            return;
        }

        if (!hudRenderingEnabled) {
            hide(uuid, player, playerRef);
            return;
        }

        String desiredKey = "wave=" + state.wave + "|total=" + state.totalWaves;
        String previousKey = lastRenderedKeyByPlayer.get(uuid);
        boolean currentlyShown = shownHudByPlayer.contains(uuid);
        if (desiredKey.equals(previousKey) && currentlyShown) {
            return;
        }

        FightCavesWaveHud hud = hudByPlayer.computeIfAbsent(uuid, ignored -> new FightCavesWaveHud(playerRef));
        int shownWave = Math.max(1, state.wave);
        hud.show("Wave " + shownWave + " / " + Math.max(1, state.totalWaves));

        boolean applied = multipleHudBridge.setCustomHud(player, playerRef, HUD_SLOT_ID, hud);
        if (applied) {
            shownHudByPlayer.add(uuid);
            lastRenderedKeyByPlayer.put(uuid, desiredKey);
            return;
        }

        if (multipleHudBridge.isRuntimeFailed()) {
            disableHudRendering("MultipleHUD bridge runtime failure while applying wave HUD.");
        }
    }

    private void hide(String uuid, Player player, PlayerRef playerRef) {
        FightCavesWaveHud hud = hudByPlayer.get(uuid);
        if (hud != null && hud.isVisible()) {
            hud.hide();
        }
        lastRenderedKeyByPlayer.remove(uuid);

        if (!shownHudByPlayer.remove(uuid)) {
            return;
        }
        if (!hudRenderingEnabled) {
            return;
        }

        boolean hidden = multipleHudBridge.hideCustomHud(player, playerRef, HUD_SLOT_ID);
        if (!hidden && multipleHudBridge.isRuntimeFailed()) {
            disableHudRendering("MultipleHUD bridge runtime failure while hiding wave HUD.");
        }
    }

    private void disableHudRendering(String reason) {
        if (!hudRenderingEnabled) return;
        hudRenderingEnabled = false;
        shownHudByPlayer.clear();
        lastRenderedKeyByPlayer.clear();
        if (runtimeDisableLogged.compareAndSet(false, true)) {
            logger.atWarning().log("[FightCaves-HUD] %s HUD rendering disabled for this session.", reason);
        }
    }

    private void maybeQueueMessage(String playerId, String message) {
        if (playerId == null || playerId.isBlank() || message == null || message.isBlank()) return;
        pendingMessagesByPlayer.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).addLast(message);
    }

    private void drainMessages(String playerId, Player player) {
        ArrayDeque<String> queue = pendingMessagesByPlayer.get(playerId);
        if (queue == null || queue.isEmpty()) return;
        String prefix = runtime.getPrefix() + " ";
        while (!queue.isEmpty()) {
            String msg = queue.pollFirst();
            if (msg == null || msg.isBlank()) continue;
            try {
                player.sendMessage(Message.raw(prefix + msg));
            } catch (Throwable ignored) {
            }
        }
        if (queue.isEmpty()) {
            pendingMessagesByPlayer.remove(playerId, queue);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
